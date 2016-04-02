package org.jetbrains.bio.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class POITest {

    @Test fun testEmpty() {
        assertEquals("[]", POI(listOf<String>()).filterByModification("H3K4me3").toString())
    }

    @Test fun testAll() {
        assertEquals("[all]", POI(listOf("all")).filterByModification("H3K4me3").toString())
        assertTrue(POI(listOf("all")).transcription())
    }

    @Test fun testModificationAll() {
        assertEquals("[all@tss]", POI(listOf("all@tss")).filterByModification("H3K4me2").toString())
        assertEquals("[H3K4me3@all]", POI(listOf("H3K4me3@all")).filterByModification("H3K4me3").toString())
        assertEquals("[H3K4me3@all, H3K4me3@tss]",
                POI(listOf("H3K4me3@all", "H3K4me3@tss")).filterByModification("H3K4me3").toString())
    }

    @Test fun testModification() {
        assertEquals("[H3K4me2@tss]", POI(listOf("H3K4me3@all", "H3K4me2@tss")).filterByModification("H3K4me2").toString())
        assertEquals("[H3K4me2@tss[-100,100], H3K4me2@transcript]",
                POI(listOf("H3K4me3@all", "H3K4me2@tss[-100,100]", "H3K4me2@transcript")).filterByModification("H3K4me2").toString())
    }

    @Test fun testTranscription() {
        assertTrue(POI(listOf("transcription")).transcription())
        assertTrue(POI(listOf("transcription[{0.25,1.0,0.25}]")).transcription())
    }

    @Test fun testParameterizedModification() {
        assertEquals("[H3K4me2[500][0.9]@tss]",
                POI(listOf("H3K4me3[100%][0.5]@all", "H3K4me2[500][0.9]@tss")).filterByModification("H3K4me2").toString())
        assertEquals("[H3K4me2[500][0.9]@tss[-100;100], H3K4me2[100][1]@transcript]",
                POI(listOf("H3K4me3[100%][0.5]@all", "H3K4me2[500][0.9]@tss[-100;100]", "H3K4me2[100][1]@transcript"))
                        .filterByModification("H3K4me2").toString())
    }
}