package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.histones.BedTrackQuery
import org.jetbrains.bio.histones.GenomeCoverageQuery
import java.awt.Color
import javax.swing.JComponent

/**
 * @author Roman.Chernyatchik
 *
 * Binned representation, if 1bp accuracy not required.
 * Profit - smaller bigwig caches, less noise due to summarizing by bins.
 */

class BedCovTrackBinnedView @JvmOverloads constructor(
        private val query: BedTrackQuery,
        private val uniqueTagsOnly: Boolean = true,
        binSize: Int = 50,
        title: String = "Binned ($binSize bp) track coverage for ${query.id}" +
                "${if (uniqueTagsOnly) "" else " (not unique tags)"}",
        private val trackColor: Color = Color.BLUE)
: AbstractBinnedDataTrackView(title = title, binSize = binSize) {

    override val layersNumber = 1
    override val strandedData = false
    override val yAxisTitle = "tags count in range (bin)"

    init {
        preferredHeight = 30
    }

    override fun fileName(layer: Int, strand: Strand, binSize: Int, gq: GenomeQuery): String {
        // not stranded data
        return "cov-b$binSize-${gq.getShortNameWithChromosomes()}-$query" +
                "${if (uniqueTagsOnly) "" else "-nu"}.bw"
    }

    override fun ignoredValue(value: Float): Boolean = value == 0.0f

    override fun preprocess(layer: Int, binSize: Int, chr: Chromosome, strand: Strand): FloatArray {
        val coverage = GenomeCoverageQuery.of(query, uniqueTagsOnly).get()

        return chr.range.slice(binSize).mapToDouble {
            coverage.getBothStrandCoverage(it.on(chr)).toDouble()
        }.toArray().map { it.toFloat() }.toFloatArray()
    }

    override fun computeScale(model: SingleLocationBrowserModel, conf: Storage): List<TrackView.Scale> {
        val scales = super.computeScale(model, conf)

        // Let render min from 0
        return listOf(TrackView.Scale(0.0, scales[0].max))
    }

    override fun renderer(layer: Int,
                          model: SingleLocationBrowserModel,
                          conf: Storage,
                          uiOptions: Storage)
            = BinnedRenderer(uiOptions[BIN_SIZE], trackColor)

    override fun axisPresentableValue(value: Double) = Math.round(value).toDouble()


    override fun addTrackControls(): List<Pair<String, JComponent>> = emptyList()

}
