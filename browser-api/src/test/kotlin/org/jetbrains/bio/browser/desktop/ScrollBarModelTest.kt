package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.ChangeListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollBarModelTest {
    private var browserModel = SingleLocationBrowserModel(GenomeQuery("to1"))
    private val chromosome1 = Chromosome("to1", "chr1")
    private val chromosome2 = Chromosome("to1", "chr2")
    private lateinit var scrollBarModel: ScrollBarModel
    private val scrollBarChangedMarker = AtomicReference<Boolean>()
    private var listener = ChangeListener { scrollBarChangedMarker.set(true) }

    @Before fun setUp() {
        browserModel.range = Range(0, 10)

        scrollBarChangedMarker.set(false)
        scrollBarModel = ScrollBarModel(browserModel)
        scrollBarModel.addChangeListener(listener)
    }

    @Test fun testGetMinimumInitial() {
        assertEquals(0, scrollBarModel.minimum)
    }

    @Test fun testGetMinimumOnLocationChanged() {
        browserModel.setChromosomeRange(Range(5, 100).on(chromosome2))
        assertEquals(0, scrollBarModel.minimum)
    }

    @Test fun testGetMaximumInitial() {
        assertEquals(chromosome1.length, scrollBarModel.maximum)
    }

    @Test fun testGetMaximumOnLocationChanged() {
        browserModel.setChromosomeRange(Range(5, 100).on(chromosome2))
        assertEquals(chromosome2.length, scrollBarModel.maximum)
    }

    @Test fun testGetValueInitial() {
        assertEquals(0, scrollBarModel.value)
    }

    @Test fun testGetValueRangeChanged() {
        browserModel.range = Range(65, 110)
        assertEquals(65, scrollBarModel.value)
    }

    @Test fun testGetValueChrChanged() {
        browserModel.setChromosomeRange(Range(65, 110).on(chromosome2))
        assertEquals(65, scrollBarModel.value)
    }

    @Test fun testGetValueLocChanged() {
        browserModel.setChromosomeRange(Range(110, 210).on(chromosome2))
        assertEquals(110, scrollBarModel.value)
    }

    @Test fun testSetValue() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = 50
        assertEquals(Range(50, 150), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueAfterRange() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = 150
        assertEquals(Range(150, 250), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueBeforeRange() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = 0
        assertEquals(Range(0, 100), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueNegative() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = -1
        assertEquals(Range(0, 100), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueOutOfChr() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = chromosome1.length
        assertEquals(Range(chromosome1.length - 100, chromosome1.length), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueIntersectingChrEnd1() {
        browserModel.range = Range(5, 5 + 100)

        scrollBarModel.value = chromosome1.length - 1
        assertEquals(Range(chromosome1.length - 100, chromosome1.length), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueIntersectingChrEnd2() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = chromosome1.length - 100
        assertEquals(Range(chromosome1.length - 100, chromosome1.length), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetValueNearChrEnd() {
        browserModel.range = Range(5, 105)

        scrollBarModel.value = chromosome1.length - 101
        assertEquals(Range(chromosome1.length - 101, chromosome1.length - 1),
                     browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testGetValueIsAdjusting() {
        assertFalse(scrollBarModel.valueIsAdjusting)
    }

    @Test fun testGetExtentInitial() {
        assertEquals(10, scrollBarModel.extent)
    }

    @Test fun testGetExtentRangeChanged() {
        browserModel.range = Range(5, 105)
        assertEquals(100, scrollBarModel.extent)
    }

    @Test fun testGetExtentChrChanged() {
        browserModel.setChromosomeRange(chromosome2.range.on(chromosome2))
        assertEquals(chromosome2.length, scrollBarModel.extent)
    }

    @Test fun testGetExtentLocChanged() {
        browserModel.setChromosomeRange(Range(60, 110).on(chromosome2))
        assertEquals(50, scrollBarModel.extent)
    }

    @Test fun testSetExtent() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = 50
        assertEquals(Range(5, 55), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetLargerExtent() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = 200
        assertEquals(Range(5, 205), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentIntersectingChrEnd1() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = chromosome1.length
        assertEquals(Range(5, chromosome1.length), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentIntersectingChrEnd2() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = chromosome1.length - 1
        assertEquals(Range(5, chromosome1.length), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentNearChrEnd() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = chromosome1.length - 100
        assertEquals(Range(5, chromosome1.length - 95), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentNegative() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = -1
        // FIXME(lebedev): this range is empty [5, 5).
        assertEquals(Range(5, 5), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentZero() {
        browserModel.range = Range(5, 105)

        scrollBarModel.extent = 0
        assertEquals(Range(5, 5), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetExtentLess10() {
        browserModel.range = Range(5, 105)

        // length >= 10 limitation - only in user zoom events handlers
        scrollBarModel.extent = 9
        assertEquals(Range(5, 14), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetRangeProperties() {
        scrollBarModel.setRangeProperties(50, 1000, 10, 2000, true)
        assertEquals(0, scrollBarModel.minimum)
        assertEquals(chromosome1.length, scrollBarModel.maximum)
        assertFalse(scrollBarModel.valueIsAdjusting)
        assertEquals(Range(50, 1050), browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test fun testSetRangePropertiesLess10() {
        // length >= 10 limitation - only in user zoom events handlers
        scrollBarModel.value = 6
        scrollBarModel.extent = 100
        scrollBarModel.setRangeProperties(5, 9, 1, 400, true)
        assertEquals(0, scrollBarModel.minimum)
        assertEquals(chromosome1.length, scrollBarModel.maximum)
        assertFalse(scrollBarModel.valueIsAdjusting)
        assertEquals(Range(5, 14), browserModel.range)
    }

    @Test fun testAddChangeListenerChangedAdaptorValue() {
        // prerequisite
        scrollBarModel.value = 2
        scrollBarChangedMarker.set(false)

        scrollBarModel.value = 1
        assertTrue(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedAdaptorSameValueTwice() {
        // prerequisite
        scrollBarModel.value = 2
        scrollBarChangedMarker.set(false)

        // - same value twice
        scrollBarModel.value = 2
        assertFalse(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedAdaptorExtent() {
        // prerequisite
        scrollBarModel.extent = 200
        scrollBarChangedMarker.set(false)

        scrollBarModel.extent = 100
        assertTrue(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedAdaptorSameExtentTwice() {
        // prerequisite
        scrollBarModel.extent = 200
        scrollBarChangedMarker.set(false)

        scrollBarModel.extent = 200
        assertFalse(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedAdaptorProperties() {
        // prerequisite
        scrollBarChangedMarker.set(false)

        scrollBarModel.setRangeProperties(5, 100, 1, 200, true)
        assertTrue(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedModelChr() {
        val range = browserModel.range
        // prerequisite
        browserModel.setChromosomeRange(range.on(chromosome2))
        scrollBarChangedMarker.set(false)

        browserModel.setChromosomeRange(range.on(chromosome1))
        assertTrue(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedModelSameChrTwice() {
        val range = browserModel.range
        // prerequisite
        browserModel.setChromosomeRange(range.on(chromosome2))
        scrollBarChangedMarker.set(false)

        browserModel.setChromosomeRange(range.on(chromosome2))
        assertFalse(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedModelRange() {
        // prerequisite
        browserModel.range = Range(100, 150)
        scrollBarChangedMarker.set(false)

        browserModel.range = Range(100, 200)
        assertTrue(scrollBarChangedMarker.get())
    }

    @Test fun testAddChangeListenerChangedModelSameRangeTwice() {
        // prerequisite
        browserModel.range = Range(100, 150)
        scrollBarChangedMarker.set(false)

        browserModel.range = Range(100, 150)
        assertFalse(scrollBarChangedMarker.get())
    }

    @Test fun testRemoveChangeListenerAdapterChanged() {
        // prerequisite
        scrollBarModel.value = 10
        scrollBarModel.extent = 200
        scrollBarChangedMarker.set(false)

        scrollBarModel.removeChangeListener(listener)
        scrollBarModel.value = 20
        scrollBarModel.extent = 300
        assertFalse(scrollBarChangedMarker.get())
    }

    @Test fun testRemoveChangeListenerModelChanged() {
        // prerequisite
        browserModel.setChromosomeRange(Range(1, 2).on(chromosome1))
        scrollBarChangedMarker.set(false)

        scrollBarModel.removeChangeListener(listener)
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome2))
        assertFalse(scrollBarChangedMarker.get())
    }
}
