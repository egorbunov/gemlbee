package org.jetbrains.bio.data

import org.junit.Test
import kotlin.test.assertNotNull

class DataSetTest {
    @Test fun toDataType() {
        assertNotNull("Transcription".toDataType())
        assertNotNull("transcription".toDataType())
        assertNotNull("Methylation".toDataType())
        assertNotNull("methylation".toDataType())
        ChipSeqTarget.values().forEach { assertNotNull(it.name.toDataType()) }
    }
}
