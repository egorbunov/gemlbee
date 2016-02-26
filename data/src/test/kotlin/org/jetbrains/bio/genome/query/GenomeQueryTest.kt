package org.jetbrains.bio.genome.query

import org.jetbrains.bio.genome.ChromosomeNamesMap
import org.jetbrains.bio.genome.Genome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenomeQueryTest {
    @Test fun testUnspecifiedChromosome() {
        val genomeQuery = GenomeQuery("to1")
        val chromosomes = genomeQuery.get()
        assertEquals(Genome("to1").chromosomes.size - 1,
                chromosomes.size)  // ^^^ minus chrM.
    }

    @Test fun testGetChromosome() {
        val genomeQuery = GenomeQuery("to1", "chr1", "chr2")
        val map = ChromosomeNamesMap.create(genomeQuery)
        assertNotNull(map["1"])
        assertNotNull(map["2"])
    }

    @Test fun testNames() {
        val genomeQuery = GenomeQuery("to1", "chr1", "chr2")
        assertTrue("chr1" in genomeQuery.restriction)
        assertTrue("chr2" in genomeQuery.restriction)
        assertFalse("chr3" in genomeQuery.restriction)
    }

    @Test fun testNamesAllChromosomes() {
        val genomeQuery = GenomeQuery("to1")
        assertTrue(genomeQuery.restriction.isEmpty())
    }

    @Test fun testSize() {
        val genomeQuery = GenomeQuery("to1", "chr1", "chr2")
        assertEquals(2, genomeQuery.get().size)

        val genomeQueryAll = GenomeQuery("to1")
        assertEquals(Genome("to1").chromosomes.size - 1,
                genomeQueryAll.get().size)
    }

    @Test fun testGetShortName() {
        val genomeQuery = GenomeQuery("to1", "chr1", "chr2")
        assertEquals("to1", genomeQuery.id)
        assertEquals("to1[chr1,chr2]", genomeQuery.getShortNameWithChromosomes())
        assertEquals("Test organism to1 [chr1, chr2]", genomeQuery.description)
    }

    @Test fun testParse() {
        assertEquals(Genome("to1").chromosomes.size - 1,
                GenomeQuery.parse("to1").get().size)
        assertEquals(1, GenomeQuery.parse("to1[chr1]").get().size)
        assertEquals(2, GenomeQuery.parse("to1[chr1,chr3]").get().size)
    }
}
