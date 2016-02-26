package org.jetbrains.bio.data

import org.junit.Test
import kotlin.test.assertNotNull

class DataSetTest {
    @Test fun toDataType() {
        assertNotNull("RNA-Seq".toDataType())
        assertNotNull("RNA-SEQ".toDataType())
        ChipSeqTarget.values().forEach { assertNotNull(it.name.toDataType()) }
    }
}
