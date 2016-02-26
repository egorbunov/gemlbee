package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.CytosineContext
import org.jetbrains.bio.methylome.MethylomeQuery
import org.jetbrains.bio.methylome.MethylomeStats

/**
 * @author Roman.Chernyatchik
 */
class CFreq(private val cContextFilter: CytosineContext?) : McStatStrategy<McStatStrategy.Data> {
    override val id = "cf${cContextFilter?.let { "-$it" }}"
    override val presentableName = "#{${cContextFilter?.name ?: "C"}}/length"
    override fun toString() = "Cytosines quantity per region length : $presentableName"

    override fun prepareSampleData(chr: Chromosome,
                                   samples: Array<String>,
                                   strandFilter: StrandFilter,
                                   sampleToChrMethylome: (String) -> List<MethylomeQuery>)
            = McStatStrategy.Data(chr)

    override fun test(sampleId: Int, samplesData: McStatStrategy.Data, vararg locations: Location): Double {
        val locSize = locations.firstOrNull()?.length() ?: -1;
        val sequence = samplesData.chromosome.sequence

        var cCount = 0L
        for (location in locations) {
            require(location.chromosome == samplesData.chromosome)
            require(location.length() == locSize)

            cCount += MethylomeStats.countCytosines(location.startOffset,
                                                    location.endOffset,
                                                    sequence,
                                                    location.strand,
                                                    cContextFilter)
        }
        return cCount.toDouble() / (locSize.toDouble() * locations.size);
    }
}
