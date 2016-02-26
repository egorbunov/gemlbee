package org.jetbrains.bio.genome

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Maps
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.ext.toPath
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.sequence.NucleotideSequence
import org.jetbrains.bio.genome.sequence.TwoBitReader
import org.jetbrains.bio.util.Configuration
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Path

/**
 * The genome.
 *
 * Currently supported builds are:
 *
 *   mm9, mm10
 *   hg18, hg19, hg38
 *
 * A [Genome] is completely defined by the UCSC build string, however,
 * please use [Genome] instead of the build string for all public methods.
 * Stringly-typed interfaces don't help anyone.
 *
 * If your API is capable of processing a subset of chromosomes, consider
 * using [GenomeQuery] to indicate that.
 *
 * @author Sergei Lebedev
 * @since 06/10/15
 */
data class Genome private constructor(
        /** Build in UCSC nomenclature, e.g. `"mm9"`. */
        val build: String) : Comparable<Genome> {

    /** Species token, e.g. `"mm"`. */
    val species: String get() = build.takeWhile { !it.isDigit() }

    /** Species binomial name, e.g. `"Mus musculus"`. */
    val description: String = when (species) {
        "mm" -> "Mus musculus"
        "hg" -> "Homo sapiens"
        "to" -> "Test organism"
        else -> "<unknown>"
    }

    /**
     * Absolute path to the genome data folder.
     *
     * @see Configuration for details.
     */
    val dataPath: Path get() = Configuration.genomesPath / build

    // XXX this should really be internal, but ...
    val twoBitPath: Path get() {
        // We all know global mutable state is evil, but ...
        val path = if (System.getProperties().containsKey("reference.2bit" as Any?)) {
            System.getProperty("reference.2bit").toPath()
        } else {
            Genome(build).dataPath / "$build.2bit"
        }
        UCSC.downloadTo(path, build, "bigZips", path.name)
        return path
    }

    // Must be cached. Used for resolving chromosome IDs in [Chromosome#get].
    internal val names: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TwoBitReader.names(twoBitPath).sorted()
    }

    val chromosomes: List<Chromosome> get() {
        return names.mapIndexed { id, name -> Chromosome[this, id] }
    }

    val genes: Collection<Gene> get() = Genes.all(this).values()

    fun toQuery() = GenomeQuery(build)

    override fun compareTo(other: Genome) = build.compareTo(other.build)

    companion object {
        private val CACHE = Maps.newConcurrentMap<String, Genome>()

        // Caching is required, because '2bit' sequence must be loaded
        // only once for each build.
        @JvmStatic fun get(build: String): Genome {
            return CACHE.computeIfAbsent(build) { Genome(build) }
        }

        operator fun invoke(build: String) = get(build)
    }
}

/**
 * The Chromosome.
 *
 * @author Sergei Lebedev
 * @since 30/04/15
 */
data class Chromosome private constructor(
        /** Reference to the owner genome. */
        val genome: Genome,
        /**
         * Internal chromosome ID.
         *
         * Guaranteed to be a valid index into [Genome.chromosomes].
         */
        val id: Int) : Comparable<Chromosome> {

    /** Unique chromosome name prefixed by `"chr"`, e.g. `"chr19"`. */
    val name: String get() = genome.names[id]

    val isMitochondrial: Boolean get() = name == "chrM"

    val sequence: NucleotideSequence by lazy {
        try {
            TwoBitReader.read(genome.twoBitPath, name)
        } catch (e: IOException) {
            throw UncheckedIOException(
                    "Error loading $name from ${genome.twoBitPath}", e)
        }
    }

    val range: Range get() = Range(0, length)

    val centromere: Range get() {
        val centromeres = gaps.asSequence()
                .filter { it.isCentromere }.map { it.location }
                .toList()
        return when (centromeres.size) {
            0 -> error("no centromeres found on $this")
            1 -> centromeres.first().toRange()
            else -> {
                // Standards are for breaking! hg38 uses a completely
                // different approach to centromere annotations. Each
                // centromere is split into multiple consequent chunks.
                // Thus the "whole" centromere can be obtained as a
                // bounding range.
                val startOffset = centromeres.map { it.startOffset }.min()!!
                val endOffset = centromeres.map { it.endOffset }.max()!!
                Range(startOffset, endOffset)
            }
        }
    }

    val length: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TwoBitReader.length(genome.twoBitPath, name)
    }

    val genes: List<Gene> get() = Genes.all(genome)[this]

    val repeats: List<Repeat> get() = Repeats.all(genome)[this]

    val gaps: List<Gap> get() = Gaps.all(genome)[this]

    val cytoBands: List<CytoBand> get() = CytoBands.all(genome)[this]

    val cpgIslands: List<CpGIsland> get() = CpGIslands.all(genome)[this]

    override fun compareTo(other: Chromosome) = ComparisonChain.start()
            .compare(genome, other.genome)
            .compare(name, other.name)
            .result()

    override fun toString() = "${genome.build}:$name"

    companion object {
        private val CACHE = Maps.newConcurrentMap<Pair<Genome, Int>, Chromosome>()

        @JvmStatic operator fun get(genome: Genome, id: Int): Chromosome {
            return CACHE.computeIfAbsent(genome to id) { Chromosome(genome, id) }
        }

        /**
         * Constructs a chromosome from a human-readable name, e.g. "chrM".
         */
        @JvmStatic operator fun get(build: String, name: String): Chromosome {
            val genome = Genome(build)
            val id = genome.names.indexOf(name)
            check(id >= 0) { "unknown chromosome $name for genome $build" }
            return get(genome, id)
        }

        /**
         * A custom JSON adapter which uses [Chromosome#get] to ensure
         * reference equality for loaded chromosomes.
         */
        @JvmStatic val ADAPTER = object : TypeAdapter<Chromosome>() {
            override fun read(`in`: JsonReader) = with(`in`) {
                beginObject()
                check(nextName() == "build")
                val build = nextString()
                check(nextName() == "id")
                val id = nextInt()
                endObject()
                Chromosome[Genome(build), id]
            }

            override fun write(out: JsonWriter, chromosome: Chromosome) {
                out.beginObject()
                        .name("build").value(chromosome.genome.build)
                        .name("id").value(chromosome.id)
                        .endObject()
            }
        }.nullSafe()
    }
}
