package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Color
import java.awt.Graphics
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 *
 * Track to paint multiple [TrackView] in a single view
 * See [CombinedTrackView] as another tracks combination
 *
 * @author Evgeny Kurbatsky
 * @author Oleg Shpynov
 *
 * @since 6/1/15
 */
open class MultiTrackView(val tracks: List<TrackView>, title: String) : TrackView(title) {
    val size = tracks.size
    val TRACK_KEYS = (0 until size).map { Key<Storage>("TRACK$it") }

    init {
        require(tracks.isNotEmpty()) { "no tracks" }
        preferredHeight = (tracks.map { it.preferredHeight }.sum());
    }

    override fun preprocess(genomeQuery: GenomeQuery) {
        tracks.forEach { it.preprocess(genomeQuery) }
    }

    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        for ((i, track) in tracks.withIndex()) {
            val trackConf = conf.copy()
            trackConf[TrackView.HEIGHT] = conf[TrackView.HEIGHT] / size
            track.initConfig(model, trackConf)
            conf[TRACK_KEYS[i]] = trackConf
        }
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        for ((i, track) in tracks.withIndex()) {
            val tackConf = conf[TRACK_KEYS[i]]
            val scales = conf[TrackView.TRACK_SCALE]
            tackConf[TrackView.TRACK_SCALE] = relevantScales(i, scales)
            track.paintTrack(createNewGraphics(conf[TrackView.WIDTH], conf[TrackView.HEIGHT], g, i),
                             model, tackConf)
        }
    }

    private fun relevantScales(i: Int, scales: List<TrackView.Scale>): List<TrackView.Scale> {
        val offset = tracks.map { it.scalesNumber }.subList(0, i).sum()
        return scales.subList(offset, offset + tracks[i].scalesNumber)
    }

    override fun drawAxis(g: Graphics, width: Int, height: Int,
                          drawInBG: Boolean,
                          scales: List<TrackView.Scale>) {
        tracks.forEachIndexed { i, track ->
            track.drawAxis(createNewGraphics(width, height, g, i),
                           width, height / size,
                           drawInBG,
                           relevantScales(i, scales))
        }
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        tracks.forEachIndexed { i, track ->
            val graphics = createNewGraphics(width, height, g, i)
            graphics.font = TrackUIUtil.SMALL_FONT
            TrackUIUtil.drawString(graphics,
                                   track.title,
                                   100, TrackUIUtil.SMALL_FONT_HEIGHT - 3, Color.BLACK)
        }
        tracks.last().drawLegend(g, width, height, drawInBG)
    }

    private fun createNewGraphics(width: Int, height: Int, g: Graphics, i: Int)
            = g.create(0, i * height / size, width, height / size)

    override fun computeScale(model: SingleLocationBrowserModel,
                                     conf: Storage): List<TrackView.Scale> {
        return IntStream.range(0, tracks.size).mapToObj { i ->
            tracks[i].computeScale(model, conf[TRACK_KEYS[i]])
        }.collect(Collectors.toList<List<TrackView.Scale>>()).flatten()
    }

    override val scalesNumber = tracks.map { it.scalesNumber }.sum()
}
