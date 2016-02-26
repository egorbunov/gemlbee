package org.jetbrains.bio.genome.containers

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ConcurrentGenomeMapTest {
    val genomeQuery = GenomeQuery("to1")
    val chromosomes = genomeQuery.get()

    @Test fun singleChromosome() {
        val genomeMap = genomeMap(genomeQuery) { "" }

        val chromosome = chromosomes[0]
        genomeMap[chromosome] = "1"
        assertEquals("1", genomeMap[chromosome])
        genomeMap[chromosome] = "2"
        assertEquals("2", genomeMap[chromosome])
    }

    @Test fun multipleChromosomes() {
        val genomeMap = genomeMap(genomeQuery) { "" }

        val chromosome1 = chromosomes[0]
        genomeMap[chromosome1] = "1"
        assertEquals("1", genomeMap[chromosome1])

        val chromosome2 = chromosomes[1]
        genomeMap[chromosome2] = "2"
        assertEquals("2", genomeMap[chromosome2])
    }

    @Test(expected = IllegalStateException::class) fun exceptionInConstructor() {
        genomeMap(genomeQuery) { throw IllegalStateException() }
    }
}

class ConcurrentGenomeStrandMapTest {
    @Test fun singleChromosome() {
        val genomeQuery = GenomeQuery("to1")
        val genomeMap = genomeStrandMap(genomeQuery) { _c, _s -> "" }

        val chromosome = genomeQuery.get().first()
        genomeMap[chromosome, Strand.PLUS] = "1"
        assertEquals("1", genomeMap[chromosome, Strand.PLUS])
        genomeMap[chromosome, Strand.MINUS] = "2"
        assertEquals("2", genomeMap[chromosome, Strand.MINUS])
    }

    @Test fun multipleChromosomes() {
        val genomeQuery = GenomeQuery("to1")
        val genomeMap = genomeStrandMap(genomeQuery) { _c, _s -> "" }

        val (chromosome1, chromosome2) = genomeQuery.get()
        genomeMap[chromosome1, Strand.PLUS] = "1"
        assertEquals("1", genomeMap[chromosome1, Strand.PLUS])
        genomeMap[chromosome2, Strand.MINUS] = "2"
        assertEquals("2", genomeMap[chromosome2, Strand.MINUS])
    }

    @Test(expected = NoSuchElementException::class) fun testPut_ByUnexpectedChr() {
        val map = genomeStrandMap(GenomeQuery("to1", "chr2")) { _c, _s -> "" }
        val chromosome1 = Chromosome["to1", "chr1"]
        map[chromosome1, Strand.PLUS] = "1"
    }

    @Test(expected = NoSuchElementException::class) fun testGetByUnexpectedChr() {
        val map = genomeStrandMap(GenomeQuery("to1", "chr2")) { _c, _s -> "" }
        val chromosome1 = Chromosome["to1", "chr1"]
        map[chromosome1, Strand.PLUS]
    }
}
