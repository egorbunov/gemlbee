package org.jetbrains.bio.genome

import org.jetbrains.bio.genome.sequence.Nucleotide
import org.jetbrains.bio.genome.sequence.asNucleotideSequence
import org.junit.Test
import kotlin.test.assertEquals

class CpGContentTest {
    @Test fun testComputeCpG() {
        val buffer = byteArrayOf(Nucleotide.C.byte, Nucleotide.A.byte,
                                 Nucleotide.C.byte, Nucleotide.G.byte,
                                 Nucleotide.G.byte)
        assertEquals(1, CpGContent.computeCpG(buffer))
    }

    @Test fun testComputeCG() {
        val buffer = byteArrayOf(Nucleotide.A.byte, Nucleotide.C.byte, Nucleotide.G.byte)
        assertEquals(2, CpGContent.computeCG(buffer))
    }

    @Test fun testClassify() {
        assertEquals(CpGContent.LCP, CpGContent.classify("AAAAAA".asNucleotideSequence(), 3))
        assertEquals(CpGContent.LCP, CpGContent.classify("CCCCCC".asNucleotideSequence(), 5))
        assertEquals(CpGContent.HCP, CpGContent.classify("CCCCGCCC".asNucleotideSequence(), 5))
        assertEquals(CpGContent.ICP, CpGContent.classify("CCCCGCCC".asNucleotideSequence(), 8))
        assertEquals(CpGContent.ICP, CpGContent.classify("CCCCGCCC".asNucleotideSequence(), 8))
        assertEquals(CpGContent.HCP, CpGContent.classify("CCCGCG".asNucleotideSequence(), 5))
        assertEquals(CpGContent.HCP, CpGContent.classify("CCCGCG".asNucleotideSequence(), 5))
        assertEquals(CpGContent.HCP, CpGContent.classify("CGCGCG".asNucleotideSequence(), 5))
    }
}
