package org.jetbrains.bio.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class POITest {

    @Test fun testEmpty() {
        assertEquals("[]", POI(listOf<String>()).patternsForModification("H3K4me3").toString())
    }

    @Test fun testAll() {
        assertEquals("[all]", POI(listOf("all")).patternsForModification("H3K4me3").toString())
        assertTrue(POI(listOf("all")).transcription())
    }

    @Test fun testApplicableAll() {
        assertTrue(POI.isApplicable("H3K4me2", "all@tss"))
        assertTrue(POI.isApplicable("H3K4me3", "H3K4me3@all"))
        assertTrue(POI.isApplicable("H3K4me3", "all"))
    }

    @Test fun testModification() {
        assertTrue(POI.isApplicable("H3K4me3", "H3K4me3@all"))
        assertTrue(POI.isApplicable("H3K4me3", "H3K4me3[100%][0.5]@tss[-2000..2000]"))
        assertTrue(POI.isApplicable("H3K4me3",
                "H3K4me3[{1000,2000,500}][{0.1,0.9,0.1}]@tss[-{2000,-1000,500}..{1000,2000,500}]"))
        assertTrue(POI.isApplicable("transcription", "transcription"))
    }

    @Test fun testTranscription() {
        assertTrue(POI(listOf("transcription")).transcription())
        assertTrue(POI(listOf("transcription[4]")).transcription())
    }

    @Test fun testParameterizedModification() {
        assertEquals("[H3K4me2[500][0.9]@tss]",
                POI(listOf("H3K4me3[100%][0.5]@all", "H3K4me2[500][0.9]@tss")).patternsForModification("H3K4me2").toString())
        assertEquals("[H3K4me2[500][0.9]@tss[-100;100], H3K4me2[100][1]@transcript]",
                POI(listOf("H3K4me3[100%][0.5]@all", "H3K4me2[500][0.9]@tss[-100;100]", "H3K4me2[100][1]@transcript"))
                        .patternsForModification("H3K4me2").toString())
    }

    @Test fun testLociForModification() {
        assertEquals("[tss[-2000..2000]]",
                POI(listOf("H3K4me2[{500,1000,500}][{0.9]@tss")).lociForModification("H3K4me2").toString())
        assertEquals("[tss[-5000..-2500], tss[-2000..2000], " +
                "utr5, utr3, cds, introns, exons, transcript, " +
                "tes[-2000..2000], tes[2500..5000]]",
                POI(listOf("all")).lociForModification("H3K4me3").toString())
        assertEquals("[tss[-2000..0], tss[-2000..1000]]", POI(listOf("all@tss[{-2000,-1000,1000}..{0,2000,1000}]")).lociForModification("H3K4me3").toString())
    }

    @Test fun testExtractModification() {
        assertEquals("H3K36me3", POI.modification("H3K36me3@tss"));
        assertEquals("methylation", POI.modification("NO methylation@tss"));
        assertEquals("H3K4me3", POI.modification("H3K4me3[100%][0.5]@tss"));
    }

    @Test fun testLocus() {
        assertEquals("tss", POI.locus("H3K4me3@tss"))
        assertEquals("tss[{-2000,1000,500}..1000]", POI.locus("methylation@tss[{-2000,1000,500}..1000]"))
    }

    @Test fun testParameters() {
        assertEquals("", POI.parameters("H3K4me3@tss"))
        assertEquals("[100%][0.5]", POI.parameters("H3K4me3[100%][0.5]"))
        assertEquals("[10][0.8]", POI.parameters("methylation[10][0.8]"))
    }
}