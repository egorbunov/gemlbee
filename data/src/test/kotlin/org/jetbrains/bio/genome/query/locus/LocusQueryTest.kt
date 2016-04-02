package org.jetbrains.bio.genome.query.locus

import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.junit.Assert.assertNotSame
import org.junit.Test
import kotlin.test.assertEquals

class LocusQueryTest {

    @Test fun testEquals() {
        assertEquals(TranscriptQuery(), TranscriptQuery())
        assertEquals(TssQuery(), TssQuery(-2000, 2000))
        assertNotSame(TssQuery(), TssQuery(-2000, 2001))
        assertEquals(TesQuery(), TesQuery(-2000, 2000))
        assertNotSame(TesQuery(), TesQuery(-2000, 2001))
    }

    @Test fun testParseAll() {
        assertEquals("tss[-5000..-2500], tss[-2000..2000], " +
                "utr5, utr3, cds, introns, exons, transcript, " +
                "tes[-2000..2000], tes[2500..5000]", toString("all"))
    }

    @Test fun testParseLoci() {
        for (locus in ImportantGenesAndLoci.REGULATORY) {
            assertEquals(locus.id, toString(locus.id))
        }
    }

    @Test fun testParseTssWidth() {
        assertEquals("tss[-500..500]", toString("tss500"))
        assertEquals("tes[-500..500]", toString("tes500"))
        assertEquals("tss[-500..500]", toString("tss[500]"))
        assertEquals("tss[-1000..1000], tss[-1500..1500]", toString("tss[{1000,2000,500}]"))
    }

    @Test fun testParseTssWithBoundaries() {
        assertEquals("tss[-500..500]", toString("tss[-500..500]"))
        assertEquals("tes[-500..500]", toString("tes[-500..500]"))
        assertEquals("tss[-2000..1000], tss[-2000..1500], tss[-1500..1000], tss[-1500..1500]",
                toString("tss[{-2000,-1000,500}..{1000,2000,500}]"))
    }

    private fun toString(locus: String) = LocusQuery.parse(locus).map { it.id }.joinToString(", ")
}
