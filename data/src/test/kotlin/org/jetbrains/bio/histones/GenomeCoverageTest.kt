package org.jetbrains.bio.histones

import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.containers.ConcurrentGenomeStrandMap
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.genome.toBedEntry
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.io.BedFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenomeCoverageTest {
    private var genomeQuery: GenomeQuery = GenomeQuery("to1")
    private var chromosome1: Chromosome = genomeQuery.get()[0]
    private var chromosome2: Chromosome = genomeQuery.get()[1]

    @Test fun testEmptyCoverage() {
        val coverage = GenomeCoverage(genomeQuery)
        assertEquals(0, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testSimpleCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 0)
                .put(chromosome1, Strand.PLUS, 5)
                .put(chromosome1, Strand.PLUS, 9)
                .build(unique = false)
        assertEquals(3, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testStartIndex() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 0)
                .put(chromosome1, Strand.PLUS, 5)
                .put(chromosome1, Strand.PLUS, 9)
                .build(unique = false)
        assertEquals(2, coverage.getCoverage(Location(1, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testSortingCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 5)
                .put(chromosome1, Strand.PLUS, 9)
                .put(chromosome1, Strand.PLUS, 0)
                .build(unique = false)
        assertEquals(3, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testMultiplePointsCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 5)
                .put(chromosome1, Strand.PLUS, 5)
                .put(chromosome1, Strand.PLUS, 9)
                .put(chromosome1, Strand.PLUS, 9)
                .put(chromosome1, Strand.PLUS, 0)
                .put(chromosome1, Strand.PLUS, 0)
                .put(chromosome1, Strand.PLUS, 0)
                .build(unique = false)
        assertEquals(7, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testWrongStrandCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.MINUS, 1)
                .build(unique = false)
        assertEquals(0, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
    }

    @Test fun testWrongOutOfBoundsLeftCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 0)
                .build(unique = false)
        assertEquals(0, coverage.getCoverage(Location(10, 20, chromosome1, Strand.PLUS)))
    }

    @Test fun testWrongOutOfBoundsRightCoverage() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .put(chromosome1, Strand.PLUS, 30)
                .build(unique = false)
        assertEquals(0, coverage.getCoverage(Location(10, 20, chromosome1, Strand.PLUS)))
    }

    @Test fun testGetTagsSimple() {
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .putAll(chromosome1, Strand.PLUS, 5, 13, 23, 1, 111, 7, 4, 5, 50)
                .build(unique = false)

        assertArrayEquals(intArrayOf(5, 5, 7, 13, 23),
                          coverage.getTags(Location(5, 50, chromosome1, Strand.PLUS)))
        assertArrayEquals(intArrayOf(13, 23),
                          coverage.getTags(Location(10, 25, chromosome1, Strand.PLUS)))
        assertArrayEquals(intArrayOf(5, 5, 7, 13, 23, 50),
                          coverage.getTags(Location(5, 55, chromosome1, Strand.PLUS)))
    }

    @Test fun testGetEqualTags() {
        val tags = intArrayOf(5, 5, 5, 5, 5, 5, 5, 5, 5)
        val coverage = GenomeCoverage.Builder(genomeQuery)
                .putAll(chromosome1, Strand.PLUS, *tags)
                .build(unique = false)

        assertArrayEquals(tags, coverage.getTags(Location(5, 50, chromosome1, Strand.PLUS)))
    }

    @Test fun testSerialization() {
        val builder = GenomeCoverage.Builder(genomeQuery)
        for (chromosome in genomeQuery.get()) {
            for (i in 0..99) {
                val strand = if ((i + chromosome.id) % 2 == 0) Strand.PLUS else Strand.MINUS
                builder.put(chromosome, strand, i)
            }
        }

        val coverage = builder.build(false)
        withTempFile("coverage", ".cov") { coveragePath ->
            coverage.save(coveragePath)
            val loaded = GenomeCoverage(genomeQuery)
            loaded.load(coveragePath)
            assertEquals(coverage, loaded)
        }
    }

    @Test fun testPartialLoading() {
        val builder = GenomeCoverage.Builder(genomeQuery)
        for (chromosome in genomeQuery.get()) {
            for (i in 0..99) {
                val strand = if ((i + chromosome.id) % 2 == 0) Strand.PLUS else Strand.MINUS
                builder.put(chromosome, strand, i)
            }
        }

        val coverage = builder.build(false)
        withTempFile("coverage", ".cov") { coveragePath ->
            coverage.save(coveragePath)
            assertTrue(chromosome2 in coverage.genomeQuery.get())
            val loaded = GenomeCoverage(GenomeQuery("to1", chromosome1.name))
            loaded.load(coveragePath)
            assertFalse(chromosome2 in loaded.genomeQuery.get())
        }
    }

    private fun assertEquals(coverage1: GenomeCoverage, coverage2: GenomeCoverage) {
        // Atomics cannot provide a useful '#equals' method, thus for testing
        // we use a non-atomic snapshot-hack.
        val data1 = (coverage1.data as ConcurrentGenomeStrandMap<*>).snapshot()
        val data2 = (coverage2.data as ConcurrentGenomeStrandMap<*>).snapshot()
        assertEquals(data1, data2)
    }

    @Test fun testSortInComputeCoverage() {
        withTempFile("track", ".bed") { trackPath ->
            val bedFormat = BedFormat.SIMPLE
            bedFormat.print(trackPath).use { bedPrinter ->
                bedPrinter.print(Location(0, 3, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(2, 4, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(4, 7, chromosome1, Strand.PLUS).toBedEntry())

                // Not sorted!
                bedPrinter.print(Location(4, 7, chromosome2, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(2, 9, chromosome2, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(0, 11, chromosome2, Strand.PLUS).toBedEntry())
            }

            val coverage = GenomeCoverage.compute(
                    genomeQuery, bedFormat.parse(trackPath).wrap(), unique = true)

            assertEquals(3, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
            assertEquals(3, coverage.getCoverage(Location(0, 10, chromosome2, Strand.PLUS)))
        }
    }

    @Test fun testUniqueTags() {
        withTempFile("track", ".bed") { trackPath ->
            val bedFormat = BedFormat.SIMPLE
            bedFormat.print(trackPath).use { bedPrinter ->
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
            }

            val coverage = GenomeCoverage.compute(
                    genomeQuery, bedFormat.parse(trackPath).wrap(), unique = true)

            assertEquals(1, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
        }
    }

    @Test fun testNonUniqueTags() {
        withTempFile("track", ".bed") { trackPath ->
            val bedFormat = BedFormat.SIMPLE
            bedFormat.print(trackPath).use { bedPrinter ->
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(0, 1, chromosome1, Strand.PLUS).toBedEntry())
            }

            val coverage = GenomeCoverage.compute(
                    genomeQuery, bedFormat.parse(trackPath).wrap(), unique = false)

            assertEquals(3, coverage.getCoverage(Location(0, 10, chromosome1, Strand.PLUS)))
        }
    }

    @Test fun testNegativeStrand() {
        withTempFile("track", ".bed") { trackPath ->
            val bedFormat = BedFormat.SIMPLE
            bedFormat.print(trackPath).use { bedPrinter ->
                bedPrinter.print(Location(1, 5, chromosome1, Strand.PLUS).toBedEntry())
                bedPrinter.print(Location(1, 5, chromosome1, Strand.MINUS).toBedEntry())
            }

            val coverage = GenomeCoverage.compute(
                    genomeQuery, bedFormat.parse(trackPath).wrap(), unique = false)

            assertArrayEquals(intArrayOf(1), coverage.getTags(Location(0, 10, chromosome1, Strand.PLUS)))
            assertArrayEquals(intArrayOf(4), coverage.getTags(Location(0, 10, chromosome1, Strand.MINUS)))
        }
    }


    private fun Iterable<BedEntry>.wrap(): InputQuery<Iterable<BedEntry>> {
        return object : InputQuery<Iterable<BedEntry>> {
            override fun getUncached(): Iterable<BedEntry> = this@wrap

            override val id: String get() = TODO()
        }
    }
}

private fun GenomeCoverage.Builder.putAll(chromosome: Chromosome, strand: Strand,
                                          vararg offsets: Int): GenomeCoverage.Builder {
    coverage.data[chromosome, strand].addAll(offsets)
    return this
}