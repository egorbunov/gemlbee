package org.jetbrains.bio.browser

import com.google.common.annotations.VisibleForTesting
import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.ImportantGenesAndLoci.getDevelopmentalGenes
import org.jetbrains.bio.genome.ImportantGenesAndLoci.getHouseKeepingGenes2013Short
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*

object LociCompletion {
    val DEFAULT_COMPLETION = mapOf(
            "housekeeping" to { gq: GenomeQuery ->
                GeneResolver.resolve(gq.build, getHouseKeepingGenes2013Short(gq))
                        .map { GeneLocRef(it) }
            },
            "development" to { gq: GenomeQuery ->
                GeneResolver.resolve(gq.build, getDevelopmentalGenes(gq)).map { GeneLocRef(it) }
            },
            "cg_islands" to { gq: GenomeQuery ->
                gq.get().asSequence().flatMap { it.cpgIslands.asSequence() }
                        .map { cgi -> SimpleLocRef(cgi.location) }
                        .toList()
            })

    val LOCI_COMPLETION =
            intArrayOf(1000, 2000).flatMap { listOf("tss$it", "tes$it", "tss-$it;$it", "tes-$it;$it") } +
                    LocusType.values().map { if (it === LocusType.TSS_GENE_TES) "genes" else it.toString() }

    /**
     * See [.parse]
     */
    operator fun get(genomeQuery: GenomeQuery): Set<String> {
        val result = HashSet<String>()
        for (chromosome in genomeQuery.get()) {
            result.add(chromosome.name)
            result.addAll(chromosome.genes.asSequence()
                                  .flatMap { it.names.values.asSequence() })
        }

        return result
    }

    @VisibleForTesting
    internal val ABSTRACT_LOCATION_PATTERN = "([^:]+)(:([\\d\\.,]+)-([\\d\\.,]+))?".toPattern()

    /**
     * See [.get]
     */
    fun parse(text: String, genomeQuery: GenomeQuery): LocationReference? {
        val name = text.toLowerCase().trim { it <= ' ' }
        val matcher = ABSTRACT_LOCATION_PATTERN.matcher(name)
        if (matcher.matches()) {
            val chromosomeName = matcher.group(1)
            val chromosome = ChromosomeNamesMap.create(genomeQuery)[chromosomeName]
            if (chromosome != null) {
                val startGroup = if (matcher.groupCount() >= 3) matcher.group(3) else null
                val endGroup = if (matcher.groupCount() >= 4) matcher.group(4) else null
                if (startGroup != null && endGroup != null) {
                    val loc = Location(Integer.parseInt(startGroup.replace("\\.|,".toRegex(), "")),
                                       Integer.parseInt(endGroup.replace("\\.|,".toRegex(), "")),
                                       chromosome,
                                       Strand.PLUS)
                    return SimpleLocRef(loc)
                }
                return SimpleLocRef(Location(0, chromosome.length, chromosome, Strand.PLUS))
            }
        }
        val gene = GeneResolver.getAny(genomeQuery.build, name) ?: return null
        return GeneLocRef(gene, RelativePosition.AROUND_WHOLE_SEGMENT.of(
                gene.location, -gene.length() / 2, gene.length() / 2))
    }
}
