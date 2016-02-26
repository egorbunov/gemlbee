package org.jetbrains.bio.genome.sequence

import java.util.*

/**
 * A lookup table reducing the search space for binary search
 * over a sorted array of non-negative integers.
 *
 * See https://geidav.wordpress.com/2013/12/29/optimizing-binary-search.
 *
 * @author Sergei Lebedev
 * @since 06/07/15
 */
open class BinaryLut(private val index: IntArray,
                     /** The number of bits to use for LUT indexing. */
                     private val bits: Int,
                     /**
                      * The last LUT boundary. Keys larger than this should
                      * have the `toIndex` equal to the total number of
                      * elements indexed.
                      */
                     private val end: Int) {

    open fun binarySearch(data: IntArray, key: Int): Int {
        val idx = key ushr Integer.SIZE - bits
        val from = index[idx]
        val to = if (idx + 1 > end) data.size else index[idx + 1]
        return Arrays.binarySearch(data, from, to, key)
    }

    companion object {
        @JvmStatic fun of(data: IntArray, bits: Int): BinaryLut {
            require(bits < Integer.SIZE) { "bits must be <${Integer.SIZE}" }
            if (data.isEmpty()) {
                return EmptyBinaryLut(bits)
            }

            // Invariant: index[key(x)] = i, s.t. data[i] <= x
            val index = IntArray((1 shl bits) + 1)
            var bound = 0
            var ptr = 0
            for (i in 1..data.size - 1) {
                val nextBound = data[i] ushr Integer.SIZE - bits
                index[bound] = ptr

                if (nextBound > bound) {
                    ptr = i
                    Arrays.fill(index, bound + 1, nextBound, ptr)
                }

                bound = nextBound
            }

            Arrays.fill(index, bound, index.size, ptr)
            return BinaryLut(index, bits, bound)
        }
    }
}

private class EmptyBinaryLut(bits: Int) : BinaryLut(IntArray(0), bits, 0) {
    override fun binarySearch(data: IntArray, key: Int) = -1
}
