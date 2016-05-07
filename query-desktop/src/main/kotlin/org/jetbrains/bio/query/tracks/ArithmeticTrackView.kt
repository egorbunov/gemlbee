package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.query.parse.ArithmeticTrack
import java.awt.Color
import java.awt.Graphics
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class ArithmeticTrackView(name: String, val track: ArithmeticTrack, val binsNum: Int = 50): TrackView(name) {
    private val cache = HashMap<String, List<Double>>()

    /**
     * TODO: caching?
     */
    private fun cacheAndGet(model: SingleLocationBrowserModel): List<Double> {
//        val r = model.chromosome.range // whole chromosome range
//        return cache.getOrPut(model.chromosome.name) {
//            track.eval(ChromosomeRange(r.startOffset, r.endOffset, model.chromosome), binsNum)
//        }.slice(model.chromosomeRange.startOffset..model.chromosomeRange.endOffset)

        return track.eval(model.chromosomeRange, binsNum);
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {

        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]
        val (_min, max) = conf[TrackView.SCALES].first()
        val step = width / binsNum

        cacheAndGet(model).forEachIndexed { i, s ->
            val h = (s / max * height).toInt()
            g.color = Color.RED
            g.fillRect(i * step, height - h, step, h)
        }
    }

    override fun computeScales(model: SingleLocationBrowserModel,
                               conf: Storage): List<Scale> {
        val max = Math.ceil(cacheAndGet(model).max()!!)
        return listOf(Scale(0.0, max))
    }
}