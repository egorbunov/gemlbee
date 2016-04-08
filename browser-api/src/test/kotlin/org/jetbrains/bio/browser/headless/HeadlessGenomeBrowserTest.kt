package org.jetbrains.bio.browser.headless

import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import java.awt.Graphics
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeavyTrackView(private val cb: () -> Unit) : TrackView("heavy") {
    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1))
        } catch(ignored: InterruptedException) {}

        cb()
    }
}

class HeadlessGenomeBrowserTest {
    @Test fun paintCanBeCancelled() {
        val genomeQuery = GenomeQuery("to1")
        val chromosome = Chromosome("to1", "chr1")
        val references = listOf(SimpleLocRef(Location(0, 1000, chromosome, Strand.PLUS)),
                                SimpleLocRef(Location(100, 2000, chromosome, Strand.MINUS)))

        val acc = AtomicInteger(0)
        val trackViews = (0..1024).map { HeavyTrackView { acc.incrementAndGet() } }
        val browser = HeadlessGenomeBrowser(
                SingleLocationBrowserModel(genomeQuery),
                trackViews,
                mapOf("housekeeping" to { qg: GenomeQuery -> references }))

        assertFailsWith(CancellationException::class) {
            val cancellableState = CancellableState.current()
            Thread {
                while (acc.get() == 0) {}
                cancellableState.cancel()
            }.start()

            browser.paint(1024)
        }

        assertTrue(acc.get() < 1024)
    }
}
