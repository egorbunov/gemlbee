package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.ScoredInterval
import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.tdf.TdfFile
import java.awt.Color
import java.awt.Graphics
import java.nio.file.Path

class TdfTrackView(val path: Path, val trackNumber: Int = 0) : TrackView(path.name) {

    private val tdf : TdfFile by lazy(LazyThreadSafetyMode.NONE) { TdfFile.read(path) }

    init {
        preferredHeight = 30
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]
        val scores = tdf.summarize(model, trackNumber)

        // No visible tiles
        if (scores.isEmpty()) {
            return
        }
        val max = scores.map { it.score }.max()!!
        val range = model.range
        g.color = Color.BLUE
        scores.forEach {
            val h = (it.score / max * height).toInt()
            val startOffset = genomeToScreen(it.start, width, range)
            val endOffset = genomeToScreen(it.end, width, range)
            if (endOffset == startOffset) {
                // draw 3 x height: rectangle
                g.fillRect(startOffset - 1, height - h, 3, height)
            } else {
                g.fillRect(startOffset, height - h, endOffset - startOffset, height)
            }
        }
    }

    override fun computeScale(model: SingleLocationBrowserModel, conf: Storage): List<Scale> {
        val scores = tdf.summarize(model, trackNumber)
        return if (scores.isEmpty()) {
            listOf(Scale.undefined())
        } else {
            listOf(Scale(0.0, scores.map { it.score }.max()!!.toDouble()))
        }
    }

    private fun TdfFile.summarize(model: SingleLocationBrowserModel, trackNumber: Int): List<ScoredInterval> {
        // Zoom level x leads to 2^x tiles. Correct zoom level is log base 2
        val zoom = (Math.log(1.0 * model.chromosome.length / model.chromosomeRange.length())
                    / Math.log(2.0)).toInt()

        return synchronized(this) {
            summarize(model.chromosome.name,
                      model.chromosomeRange.startOffset,
                      model.chromosomeRange.endOffset,
                      zoom)[trackNumber]
        }
    }

    override fun drawAxis(g: Graphics, width: Int, height: Int, drawInBG: Boolean, scales: List<Scale>) {
        TrackUIUtil.drawVerticalAxis(g, "", scales[0], drawInBG, width, height)
    }
}


