package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.data.frame.all
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.*

/**
 * @author Roman.Chernyatchik
 */
class McFreq(private val mcContextFilter: CytosineContext?) : McStatStrategy<MethylomesData> {
    override val id = "mcf${mcContextFilter?.let { "-$it" }}"
    override val presentableName =  "#{m${mcContextFilter?.name ?: "C"}}/length"
    override fun toString() = "Mc quantity per region length : $presentableName"

    override fun prepareSampleData(chr: Chromosome,
                                   samples: Array<String>,
                                   strandFilter: StrandFilter,
                                   sampleToChrMethylome: (String) -> List<MethylomeQuery>)
            = MethylomesData.build(chr, samples, strandFilter, sampleToChrMethylome)

    override fun test(sampleId: Int, samplesData: MethylomesData, vararg locations: Location): Double {
        val locSize = locations.firstOrNull()?.length() ?: -1;

        var mcCount = 0L
        for (location in locations) {
            require(location.chromosome == samplesData.chromosome)
            require(location.length() == locSize)

            val mcDf = samplesData[location.strand, sampleId]
            val rows = MethylomeStats.binarySearch(mcDf, location.startOffset, location.endOffset)
            mcCount += mcDf.count(all(byPatternOptional(mcContextFilter), withMcReads()),
                                  rows.startOffset, rows.endOffset);
        }
        return mcCount.toDouble() / (locSize.toDouble() * locations.size);
    }
}
