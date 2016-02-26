package org.jetbrains.bio.genome

import com.google.common.collect.Ordering
import org.junit.Test
import kotlin.test.assertTrue

class GeneTest {
    @Test fun testGetExonsPlusStrand() {
        val gene = Chromosome["to1", "chr1"].genes
                .filter { it.isCoding && it.exons.size > 1 }
                .filter { it.strand.isPlus() }
                .first()
        val exons = gene.exons.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().isOrdered(exons))
    }

    @Test fun testGetExonsMinusStrand() {
        val gene = Chromosome["to1", "chr1"].genes
                .filter { it.isCoding && it.exons.size > 1 }
                .filter { it.strand.isMinus() }
                .first()
        val exons = gene.exons.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().reverse<Int>().isOrdered(exons))
    }

    @Test fun testGetIntronsPlusStrand() {
        val gene = Chromosome["to1", "chr1"].genes
                .filter { it.isCoding && it.introns.size > 1 }
                .filter { it.strand.isPlus() }
                .first()
        val introns = gene.introns.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().isOrdered(introns))
    }

    @Test fun testGetIntronsMinusStrand() {
        val gene = Chromosome["to1", "chr1"].genes
                .filter { it.isCoding && it.introns.size > 1 }
                .filter { it.strand.isMinus() }
                .first()
        val introns = gene.introns.map { it.startOffset }.toList()
        assertTrue(Ordering.natural<Int>().reverse<Int>().isOrdered(introns))
    }
}
