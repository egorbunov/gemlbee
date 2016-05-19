package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.LociCompletion
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.query.desktop.DesktopInterpreter
import org.jetbrains.bio.browser.query.desktop.NewTrackViewListener
import org.jetbrains.bio.browser.query.desktop.TrackNameListener
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import java.awt.Dimension
import java.awt.Graphics
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class RenderComponentTest {
    @Test fun cancellationFailsTheFuture() {
        val flag = AtomicBoolean(false)
        val browser = DesktopGenomeBrowser(
                SingleLocationBrowserModel(GenomeQuery("to1")), arrayListOf(DummyTrackView(flag)),
                LociCompletion.DEFAULT_COMPLETION, DummyInterpreter())
        val c = RenderComponent(browser.trackViews.single(), browser, Storage())
        c.size = Dimension(640, 480)
        c.repaintRequired()

        while (!flag.get()) {}

        assertNull(c.currentImage)
        assertNotNull(c.currentTask)
        assertFailsWith(CancellationException::class) { c.currentTask!!.get() }
    }
}

private class DummyTrackView(private val flag: AtomicBoolean) : TrackView("dummy") {
    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        CancellableState.current().apply {
            cancel()
            flag.set(true)
            checkCanceled()
        }
    }
}

class DummyInterpreter: DesktopInterpreter {
    override fun isParseable(query: String): Boolean {
        return false
    }
    override fun interpret(query: String): String {
        return ""
    }
    override fun addNewTrackNameListener(listener: TrackNameListener) {
    }
    override fun removeNewTrackNameListener(listener: TrackNameListener) {
    }
    override fun addNewTrackViewListener(listener: NewTrackViewListener) {
    }
    override fun removeNewTrackViewListener(listener: NewTrackViewListener) {
    }
    override fun getAvailableNamedTracks(): List<String> {
        return emptyList()
    }
}