package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery

/**
 * @author Roman.Chernyatchik
 */
class SingleLocationBrowserModel @JvmOverloads constructor(
        query: GenomeQuery,
        chromosome: Chromosome = query.get().first(),
        range: Range = chromosome.range,
        rangeMetaInfo: LocationReference? = null) : BrowserModel(query, range) {

    var chromosome: Chromosome = chromosome
        private set // chromosomeRange setter fires events


    val chromosomeRange: ChromosomeRange
        get() = range.on(chromosome)

    var rangeMetaInf: LocationReference? = rangeMetaInfo
        private set // chromosomeRange setter fires events

    @JvmOverloads fun setChromosomeRange(newCR: ChromosomeRange, metaInf: LocationReference? = null) {
        val oldChr = chromosome
        chromosome = newCR.chromosome

        val oldMetaInf = rangeMetaInf
        rangeMetaInf = metaInf;

        val oldRange = range
        range = newCR.toRange()

        // Fire event if hasn't been already fired by range
        if (oldRange == range && (oldChr != chromosome || oldMetaInf != metaInf)) {
            modelChanged()
        }
    }

    override val length: Int
        get() = chromosome.length

    override fun presentableName(): String
            = "${chromosome.name}:${range.startOffset}-${range.endOffset}"

    override fun copy(): SingleLocationBrowserModel
            = SingleLocationBrowserModel(genomeQuery, chromosome, range, rangeMetaInf)
}
