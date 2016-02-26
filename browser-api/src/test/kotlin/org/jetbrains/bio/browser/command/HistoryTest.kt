package org.jetbrains.bio.browser.command

import junit.framework.TestCase
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableTask
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Graphics
import java.util.concurrent.CancellationException

class HistoryTest: TestCase() {
    lateinit var browser: HeadlessGenomeBrowser
    lateinit var history: History

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        CancellableTask.resetCounter()
        browser = createBrowser()
        history = History()
    }

    private fun createBrowser(): HeadlessGenomeBrowser {
        return HeadlessGenomeBrowser(
                SingleLocationBrowserModel(GenomeQuery("to1")),
                listOf(object : TrackView("test") {
                    @Throws(CancellationException::class)

                    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
                        try {
                            Thread.sleep(300)
                        } catch (e: InterruptedException) {
                            // Ignore
                        }

                    }
                }), emptyMap())
    }

    fun testRedoUndo() {
        val range = browser.browserModel.range
        history.execute(Commands.createZoomGenomeRegionCommand(browser.browserModel, 2.0))
        history.undo()
        assertEquals(range, browser.browserModel.range)
    }

    fun testRedoUndoClear() {
        history.execute(Commands.createZoomGenomeRegionCommand(browser.browserModel, 2.0))
        val range = browser.browserModel.range
        history.clear()
        history.undo()
        assertEquals(range, browser.browserModel.range)
    }

    fun testChangeModel() {
        val gq = GenomeQuery("to1")
        val last = gq.get().last()
        history.execute(Commands.createChangeModelCommand(browser, SingleLocationBrowserModel(gq, last), { o, n ->
            browser.browserModel = n
        }))
        assertEquals(last.length, browser.browserModel.range.endOffset)
        history.undo()
        assertEquals(gq.get().first().length, browser.browserModel.range.endOffset)
    }

}