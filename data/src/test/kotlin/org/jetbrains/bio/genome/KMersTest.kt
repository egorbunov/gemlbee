package org.jetbrains.bio.genome

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.test.assertEquals

class KMersTest {
    @Test fun testEncodeDecode() {
        val k = 10
        val alphabet = "abc"
        for (item in KMers.generate(k, alphabet)) {
            val idx = KMers.encode(item, alphabet)
            assertEquals(item, KMers.decode(idx, k, alphabet))
        }
    }

    @Test fun testGenerate() {
        assertArrayEquals(arrayOf("aa"), KMers.generate(2, "a"))
        assertArrayEquals(arrayOf("aa", "ab", "ac", "ba", "bb", "bc", "ca", "cb", "cc"),
                          KMers.generate(2, "abc"))
    }

    @Test fun testOf() {
        val kmers = KMers.of(2, "abracadabra")
        assertEquals(7, kmers.size())
        assertEquals(2, kmers["ab"])
        assertEquals(2, kmers["br"])
        assertEquals(2, kmers["ra"])
        assertEquals(1, kmers["ac"])
        assertEquals(1, kmers["ca"])
        assertEquals(1, kmers["ad"])
        assertEquals(1, kmers["da"])
    }

    @Test fun testOfOffsetLength() {
        val s = "abracadabra"
        val kmers = KMers.of(2, s, 3, 3)  // aca
        assertEquals(2, kmers.size())
        assertEquals(1, kmers["ac"])
        assertEquals(1, kmers["ca"])
    }

    @Test fun testOfEmpty() {
        assertEquals(0, KMers.of(2, "").size())
        assertEquals(0, KMers.of(10, "aca").size())
    }
}