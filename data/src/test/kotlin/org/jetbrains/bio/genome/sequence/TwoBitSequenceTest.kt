package org.jetbrains.bio.genome.sequence

import com.google.common.base.Strings
import htsjdk.samtools.util.SequenceUtil
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.ext.withTempDirectory
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.UCSC
import org.jetbrains.bio.io.FastaReader
import org.jetbrains.bio.sampling.Sampling
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwoBitSequenceTest {
    @Test fun testCharAtNoNs() {
        val sequence = getSequence(FULL, FULL, 1)
        val tbs = TwoBitSequence(sequence.length, IntArray(0), IntArray(0),
                                 getPack(FULL, FULL, 1))

        for (i in 0..tbs.length() - 1) {
            assertEquals(sequence[i], tbs.charAt(i))
        }
    }

    @Test fun testCharAtWithNs() {
        val sequence = getSequence(FULL, FULL, 1)
        val nStart1 = 2
        val nSize1 = 7
        val nStart2 = 15
        val nSize2 = 4
        val tbs = TwoBitSequence(sequence.length,
                                 intArrayOf(nStart1, nStart2),
                                 intArrayOf(nSize1, nSize2),
                                 getPack(FULL, FULL, 1))

        for (i in 0..tbs.length() - 1) {
            if ((i >= nStart1 && i < nStart1 + nSize1) ||
                (i >= nStart2 && i < nStart2 + nSize2)) {
                assertEquals('n', tbs.charAt(i))
            } else {
                assertEquals(sequence[i], tbs.charAt(i))
            }
        }
    }

    @Test fun testSubStringNoNs() {
        val sequence = getSequence(FULL, FULL, 1)
        val tbs = TwoBitSequence(sequence.length, IntArray(0), IntArray(0),
                                 getPack(FULL, FULL, 1))

        assertEquals("ca", tbs.substring(1, 3))
        assertEquals("ca", tbs.substring(1, 3, Strand.PLUS))
        assertEquals("tg", tbs.substring(1, 3, Strand.MINUS))
        assertEquals(sequence, tbs.substring(0, tbs.length()))
    }

    @Test fun testSubStringWithNs() {
        val sequence = getSequence(FULL, FULL, 1)
        val nStart1 = 2
        val nSize1 = 7
        val nStart2 = 15
        val nSize2 = 4
        val tbs = TwoBitSequence(sequence.length,
                                 intArrayOf(nStart1, nStart2),
                                 intArrayOf(nSize1, nSize2),
                                 getPack(FULL, FULL, 1))

        val ch1 = sequence[14]
        val ch2 = sequence[19]
        assertEquals(ch1 + "nnnn" + ch2, tbs.substring(14, 20))
        assertEquals(ch1 + "nnnn" + ch2, tbs.substring(14, 20, Strand.PLUS))
        assertEquals(SequenceUtil.complement(ch2.toByte()).toChar() +
                     "nnnn" +
                     SequenceUtil.complement(ch1.toByte()).toChar(),
                     tbs.substring(14, 20, Strand.MINUS))
        assertEquals(sequence.length, tbs.substring(0, tbs.length()).length)
    }

    @Test fun testAgainstFasta() = withTempDirectory("2bit") { tempDir ->
        val build = "droSec1"
        val fastaPath = tempDir / "scaffoldFa.gz"
        val tbPath = tempDir / "$build.2bit"

        UCSC.downloadTo(fastaPath, build, "bigZips", fastaPath.name)
        UCSC.downloadTo(tbPath, build, "bigZips", tbPath.name)

        val it = FastaReader.read(fastaPath)
        assertTrue(it.hasNext())

        val fr = it.asSequence().find { 'N' !in it.sequence }!!
        val tbs = TwoBitReader.read(tbPath, fr.description)
        assertEquals(fr.sequence.toLowerCase(), tbs.toString())
        assertEquals(fr.sequence.length,
                     TwoBitReader.length(tbPath, fr.description))
    }

    @Test fun testEncodeBasic() {
        val sequences = arrayOf("n", "nnn", "an", "annn", "na", "nnna",
                                "acgtttgcacacagnnnnnnnnacagnnnnngagagnn")

        for (s in sequences) {
            val tbs = TwoBitSequence.encode(s)
            assertEquals(s, tbs.toString())
        }
    }

    @Test fun testEncodeRandom() {
        for (i in 0..999) {
            val s = Sampling.sampleString("acgt".toCharArray(), i + 1)
            val tbs = TwoBitSequence.encode(s)
            assertEquals(s, tbs.toString())
        }
    }

    companion object {
        val FULL = Integer.BYTES

        fun getSequence(vararg sizes: Int): String {
            // Example from 'http://genome.ucsc.edu/FAQ/FAQformat.html#format7'.
            return Strings.repeat("tcag", sizes.sum())
        }

        fun getPack(vararg sizes: Int): IntArray {
            val pack = IntArray(sizes.size)
            for (i in sizes.indices) {
                var acc = Strings.repeat("00011011", sizes[i])
                if (sizes[i] < FULL) {
                    acc = Strings.padEnd(acc, FULL * java.lang.Byte.SIZE, '0')
                }

                pack[i] = Integer.parseInt(acc, 2)
            }

            return pack
        }
    }
}
