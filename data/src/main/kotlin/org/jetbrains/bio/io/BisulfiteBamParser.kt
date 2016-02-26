package org.jetbrains.bio.io

import com.google.common.collect.Iterators
import gnu.trove.list.array.TByteArrayList
import htsjdk.samtools.CigarOperator
import htsjdk.samtools.SAMFileHeader.SortOrder
import htsjdk.samtools.SAMRecord
import htsjdk.samtools.SamReader
import htsjdk.samtools.SamReaderFactory
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.awaitAll
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.ext.parallelStream
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.sequence.Nucleotide
import org.jetbrains.bio.genome.sequence.NucleotideSequence
import org.jetbrains.bio.genome.sequence.asNucleotideSequence
import org.jetbrains.bio.methylome.CytosineContext
import org.jetbrains.bio.methylome.Methylome
import org.jetbrains.bio.methylome.MethylomeBuilder
import org.jetbrains.bio.util.Progress
import java.io.Closeable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A parser for raw BS-seq alignments in BAM or CRAM formats.
 *
 * The only counts the reads for the strand with a cytosine. For example
 * the following
 *
 *   +5C
 *   |
 *   ACAGATCGG
 *   |
 *   -2C
 *
 * results in 5C count for the position being considered.
 *
 * @author Sergei Lebedev
 * @since 29/04/14
 */
object BisulfiteBamParser {
    fun parse(path: Path, genomeQuery: GenomeQuery): Methylome {
        // This is of course an upper bound, but it's better than nothing.
        var total = genomeQuery.get().parallelStream().mapToLong {
            var acc = 0L
            for (i in 0..it.length - 1) {
                val b = it.sequence.byteAt(i)
                if (b == Nucleotide.C.byte || b == Nucleotide.G.byte) {
                    acc++
                }
            }

            acc
        }.sum()

        val builder = Methylome.builder(genomeQuery)
        val progress = Progress.builder()
                .title(path.name)
                .period(10, TimeUnit.SECONDS)
                .incremental(total)

        val executor = Executors.newCachedThreadPool()
        executor.awaitAll(genomeQuery.get().map {
            Callable {
                val sequence = it.sequence.toString().asNucleotideSequence()
                parse(path, it, sequence, builder, progress)
            }
        })
        check(executor.shutdownNow().isEmpty())

        return builder.build()
    }

    fun parse(path: Path, chromosome: Chromosome, sequence: NucleotideSequence,
              builder: MethylomeBuilder, progress: Progress.Incremental) {
        getPiler(path, chromosome).use { piler ->
            loop@ while (piler.hasNext()) {
                val pc = piler.next()
                // Reference position is 1-based in SAM.
                val offset = pc.position - 1
                // XXX: Workaround for [KT-10031] java.lang.VerifyError: Bad local variable type
                //  > add 'strand' initialization before 'else -> error("...")'
                var strand: Strand = Strand.PLUS
                when (sequence.charAt(offset)) {
                // Skip records corresponding to non-cytosines.
                    'a', 't', 'n' -> continue@loop
                    'c' -> strand = Strand.PLUS
                    'g' -> strand = Strand.MINUS
                    else -> error("unexpected character in sequence '${sequence.charAt(offset)}'.")
                }

                val context = CytosineContext.determine(sequence, offset, strand)
                handleLocus(builder, chromosome, strand, offset, context, pc)
                progress.report()
            }
        }
    }

    private fun getPiler(path: Path, chromosome: Chromosome): SamPiler {
        // 'htsjdk' doesn't allow concurrent queries on 'BAMFileReader'
        // thus we have to re-create 'SamReader' for each chromosome.
        val samReader = SamReaderFactory.makeDefault()
                .referenceSource(WrappedReferenceSource(chromosome.name,
                                                        chromosome.sequence))
                .open(path.toFile())
        return SamPiler(samReader, chromosome)
    }

    private fun handleLocus(builder: MethylomeBuilder,
                            chromosome: Chromosome, strand: Strand,
                            offset: Int, context: CytosineContext?,
                            pc: PilerColumn) {
        var countA = 0
        var countT = 0
        var countC = 0
        var countG = 0
        if (strand.isPlus()) {
            for (i in 0..pc.size() - 1) {
                // The strand of the record should match the reference strand,
                // because otherwise we aren't looking at a cytosine.
                if (pc.getStrand(i).isMinus()) {
                    continue
                }

                when (pc.getReadBase(i).toChar()) {
                    'A' -> countA++
                    'T' -> countT++
                    '=',  // fall-through.
                    'C' -> countC++
                    'G' -> countG++
                }
            }
        } else {
            for (i in 0..pc.size() - 1) {
                if (pc.getStrand(i).isPlus()) {
                    continue
                }

                when (pc.getReadBase(i).toChar()) {
                    'A' -> countT++
                    'T' -> countA++
                    'C' -> countG++
                    '=',  // fall-through.
                    'G' -> countC++
                }
            }
        }

        val countATCG = countA + countT + countC + countG
        // XXX for the minus strand we add up the counts using
        // reverse-complementary nucleotides, so to get 'methylatedCount'
        // we need to use 'countC' and NOT 'countG'.
        builder.add(chromosome, strand, offset, context, countC, countATCG)
    }
}

/**
 * Piler piles up SAM and BAM records.
 *
 * See http://thefreedictionary.com/piler if you don't believe the KDoc.
 *
 * @author Sergei Lebedev
 * @since 04/07/14
 */
private class SamPiler(private val samReader: SamReader, chromosome: Chromosome) :
        Iterator<PilerColumn>, Closeable, AutoCloseable {

    /** An iterator for SAM records with the SAME reference index. */
    private val samIterator = Iterators.peekingIterator<SAMRecord>(
            samReader.query(chromosome.name, 0, 0, false))
    /** A queue for loci waiting for more SAM records coming.  */
    private val waitingQueue = ArrayList<PilerColumn>()
    /** A queue for completed loci, which won't get any more SAM records.  */
    private val completedQueue = ArrayDeque<PilerColumn>()

    init {
        val samHeader = samReader.fileHeader
        if (samHeader.sequenceDictionary.getSequence(chromosome.name) == null) {
            throw NoSuchElementException(chromosome.name)
        }

        val sortOrder = samHeader.sortOrder
        if (sortOrder == null || sortOrder == SortOrder.unsorted) {
            LOG.warn("SAM sort order is unspecified. " +
                     "Assuming SAM is coordinate sorted, but exceptions may occur if it is not.")
        } else if (sortOrder != SortOrder.coordinate) {
            throw IllegalArgumentException(
                    "Cannot operate on a SAM file that is not coordinate sorted.")
        }
    }

    override fun hasNext(): Boolean {
        prefetch()
        return completedQueue.isNotEmpty()
    }

    override fun next(): PilerColumn {
        check(completedQueue.isNotEmpty()) { "no data" }
        return completedQueue.removeFirst()
    }

    override fun close() = samReader.close()

    private fun prefetch() {
        while (completedQueue.isEmpty() && samIterator.hasNext()) {
            val record = samIterator.peek()
            if (record.readUnmappedFlag
                || record.isSecondaryOrSupplementary
                || record.duplicateReadFlag
                || record.mappingQuality == 0) {  // BWA multi-alignment evidence
                samIterator.next()
                continue
            }

            completeWaiting(record)
            samIterator.next()
            updateWaiting(record)
        }

        if (completedQueue.isEmpty() && !samIterator.hasNext()) {
            while (waitingQueue.isNotEmpty()) {
                val pc = waitingQueue.removeAt(0)
                if (pc.isNotEmpty()) {
                    completedQueue.addLast(pc)
                }
            }
        }
    }

    private fun completeWaiting(record: SAMRecord) {
        // Complete piled up columns preceding alignment start.
        val alignmentStart = record.alignmentStart
        while (waitingQueue.isNotEmpty()
               && waitingQueue[0].position < alignmentStart) {
            val pc = waitingQueue.removeAt(0)
            if (pc.isNotEmpty()) {
                completedQueue.addLast(pc)
            }
        }
    }

    private fun updateWaiting(record: SAMRecord) {
        val cigar = record.cigar ?: return

        val alignmentStart = record.alignmentStart
        var readBase = 1
        var refBase = alignmentStart
        for (c in 0..cigar.numCigarElements() - 1) {
            val e = cigar.getCigarElement(c)
            val length = e.length
            when (e.operator!!) {
                CigarOperator.H, CigarOperator.P -> {}  // ignore hard clips and pads
                CigarOperator.S -> readBase += length   // soft clip read bases
                CigarOperator.N -> refBase += length    // reference skip
                CigarOperator.D -> refBase += length
                CigarOperator.I -> readBase += length
                CigarOperator.M, CigarOperator.EQ, CigarOperator.X -> {
                    for (i in 0..length - 1) {
                        // 1-based reference position that the current base aligns to
                        val refPos = refBase + i

                        // 0-based offset from the aligned position of the first base in
                        // the read to the aligned position of the current base.
                        val refOffset = refPos - alignmentStart

                        // Ensure there are columns up to and including this position.
                        waitingQueue.ensureCapacity(refOffset - waitingQueue.size)
                        for (j in waitingQueue.size..refOffset) {
                            waitingQueue.add(PilerColumn(alignmentStart + j))
                        }

                        // 0-based offset into the read of the current base
                        val readOffset = readBase + i - 1
                        waitingQueue[refOffset].add(readOffset, record)
                    }

                    readBase += length
                    refBase += length
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getLogger(SamPiler::class.java)
    }
}

/**
 * A single piled up column. We deliberately store _only_ the information
 * required for [BisulfiteBamParser] to reduce allocation rate.
 *
 * @author Sergei Lebedev
 * @since 07/07/14
 */
private data class PilerColumn(val position: Int) {
    private val bases = TByteArrayList()

    fun add(offset: Int, record: SAMRecord) {
        val base = record.readBases[offset]
        assert(base > 0) { "read base should be positive." }
        bases.add(if (record.readNegativeStrandFlag) (-base).toByte() else base)
    }

    fun getStrand(i: Int): Strand {
        return if (bases[i] < 0) Strand.MINUS else Strand.PLUS
    }

    fun getReadBase(i: Int): Byte {
        val base = bases[i]
        return if (base < 0) (-base).toByte() else base
    }

    fun size(): Int = bases.size()

    fun isNotEmpty(): Boolean = !bases.isEmpty
}
