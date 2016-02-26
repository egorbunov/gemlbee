package org.jetbrains.bio.genome

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.asFractionOf
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*

/**
 * A resolver for chromosome names.
 *
 * Currently support the following names:
 *
 *     chr21
 *     CHRX
 *     21 or X
 *
 * @author Sergei Lebedev
 * @since 30/10/15
 */
class ChromosomeNamesMap private constructor(chromosomes: Collection<Chromosome>) {
    private val resolver = HashMap<String, Chromosome>()
    private var collectErrors = true
    private var ignoreUnmappedAndUnlocalized = true
    val unrecognized = HashSet<String>()
    var recognizedCount = 0

    init {
        for (chr in chromosomes) {
            val name = chr.name
            resolver[name.substringAfter("chr")] = chr
            resolver[name] = chr
            resolver[name.toLowerCase()] = chr
        }
    }

    operator fun get(name: String): Chromosome? {
        val chromosome = resolver[name]
        if (chromosome == null) {
            if (collectErrors &&
                    !(ignoreUnmappedAndUnlocalized && name.isUnmappedOrUnlocalized)) {
                unrecognized.add(name)
            }
        } else {
            recognizedCount++
        }

        return chromosome
    }

    val String.isUnmappedOrUnlocalized: Boolean get() {
        return startsWith("chrUn") || endsWith("_random")
    }

    operator fun contains(name: String) = name in resolver

    fun report(title: String) {
        if (collectErrors && unrecognized.isNotEmpty()) {
            val names = unrecognized.sorted()
            val unrecognizedCount = unrecognized.size.toLong()
            val totalCount = unrecognizedCount + recognizedCount
            LOG.info("$title parsing error: Chromosomes weren't recognized " +
                     "'$names' for some rows " + unrecognizedCount.asFractionOf(totalCount))
        }
    }

    companion object {
        private val LOG = Logger.getLogger(ChromosomeNamesMap::class.java)

        /**
         * Build a mapping for chromosomes excluding the mitochondrial chromosome
         * and unmapped and unlocalized fragments.
         */
        @JvmStatic fun create(genomeQuery: GenomeQuery): ChromosomeNamesMap {
            return ChromosomeNamesMap(genomeQuery.get())
        }

        /**
         * Build a mapping for *all* chromosomes.
         */
        @JvmStatic fun create(build: String): ChromosomeNamesMap {
            return ChromosomeNamesMap(Genome(build).chromosomes)
        }
    }
}