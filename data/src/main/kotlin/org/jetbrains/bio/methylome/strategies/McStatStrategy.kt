package org.jetbrains.bio.methylome.strategies

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.StrandFilter
import org.jetbrains.bio.methylome.MethylomeQuery

/**
 * Stateless class for methylome related per location statistics. Descendants implements some statistics, like average
 * mc level in genome, max vote for changed methylation by location, etc.
 *
 * @author Roman.Chernyatchik
 */
interface McStatStrategy<T : McStatStrategy.Data> {
    val id: String
    val presentableName: String

    /**
     * @param chr Chromosome
     * @param samples Sample names
     * @param sampleToChrMethylome Mapping from sample name to replicate methylomes.
     * @return Cached data necessary for by location calculations
     * @throws Exception
     */

    fun prepareSampleData(chr: Chromosome,
                                 samples: Array<String>,
                                 strandFilter: StrandFilter = StrandFilter.BOTH,
                                 sampleToChrMethylome: (String) -> List<MethylomeQuery>): T


    /**
     * @param sampleId 0-based sample index in samples
     * @param samplesData pre-calculated samples data
     * @param locations Given locations
     */
    fun test(sampleId: Int,
                    samplesData: T,
                    vararg locations: Location): Double

    open class Data(val chromosome: Chromosome) {
    }
}
