package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.query.parse.ArithmeticTrack
import java.awt.Color
import java.awt.Graphics
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class FixBinnedArithmeticTrackView(name: String, val track: ArithmeticTrack, val binsNum: Int = 50): TrackView(name) {
    private fun getData(model: SingleLocationBrowserModel): List<Double> {
        return track.eval(model.chromosomeRange, binsNum);
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {

        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]
        val (_ignored, max) = conf[TrackView.SCALES].first()
        val step = width / binsNum

        getData(model).forEachIndexed { i, s ->
            val h = (s / max * height).toInt()
            g.color = Color.CYAN
            g.fillRect(i * step, height - h, step, h)
        }
    }

    override fun computeScales(model: SingleLocationBrowserModel,
                               conf: Storage): List<Scale> {
        val max = Math.ceil(getData(model).max()!!)
        return listOf(Scale(0.0, max))
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                Color.CYAN to "coverage")
    }


    override fun drawAxis(g: Graphics, conf: Storage,
                          width:Int, height:Int,
                          drawInBG: Boolean) {
        TrackUIUtil.drawVerticalAxis(g, "?", conf[SCALES].single(), drawInBG,
                width, height)
    }
}