package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.big.BigBedFile
import org.jetbrains.bio.big.BigFile
import org.jetbrains.bio.big.BigSummary
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.genome.ChromosomeRange
import java.awt.Color
import java.awt.Graphics
import java.nio.file.Path

/**
 * A track view for BigBED files.
 *
 * @author Sergei Lebedev
 * @since 24/07/15
 */
class BigBedTrackView(path: Path, val numBins: Int) : TrackView(path.name) {
    val bbf = BigBedFile.read(path)

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]
        val (_min, max) = conf[TrackView.SCALES].first()
        val step = width / numBins
        bbf.summarize(model.chromosomeRange, numBins).forEachIndexed { i, summary ->
            val h = (summary.sum / max * height).toInt()
            g.color = Color.BLACK
            g.fillRect(i * step, height - h, step, h)
        }
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                                    Color.BLACK to "coverage")
    }


    override fun drawAxis(g: Graphics, conf: Storage,
                          width:Int, height:Int,
                          drawInBG: Boolean) {
        TrackUIUtil.drawVerticalAxis(g, "The Marsians", conf[SCALES].single(), drawInBG,
                                     width, height)
    }

    override fun computeScales(model: SingleLocationBrowserModel,
                               conf: Storage): List<Scale> {
        val summaries = bbf.summarize(model.chromosomeRange, numBins)
        val max = Math.ceil(summaries.map { it.sum }.max()!!)
        return listOf(Scale(0.0, max))
    }
}

fun BigFile<*>.summarize(query: ChromosomeRange, numBins: Int): List<BigSummary> {
    return summarize(query.chromosome.name,
                     query.startOffset,
                     query.endOffset,
                     numBins)
}
