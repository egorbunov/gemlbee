package org.jetbrains.bio.genome

import com.google.common.math.IntMath
import org.jetbrains.bio.genome.sequence.asNucleotideSequence
import org.junit.Test
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RangeTest {
    @Test fun testContainsOffset() {
        assertTrue(0 in Range(0, 10))
        assertTrue(9 in Range(0, 10))
        assertFalse(10 in Range(0, 10))
        assertFalse(-1 in Range(0, 10))
    }

    @Test fun testIntersects() {
        assertFalse(Range(10, 11) intersects Range(11, 12))
        assertFalse(Range(11, 12) intersects Range(10, 11))
        assertTrue(Range(0, 10) intersects Range(0, 10))
        assertTrue(Range(0, 10) intersects Range(5, 15))
        assertTrue(Range(5, 15) intersects Range(0, 10))
        assertTrue(Range(0, 10) intersects Range(4, 5))
        assertTrue(Range(4, 5) intersects Range(0, 10))
    }

    @Test fun testIntersection() {
        assertEquals(Range(0, 0), Range(10, 11) intersection Range(11, 12))
        assertEquals(Range(11, 12), Range(10, 12) intersection Range(11, 12))
    }

    @Test fun testSlice() {
        var bins = 0
        var prevBound = 0
        for (r in Range(0, 103).slice(10)) {
            assertEquals(prevBound, r.startOffset)
            prevBound = r.endOffset
            assertTrue(r.length() <= 10)
            bins++
        }

        assertEquals(11, bins)
        assertEquals(103, prevBound)
        assertEquals(IntMath.divide(103, 10, RoundingMode.CEILING), bins)
    }
}

class LocationTest {
    private var chromosome = Chromosome("to1", "chr1")

    @Test fun testGetLength_EmptyLocation() {
        assertEquals(0, Location(0, 0, chromosome, Strand.PLUS).length())
        assertEquals(0, Location(1, 1, chromosome, Strand.PLUS).length())
    }

    @Test fun testGetLength() {
        assertEquals(2, Location(0, 2, chromosome, Strand.PLUS).length())
        assertEquals(2, Location(1, 3, chromosome, Strand.PLUS).length())
    }

    @Test fun testConvert5BoundRelativeToAbsoluteOffset() {
        assertEquals(0, Location(1, 5, chromosome, Strand.PLUS).get5BoundOffset(-1))
        assertEquals(1, Location(1, 5, chromosome, Strand.PLUS).get5BoundOffset(0))
        assertEquals(2, Location(1, 5, chromosome, Strand.PLUS).get5BoundOffset(1))

        assertEquals(5, Location(1, 5, chromosome, Strand.MINUS).get5BoundOffset(-1))
        assertEquals(4, Location(1, 5, chromosome, Strand.MINUS).get5BoundOffset(0))
        assertEquals(3, Location(1, 5, chromosome, Strand.MINUS).get5BoundOffset(1))
    }

    @Test fun testConvert53BoundRelativeToAbsoluteOffset() {
        assertEquals(3, Location(1, 5, chromosome, Strand.PLUS).get3BoundOffset(-1))
        assertEquals(4, Location(1, 5, chromosome, Strand.PLUS).get3BoundOffset(0))
        assertEquals(5, Location(1, 5, chromosome, Strand.PLUS).get3BoundOffset(1))

        assertEquals(2, Location(1, 5, chromosome, Strand.MINUS).get3BoundOffset(-1))
        assertEquals(1, Location(1, 5, chromosome, Strand.MINUS).get3BoundOffset(0))
        assertEquals(0, Location(1, 5, chromosome, Strand.MINUS).get3BoundOffset(1))
    }

    @Test fun testGetSequence() {
        val plusSequence = Location(1, 10, chromosome, Strand.PLUS).sequence
        val rcSequence = plusSequence.asNucleotideSequence()
                .substring(0, plusSequence.length, Strand.MINUS)
        assertEquals(rcSequence, Location(1, 10, chromosome, Strand.MINUS).sequence)
    }

    @Test fun testAroundStart_Plus() {
        val location = Location(100, 200, chromosome, Strand.PLUS)
        assertEquals("chr1:+[100, 106)",
                     RelativePosition.AROUND_START.of(location, 0, 6).toString())
        assertEquals("chr1:+[100, 200)",
                     RelativePosition.AROUND_START.of(location, 0, 100).toString())
        assertEquals("chr1:+[0, 2100)",  // Edge case.
                     RelativePosition.AROUND_START.of(location, -2000, 2000).toString())
    }

    @Test fun testAroundStart_Minus() {
        val location = Location(100, 200, chromosome, Strand.MINUS)
        assertEquals("chr1:-[194, 200)",
                     RelativePosition.AROUND_START.of(location, 0, 6).toString())
        assertEquals("chr1:-[100, 200)",
                     RelativePosition.AROUND_START.of(location, 0, 100).toString())
    }

    @Test fun testAroundEnd_Plus() {
        val location = Location(100, 200, chromosome, Strand.PLUS)
        assertEquals("chr1:+[199, 205)",
                     RelativePosition.AROUND_END.of(location, 0, 6).toString())
        assertEquals("chr1:+[100, 200)",
                     RelativePosition.AROUND_END.of(location, -99, 1).toString())
    }

    @Test fun testAroundEnd_Minus() {
        val location = Location(100, 200, chromosome, Strand.MINUS)
        assertEquals("chr1:-[95, 101)",
                     RelativePosition.AROUND_END.of(location, 0, 6).toString())
        assertEquals("chr1:-[100, 200)",
                     RelativePosition.AROUND_END.of(location, -99, 1).toString())
    }

    @Test fun testAroundWhole_Plus() {
        val location = Location(100, 200, chromosome, Strand.PLUS)
        assertEquals("chr1:+[100, 200)",
                     RelativePosition.AROUND_WHOLE_SEGMENT.of(location, 0, 1).toString())
    }

    @Test fun testAroundWhole_End() {
        val location = Location(100, 200, chromosome, Strand.MINUS)
        assertEquals("chr1:-[100, 200)",
                     RelativePosition.AROUND_WHOLE_SEGMENT.of(location, 0, 1).toString())
    }

    @Test fun testComparator() {
        val location1 = Location(0, 100, chromosome, Strand.PLUS)
        val location2 = Location(0, 100, chromosome, Strand.MINUS)
        assertNotEquals(0, location1.compareTo(location2))

        val location3 = Location(0, 100, chromosome, Strand.PLUS)
        assertEquals(0, location1.compareTo(location3))

        val location4 = Location(0, 100, Chromosome("to1", "chr2"), Strand.PLUS)
        assertEquals(-1, location1.compareTo(location4))
        assertEquals(1, location4.compareTo(location1))
    }
}
