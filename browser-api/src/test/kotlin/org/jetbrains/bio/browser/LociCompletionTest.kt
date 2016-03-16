package org.jetbrains.bio.browser

import org.jetbrains.bio.browser.LociCompletion.ABSTRACT_LOCATION_PATTERN
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LociCompletionTest {
    @Test fun testParseAsChrLocation() {
        val genomeQuery = GenomeQuery("to1", "chr1")

        assertNotNull(LociCompletion.parse("chr1", genomeQuery))
        assertNotNull(LociCompletion.parse("chr1:1234-2345", genomeQuery))

        assertNull(LociCompletion.parse("chr1:1aa", genomeQuery))
        assertNull(LociCompletion.parse("chr1:1234", genomeQuery))
        assertNull(LociCompletion.parse("chr2:1234-2345", genomeQuery))

        assertEquals("chr1:+[1234, 4568)",
                     LociCompletion.parse("chr1:1234-4568", genomeQuery)!!.location.toString())
        assertEquals("chr1:+[2100234, 2100568)",
                     LociCompletion.parse("chr1:2.100.234-2.100.568", genomeQuery)!!.location.toString())
        assertEquals("chr1:+[2100234, 2100568)",
                     LociCompletion.parse("chr1:2,100,234-2,100,568", genomeQuery)!!.location.toString())
    }

    @Test fun testParseChrX() {
        val query = GenomeQuery("to1", "chr2", "chrX")
        assertNotNull(LociCompletion.parse("chrX", query))
    }

    @Test fun testPredicate() {
        assertTrue(ABSTRACT_LOCATION_PATTERN.matcher("h3K4me3_h1<>huvec").matches())
        assertTrue(ABSTRACT_LOCATION_PATTERN.matcher("h3K4me3_h1<>huvec:1-1000").matches())
    }

    @Test fun testCompletionResolve() {
        val genomeQuery = GenomeQuery("to1")
        LociCompletion[genomeQuery].forEach { s -> assertNotNull(LociCompletion.parse(s, genomeQuery)) }
    }
}

@RunWith(Parameterized::class)
class LociCompletionPatternTest(private val s: String) {
    @Test fun abstractLocationPatternMatches() {
        assertTrue(ABSTRACT_LOCATION_PATTERN.matcher(s).matches())
        assertTrue(ABSTRACT_LOCATION_PATTERN.matcher(s + ":1-1000").matches())
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic fun data() = listOf("tss-500_500", "tss-500_+500", "tss-500;+500",
                                       "tss-500,+500", "tss-500,500", "tss200 300",
                                       "tss200 +300", "tss 200 +300", "tss_-200 +300")
    }
}