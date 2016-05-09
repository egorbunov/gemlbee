package org.jetbrains.bio.query.containers

import com.google.common.collect.Iterators
import com.google.common.collect.Lists
import com.google.common.collect.Ordering
import org.jetbrains.bio.genome.Range
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 09.05.16
 */

class SortedRangeList internal constructor(val bounds: ArrayList<Bound>): Iterable<Range> {
    data class Bound(val v: Int, val isOpen: Boolean) {
        var mark = 0
    }

    init {
        require(bounds.size % 2 == 0)
    }

    companion object {
        val OPEN_BOUND_FIRST = Comparator<Bound> { a, b ->
            if (a.v != b.v) {
                Integer.compare(a.v, b.v)
            } else if (a.isOpen && b.isOpen) {
                0
            } else if (a.isOpen) {
                -1
            } else {
                1
            }
        }

        val CLOSE_BOUND_FIRST = Comparator<Bound> { a, b ->
            if (a.v != b.v) {
                Integer.compare(a.v, b.v)
            } else if (a.isOpen && b.isOpen) {
                0
            } else if (a.isOpen) {
                1
            } else {
                -1
            }
        }
    }

    fun size(): Int {
        return bounds.size / 2
    }

    infix fun or(other: SortedRangeList): SortedRangeList {
        val it = Iterators.mergeSorted(listOf(bounds.iterator(), other.bounds.iterator()), OPEN_BOUND_FIRST)
        return SortedRangeList(removeOverlaps(Lists.newArrayList<Bound>(it)))
    }

    infix fun and(other: SortedRangeList): SortedRangeList {
        bounds.forEach { it.mark = 0 }
        other.bounds.forEach { it.mark = 1 }
        val it = Iterators.mergeSorted(listOf(bounds.iterator(), other.bounds.iterator()), CLOSE_BOUND_FIRST)
        val bounds_ = Lists.newArrayList<Bound>(it)
        val newBounds = ArrayList<Bound>()
        var isLOpened = false
        var isROpened = false
        for (b in bounds_) {
            // here we assume, that ranges in this and other lists are non-overlapping
            if (b.mark == 0 && b.isOpen) isLOpened = true
            if (b.mark == 1 && b.isOpen) isROpened = true
            if (b.isOpen && isLOpened && isROpened) {
                newBounds.add(Bound(b.v, b.isOpen))
            }
            if (!b.isOpen && isLOpened && isROpened) {
                newBounds.add(Bound(b.v, b.isOpen))
            }
            if (b.mark == 0 && !b.isOpen) isLOpened = false
            if (b.mark == 1 && !b.isOpen) isROpened = false
        }
        return SortedRangeList(newBounds)
    }

    override fun iterator(): Iterator<Range> {
        return object: Iterator<Range> {
            var i = 0
            override fun hasNext(): Boolean {
                return i + 1 < bounds.size
            }

            override fun next(): Range {
                val a = i
                val b = i + 1
                assert(bounds[a].isOpen && (!bounds[b].isOpen))
                i += 2
                return Range(bounds[a].v, bounds[b].v)
            }

        }
    }
}

private fun removeOverlaps(bounds_: Iterable<SortedRangeList.Bound>): ArrayList<SortedRangeList.Bound> {
    val bounds = ArrayList<SortedRangeList.Bound>()
    var openedCnt = 0
    for (b in bounds_) {
        if (openedCnt == 0) bounds.add(b)
        if (b.isOpen) openedCnt += 1 else openedCnt -= 1
        if (openedCnt == 0) bounds.add(SortedRangeList.Bound(b.v, b.isOpen))
    }
    return bounds
}

fun Iterable<Range>.toSortedRangeList(): SortedRangeList {
    val bounds_ = ArrayList<SortedRangeList.Bound>()
    this.forEach {
        bounds_.add(SortedRangeList.Bound(it.startOffset, true))
        bounds_.add(SortedRangeList.Bound(it.endOffset, false))
    }
    if (!Ordering.from(SortedRangeList.OPEN_BOUND_FIRST).isOrdered(bounds_)) {
        bounds_.sort(SortedRangeList.OPEN_BOUND_FIRST)
    }
    return SortedRangeList(removeOverlaps(bounds_))
}
