package org.jetbrains.bio.io

import htsjdk.samtools.BAMIndexer
import htsjdk.samtools.SamReaderFactory
import org.jetbrains.bio.ext.withExtension
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.util.withResource
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CoverageBamParserTest {
    @Test fun exampleBam() {
        val genomeQuery = GenomeQuery("to1")
        withResource(CoverageBamParserTest::class.java, "example-chipseq.bam") { path ->
            val samReader = SamReaderFactory.makeDefault()
                    .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                    .open(path.toFile())
            BAMIndexer.createIndex(samReader, path.withExtension("bam.bai").toFile())

            val coverage = CoverageBamParser().parse(path, genomeQuery, false)
            val chromosome = genomeQuery.get().first()

            // Validate with 'samtools view path/to/example-chipseq.bam'.
            assertArrayEquals(intArrayOf(3001714, 3002705, 3003870, 3004510, 3004747),
                              coverage.data[chromosome, Strand.PLUS].toArray())
            assertArrayEquals(intArrayOf(),
                              coverage.data[chromosome, Strand.MINUS].toArray())
        }
    }
}