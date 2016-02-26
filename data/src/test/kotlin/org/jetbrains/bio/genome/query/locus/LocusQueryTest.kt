package org.jetbrains.bio.genome.query.locus

import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.junit.Assert.assertNotSame
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocusQueryTest {
    @Test fun testParse() {
        for (locus in ImportantGenesAndLoci.REGULATORY) {
            assertNotNull(GeneLocusQuery.of(locus.id), "Cannot parse ${locus.id}")
        }
    }

    @Test fun testParseGeneLocusType500() {
        assertNotNull(GeneLocusQuery.of("tss500"))
        assertNotNull(GeneLocusQuery.of("tss-500"))
        assertNotNull(GeneLocusQuery.of("tss_500"))
        assertNotNull(GeneLocusQuery.of("tss 500"))
        assertNotNull(GeneLocusQuery.of("tes500"))
    }

    @Test fun testParseBoundaries() {
        assertNotNull(GeneLocusQuery.of("tss-500_500"))
        assertNotNull(GeneLocusQuery.of("tss-500_+500"))
        assertNotNull(GeneLocusQuery.of("tss-500;+500"))
        assertNotNull(GeneLocusQuery.of("tss-500,+500"))
        assertNotNull(GeneLocusQuery.of("tss-500,500"))
        assertNotNull(GeneLocusQuery.of("tss200 300"))
        assertNotNull(GeneLocusQuery.of("tss200 +300"))
        assertNotNull(GeneLocusQuery.of("tss 200 +300"))
        assertNotNull(GeneLocusQuery.of("tss_-200 +300"))
        assertNotNull(GeneLocusQuery.of("tss[-500..+500]"))
        assertNotNull(GeneLocusQuery.of("tss(-500,+500)"))
    }

    @Test fun testEquals() {
        assertEquals(WholeGeneQuery(), WholeGeneQuery())
        assertEquals(TssQuery(), TssQuery(-2000, 2000))
        assertNotSame(TssQuery(), TssQuery(-2000, 2001))
        assertEquals(TesQuery(), TesQuery(-2000, 2000))
        assertNotSame(TesQuery(), TesQuery(-2000, 2001))
    }
}
