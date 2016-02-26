package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.GeneAliasType
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @author Roman.Chernyatchik
 */
class SimpleLocRefTest {
    @Test fun ref() {
        val loc = Location(0, 10, Chromosome["to1", "chr1"], Strand.PLUS)
        val locRef = SimpleLocRef(loc)
        assertEquals(loc, locRef.location)
        assertEquals("", locRef.name)
        assertNull(locRef.metaData)
    }

    @Test fun refUpdate() {
        val loc = Location(0, 10, Chromosome["to1", "chr1"], Strand.PLUS)
        val locRef = SimpleLocRef(loc)
        val newLoc = loc.copy(startOffset = 5)
        val newLocRef = locRef.update(newLoc)

        assertEquals(loc, locRef.location)

        assertEquals(newLoc, newLocRef.location)
        assertNull(newLocRef.metaData)
    }
}

class GeneLocRefTest {
    @Test fun ref() {
        val chr = Chromosome["to1", "chr1"]
        val someGene = chr.genes[0]
        val locRef = GeneLocRef(someGene)

        assertEquals(someGene.location, locRef.location)
        assertEquals(someGene.getName(GeneAliasType.GENE_SYMBOL), locRef.name)
        assertEquals(someGene, locRef.metaData)
    }

    @Test fun refCustomLocation() {
        val chr = Chromosome["to1", "chr1"]
        val someGene = chr.genes[0]

        val loc = Location(0, 10, chr, Strand.PLUS)
        val locRef = GeneLocRef(someGene, loc)

        assertEquals(loc, locRef.location)
        assertEquals(someGene.getName(GeneAliasType.GENE_SYMBOL), locRef.name)
        assertEquals(someGene, locRef.metaData)
    }

    @Test fun refUpdate() {
        val chr = Chromosome["to1", "chr1"]
        val someGene = chr.genes[0]
        val locRef = GeneLocRef(someGene)

        val newLoc = Location(0, 10, chr, Strand.PLUS)
        val newRef = locRef.update(newLoc)

        // old not affected:
        assertEquals(someGene.location, locRef.location)

        assertEquals(newLoc, newRef.location)
        assertEquals(someGene.getName(GeneAliasType.GENE_SYMBOL), newRef.name)
        assertEquals(someGene, newRef.metaData)
    }
}