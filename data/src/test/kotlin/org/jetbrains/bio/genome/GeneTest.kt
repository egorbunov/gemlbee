package org.jetbrains.bio.genome

import com.google.common.collect.Ordering
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GeneTest {
    @Test fun testGetExonsPlusStrand() {
        val gene = Chromosome("to1", "chr1").genes
                .filter { it.isCoding && it.exons.size > 1 }
                .filter { it.strand.isPlus() }
                .first()
        val exons = gene.exons.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().isOrdered(exons))
    }

    @Test fun testGetExonsMinusStrand() {
        val gene = Chromosome("to1", "chr1").genes
                .filter { it.isCoding && it.exons.size > 1 }
                .filter { it.strand.isMinus() }
                .first()
        val exons = gene.exons.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().reverse<Int>().isOrdered(exons))
    }

    @Test fun testGetIntronsPlusStrand() {
        val gene = Chromosome("to1", "chr1").genes
                .filter { it.isCoding && it.introns.size > 1 }
                .filter { it.strand.isPlus() }
                .first()
        val introns = gene.introns.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().isOrdered(introns))
    }

    @Test fun testGetIntronsMinusStrand() {
        val gene = Chromosome("to1", "chr1").genes
                .filter { it.isCoding && it.introns.size > 1 }
                .filter { it.strand.isMinus() }
                .first()
        val introns = gene.introns.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().reverse<Int>().isOrdered(introns))
    }

    /**
     * Equals and hashcode are defined by ensemblId
     */
    @Test fun testEqualsHashCode() {
        val gene1 = Gene("foo", "refseq1", "symbol1", "test gene1",
                Location(0, 100, Chromosome("to1", "chr1"), Strand.PLUS), null, arrayListOf<Range>())
        val gene2 = Gene("foo", "refseq2", "symbol2", "test gene2",
                Location(0, 200, Chromosome("to1", "chr1"), Strand.PLUS), null, arrayListOf<Range>())
        val gene3 = Gene("foo2", "refseq3", "symbol3", "test gene3",
                Location(0, 300, Chromosome("to1", "chr1"), Strand.PLUS), null, arrayListOf<Range>())
        assertEquals(gene1.hashCode(), gene2.hashCode())
        assertNotEquals(gene1.hashCode(), gene3.hashCode())
        assertEquals(gene1, gene2)
        assertNotEquals(gene1, gene3)
        assertNotEquals(gene2, gene3)
    }

}
