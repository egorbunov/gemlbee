package org.jetbrains.bio.io

import org.jetbrains.bio.ext.bufferedReader
import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.sampling.Sampling
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class FastaTest {
    @Test fun testWriteOne() {
        withTempFile("sample", ".fa") { path ->
            val record = FastaRecord("description", "ACGT")
            listOf(record).write(path)

            path.bufferedReader().use {
                val lines = it.lineSequence().toList()
                assertEquals(2, lines.size)
                val (description, sequence) = lines
                assertEquals(">${record.description}", description)
                assertEquals(record.sequence, sequence)
            }
        }
    }

    @Test fun testWriteRead() {
        val r = Random()
        val alphabet = "ACGT".toCharArray()
        val records = (0..2).map {
            FastaRecord("sequence$it",
                        Sampling.sampleString(alphabet, r.nextInt(20 - 1) + 1))
        }

        withTempFile("random", ".fa.gz") { path ->
            records.write(path)
            assertEquals(records, FastaReader.read(path).asSequence().toList())
        }
    }
}
