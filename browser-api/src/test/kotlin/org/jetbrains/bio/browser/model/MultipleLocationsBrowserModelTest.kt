package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.GeneAliasType
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class MultipleLocationsBrowserModelTest {
    private val genomeQuery = GenomeQuery("to1", "chr1")
    private val chromosome = genomeQuery.get().single()
    private var location1 = Location(0, 100, chromosome, Strand.PLUS)
    private var location2 = Location(200, 300, chromosome, Strand.PLUS)

    private lateinit var browserModel: MultipleLocationsBrowserModel

    @Before fun setUp() {
        browserModel = MultipleLocationsBrowserModel.create(
                "test",
                { gq -> listOf(SimpleLocRef(location1), SimpleLocRef(location2)) },
                SingleLocationBrowserModel(genomeQuery))
    }

    @Test fun testVisibleFull() {
        browserModel.range = Range(0, 200)
        val visibleLocations = browserModel.visibleLocations()

        assertEquals(2, visibleLocations.size)
        assertEquals(location1, visibleLocations[0].location)
        assertEquals(location2, visibleLocations[1].location)
    }

    @Test fun testVisibleFirst() {
        browserModel.range = Range(0, 100)
        val visibleLocations = browserModel.visibleLocations()

        assertEquals(1, visibleLocations.size)
        assertEquals(location1, visibleLocations[0].location)
    }

    @Test fun testVisibleLast() {
        browserModel.range = Range(100, 200)
        val visibleLocations = browserModel.visibleLocations()
        assertEquals(1, visibleLocations.size)
        assertEquals(location2, visibleLocations[0].location)
    }

    @Test fun testVisible_Cropped() {
        val (gene1, gene2) = chromosome.genes
        val locRef1 = GeneLocRef(gene1, Location(0, 100, chromosome, Strand.PLUS))
        val locRef2 = GeneLocRef(gene2, Location(200, 300, chromosome, Strand.PLUS))

        val browserModel = MultipleLocationsBrowserModel.create(
                "test",
                { gq -> listOf(locRef1, locRef2) },
                SingleLocationBrowserModel(genomeQuery))

        browserModel.range = Range(50, 150)
        val visibleLocations = browserModel.visibleLocations()

        assertEquals(2, visibleLocations.size)
        assertEquals(50, visibleLocations[0].location.startOffset)
        assertEquals(100, visibleLocations[0].location.endOffset)
        assertEquals(gene1.getName(GeneAliasType.GENE_SYMBOL), visibleLocations[0].name)
        assertEquals(200, visibleLocations[1].location.startOffset)
        assertEquals(250, visibleLocations[1].location.endOffset)
        assertEquals(gene2.getName(GeneAliasType.GENE_SYMBOL), visibleLocations[1].name)
    }

    @Test fun testCopy() {
        val range = Range(0, 100)
        browserModel.range = range
        val copy = browserModel.copy()
        copy.range = Range(0, 200)
        assertEquals(range, browserModel.range)
    }
}