package org.jetbrains.bio.genome

import org.jetbrains.bio.genome.sequence.Nucleotide
import org.junit.Test
import kotlin.test.assertEquals

class NucleotideTest {
    @Test fun testToFromByte() {
        for (n in Nucleotide.values()) {
            assertEquals(n, Nucleotide.fromByte(n.byte.toInt()))
        }
    }

    @Test fun testToFromChar() {
        for (n in Nucleotide.values()) {
            assertEquals(n, Nucleotide.fromChar(Character.toUpperCase(n.char)))
            assertEquals(n, Nucleotide.fromChar(Character.toLowerCase(n.char)))
        }
    }

    @Test fun testAlphabet() {
        for (n in Nucleotide.values()) {
            assertEquals(Nucleotide.ALPHABET[n.byte.toInt()], n.char)
        }
    }

    @Test fun testComplementId() {
        for (n in Nucleotide.values()) {
            val b = n.byte
            assertEquals(b, Nucleotide.complement(Nucleotide.complement(b)))
        }
    }

    @Test fun testComplement() {
        assertEquals(Nucleotide.T.byte, Nucleotide.complement(Nucleotide.A.byte))
        assertEquals(Nucleotide.C.byte, Nucleotide.complement(Nucleotide.G.byte))
    }

}
