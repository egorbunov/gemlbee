package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class SingleLocationBrowserModelTest {
    private var browserModel = SingleLocationBrowserModel(GenomeQuery("to1"))
    private val chromosome1 = Chromosome("to1", "chr1")
    private val chromosome2 = Chromosome("to1", "chr2")
    private val modelChangedMarker = AtomicReference<Boolean>()
    private val listener = object : ModelListener {
        override fun modelChanged() {
            modelChangedMarker.set(true)
        }
    }

    @Before fun setUp() {
        modelChangedMarker.set(false)

        browserModel.setChromosomeRange(Range(0, 10).on(chromosome1))
        browserModel.addListener(listener)
    }

    @Test fun testListenerRangeChangedSame() {
        browserModel.range = Range(100, 200)
        modelChangedMarker.set(false)
        browserModel.range = Range(100, 200)
        assertFalse(modelChangedMarker.get())
    }

    @Test fun testListenerRangeChanged() {
        browserModel.range = Range(100, 150)
        modelChangedMarker.set(false)
        browserModel.range = Range(100, 200)
        assertTrue(modelChangedMarker.get())
    }

    @Test fun testListenerChromosomeChangedSame() {
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome1))
        modelChangedMarker.set(false)
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome1))
        assertFalse(modelChangedMarker.get())
    }

    @Test fun testListenerChromosomeChangedOtherRange() {
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome1))
        modelChangedMarker.set(false)
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome1))
        assertTrue(modelChangedMarker.get())
    }

    @Test fun testListenerChromosomeChanged() {
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome2))
        modelChangedMarker.set(false)
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome1))
        assertTrue(modelChangedMarker.get())
    }

    @Test fun testRemoveModelListener() {
        // prerequisites
        browserModel.setChromosomeRange(Range(100, 150).on(chromosome2))
        modelChangedMarker.set(false)
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome1))
        assertTrue(modelChangedMarker.get())

        // unsubscribe:
        browserModel.removeListener(listener)

        browserModel.setChromosomeRange(Range(100, 150).on(chromosome2))
        modelChangedMarker.set(false)
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome1))
        assertFalse(modelChangedMarker.get())
    }


    @Test fun testGetChromosome() {
        browserModel = SingleLocationBrowserModel(GenomeQuery("to1"))
        assertNotNull(browserModel.chromosome)
        assertEquals(Chromosome("to1", "chr1"),
                     browserModel.chromosome)

        browserModel.setChromosomeRange(Range(100, 150).on(chromosome2))
        assertSame(chromosome2, browserModel.chromosome)
        assertEquals(Range(100, 150), browserModel.range)
    }

    @Test
    fun testSetChromosomeSame() {
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome1))
        assertSame(chromosome1, browserModel.chromosome)
        assertEquals(Range(100, 200), browserModel.range)
    }


    @Test fun testSetChromosome() {
        val range = browserModel.range
        browserModel.setChromosomeRange(range.on(chromosome2))
        assertSame(chromosome2, browserModel.chromosome)
        assertEquals(range, browserModel.range)
    }


    @Test fun testGetRange() {
        browserModel = SingleLocationBrowserModel(GenomeQuery("to1"))
        assertNotNull(browserModel.chromosome)
        assertEquals(Range(0, chromosome1.length), browserModel.range)

        browserModel.setChromosomeRange(Range(10, 20).on(chromosome1))
        assertEquals(10, browserModel.range.startOffset)
        assertEquals(20, browserModel.range.endOffset)
    }

    @Test
    fun testSetRange() {
        browserModel.setChromosomeRange(Range(100, 200).on(chromosome1))
        browserModel.range = chromosome1.range
        assertEquals(0, browserModel.range.startOffset)
        assertEquals(chromosome1.length, browserModel.range.endOffset)

        browserModel.range = Range(10, 20)
        assertEquals(10, browserModel.range.startOffset)
        assertEquals(20, browserModel.range.endOffset)
        assertEquals("[10, 20)", browserModel.range.toString())
    }

    @Test
    fun testCopy_Independent() {
        browserModel = SingleLocationBrowserModel(GenomeQuery("to1", "chr1"))
        val range = Range(0, 100)
        browserModel.range = range

        browserModel.copy().setChromosomeRange(Range(0, 200).on(chromosome2))
        // not affected by copy
        assertEquals(range, browserModel.range)
        assertEquals(chromosome1, browserModel.chromosome)
    }

    @Test
    fun testCopy() {
        browserModel = SingleLocationBrowserModel(GenomeQuery("to1", "chr1"))
        val metaInf = SimpleLocRef(Location(99, 199, chromosome2, Strand.PLUS))
        browserModel.setChromosomeRange(Range(0, 200).on(chromosome2), metaInf)

        val copy = browserModel.copy()
        assertEquals(Range(0, 200), copy.range)
        assertEquals(chromosome2, copy.chromosome)
        assertEquals(metaInf, copy.rangeMetaInf)
    }

    @Test fun testMetaInf() {
        browserModel = SingleLocationBrowserModel(GenomeQuery("to1", "chr1"))
        browserModel.setChromosomeRange(Range(0, 200).on(chromosome2), object : LocationReference {
            override val name: String get() = "foo"

            override fun update(newLoc: Location) = TODO()

            override val location: Location get() = Location(99, 199, chromosome2, Strand.PLUS)
        })

        // custom metainf
        assertNotNull(browserModel.rangeMetaInf)
        assertEquals("foo", browserModel.rangeMetaInf!!.name)

        // change
        val metaInf = SimpleLocRef(Location(99, 199, chromosome2, Strand.PLUS))
        browserModel.setChromosomeRange(Range(0, 10).on(chromosome2), metaInf)
        assertEquals(metaInf, browserModel.rangeMetaInf)
        assertEquals("", browserModel.rangeMetaInf!!.name)

        // clear
        browserModel.setChromosomeRange(Range(0, 10).on(chromosome2))
        assertNull(browserModel.rangeMetaInf)
    }

    @Test fun testGetCurrentPositionPresentableName() {
        val genomeQuery = GenomeQuery("to1")
        assertEquals("chr1:0-10000000",
                     SingleLocationBrowserModel(genomeQuery).toString())
        browserModel.setChromosomeRange(Range(10, 400).on(chromosome1))
        assertEquals("chr1:10-400", browserModel.toString())
    }
}
