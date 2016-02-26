package org.jetbrains.bio.browser.util

import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.*
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.CancellationException
import java.util.stream.Collectors

/**
 * Util class to paint either trackView for browser model or for multiple locations.
 *
 * @author Oleg Shpynov
 */
object TrackViewRenderer {
    @JvmField val TITLE_HEIGHT = TrackUIUtil.DEFAULT_FONT_HEIGHT + 10

    @Throws(CancellationException::class)
    @JvmStatic fun paintHeadless(model: BrowserModel,
                                        g: Graphics,
                                        trackView: TrackView,
                                        width: Int, height: Int,
                                        cancellableState: CancellableState) {
        (g as Graphics2D).background = Color.WHITE
        g.clearRect(0, 0, width, TITLE_HEIGHT)
        if (trackView.title.isNotBlank()) {
            TrackUIUtil.drawString(g, trackView.title, 5, TITLE_HEIGHT / 2 + 5, Color.BLACK)
        }

        val uiModel = Storage()
        uiModel[TrackView.SHOW_AXIS] = true
        uiModel[TrackView.SHOW_LEGEND] = true

        val plotHeight = height - TITLE_HEIGHT
        if (plotHeight > 0) {
            val bufferedPlot = BufferedImage(width, plotHeight, BufferedImage.TYPE_INT_ARGB)
            paintToImage(bufferedPlot, model, width, plotHeight, trackView, cancellableState, true, uiModel)

            g.drawImage(bufferedPlot, 0, TITLE_HEIGHT + 1, null)
            // Component separator
            g.color = Color.GRAY
            g.drawLine(0, 0, width, 0)
        }
    }

    @Throws(CancellationException::class)
    @JvmStatic fun paintToImage(bufferedImage: BufferedImage,
                                       model: BrowserModel,
                                       width: Int, height: Int,
                                       trackView: TrackView,
                                       cancellableState: CancellableState,
                                       drawLocationsSeparator: Boolean,
                                       uiModel: Storage) {

        val modelCopy = model.copy()

        val config = uiModel.copy()
        config[TrackView.WIDTH] = width
        config[TrackView.HEIGHT] = height

        val g2d = bufferedImage.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        try {
            if (modelCopy is MultipleLocationsBrowserModel) {
                val modelsAndConfigs
                        = prepareModelsAndConfigs(modelCopy.genomeQuery,
                        config,
                        modelCopy.visibleLocations(),
                        width)

                // Init configs:
                for ((locModel, locConf) in modelsAndConfigs) {
                    trackView.initConfig(locModel, locConf)
                }

                // Scales
                computeScale(trackView, config, modelsAndConfigs)

                var x = 0
                for ((locModel, locConf) in modelsAndConfigs) {
                    cancellableState.checkCanceled()
                    val locWidth = locConf[TrackView.WIDTH]
                    val trackGraphics = g2d.create(x, 0, locWidth, height) as Graphics2D
                    x += locWidth
                    try {
                        trackView.paintTrack(trackGraphics, locModel, locConf)
                    } finally {
                        trackGraphics.dispose()
                    }
                }

                // Draw common separators in case multi locations model
                if (drawLocationsSeparator) {
                    TrackUIUtil.drawGrid(g2d, width, 0, height, modelCopy)
                }
            } else {
                val singleModelCopy = modelCopy as SingleLocationBrowserModel
                trackView.initConfig(singleModelCopy, config)
                computeScale(trackView, config, listOf(singleModelCopy to config))
                trackView.paintTrack(g2d, singleModelCopy, config)
            }

            // all have same scale
            if (config[TrackView.SHOW_AXIS]) {
                g2d.composite = AlphaComposite.SrcOver
                trackView.drawAxis(g2d, width, height, false, config[TrackView.TRACK_SCALE])
            }

            // Draw legend
            if (config[TrackView.SHOW_LEGEND]) {
                g2d.composite = AlphaComposite.SrcOver
                trackView.drawLegend(g2d, width, height, false)
            }
        } finally {
            g2d.dispose()
        }
    }

    /**
     * Prepares models and configurations for given list of locations, used in case of multiple location model
     */
    private fun prepareModelsAndConfigs(genomeQuery: GenomeQuery,
                                        config: Storage,
                                        locationReferences: List<LocationReference>,
                                        width: Int): List<Pair<SingleLocationBrowserModel, Storage>> {

        // Locations cumulative length
        val locWidths = TrackUIUtil.locationsWidths(locationReferences, width)

        val modelAndConfList = ArrayList<Pair<SingleLocationBrowserModel, Storage>>()
        locationReferences.forEachIndexed { i, locRef ->
            val locWidth = locWidths[i]
            if (locWidth > 0) {
                val model = SingleLocationBrowserModel(genomeQuery, locRef.location.chromosome,
                        locRef.location.toRange(), locRef)
                val newConfiguration = config.copy()
                newConfiguration[TrackView.WIDTH] = locWidth

                modelAndConfList.add(model to newConfiguration)
            }
        }
        return modelAndConfList
    }

    /**
     * Compute scales for all the models, i.e. difference locations and summarize them
     * using [org.jetbrains.bio.browser.view.TrackView.Scale.or]
     */
    private fun computeScale(trackView: TrackView,
                             storage: Storage,
                             modelsAndConfigs: List<Pair<SingleLocationBrowserModel, Storage>>) {
        val locsScales = modelsAndConfigs.stream().map { m2Config ->
            trackView.computeScale(m2Config.first, m2Config.second)
        }.collect(Collectors.toList<List<TrackView.Scale>>())

        val commonScales = (0 until trackView.scalesNumber).map { layer ->
            when {
                locsScales.isEmpty() -> TrackView.Scale.undefined()
                else -> locsScales.map { it[layer] }.reduce { sc1, sc2 -> sc1.union(sc2) }
            }
        }

        storage[TrackView.TRACK_SCALE] = commonScales
        for ((model, conf) in modelsAndConfigs) {
            conf[TrackView.TRACK_SCALE] = commonScales
        }
    }

}
