package org.jetbrains.bio.browser

import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbstractGenomeBrowserTest {
    @Test fun testHandleMultipleLocModel_Empty() {
        val gq = GenomeQuery("to1")
        val browser = HeadlessGenomeBrowser(SingleLocationBrowserModel(gq),
                                            emptyList(), emptyMap())
        assertFalse(browser.model is MultipleLocationsBrowserModel)
        assertFalse(browser.handleMultipleLocationsModel("housekeeping"))
    }

    @Test fun testHandleMultipleLocModel() {
        val gq = GenomeQuery("to1")
        val chr = Chromosome("to1", "chr1")
        val locRef = SimpleLocRef(Location(0, 1000, chr, Strand.PLUS))

        val browser = HeadlessGenomeBrowser(
                SingleLocationBrowserModel(gq),
                emptyList(),
                mapOf("housekeeping" to { qg: GenomeQuery -> listOf(locRef) }))
        assertTrue(browser.handleMultipleLocationsModel("housekeeping"))
        assertTrue(browser.model is MultipleLocationsBrowserModel)
        assertEquals(0, browser.model.range.startOffset)
        assertEquals(1000, browser.model.range.endOffset)
        assertTrue(browser.handleMultipleLocationsModel("housekeeping"))
    }

    @Test fun testHandleMultipleLocModel_RangeFilter() {
        val gq = GenomeQuery("to1")
        val chr = Chromosome("to1", "chr1")
        val locRef = SimpleLocRef(Location(0, 1000, chr, Strand.PLUS))

        val browser = HeadlessGenomeBrowser(
                SingleLocationBrowserModel(gq),
                emptyList<TrackView>(),
                mapOf("housekeeping" to { qg: GenomeQuery -> listOf(locRef) }))
        assertTrue(browser.handleMultipleLocationsModel("housekeeping"))
        assertEquals(0, browser.model.range.startOffset)
        assertEquals(1000, browser.model.range.endOffset)
        assertTrue(browser.handleMultipleLocationsModel("housekeeping:100-200"))
        assertEquals(100, browser.model.range.startOffset)
        assertEquals(200, browser.model.range.endOffset)
    }
}
