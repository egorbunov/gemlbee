package org.jetbrains.bio.browser.headless

import org.jetbrains.bio.browser.desktop.MultipleLocationsHeader
import org.jetbrains.bio.browser.desktop.SingleLocationHeader
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import kotlin.test.assertEquals

class HeaderTest {

    @Test fun testHeaderPosY() {
        val singleModel = SingleLocationBrowserModel(GenomeQuery("to1"))
        val loc = Location(0, 1000, Chromosome("to1", "chr1"), Strand.PLUS)
        val multiModel = MultipleLocationsBrowserModel.create(
                "foo",
                { gq -> listOf(SimpleLocRef(loc)) },
                singleModel)

        val singleLocationHeader = SingleLocationHeader(singleModel)
        val multipleLocationsHeader = MultipleLocationsHeader(multiModel)
        // Let's avoid handler blinking in web UI
        assertEquals(singleLocationHeader.pointerHandlerY, multipleLocationsHeader.pointerHandlerY)
    }
}
