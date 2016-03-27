package org.jetbrains.bio.browser.command

import org.jetbrains.bio.browser.History
import org.jetbrains.bio.browser.changeTo
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.truncate
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.zoom
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Before
import org.junit.Test
import java.awt.Graphics
import java.util.*
import kotlin.test.assertEquals

class HistoryTest {
    lateinit var browser: HeadlessGenomeBrowser
    lateinit var history: History

    @Before fun setUp() {
        browser = createBrowser()
        history = History()
    }

    private fun createBrowser(): HeadlessGenomeBrowser {
        return HeadlessGenomeBrowser(
                SingleLocationBrowserModel(GenomeQuery("to1")),
                listOf(object : TrackView("test") {
                    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
                        try {
                            Thread.sleep(300)
                        } catch (e: InterruptedException) {
                            // Ignore
                        }
                    }
                }), emptyMap())
    }

    @Test fun truncateSmallerStack() {
        val s = ArrayDeque<Int>().apply { push(42) }
        s.truncate(10)
        assertEquals(1, s.size)
        assertEquals(listOf(42), s.toList())
    }

    @Test fun truncateLargerStack() {
        val s = ArrayDeque<Int>()
        for (i in 0..4) {
            s.push(i)
        }

        assertEquals(5, s.size)

        s.truncate(2)
        assertEquals(2, s.size)
        assertEquals(4, s.pop())
        assertEquals(3, s.pop())
    }

    @Test fun redoUndo() {
        val range = browser.model.range
        history.execute(browser.model.zoom(2.0))
        history.undo()
        assertEquals(range, browser.model.range)
    }

    @Test fun redoUndoClear() {
        history.execute(browser.model.zoom(2.0))
        val range = browser.model.range
        history.clear()
        history.undo()
        assertEquals(range, browser.model.range)
    }

    @Test fun changeModel() {
        val gq = GenomeQuery("to1")
        val last = gq.get().last()
        history.execute(browser.model.changeTo(SingleLocationBrowserModel(gq, last)) { o, n ->
            browser.model = n
        })
        assertEquals(last.length, browser.model.range.endOffset)
        history.undo()
        assertEquals(gq.get().first().length, browser.model.range.endOffset)
    }
}