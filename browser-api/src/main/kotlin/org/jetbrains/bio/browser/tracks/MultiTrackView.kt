package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.*

/**
 *
 * Track to paint multiple [TrackView] in a single view
 * See [CombinedTrackView] as another tracks combination
 *
 * @author Evgeny Kurbatsky
 * @author Oleg Shpynov
 *
 * @since 06/01/15
 */
open class MultiTrackView(val tracks: List<TrackView>, title: String) : TrackView(title) {
    private val TRACK_KEYS = tracks.indices.map { Key<Storage>("TRACK$it") }

    init {
        require(tracks.isNotEmpty()) { "no tracks" }

        preferredHeight = tracks.sumBy { it.preferredHeight }
    }

    override fun preprocess(genomeQuery: GenomeQuery) {
        tracks.forEach { it.preprocess(genomeQuery) }
    }

    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        for ((i, track) in tracks.withIndex()) {
            val trackConf = conf.copy()
            trackConf[TrackView.HEIGHT] = conf[TrackView.HEIGHT] / tracks.size
            track.initConfig(model, trackConf)
            conf[TRACK_KEYS[i]] = trackConf
        }
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]
        for ((i, track) in tracks.withIndex()) {
            track.paintTrack(createNewGraphics(width, height, g, i),
                             model, conf[TRACK_KEYS[i]])
        }
    }

    override fun drawAxis(g: Graphics, conf: Storage, width: Int, height: Int,
                          drawInBG: Boolean) {
        tracks.forEachIndexed { i, track ->
            track.drawAxis(createNewGraphics(width, height, g, i),
                           conf[TRACK_KEYS[i]],
                           width, height / tracks.size, drawInBG)
        }
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        tracks.forEachIndexed { i, track ->
            val graphics = createNewGraphics(width, height, g, i)
            graphics.font = TrackUIUtil.SMALL_FONT
            TrackUIUtil.drawString(graphics,
                                   track.title,
                                   100, TrackUIUtil.SMALL_FONT_HEIGHT - 3, Color.BLACK,
                                   0.6f)
        }
        tracks.last().drawLegend(g, width, height, drawInBG)
    }

    private fun createNewGraphics(width: Int, height: Int, g: Graphics, i: Int)
            = g.create(0, i * height / tracks.size, width, height / tracks.size) as Graphics2D

    override fun computeScales(model: SingleLocationBrowserModel,
                               conf: Storage): List<Scale> {
        val acc = ArrayList<Scale>()
        for ((trackKey, track) in TRACK_KEYS zip tracks) {
            val trackConf = conf[trackKey]
            val trackScales = track.computeScales(model, trackConf)
            trackConf[SCALES] = trackScales
            acc.addAll(trackScales)
        }

        return acc
    }
}
