package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.data.frame.DataFrame
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.MethylomeQuery

/**
 * @author Roman.Chernyatchik
 */
class MethylomesData(chromosome: Chromosome,
                     private val methylomesPlus: List<DataFrame>,
                     private val methylomesMinus: List<DataFrame>) : McStatStrategy.Data(chromosome) {

    operator fun get(strand: Strand, sampleId: Int): DataFrame =
            (if (strand.isPlus()) methylomesPlus else methylomesMinus)[sampleId]

    companion object {
        fun build(chr: Chromosome,
                  samples: Array<String>,
                  strandFilter: StrandFilter = StrandFilter.BOTH,
                  sampleToMethylome: (String) -> List<MethylomeQuery>): MethylomesData {

            val methForSample = samples.map {
                val methylomes = sampleToMethylome(it)

                require(methylomes.size == 1) { "Only one replicate expected here" }
                methylomes.first()
            }.map { it.get() }

            val methylomesOn = { strand: Strand ->
                when {
                    strandFilter.accepts(strand) ->
                        methForSample.map { it[chr, strand].peel() }
                    else -> emptyList<DataFrame>()
                }
            }
            return MethylomesData(chr, methylomesOn(Strand.PLUS), methylomesOn(Strand.MINUS))
        }
    }
}
