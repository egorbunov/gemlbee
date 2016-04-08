package org.jetbrains.bio.genome

import com.google.common.base.Joiner
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import org.apache.commons.csv.CSVFormat
import org.jetbrains.bio.ext.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * A shortcut for annotation caches.
 *
 * The cache uses soft-values policy, thus make sure the cached class
 * implements [.equals] and [.hashCode] either manually or via data syntax
 * sugar. Otherwise, there be heisenbugs!
 *
 * Based on a true story. Consider a long-living map `M` indexed by genes.
 * Once the memory is low, GC collects soft-referenced 'ListMultimap<Chromosome, Gene>>',
 * so the next cache access re-populates the cache creating new instance for
 * each of the genes. If the 'Gene' relies on the default implementation
 * of [.hashCode] (which compares memory addresses), then `gene in M` would
 * always return false, and the entries of `M` could never be updated or
 * retrieved.
 */
internal fun <T> cache(): Cache<String, ListMultimap<Chromosome, T>> {
    return CacheBuilder.newBuilder()
            .softValues()
            .initialCapacity(1)
            .build<String, ListMultimap<Chromosome, T>>()
}

/** UCSC annotations always use "chrN" format, so we don't need [ChromosomeNamesMap]. */
internal fun chromosomeMap(build: String): Map<String, Chromosome> {
    return Genome(build).chromosomes.associateBy { it.name }
}

data class Repeat(val name: String,
                  override val location: Location,
                  val repeatClass: String,
                  val family: String) : LocationAware

/**
 * A registry for repetitive genomic elements.
 *
 * @author Sergei Lebedev
 * @since 16/05/14
 */
object Repeats {
    private val CACHE = cache<Repeat>()

    /** Hopefully UCSC won't change the file name in the near future. */
    private const val REPEATS_FILE_NAME = "rmsk.txt.gz"
    /** Builds with per-chromosome repeat annotations. */
    private val LEGACY_FORMAT = setOf("mm9", "hg18")

    internal fun all(genome: Genome): ListMultimap<Chromosome, Repeat> {
        val build = genome.build
        return CACHE.get(build) {
            val repeatsPath = genome.dataPath / REPEATS_FILE_NAME
            repeatsPath.checkOrRecalculate("Repeats") { output ->
                output.let {
                    if (build in LEGACY_FORMAT) {
                        UCSC.downloadBatchTo(it, build, "database",
                                             "%s_$REPEATS_FILE_NAME");
                    } else {
                        UCSC.downloadTo(it, build, "database", REPEATS_FILE_NAME)
                    }
                }
            }

            read(build, repeatsPath)
        }
    }

    private fun read(build: String, repeatsPath: Path): ListMultimap<Chromosome, Repeat> {
        val builder = ImmutableListMultimap.builder<Chromosome, Repeat>()
        val chromosomes = chromosomeMap(build)
        val csvFormat = CSVFormat.TDF.withHeader(
                "bin", "sw_score", "mismatches", "deletions", "insertions",
                "chrom", "genomic_start", "genomic_end", "genomic_left",
                "strand", "name", "class", "family", "repeat_start",
                "repeat_end", "repeat_left", "id")
        csvFormat.parse(repeatsPath.bufferedReader()).use { csvParser ->
            for (row in csvParser) {
                val chromosome = chromosomes[row["chrom"]] ?: continue
                val strand = row["strand"].toStrand()
                val startOffset = row["genomic_start"].toInt()
                val endOffset = row["genomic_end"].toInt()
                val location = Location(
                        startOffset, endOffset, chromosome, strand)
                val repeat = Repeat(row["name"], location,
                                    row["class"].toLowerCase(),
                                    row["family"].toLowerCase())
                builder.put(chromosome, repeat)
            }
        }

        return builder.build()
    }
}

data class CytoBand(val name: String,
                    val gieStain: String,
                    override val location: Location) :
        Comparable<CytoBand>, LocationAware {

    override fun compareTo(other: CytoBand) = location.compareTo(other.location)

    override fun toString() = "$name: $location"
}

/**
 * Chromosomes, bands and all that.
 *
 * @author Oleg Shpynov
 * @author Sergei Lebedev
 * @since 26/06/12
 */
object CytoBands {
    private val CACHE = cache<CytoBand>()

    @JvmField val CYTOBANDS_FILE_NAME = "cytoBand.txt.gz"

    @JvmField val FORMAT = CSVFormat.TDF
            .withHeader("chrom", "start_offset", "end_offset", "name", "gie_stain")

    internal fun all(genome: Genome): ListMultimap<Chromosome, CytoBand> {
        val build = genome.build
        return CACHE.get(build) {
            val bandsFileName = when (build) {
                "hg38" -> "cytoBandIdeo.txt.gz"
                else   -> CYTOBANDS_FILE_NAME
            }

            val bandsPath = genome.dataPath / bandsFileName
            bandsPath.checkOrRecalculate("CytoBands") { output ->
                output.let {
                    UCSC.downloadTo(it, build, "database", bandsPath.name)
                }
            }

            read(build, bandsPath)
        }
    }

    private fun read(build: String, bandsPath: Path): ListMultimap<Chromosome, CytoBand> {
        val builder = ImmutableListMultimap.builder<Chromosome, CytoBand>()
        val chromosomes = chromosomeMap(build)
        FORMAT.parse(bandsPath.bufferedReader()).use { csvParser ->
            for (row in csvParser) {
                val chromosome = chromosomes[row["chrom"]] ?: continue

                // Note(lebedev): unsure why the +1, ask @iromeo.
                val location = Location(
                        row["start_offset"].toInt() + 1, row["end_offset"].toInt(),
                        chromosome, Strand.PLUS)
                builder.put(chromosome, CytoBand(row["name"], row["gie_stain"], location))
            }
        }

        return builder.build()
    }
}

/**
 * A container for centromeres and telomeres.
 *
 * @author Roman Chernyatchik
 * @since 05/06/15
 */
class Gap(val name: String, override val location: Location) :
        Comparable<Gap>, LocationAware {

    val isCentromere: Boolean get() = name == "centromere"

    val isTelomere: Boolean get() = name == "telomere"

    val isHeterochromatin: Boolean get() = name == "heterochromatin"

    override fun compareTo(other: Gap) = location.compareTo(other.location)
}

object Gaps {
    private val CACHE = cache<Gap>()

    const val GAPS_FILE_NAME = "gap.txt.gz"

    @JvmField val FORMAT = CSVFormat.TDF.withHeader(
            "bin", "chrom", "start_offset", "end_offset", "ix", "n",
            "size", "type", "bridge")

    private const val CENTROMERES_FILE_NAME = "centromeres.txt.gz"

    /** Builds with per-chromosome gap annotations. */
    private val LEGACY_FORMAT = setOf("mm9", "hg18")
    /** Builds with separate centromere annotations. */
    private val SPLIT_FORMAT = setOf("hg38")

    internal fun all(genome: Genome): ListMultimap<Chromosome, Gap> {
        val build = genome.build
        return CACHE.get(build) {
            val gapsPath = genome.dataPath / GAPS_FILE_NAME
            gapsPath.checkOrRecalculate("Gaps") { output ->
                output.let { path ->
                    download(build, path)
                }
            }

            read(build, gapsPath)
        }
    }

    private fun read(build: String, gapsPath: Path): ListMultimap<Chromosome, Gap> {
        val builder = ImmutableListMultimap.builder<Chromosome, Gap>()
        val chromosomes = chromosomeMap(build)
        FORMAT.parse(gapsPath.bufferedReader()).use { csvParser ->
            for (row in csvParser) {
                val chromosome = chromosomes[row["chrom"]] ?: continue
                val location = Location(
                        row["start_offset"].toInt(), row["end_offset"].toInt(),
                        chromosome, Strand.PLUS)
                builder.put(chromosome, Gap(row["type"].toLowerCase(), location))
            }
        }

        return builder.build()
    }

    private fun download(build: String, gapsPath: Path) {
        if (build in LEGACY_FORMAT) {
            UCSC.downloadBatchTo(gapsPath, build, "database", "%s_$GAPS_FILE_NAME")
        } else {
            UCSC.downloadTo(gapsPath, build, "database", GAPS_FILE_NAME)
            if (build in SPLIT_FORMAT) {
                // hg38 is special, it has centromere annotations in a
                // separate file. Obviously, the format of the file doesn't
                // match the one of 'gap.txt.gz', so we fake the left
                // out columns below.
                val centromeresPath = gapsPath.parent  / "centromere.txt.gz"
                try {
                    UCSC.downloadTo(centromeresPath, build, "database", CENTROMERES_FILE_NAME)
                    centromeresPath.bufferedReader().useLines { centromeres ->
                        gapsPath.bufferedWriter(StandardOpenOption.WRITE,
                                                StandardOpenOption.APPEND).use { target ->
                            for (line in centromeres) {
                                val leftout = Joiner.on('\t')
                                        .join("", -1, -1, "centromere", "no")
                                target.write(line.trimEnd() + leftout + '\n')
                            }
                            target.close()
                        }
                    }
                } catch (e: Exception) {
                    gapsPath.delete()  // we aren't done yet.
                } finally {
                    centromeresPath.deleteIfExists()
                }
            }
        }
    }
}

/**
 * A CpG island. Nobody knows what it is really.
 *
 * @author Roman Chernyatchik
 * @since 07/10/13
 */
data class CpGIsland(
        /** Number of CpG dinucleotides within the island.  */
        val CpGNumber: Int,
        /** Number of C and G nucleotides within the island.  */
        val GCNumber: Int,
        /**
         * Ratio of observed CpG to expected CpG counts within the island,
         * where the expected number of CpGs is calculated as
         * `numC * numG / length`.
         */
        val observedToExpectedRatio: Double,
        override val location: Location) : LocationAware {
}

object CpGIslands {
    private val CACHE = cache<CpGIsland>()

    private const val ISLANDS_FILE_NAME = "cpgIslandExt.txt.gz"
    /** Builds missing `bin` column in the annotations. */
    private val LEGACY_FORMAT = setOf("hg18")

    internal fun all(genome: Genome): ListMultimap<Chromosome, CpGIsland> {
        val build = genome.build
        return CACHE.get(build) {
            val islandsPath = genome.dataPath / ISLANDS_FILE_NAME
            islandsPath.checkOrRecalculate("CpGIslands") { output ->
                output.let {
                    UCSC.downloadTo(it, build, "database", ISLANDS_FILE_NAME)
                }
            }

            read(build, islandsPath)
        }
    }

    private fun read(build: String, islandsPath: Path): ListMultimap<Chromosome, CpGIsland> {
        val builder = ImmutableListMultimap.builder<Chromosome, CpGIsland>()
        val chromosomes = chromosomeMap(build)

        val headers = arrayOf("chrom", "start_offset", "end_offset", "name", "length",
                              "cpg_num", "gc_num", "per_cpg", "per_gc", "obs_exp")
        val csvFormat = if (build in LEGACY_FORMAT) {
            CSVFormat.TDF.withHeader(*headers)
        } else {
            CSVFormat.TDF.withHeader("bin", *headers)
        }

        csvFormat.parse(islandsPath.bufferedReader()).use { csvParser ->
            for (row in csvParser) {
                val chromosome = chromosomes[row["chrom"]] ?: continue
                val location = Location(
                        row["start_offset"].toInt(), row["end_offset"].toInt(),
                        chromosome)
                val island = CpGIsland(
                        row["cpg_num"].toInt(), row["gc_num"].toInt(),
                        row["obs_exp"].toDouble(), location)
                builder.put(chromosome, island)
            }
        }

        return builder.build()
    }
}
