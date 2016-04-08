package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.data.frame.all
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.*

/**
 * @author Roman.Chernyatchik
 */
class McC(private val mcContextFilter: CytosineContext? = null) : McStatStrategy<MethylomesData> {
    override val id = "mcc${mcContextFilter?.let { "-$it" }}" //mlevel mc
    override val presentableName = "#{m${mcContextFilter?.name ?: "C"}}/#{${mcContextFilter?.name ?: "C"}}"
    override fun toString() = "Mc to c frequency: $presentableName"

    override fun prepareSampleData(chr: Chromosome,
                                   samples: Array<String>,
                                   strandFilter: StrandFilter,
                                   sampleToChrMethylome: (String) -> List<MethylomeQuery>)
            = MethylomesData.build(chr, samples, strandFilter, sampleToChrMethylome)

    override fun test(sampleId: Int, samplesData: MethylomesData, vararg locations: Location): Double {
        var cCount = 0
        var mcCount = 0L

        val sequence = samplesData.chromosome.sequence
        for (location in locations) {
            require(location.chromosome == samplesData.chromosome)

            val mcDf = samplesData.get(location.strand, sampleId)
            val startOffset = location.startOffset
            val endOffset = location.endOffset
            val rows = MethylomeStats.binarySearch(mcDf, startOffset, endOffset)
            cCount += MethylomeStats.countCytosines(startOffset,
                                                    endOffset,
                                                    sequence,
                                                    location.strand,
                                                    mcContextFilter)

            mcCount += mcDf.count(all(byPatternOptional(mcContextFilter), withMcReads()),
                                  rows.startOffset, rows.endOffset);
        }

        return if (cCount == 0) Double.NaN else mcCount.toDouble() / cCount.toDouble();
    }
}
