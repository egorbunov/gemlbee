package org.jetbrains.bio.genome

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Maps
import org.apache.log4j.Logger
import java.util.*

/**
 * You've got names? We've got genes!
 *
 * @see org.jetbrains.bio.genome.GeneAliasType
 * @author Sergei Lebedev
 * @since 27/04/15
 */
object GeneResolver {
    /** A mapping of UPPERCASE "gene names" to genes for a specific organism.  */
    private val GENES_MAPS_CACHE
            = Maps.newConcurrentMap<Pair<String, GeneAliasType>, ListMultimap<String, Gene>>()

    private val LOG = Logger.getLogger(GeneResolver::class.java)

    /**
     * Returns a gene with a given name. If the name is ambiguous then
     * any of the matching genes might be returned.
     */
    @JvmStatic fun getAny(build: String, anyAlias: String): Gene? {
        val matches = matching(build, anyAlias)
        if (matches.size > 1) {
            LOG.warn("$anyAlias resolved to ${matches.size} genes: $matches")
        }

        return matches.firstOrNull()
    }

    @JvmStatic fun getAny(build: String, alias: String,
                                 aliasType: GeneAliasType): Gene? {
        val matches = matching(build, alias, aliasType).toList()
        if (matches.size > 1) {
            LOG.warn("$alias ($aliasType) resolved to ${matches.size} genes: $matches")
        }

        return matches.firstOrNull()
    }

    /**
     * Returns all genes with a given name.
     */
    @JvmStatic fun get(build: String, anyAlias: String): List<Gene> {
        return ImmutableList.copyOf(matching(build, anyAlias))
    }

    @JvmStatic fun get(build: String, alias: String,
                              aliasType: GeneAliasType): List<Gene> {
        return ImmutableList.copyOf(matching(build, alias, aliasType).iterator())
    }

    private fun matching(build: String, anyAlias: String): LinkedHashSet<Gene> {
        val genes = LinkedHashSet<Gene>()
        GeneAliasType.values().asSequence()
                .flatMapTo(genes) { aliasType -> matching(build, anyAlias, aliasType) }
        return genes;
    }

    private fun matching(build: String, alias: String, aliasType: GeneAliasType): Sequence<Gene> {
        val genesMap = genesMapFor(build, aliasType)
        return genesMap[alias.toUpperCase()].asSequence()
    }

    private fun genesMapFor(build: String, aliasType: GeneAliasType): ListMultimap<String, Gene> {
        return GENES_MAPS_CACHE.computeIfAbsent(build to aliasType) {
            val genesMap = ImmutableListMultimap.builder<String, Gene>()
            for (gene in Genome(build).genes) {
                val name = gene.names[aliasType] ?: ""
                if (name.isNotEmpty()) {
                    genesMap.put(name.toUpperCase(), gene)
                }
            }

            genesMap.build()
        }
    }

    @JvmStatic fun resolve(build: String, geneNames: Array<String>): List<Gene> =
            geneNames.map { GeneResolver.getAny(build, it) }.filterNotNull().toList()
}
