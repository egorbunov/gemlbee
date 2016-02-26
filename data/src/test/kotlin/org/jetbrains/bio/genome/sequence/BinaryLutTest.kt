package org.jetbrains.bio.genome.sequence

import org.junit.Test
import java.util.Random
import kotlin.test.assertTrue

class BinaryLutTest {
    @Test fun random() {
        val data = RANDOM.ints(512, 0, 1024).toArray()
        data.sort()

        val lut = BinaryLut.of(data, 24)
        for (iter in 0..99) {
            val key = RANDOM.nextInt(2048)
            assertSameIndex(data, key, lut)
        }
    }

    @Test fun powersOfTwo() {
        val data = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
        var lut = BinaryLut.of(data, 24)
        for (iter in 0..99) {
            val key = RANDOM.nextInt(data.max()!!)
            assertSameIndex(data, key, lut)
        }
    }

    private fun assertSameIndex(data: IntArray, key: Int, lut: BinaryLut) {
        val i = lut.binarySearch(data, key)
        val j = data.binarySearch(key)
        assertTrue(i == j
                   || (i >= 0 && j >= 0 && data[i] == data[j])
                   || (i < 0 && j < 0 && data[-(i + 1)] == data[-(j + 1)]))
    }

    companion object {
        private val RANDOM = Random()
    }
}