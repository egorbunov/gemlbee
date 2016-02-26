package org.jetbrains.bio.genome

import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromosomeNamesTest {
    @Test fun testInitialization() {
        val genome = Genome("to1")
        val chromosomes = ChromosomeNamesMap.create(genome.build)

        for (chromosome in genome.chromosomes) {
            assertTrue(chromosome.name.substringAfter("chr") in chromosomes)
            assertTrue(chromosome.name in chromosomes)
            assertTrue(chromosome.name.toLowerCase() in chromosomes)
        }
    }

    @Test fun testReport() {
        val genome = Genome("to1")
        val chromosomes = ChromosomeNamesMap.create(GenomeQuery("to1", "chr1"))

        for (chromosome in genome.chromosomes) {
            chromosomes[chromosome.name]
        }

        assertEquals(1, chromosomes.recognizedCount)
        assertEquals(genome.chromosomes.map { it.name }.toSet() - setOf("chr1"),
                chromosomes.unrecognized)
    }
}