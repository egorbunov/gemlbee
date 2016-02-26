package org.jetbrains.bio.browser.headless

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackViewRenderer
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Before
import org.junit.Test
import java.awt.Graphics
import java.awt.image.BufferedImage
import kotlin.test.assertFalse

/**
 * @author Roman.Chernyatchik
 */
class TrackViewRendererTest {
    private val model = SingleLocationBrowserModel(GenomeQuery("to1"))

    private val trackView = object : TrackView("title") {
        override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
            val range = model.range

            Thread.sleep(1000)

            rangeChanged = model.range != range
        }
    }


    private var paintCompleted = false
    private var rangeChanged = false

    @Before fun setUp() {
        rangeChanged = false
        paintCompleted = false
    }

    @Test fun paintRangeChanging() {
        Thread {
            while (!paintCompleted) {
                val range = model.range
                var end = range.endOffset + 1
                var start = range.startOffset + 1
                if (end >= model.length) {
                    end -= start
                    start = 0
                }
                model.range = Range(start, end)
                Thread.sleep(100)
            }

        }.start()

        paint()
        assertFalse(rangeChanged)
    }

    private fun paint() {
        val img = BufferedImage(1000, 50, BufferedImage.TYPE_INT_RGB)
        val uiModel = Storage()
        uiModel[TrackView.SHOW_AXIS] = true
        uiModel[TrackView.SHOW_LEGEND] = true
        TrackViewRenderer.paintToImage(img, model, img.width, img.height, trackView,
                                       CancellableState.instance,
                                       false, uiModel)
        paintCompleted = true
    }
}