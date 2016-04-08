package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.junit.Test
import kotlin.test.assertEquals

class SimpleLocRefTest {
    @Test fun ref() {
        val loc = Location(0, 10, Chromosome("to1", "chr1"), Strand.PLUS)
        val locRef = SimpleLocRef(loc)
        assertEquals(loc, locRef.location)
        assertEquals("", locRef.name)
    }

    @Test fun refUpdate() {
        val loc = Location(0, 10, Chromosome("to1", "chr1"), Strand.PLUS)
        val locRef = SimpleLocRef(loc)
        val newLoc = loc.copy(startOffset = 5)
        val newLocRef = locRef.update(newLoc)

        assertEquals(loc, locRef.location)

        assertEquals(newLoc, newLocRef.location)
    }
}

class GeneLocRefTest {
    @Test fun ref() {
        val chr = Chromosome("to1", "chr1")
        val someGene = chr.genes[0]
        val locRef = GeneLocRef(someGene)

        assertEquals(someGene.location, locRef.location)
        assertEquals(someGene.symbol, locRef.name)
    }

    @Test fun refCustomLocation() {
        val chr = Chromosome("to1", "chr1")
        val someGene = chr.genes[0]

        val loc = Location(0, 10, chr, Strand.PLUS)
        val locRef = GeneLocRef(someGene, loc)

        assertEquals(loc, locRef.location)
        assertEquals(someGene.symbol, locRef.name)
    }

    @Test fun refUpdate() {
        val chr = Chromosome("to1", "chr1")
        val someGene = chr.genes[0]
        val locRef = GeneLocRef(someGene)

        val newLoc = Location(0, 10, chr, Strand.PLUS)
        val newRef = locRef.update(newLoc)

        // old not affected:
        assertEquals(someGene.location, locRef.location)

        assertEquals(newLoc, newRef.location)
        assertEquals(someGene.symbol, newRef.name)
    }
}
