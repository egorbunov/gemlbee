package org.jetbrains.bio.genome.containers

import com.google.common.collect.Iterables
import org.jetbrains.bio.genome.Range
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RangeListTest {
    @Test fun testEmpty() {
        assertFalse(Range(0, 12) in rangeList())
        assertEquals(0, rangeList().intersectionLength(Range(0, 12)))
    }

    @Test fun testSingle() {
        assertTrue(Range(0, 12) in rangeList(Range(0, 12)))
        assertFalse(Range(0, 20) in rangeList(Range(0, 12)))
        assertEquals(12, rangeList(Range(0, 12)).intersectionLength(Range(0, 12)))
    }

    @Test fun testSingleBigSegment() {
        assertTrue(Range(10, 20) in rangeList(Range(0, 100)))
    }

    @Test fun testSingleExceedingRange() {
        assertFalse(Range(-100, 100) in rangeList(Range(0, 10)))
    }

    @Test fun testIntersectionLength() {
        val rl = rangeList(Range(0, 10))
        assertFalse(Range(5, 15) in rl)
        assertEquals(5, rl.intersectionLength(Range(5, 15)))
        assertEquals(10, rl.intersectionLength(Range(0, 15)))
        assertEquals(5, rl.intersectionLength(Range(5, 10)))
        assertEquals(5, rl.intersectionLength(Range(-10, 5)))
        assertEquals(10, rl.intersectionLength(Range(-10, 10)))
        assertEquals(0, rl.intersectionLength(Range(20, 30)))
        assertEquals(0, rl.intersectionLength(Range(10, 15)))
    }

    @Test fun testDoubleIntersectionLength() {
        assertTrue(Range(5, 15) in rangeList(Range(0, 10), Range(10, 20)))
        assertFalse(Range(5, 15) in rangeList(Range(0, 10), Range(11, 20)))
        assertEquals(10, rangeList(Range(0, 10), Range(10, 20)).intersectionLength(Range(5, 15)))
        assertEquals(5, rangeList(Range(0, 10), Range(20, 30)).intersectionLength(Range(15, 25)))
        assertEquals(0, rangeList(Range(0, 10), Range(10, 20)).intersectionLength(Range(-10, -5)))

        val rl = rangeList(Range(0, 10), Range(20, 30), Range(40, 50))
        assertEquals(1, rl.intersectionLength(Range(35, 41)))
        assertEquals(0, rl.intersectionLength(Range(30, 40)))
        assertEquals(1, rl.intersectionLength(Range(29, 40)))
        assertEquals(1, rl.intersectionLength(Range(30, 41)))
        assertEquals(2, rl.intersectionLength(Range(29, 41)))
        assertEquals(20, rl.intersectionLength(Range(5, 45)))
    }

    @Test fun testNonSorted() {
        assertTrue(Range(11, 12) in rangeList(Range(10, 20), Range(0, 5)))
    }

    @Test fun testLength() {
        assertEquals(0, rangeList().length())
        assertEquals(15, rangeList(Range(10, 20), Range(0, 5)).length())
        assertEquals(30, rangeList(Range(0, 20), Range(10, 30)).length())
    }

    @Test fun testIntersectingRanges() {
        assertEquals(30, rangeList(Range(0, 20), Range(10, 30)).intersectionLength(Range(0, 30)))
    }

    @Test fun testIteration() {
        assertEquals(0, Iterables.size(rangeList()))

        val ranges = arrayOf(Range(0, 20), Range(25, 30))
        assertEquals(ranges.toList(), rangeList(*ranges).toList())
    }

    @Test fun testOrWithEmptyOrSelf() {
        val rl = rangeList(Range(0, 10), Range(20, 30))
        assertEquals(rl.toList(), (rl or rangeList()).toList())
        assertEquals(rl.toList(), (rl or rl).toList())
    }

    @Test fun testOrWithoutIntersections() {
        val rl1 = rangeList(Range(0, 10))
        val rl2 = rangeList(Range(20, 30))
        assertEquals(rangeList(Range(0, 10), Range(20, 30)).toList(),
                (rl1 or rl2).toList())
    }

    @Test fun testOrWithIntersections() {
        assertEquals(
                rangeList(Range(0, 30)).toList(),
                (rangeList(Range(0, 10)) or rangeList(Range(5, 30))).toList())
    }

    @Test fun testAndWithEmpty() {
        var rl = rangeList(Range(0, 10), Range(20, 30))
        assertEquals(emptyList<Range>(), (rl and rangeList()).toList())
    }

    @Test fun testAndWithSelf() {
        var rl = rangeList(Range(0, 10), Range(20, 30))
        assertEquals(rl.toList(), (rl and rl).toList())
    }

    @Test fun testAnd() {
        val rl1 = rangeList(Range(0, 10))
        val rl2 = rangeList(Range(5, 30))
        assertEquals(rangeList(Range(5, 10)).toList(), (rl1 and rl2).toList())
        assertEquals(rangeList(Range(5, 10), Range(25, 30)).toList(),
                ((rl1 or rangeList(Range(25, 30))) and rl2).toList())
        assertEquals(rangeList(Range(5, 10), Range(25, 30)).toList(),
                (rl2 and (rl1 or rangeList(Range(25, 30)))).toList())
    }

    @Test fun testRangeMinus() {
        val universe = Range(0, 100)
        assertEquals(listOf(universe), universe - emptyList())
        assertEquals(listOf(Range(0, 20), Range(40, 100)),
                     universe - listOf(Range(20, 40)))
        assertEquals(listOf(Range(40, 100)), universe - listOf(Range(0, 40)))
        assertEquals(listOf(Range(0, 40)), universe - listOf(Range(40, 100)))
        assertEquals(emptyList<Range>(),
                     universe - listOf(Range(0, 40), Range(40, 100)))
    }
}