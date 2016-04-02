package org.jetbrains.bio.browser.tracks

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
) : TrackView(combinedTitle(backGroundView, frontView)), TrackViewWithControls {

    init {
        preferredHeight = Math.max(backGroundView.preferredHeight,
                                   frontView.preferredHeight)
    }

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

    override fun computeScales(model: SingleLocationBrowserModel, conf: Storage): List<Scale> {
        val bgConf = conf[BG_CONFIG]
        val bgScales = backGroundView.computeScales(model, bgConf)
        bgConf[TrackView.SCALES] = bgScales

        val fgConf = conf[FG_CONFIG]
        val fgScales = frontView.computeScales(model, fgConf)
        fgConf[TrackView.SCALES] = fgScales

        return bgScales + fgScales
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        backGroundView.paintTrack(g, model, conf[BG_CONFIG])

        with(g as Graphics2D) {
            composite = AlphaComposite.SrcOver
            color = TrackUIUtil.BG_MASK
            fillRect(0, 0, conf[TrackView.WIDTH], conf[TrackView.HEIGHT])
        }

        frontView.paintTrack(g, model, conf[FG_CONFIG])
    }

    override fun drawAxis(g: Graphics, conf: Storage, width: Int, height: Int, drawInBG: Boolean) {
        backGroundView.drawAxis(g, conf[BG_CONFIG], width, height, true)
        frontView.drawAxis(g, conf[FG_CONFIG], width, height, false)
    }

    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        backGroundView.drawLegend(g, width, height, true)
        frontView.drawLegend(g, width, height, false)
    }

    override fun removeListener(listener: TrackViewListener) {
        super.removeListener(listener)
        backGroundView.removeListener(listener)
        frontView.removeListener(listener)
    }

    override fun addListener(listener: TrackViewListener) {
        super.addListener(listener)
        backGroundView.addListener(listener)
        frontView.addListener(listener)
    }

    override fun createTrackControlsPane(): Pair<JPanel, Consumer<Boolean>>? {
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

        return contentPane to object : Consumer<Boolean> {
            override fun accept(enabled: Boolean) {
                frontPane.second.andThen(bgPane.second).accept(enabled)
            }
        }
    }

    companion object {
        private val BG_CONFIG: Key<Storage> = Key("BG_CONFIG")
        private val FG_CONFIG: Key<Storage> = Key("FG_CONFIG")

        private fun combinedTitle(bgTrack: TrackView, fgTrack: TrackView) = when {
            bgTrack.title.isEmpty() -> fgTrack.title
            fgTrack.title.isEmpty() -> bgTrack.title
            else -> "${fgTrack.title} || Back: ${bgTrack.title}"
        }
    }
}
