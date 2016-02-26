package org.jetbrains.bio.histones

import gnu.trove.list.TIntList
import gnu.trove.list.array.TIntArrayList
import gnu.trove.set.hash.TIntHashSet
import org.apache.log4j.Logger
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.containers.GenomeMap
import org.jetbrains.bio.genome.containers.genomeMap
import org.jetbrains.bio.genome.containers.genomeStrandMap
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.npy.NpzFile
import java.io.IOException
import java.nio.file.Path

/**
 * An immutable container for tag count data.
 *
 * @author Oleg Shpynov
 * @since 26/07/13
 */
class GenomeCoverage(val genomeQuery: GenomeQuery) {
    private val LOG: Logger = Logger.getLogger(GenomeCoverage::class.java)

    /**
     * A sorted per-chromosome and strand list of tags, i.e. genomic
     * offsets representing alignment events.
     */
    val data = genomeStrandMap(genomeQuery) { _c, _s -> TIntArrayList() }

    fun aggregateBothStrands(binSize: Int): GenomeMap<IntArray> {
        return genomeMap(genomeQuery) { chromosome ->
            chromosome.range.slice(binSize)
                    .mapToInt { getBothStrandCoverage(it.on(chromosome)) }
                    .toArray();
        }
    }

    /**
     * Returns the number of tags covered by a given [range] on both strands.
     */
    fun getBothStrandCoverage(range: ChromosomeRange): Int {
        return getCoverage(range.on(Strand.MINUS)) + getCoverage(range.on(Strand.PLUS))
    }

    /**
     * Returns the number of tags covered by a given [location].
     */
    fun getCoverage(location: Location): Int = getTags(location).size

    /**
     * Returns a sorted array of tags covered by a given [location].
     */
    fun getTags(location: Location): IntArray {
        val data = data[location.chromosome, location.strand]
        val index = getIndex(data, location.startOffset)
        var size = 0
        while (index + size < data.size() &&
               data[index + size] < location.endOffset) {
            size++
        }

        return data.toArray(index, size)
    }

    private fun getIndex(data: TIntList, value: Int): Int {
        var index = data.binarySearch(value)
        while (index > 0 && data[index - 1] == value) {
            index--
        }

        return if (index < 0) index.inv() else index
    }

    @Throws(IOException::class) fun save(outputPath: Path) {
        NpzFile.write(outputPath).use { writer ->
            writer.write("version", intArrayOf(VERSION))

            for (chromosome in genomeQuery.get()) {
                for (strand in Strand.values()) {
                    val key = chromosome.name + '/' + strand
                    writer.write(key, data[chromosome, strand].toArray())
                }
            }
        }
    }

    @Throws(IOException::class) fun load(inputPath: Path): GenomeCoverage {
        NpzFile.read(inputPath).use { reader ->
            val version = (reader["version"] as IntArray).single()
            check(version == VERSION) {
                "coverage version is $version instead of $VERSION"
            }

            for (chromosome in genomeQuery.get()) {
                for (strand in Strand.values()) {
                    val key = chromosome.name + '/' + strand
                    data[chromosome, strand] =
                            TIntArrayList.wrap(reader[key] as IntArray)
                }
            }
        }

        return this
    }

    class Builder(genomeQuery: GenomeQuery) {
        val coverage = GenomeCoverage(genomeQuery)

        fun put(chromosome: Chromosome, strand: Strand, offset: Int): Builder {
            coverage.data[chromosome, strand].add(offset)
            return this
        }

        fun build(unique: Boolean): GenomeCoverage {
            for (chromosome in coverage.genomeQuery.get()) {
                for (strand in Strand.values()) {
                    if (unique) {
                        // XXX we can do linear time de-duplication on
                        // the sorted sequence.
                        val tags = coverage.data[chromosome, strand]
                        coverage.data[chromosome, strand] = TIntArrayList(TIntHashSet(tags))
                    }

                    coverage.data[chromosome, strand].sort()
                }
            }

            return coverage
        }
    }

    companion object {
        /** Binary format version.  */
        private val VERSION = 2

        fun builder(genomeQuery: GenomeQuery) = Builder(genomeQuery)

        @JvmStatic fun compute(genomeQuery: GenomeQuery,
                               bedQuery: InputQuery<Iterable<BedEntry>>,
                               unique: Boolean): GenomeCoverage {
            val builder = Builder(genomeQuery)
            val chromosomes = ChromosomeNamesMap.create(genomeQuery)
            for (entry in bedQuery.get()) {
                val chromosome = chromosomes[entry.chromosome] ?: continue
                val strand = entry.strand.toStrand()

                if (entry.chromEnd == 0) {
                    builder.put(chromosome, strand, entry.chromStart)
                } else {
                    if (strand.isPlus()) {
                        builder.put(chromosome, strand, entry.chromStart)
                    } else {
                        builder.put(chromosome, strand, entry.chromEnd - 1)
                    }
                }
            }

            return builder.build(unique)
        }
    }
}

