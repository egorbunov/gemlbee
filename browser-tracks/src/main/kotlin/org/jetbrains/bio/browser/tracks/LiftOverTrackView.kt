package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.io.LiftOverRemapper
import java.awt.Color
import java.awt.Graphics

class LiftOverTrackView(private val trackView: TrackView,
                        private val master: GenomeQuery,
                        private val gq: GenomeQuery) : TrackView("${trackView.title} [${gq.build} -> ${master.build}]") {

    private val liftOverRemapper = LiftOverRemapper(master.build, gq.build)

    init {
        preferredHeight = trackView.preferredHeight
    }

    private fun remapModel(model: SingleLocationBrowserModel): SingleLocationBrowserModel? {
        val remapped = liftOverRemapper.remap(model.chromosomeRange)
        return if (remapped != null)
            SingleLocationBrowserModel(gq, remapped.chromosome, remapped.toRange(), null)
        else null
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val remapModel = remapModel(model)
        if (remapModel != null) {
            trackView.paintTrack(g, remapModel, conf)
        } else {
            TrackUIUtil.drawString(g,
                    "No liftover ${master.build} -> ${gq.build} available for ${model.chromosomeRange}. Try different range.",
                    30, g.fontMetrics.height + 5, Color.BLACK);
        }
    }

    override fun preprocess(genomeQuery: GenomeQuery) {
        trackView.preprocess(gq)
    }

    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        val remapModel = remapModel(model)
        if (remapModel != null) {
            trackView.initConfig(remapModel, conf)
        }
    }

    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        trackView.drawLegend(g, width, height, drawInBG)
    }

    override fun drawAxis(g: Graphics, conf: Storage, width: Int, height: Int, drawInBG: Boolean) {
        trackView.drawAxis(g, conf, width, height, drawInBG);
    }

    override fun computeScales(model: SingleLocationBrowserModel, conf: Storage): List<Scale> {
        val remapModel = remapModel(model)
        return if (remapModel != null) {
            trackView.computeScales(remapModel, conf)
        } else listOf(Scale.undefined())
    }
}
