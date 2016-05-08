package org.jetbrains.bio.genome.containers

import com.google.common.collect.Iterables
import com.google.common.collect.UnmodifiableIterator
import gnu.trove.list.TIntList
import gnu.trove.list.array.TIntArrayList
import org.apache.commons.csv.CSVFormat
import org.jetbrains.bio.ext.bufferedReader
import org.jetbrains.bio.ext.bufferedWriter
import org.jetbrains.bio.genome.Range
import java.io.IOException
import java.nio.file.Path
import java.util.*

/**
 * A container for possibly overlapping ranges.
 *
 * @see [org.jetbrains.bio.genome.Range] for details.
 * @author Sergei Lebedev
 * @since 20/05/15
 */
class RangeList internal constructor(
        private val startOffsets: TIntList,
        private val endOffsets: TIntList) : Iterable<Range> {

    init {
        require(startOffsets.size() == endOffsets.size())
    }

    /**
     * Performs element-wise union on ranges in the two lists.
     */
    infix fun or(other: RangeList) = Iterables.concat(this, other).toRangeList()

    /**
     * Performs element-wise intersection on ranges in the two lists.
     */
    infix fun and(other: RangeList): RangeList {
        val acc = ArrayList<Range>()
        for (range in other) {
            var i = Math.max(0, lookup(range.startOffset))
            while (i < size() && // vvv inlined Range#intersects.
                   range.endOffset > startOffsets[i] &&
                   endOffsets[i] > range.startOffset) {
                val intersection = Range(
                        Math.max(startOffsets[i], range.startOffset),
                        Math.min(endOffsets[i], range.endOffset))
                acc.add(intersection)

                i++
            }
        }

        return acc.toRangeList()
    }

    operator fun contains(range: Range) = contains(range.startOffset, range.endOffset)

    fun contains(start: Int, end: Int): Boolean {
        val i = lookup(start)
        return i >= 0 && start >= startOffsets[i] && end <= endOffsets[i]
    }

    /**
     * Returns the length of intersection.
     */
    fun intersectionLength(range: Range): Int {
        var i = Math.max(0, lookup(range.startOffset))
        var result = 0
        while (i < size()) {
            // Iterate over nearby ranges.
            if (range.endOffset < startOffsets[i]) {
                break
            }

            result += Math.max(0, Math.min(range.endOffset, endOffsets[i])
                                  - Math.max(range.startOffset, startOffsets[i]))
            i++
        }

        return result
    }

    /**
     * Returns the index of the first range starting before the given [offset].
     */
    private fun lookup(offset: Int): Int {
        var i = startOffsets.binarySearch(offset)
        return if (i < 0) i.inv() - 1 else i
    }

    fun size() = startOffsets.size()

    fun length(): Int {
        var acc = 0
        for (i in 0..size() - 1) {
            acc += endOffsets[i] - startOffsets[i]
        }

        return acc
    }

    @Throws(IOException::class)
    fun save(path: Path) = CSVFormat.TDF.print(path.bufferedWriter()).use {
        for (range in this) {
            it.printRecord(range.startOffset, range.endOffset)
        }
    }

    override fun iterator() = object : UnmodifiableIterator<Range>() {
        private var current = 0

        override fun hasNext() = current < size()

        override fun next(): Range {
            val range = Range(startOffsets[current], endOffsets[current])
            current++
            return range
        }
    }

    override fun toString() = "[${joinToString(", ")}]"

    companion object {
        @Throws(IOException::class)
        fun load(path: Path) = CSVFormat.TDF.parse(path.bufferedReader()).use {
            it.map { Range(it[0].toInt(), it[1].toInt()) }.toRangeList()
        }
    }
}

/**
 * Constructs a list of complementary ranges.
 *
 *   |---------------------|  this
 *     |--|  |-----|     |-|  ranges
 *
 *   |-|  |--|     |-----|    result
 *
 * Input ranges may be in any order and are allowed to overlap.
 */
operator fun Range.minus(ranges: Iterable<Range>): List<Range> {
    if (!ranges.any()) {
        return listOf(this)
    }

    val acc = ArrayList<Range>()
    var current = startOffset
    for ((startOffset, endOffset) in ranges.toRangeList()) {
        if (startOffset > current) {
            acc.add(Range(current, startOffset))
        }

        current = endOffset
    }

    if (current < endOffset) {
        acc.add(Range(current, endOffset))
    }

    return acc
}

fun rangeList(vararg ranges: Range) = ranges.asIterable().toRangeList()

fun Sequence<Range>.toRangeList() = asIterable().toRangeList()

fun Iterable<Range>.toRangeList(): RangeList {
    val copy = sorted()

    // Try to compress overlapping ranges.
    val startOffsets = TIntArrayList()
    val endOffsets = TIntArrayList()
    var end = 0
    for (range in copy) {
        val start = range.startOffset
        if (startOffsets.isEmpty) {
            startOffsets.add(start)
        } else if (start > end) {
            endOffsets.add(end)
            startOffsets.add(start)
        }

        end = Math.max(end, range.endOffset)
    }

    if (!startOffsets.isEmpty) {
        endOffsets.add(end)
    }

    return RangeList(startOffsets, endOffsets)
}