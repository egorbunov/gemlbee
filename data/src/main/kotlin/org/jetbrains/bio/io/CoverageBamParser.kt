package org.jetbrains.bio.io

import htsjdk.samtools.SamReaderFactory
import org.jetbrains.bio.ext.awaitAll
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.histones.GenomeCoverage
import org.jetbrains.bio.util.Progress
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A parser for raw ChIP-seq alignments in BAM or CRAM formats.
 *
 * @author Sergei Lebedev
 * @since 11/01/16
 */
class CoverageBamParser {
    fun parse(path: Path, genomeQuery: GenomeQuery, unique: Boolean): GenomeCoverage {
        val builder = GenomeCoverage.Builder(genomeQuery)
        val progress = Progress.builder()
                .title(path.name)
                .period(10, TimeUnit.SECONDS)
                .incremental(genomeQuery.get().map { it.length.toLong() }.sum())

        val executor = Executors.newCachedThreadPool()
        executor.awaitAll(genomeQuery.get().map {
            Callable { parse(path, it, builder, progress) }
        })
        check(executor.shutdownNow().isEmpty())

        return builder.build(unique)
    }

    fun parse(path: Path, chromosome: Chromosome, builder: GenomeCoverage.Builder,
              progress: Progress.Incremental) {

        // 'htsjdk' doesn't allow concurrent queries on 'BAMFileReader'
        // thus we have to re-create 'SamReader' for each chromosome.
        val samReader = SamReaderFactory.makeDefault()
                .referenceSource(WrappedReferenceSource(chromosome.name,
                                                        chromosome.sequence))
                .open(path.toFile())

        samReader.query(chromosome.name, 0, 0, false).use {
            for (record in it) {
                if (record.readUnmappedFlag
                    || record.isSecondaryOrSupplementary
                    || record.duplicateReadFlag
                    || record.mappingQuality == 0) {  // BWA multi-alignment evidence
                    continue
                }

                val strand = if (record.readNegativeStrandFlag) {
                    Strand.MINUS
                } else {
                    Strand.PLUS
                }

                builder.put(chromosome, strand, record.alignmentStart - 1)
                progress.report()
            }
        }
    }
}
