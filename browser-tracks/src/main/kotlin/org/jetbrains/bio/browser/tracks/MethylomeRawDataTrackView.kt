package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.big.BigSummary
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.methylome.CytosineContext
import org.jetbrains.bio.methylome.MethylomeQuery
import org.jetbrains.bio.methylome.strategies.*
import java.awt.Color
import java.awt.Graphics

/**
 * @author Roman.Chernyatchik
 */
class MethylomeRawDataTrackView @JvmOverloads constructor(
        val methylomeQuery: MethylomeQuery,
        val cContext: CytosineContext? = CytosineContext.CG,
        binSize: Int = 1000,
        binSizes: IntArray = intArrayOf(binSize)
) : AbstractBinnedDataTrackView(LineType.HIST_LIKE, binSize,
        (cContext?.toString()?.plus(" context:") ?: "")
                + methylomeQuery.description
                + (if (binSizes.size > 1) "" else ", bin = $binSize bp"),
        binSizes) {

    companion object {
        val HIGH_MLEVEL_COLOR: Color = Color(0, 255, 0, 255)
        val LOW_MLEVEL_COLOR: Color = Color(255, 0, 0, 255)
        val GC_CONTENT_COLOR: Color = Color(0, 0, 255, 200)

        fun gradient(cHigh: Color, cLow: Color, amount: Float): Color {
            return when {
                amount.isNaN() -> Color.YELLOW
                else -> {
                    val transform = { cH: Int, cL: Int ->
                        Math.round(cH * amount + cL * (1 - amount)).toInt()
                    }
                    Color(transform(cHigh.red, cLow.red),
                            transform(cHigh.green, cLow.green),
                            transform(cHigh.blue, cLow.blue),
                            transform(cHigh.alpha, cLow.alpha))
                }
            }
        }

    }

    override val layersNumber = 3
    override val strandedData = true

    override fun addTrackControls()
            = if (binSizes.size > 1) arrayListOf(binSizeSelector()) else arrayListOf()

    override fun axisPresentableValue(value: Double)
            = Math.round(value * binSize).toDouble() // ~ cytosines count in bin

    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        val context = cContext?.toString() ?: "C"
        TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                GC_CONTENT_COLOR to context,
                LOW_MLEVEL_COLOR to "m$context (low avg mLevel ~ 0%)",
                HIGH_MLEVEL_COLOR to "m$context (high avg mLevel ~ 100%)")
    }

    override val yAxisTitle = "C count in range (bin)"

    override fun fileName(layer: Int, strand: Strand, binSize: Int, gq: GenomeQuery): String {
        val prefix = if (gq.restriction.isNotEmpty()) "subset" else "${gq.build}_${methylomeQuery.cellId}"
        return "mcraw_${prefix}_${arrayListOf(gq, methylomeQuery.id).hashCode()}_${binSize}_${layer}_${strand.ordinal}.bw"
    }

    override fun preprocess(layer: Int, binSize: Int, chr: Chromosome,
                            strand: Strand): FloatArray {
        return if (layer == 0) {
            calcBinnedData(CFreq(cContext), McStatStrategy.Data(chr), binSize, strand)
        } else {
            val mcData = MethylomesData.build(chr, arrayOf("sample"), strand.asFilter(),
                    { listOf(methylomeQuery) })
            when (layer) {
                1 -> calcBinnedData(McFreq(cContext), mcData, binSize, strand)
                else -> calcBinnedData(MLevelMc(cContext), mcData, binSize, strand)
            }
        }
    }

    override fun renderer(layer: Int,
                          model: SingleLocationBrowserModel,
                          conf: Storage,
                          uiOptions: Storage) = when (layer) {
        0 -> BinnedRenderer(binSize, GC_CONTENT_COLOR)
        1 -> McLevelRendered(model, conf, binSize)
        else -> null
    }

    override fun computeScale(model: SingleLocationBrowserModel, conf: Storage): List<TrackView.Scale> {
        val scales = super.computeScale(model, conf)

        // Let render min from 0
        return listOf(TrackView.Scale(0.0, scales[0].max))
    }

    private inner class McLevelRendered(val model: SingleLocationBrowserModel,
                                        val conf: Storage,
                                        binSize: Int) : BinnedRenderer(binSize) {
        private var mcLevelByPxStrandedData: Array<List<BigSummary>?> = arrayOf(null, null)

        override fun render(pixelPos: Int, strand: Strand, normVal: Double): Color {
            var data = mcLevelByPxStrandedData[strand.ordinal]
            if (data == null) {
                data = conf[TRACK_DATA][2, strand]
                mcLevelByPxStrandedData[strand.ordinal] = data
            }

            val mLevel = data[pixelPos].let { (it.sum * binSize / it.count).toFloat() }
            val amount = if (mLevel.isFinite()) {
                assert(mLevel < 2.0f) { "Unexpected normalized mLevel = $mLevel" }
                // May be around 1.0 due to summary round
                Math.min(mLevel, 1.0f)
            } else {
                mLevel
            }

            return MethylomeRawDataTrackView.gradient(
                    MethylomeRawDataTrackView.HIGH_MLEVEL_COLOR,
                    MethylomeRawDataTrackView.LOW_MLEVEL_COLOR,
                    amount)
        }
    }
}


fun <D : McStatStrategy.Data> calcBinnedData(strategy: McStatStrategy<out D>,
                                             samplesData: D,
                                             binSize: Int,
                                             strand: Strand,
                                             sampleId: Int = 0): FloatArray {

    val s: McStatStrategy<D> = strategy as McStatStrategy<D>
    val chr = samplesData.chromosome
    val binsCount = chr.range.endOffset / binSize

    return (0 until binsCount).map { binIndex ->
        s.test(sampleId, samplesData,
                Location(binIndex * binSize, (binIndex + 1) * binSize, chr, strand)
        ).toFloat()
    }.toFloatArray()
}
