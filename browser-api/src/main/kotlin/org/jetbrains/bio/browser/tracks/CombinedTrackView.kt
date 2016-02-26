package org.jetbrains.bio.browser.tracks

import com.google.common.collect.ImmutableList
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.function.Consumer
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Background / foreground track view.
 * See [MultiTrackView] as another tracks combination
 *
 * @author Roman.Chernyatchik
 */
class CombinedTrackView(private val backGroundView: TrackView,
                        private val frontView: TrackView
) : TrackView(""), TrackViewWithControls {

    companion object {
        private val BG_CONFIG: Key<Storage> = Key("BG_CONFIG")
        private val FG_CONFIG: Key<Storage> = Key("FG_CONFIG")

        private fun combinedTitle(bgTrack: TrackView, fgTrack: TrackView) = when {
            bgTrack.title.isEmpty() -> fgTrack.title
            fgTrack.title.isEmpty() -> bgTrack.title
            else -> "${fgTrack.title} || Back: ${bgTrack.title}"
        }
    }

    init {
        preferredHeight = Math.max(backGroundView.preferredHeight,
                                   frontView.preferredHeight)
    }

    override var title: String
        get() = CombinedTrackView.combinedTitle(backGroundView, frontView)
        set(value) { throw UnsupportedOperationException("Not supported") }

    override val scalesNumber = frontView.scalesNumber + backGroundView.scalesNumber


    override fun preprocess(genomeQuery: GenomeQuery) {
        backGroundView.preprocess(genomeQuery)
        frontView.preprocess(genomeQuery)
    }


    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        val bgConf = conf.copy()
        backGroundView.initConfig(model, bgConf)

        val fgConf = conf.copy()
        frontView.initConfig(model, fgConf)

        conf[BG_CONFIG] = bgConf
        conf[FG_CONFIG] = fgConf
    }

    override fun computeScale(model: SingleLocationBrowserModel, conf: Storage): List<TrackView.Scale> {
        val bgConf = conf[BG_CONFIG]
        val bgScales = backGroundView.computeScale(model, bgConf)
        bgConf[TrackView.TRACK_SCALE] = bgScales

        val fgConf = conf[FG_CONFIG]
        val fgScales = frontView.computeScale(model, fgConf)
        fgConf[TrackView.TRACK_SCALE] = fgScales

        return ImmutableList.builder<TrackView.Scale>().addAll(bgScales).addAll(fgScales).build()
    }

    @SuppressWarnings("ConstantConditions")
    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val height = conf[TrackView.HEIGHT]
        val width = conf[TrackView.WIDTH]
        val scales = conf[TrackView.TRACK_SCALE]

        // BG TRACK:

        // In case of multiple locations browser conf scales will be updated to
        // common max/min scales

        val bgConfig = conf[BG_CONFIG]
        bgConfig[TrackView.TRACK_SCALE] = relevantScales(true, scales)
        backGroundView.paintTrack(g, model, bgConfig)

        // change blending to 'Stamp': front view over bg and don't affect it by plot mode
        (g as Graphics2D).composite = AlphaComposite.SrcOver

        // Decrease BG brightness: draw partly transparent white rectangle
        g.color = TrackUIUtil.BG_MASK
        g.fillRect(0, 0, width, height)

        // FRONT TRACK:

        // In case of multiple locations browser conf scales will be updated to
        // common max/min scales
        val fgConfig = conf[FG_CONFIG]
        fgConfig[TrackView.TRACK_SCALE] = relevantScales(false, scales)
        frontView.paintTrack(g, model, fgConfig)
    }

    override fun drawAxis(g: Graphics, width: Int, height: Int, drawInBG: Boolean, scales: List<TrackView.Scale>) {
        // BG TRACK:
        backGroundView.drawAxis(g, width, height, true, relevantScales(true, scales))

        // FRONT TRACK:
        frontView.drawAxis(g, width, height, false, relevantScales(false, scales))
    }

    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        backGroundView.drawLegend(g, width, height, true)
        frontView.drawLegend(g, width, height, false)
    }

    override fun removeEventsListener(listener: TrackViewListener) {
        super.removeEventsListener(listener)
        backGroundView.removeEventsListener(listener)
        frontView.removeEventsListener(listener)
    }

    override fun addEventsListener(listener: TrackViewListener) {
        super.addEventsListener(listener)
        backGroundView.addEventsListener(listener)
        frontView.addEventsListener(listener)
    }


    override fun createTrackControlsPane(): kotlin.Pair<JPanel, Consumer<Boolean>>? {
        val frontPane = when (frontView) {
            is TrackViewWithControls -> frontView.createTrackControlsPane()
            else -> null
        }

        val bgPane = when (backGroundView) {
            is TrackViewWithControls -> backGroundView.createTrackControlsPane()
            else -> null
        }

        if (frontPane == null) {
            return bgPane
        }

        if (bgPane == null) {
            return frontPane
        }

        val contentPane = JPanel()
        contentPane.layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        val bgContPane = JPanel()
        bgContPane.layout = BoxLayout(bgContPane, BoxLayout.X_AXIS)
        bgContPane.add(JLabel("Back | "))
        bgContPane.add(bgPane.first)
        contentPane.add(bgContPane)

        val fgContPane = JPanel()
        fgContPane.layout = BoxLayout(fgContPane, BoxLayout.X_AXIS)
        fgContPane.add(JLabel("Front | "))
        fgContPane.add(frontPane.first)
        contentPane.add(fgContPane)

        return contentPane to Consumer<kotlin.Boolean> {
            enabled -> frontPane.second.andThen(bgPane.second).accept(enabled)
        }
    }

    private fun relevantScales(background: Boolean, scales: List<TrackView.Scale>): List<TrackView.Scale> {
        val start = if (background) 0 else backGroundView.scalesNumber
        //noinspection ConstantConditions
        val end = if (background) backGroundView.scalesNumber else scales.size

        return (start until end).map { scales[it] }
    }
}
