package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.data.frame.all
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.*
import java.util.stream.IntStream

/**
 * @author Roman.Chernyatchik
 */
class MLevelMc(private val mcContextFilter: CytosineContext? = null) : McStatStrategy<MethylomesData> {
    override val id = "mlmc${mcContextFilter?.let { "-$it" }}" //mlevel mc
    override val presentableName = "sum[m${mcContextFilter?.name ?: "C"} in region]{" +
                                   "mLevel(m${mcContextFilter?.name ?: "C"})} /" +
                                   " #{m${mcContextFilter?.name ?: "C"}: mLevel > 0}"
    override fun toString() = "Average mc level for methylated mc : $presentableName"

    override fun prepareSampleData(chr: Chromosome,
                                   samples: Array<String>,
                                   strandFilter: StrandFilter,
                                   sampleToChrMethylome: (String) -> List<MethylomeQuery>)
            = MethylomesData.build(chr, samples, strandFilter, sampleToChrMethylome)

    override fun test(sampleId: Int, samplesData: MethylomesData, vararg locations: Location): Double {
        var mcCount = 0L
        var summaryMlevel = 0.0

        for (location in locations) {
            require(location.chromosome == samplesData.chromosome)

            val mcDf = samplesData[location.strand, sampleId]
            val rows = MethylomeStats.binarySearch(mcDf, location.startOffset, location.endOffset)
            val start = rows.startOffset
            val end = rows.endOffset

            val coveredWithTag = all(byPatternOptional(mcContextFilter), covered())
            mcCount += mcDf.count(coveredWithTag, start, end);

            val filter = mcDf.test(coveredWithTag, start, end)
            val mcLevel = mcDf.sliceAsFloat("level")
            summaryMlevel += IntStream.range(start, end).filter({filter[it - start]})
                    .mapToDouble { mcLevel[it].toDouble() }.sum()
        }

        return if (mcCount == 0L) Double.NaN else summaryMlevel / mcCount.toDouble();
    }
}
