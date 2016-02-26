package org.jetbrains.bio.data.frame

/**
 * Manually copy-pasted routines for primitive arrays.
 *
 * @author Sergei Lebedev
 * @since 26/03/15
 */

import com.google.common.collect.ComparisonChain
import org.apache.commons.math3.random.MersenneTwister
import org.apache.commons.math3.random.RandomGenerator
import java.util.*
import java.util.stream.IntStream

/**
 * Randomized linear-time selection algorithm.
 *
 * @author Sergei Lebedev
 * @since 01/04/15
 */
object QuickSelect {
    private val RANDOM: RandomGenerator = MersenneTwister()

    /**
     * Returns the n-th order statistic of a given array.
     *
     * Invariant:  left <= n <= right
     */
    tailrec fun select(values: DoubleArray, left: Int, right: Int, n: Int): Double {
        assert(left <= n && n <= right)

        if (left == right) {
            return values[left]
        }

        var split = left + RANDOM.nextInt(right - left + 1)
        split = partition(values, left, right, split)
        return when {
            split == n -> values[n]
            split > n -> select(values, left, split - 1, n)
            else -> select(values, split + 1, right, n)
        }
    }

    /**
     * Partitions values around the pivot.
     *
     * Invariants: p = partition(values, left, right, p)
     * for all i <  p: values[i] <  values[p]
     * for all i >= p: values[p] >= values[p]
     */
    fun partition(values: DoubleArray, left: Int, right: Int, p: Int): Int {
        val pivot = values[p]
        swap(values, p, right)  // move to end.

        var ptr = left
        for (i in left..right - 1) {
            if (values[i] < pivot) {
                swap(values, i, ptr)
                ptr++
            }
        }

        swap(values, right, ptr)
        return ptr
    }

    private fun swap(values: DoubleArray, i: Int, j: Int) {
        val tmp = values[i]
        values[i] = values[j]
        values[j] = tmp
    }
}

fun DoubleArray.quantile(q: Double = 0.5): Double {
    require(isNotEmpty()) { "no data" }
    val n = Math.ceil(q * (size - 1).toDouble()).toInt()
    return QuickSelect.select(clone(), 0, size - 1, n)
}

/**
 * Sort direction for [.sorted].
 *
 * @author Sergei Lebedev
 * @since 05/12/14
 */
enum class SortOrder {
    ASC, // Ascending.
    DESC;  // Descending.

    fun <T> apply(comparator: Comparator<T>): Comparator<T> {
        return when (this) {
            ASC -> comparator
            DESC -> comparator.reversed()
        }
    }
}

/**
 * Returns the ordering of the original array which makes it sorted.
 *
 * If `res` is the result of method call, then for any appropriate
 *
 *   i < j => values[res[i]] <= values[res[j]]
 *
 * E.g., `values[res[0]]` is the minimum of the array.
 */
fun ByteArray.sortedOrder(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { IntIntPair(this[it].toInt(), it) }
            .sorted(order.apply(IntIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

fun ShortArray.sortedOrder(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { IntIntPair(this[it].toInt(), it) }
            .sorted(order.apply(IntIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

fun IntArray.sortedOrder(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { IntIntPair(this[it], it) }
            .sorted(order.apply(IntIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

fun LongArray.sortedOrder(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { LongIntPair(this[it], it) }
            .sorted(order.apply(LongIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

fun FloatArray.sortedOrder(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { DoubleIntPair(this[it].toDouble(), it) }
            .sorted(order.apply(DoubleIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

fun DoubleArray.argSort(order: SortOrder): IntArray {
    return IntStream.range(0, size)
            .mapToObj { DoubleIntPair(this[it], it) }
            .sorted(order.apply(DoubleIntPair.COMPARATOR))
            .mapToInt { it.second }
            .toArray()
}

private class DoubleIntPair(val first: Double, val second: Int) {
    companion object {
        val COMPARATOR: Comparator<DoubleIntPair> = Comparator { p1, p2 ->
            ComparisonChain.start()
                    .compare(p1.first, p2.first)
                    .compare(p1.second, p2.second)
                    .result()
        }
    }
}

private class IntIntPair(val first: Int, val second: Int) {
    companion object {
        val COMPARATOR: Comparator<IntIntPair> = Comparator { p1, p2 ->
            ComparisonChain.start()
                    .compare(p1.first, p2.first)
                    .compare(p1.second, p2.second)
                    .result()
        }
    }
}

private class LongIntPair(val first: Long, val second: Int) {
    companion object {
        val COMPARATOR: Comparator<LongIntPair> = Comparator { p1, p2 ->
            ComparisonChain.start()
                    .compare(p1.first, p2.first)
                    .compare(p1.second, p2.second)
                    .result()
        }
    }
}

/**
 * Reorders the array in place using the given ordering, so that
 * `values[i]` after the method call is equal to `values[indices[i]]`
 * before the method call.
 *
 * When `indices` are the result of `sorted(values)`, the method makes
 * the array sorted.
 */
fun ByteArray.reorder(indices: IntArray) {
    require(size == indices.size) { "non-conformable arrays" }
    val copy = indices.clone()
    for (i in indices) {
        val value = this[i]
        var j = i
        while (true) {
            val k = copy[j]
            copy[j] = j
            if (k == i) {
                this[j] = value
                break
            } else {
                this[j] = this[k]
                j = k
            }
        }
    }
}

fun ShortArray.reorder(indices: IntArray) {
    require(size == indices.size) { "non-conformable arrays" }
    val copy = indices.clone()
    for (i in indices) {
        val value = this[i]
        var j = i
        while (true) {
            val k = copy[j]
            copy[j] = j
            if (k == i) {
                this[j] = value
                break
            } else {
                this[j] = this[k]
                j = k
            }
        }
    }
}

fun IntArray.reorder(indices: IntArray) {
    require(size == indices.size) { "non-conformable arrays" }
    val copy = indices.clone()
    for (i in indices) {
        val value = this[i]
        var j = i
        while (true) {
            val k = copy[j]
            copy[j] = j
            if (k == i) {
                this[j] = value
                break
            } else {
                this[j] = this[k]
                j = k
            }
        }
    }
}

fun LongArray.reorder(indices: IntArray) {
    require(size == indices.size) { "non-conformable arrays" }
    val copy = indices.clone()
    for (i in indices) {
        val value = this[i]
        var j = i
        while (true) {
            val k = copy[j]
            copy[j] = j
            if (k == i) {
                this[j] = value
                break
            } else {
                this[j] = this[k]
                j = k
            }
        }
    }
}

fun DoubleArray.reorder(indices: IntArray) {
    require(size == indices.size) { "non-conformable arrays" }
    val copy = indices.clone()
    for (i in indices) {
        val value = this[i]
        var j = i
        while (true) {
            val k = copy[j]
            copy[j] = j
            if (k == i) {
                this[j] = value
                break
            } else {
                this[j] = this[k]
                j = k
            }
        }
    }
}

operator fun ShortArray.div(other: ShortArray): FloatArray {
    require(size == other.size) { "non-conformable arrays" }
    val res = FloatArray(size)
    for (i in res.indices) {
        res[i] = this[i].toFloat() / other[i]
    }

    return res
}

fun Array<IntArray>.transpose(): Array<IntArray> {
    check(isNotEmpty())
    val numRows = size
    val numCols = first().size
    return Array(numCols) { j ->
        val row = IntArray(numRows)
        for (i in 0..numRows - 1) {
            row[i] = this[i][j]
        }

        row
    }
}