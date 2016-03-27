package org.jetbrains.bio.browser.headless

import com.google.common.base.Stopwatch
import org.apache.log4j.Logger
import org.jetbrains.bio.browser.Command
import org.jetbrains.bio.browser.GenomeBrowser
import org.jetbrains.bio.browser.createAAGraphics
import org.jetbrains.bio.browser.desktop.TrackListComponent
import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.browser.util.TrackViewRenderer
import org.jetbrains.bio.ext.awaitAll
import org.jetbrains.bio.ext.parallelStream
import org.jetbrains.bio.ext.time
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/**
 * @author Oleg Shpynov
 * @since 25/12/14
 */
class HeadlessGenomeBrowser(override var model: BrowserModel,
                            override val trackViews: List<TrackView>,
                            locationsMap: Map<String, (GenomeQuery) -> List<LocationReference>>)
:
        GenomeBrowser {

    override val locationsMap = locationsMap.mapKeys { it.key.toLowerCase() }

    @Throws(CancellationException::class)
    fun paint(width: Int) = paint(model, trackViews, width)

    override fun execute(command: Command?) {
        // Web browser client maintains its own history, so the server
        // doesn't have to. See [DesktopGenomeBrowser.execute] for
        // alternative behaviour.
        command?.redo()
    }

    companion object {
        private val LOG = Logger.getLogger(HeadlessGenomeBrowser::class.java)

        @JvmField val SCREENSHOT_WIDTH = 1600
        @JvmField val SCREENSHOT_HEIGHT = 1200

        /**
         * Renders a list of track views to an image.
         *
         * @returns the resulting image or `null` if a [CancellationException]
         *          occurred during rendering.
         */
        @Throws(CancellationException::class)
        @JvmStatic fun paint(browserModel: BrowserModel, trackViews: List<TrackView>,
                             width: Int): BufferedImage? {
            val stopwatch = Stopwatch.createStarted()

            val headerView = GenomeBrowser.createHeaderView(browserModel)
            val headerHeight = headerView.preferredSize.height
            val heights = getTrackViewHeights(trackViews)
            val totalHeight = headerHeight + heights.sum()
            val image = BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB)

            val g2d = image.createAAGraphics()

            // paint grid
            TrackListComponent.paintGrid(browserModel, g2d, width, totalHeight)

            // paint header
            val headerGraphics = g2d.create(0, 0, width, headerHeight)
            headerView.size = Dimension(width, headerHeight)
            headerView.paint(headerGraphics)
            if (browserModel is MultipleLocationsBrowserModel) {
                TrackUIUtil.drawGrid(headerGraphics,
                                     width,
                                     headerHeight - 35,
                                     headerHeight,
                                     browserModel)
            }
            headerGraphics.dispose()

            // Paint tracks in parallel
            val cancellableState = CancellableState.current()
            cancellableState.reset()
            val tasks = ArrayList<Callable<Unit>>(trackViews.size)
            var y = headerHeight
            for (i in trackViews.indices) {
                cancellableState.checkCanceled()
                val trackView = trackViews[i]
                val trackHeight = heights[i]
                val trackGraphics = g2d.create(0, y, width, trackHeight)
                y += trackHeight
                tasks.add(Callable {
                    cancellableState.checkCanceled()
                    LOG.time(message = "Paint tracks: ${trackView.title}") {
                        TrackViewRenderer.paintHeadless(browserModel, trackGraphics, trackView,
                                                        width, trackHeight,
                                                        cancellableState)
                    }
                })
            }

            val executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
            executor.awaitAll(tasks)
            executor.shutdown()
            stopwatch.stop()
            LOG.debug("Paint tracks in $stopwatch")
            return image
        }

        private fun getTrackViewHeights(trackViews: List<TrackView>): IntArray {
            return trackViews.parallelStream()
                    .mapToInt { t -> t.preferredHeight + TrackViewRenderer.TITLE_HEIGHT }
                    .toArray()
        }
    }
}
