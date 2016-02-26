package org.jetbrains.bio.browser.tracks

import gnu.trove.list.TIntList
import gnu.trove.list.array.TIntArrayList
import org.jetbrains.bio.browser.tracks.ExonicBlock.*
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.Strand
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenesTrackViewTest {
    val chr = Chromosome["to1", "chr1"]

    @Test fun annotate_CDS_noExons() {
        val cds = Range(100, 200)
        val exons = emptyList<Range>()

        assertAnnotate(emptyList(),
                       cds, exons, Strand.PLUS)

        assertAnnotate(emptyList(),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_lCDS_noExons() {
        val cds = Range(300, 450)
        val exons = listOf(Range(100, 150), Range(200, 250))

        assertAnnotate(listOf(UTR5_EXON to Range(100, 150),
                              UTR5_EXON to Range(200, 250)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(100, 150),
                              UTR3_EXON to Range(200, 250)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_rCDS_noExons() {
        val cds = Range(300, 450)
        val exons = listOf(Range(500, 650), Range(700, 800))

        assertAnnotate(listOf(UTR3_EXON to Range(500, 650),
                              UTR3_EXON to Range(700, 800)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR5_EXON to Range(500, 650),
                              UTR5_EXON to Range(700, 800)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_CDS_equalsExon() {
        val cds = Range(100, 200)
        val exons = listOf(Range(100, 200))

        assertAnnotate(listOf(CDS_EXON to Range(100, 200)),
                       cds, exons, Strand.PLUS)
        assertAnnotate(listOf(CDS_EXON to Range(100, 200)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_CDS_insideExon() {
        val cds = Range(100, 200)
        val exons = listOf(Range(50, 250))

        assertAnnotate(listOf(UTR5_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 200),
                              UTR3_EXON to Range(200, 250)),
                       cds, exons, Strand.PLUS)
        assertAnnotate(listOf(UTR3_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 200),
                              UTR5_EXON to Range(200, 250)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_CDS_madeOfExons() {
        val cds = Range(100, 1000)
        val exons = listOf(Range(100, 250), Range(300, 450), Range(500, 1000))

        assertAnnotate(exons.map { CDS_EXON to it }, cds, exons, Strand.PLUS)
        assertAnnotate(exons.map { CDS_EXON to it }, cds, exons, Strand.MINUS)
    }

    @Test fun annotate_UTRs_containExons() {
        val cds = Range(300, 450)
        val exons = listOf(Range(100, 150), Range(200, 250),
                           Range(300, 450),
                           Range(500, 650), Range(700, 800))

        assertAnnotate(listOf(UTR5_EXON to Range(100, 150),
                              UTR5_EXON to Range(200, 250),
                              CDS_EXON to Range(300, 450),
                              UTR3_EXON to Range(500, 650),
                              UTR3_EXON to Range(700, 800)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(100, 150),
                              UTR3_EXON to Range(200, 250),
                              CDS_EXON to Range(300, 450),
                              UTR5_EXON to Range(500, 650),
                              UTR5_EXON to Range(700, 800)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_lUTR_containsExons() {
        val cds = Range(300, 450)
        val exons = listOf(Range(100, 150), Range(200, 250), Range(250, 300),
                           Range(300, 450))

        assertAnnotate(listOf(UTR5_EXON to Range(100, 150),
                              UTR5_EXON to Range(200, 250),
                              UTR5_EXON to Range(250, 300),
                              CDS_EXON to Range(300, 450)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(100, 150),
                              UTR3_EXON to Range(200, 250),
                              UTR3_EXON to Range(250, 300),
                              CDS_EXON to Range(300, 450)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_rUTR_containsExons() {
        val cds = Range(300, 450)
        val exons = listOf(Range(300, 450),
                           Range(450, 500), Range(500, 650), Range(700, 800))

        assertAnnotate(listOf(CDS_EXON to Range(300, 450),
                              UTR3_EXON to Range(450, 500),
                              UTR3_EXON to Range(500, 650),
                              UTR3_EXON to Range(700, 800)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(CDS_EXON to Range(300, 450),
                              UTR5_EXON to Range(450, 500),
                              UTR5_EXON to Range(500, 650),
                              UTR5_EXON to Range(700, 800)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_lUTR_intersectsExon() {
        val cds = Range(100, 1000)
        val exons = listOf(Range(50, 800))

        assertAnnotate(listOf(UTR5_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 800)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 800)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_rUTR_intersectsExon() {
        val cds = Range(100, 1000)
        val exons = listOf(Range(500, 1200))

        assertAnnotate(listOf(CDS_EXON to Range(500, 1000),
                              UTR3_EXON to Range(1000, 1200)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(CDS_EXON to Range(500, 1000),
                              UTR5_EXON to Range(1000, 1200)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate_UTRs_intersectExons() {
        val cds = Range(100, 1000)
        val exons = listOf(Range(50, 200), Range(300, 800), Range(900, 1100))

        assertAnnotate(listOf(UTR5_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 200),
                              CDS_EXON to Range(300, 800),
                              CDS_EXON to Range(900, 1000),
                              UTR3_EXON to Range(1000, 1100)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(50, 100),
                              CDS_EXON to Range(100, 200),
                              CDS_EXON to Range(300, 800),
                              CDS_EXON to Range(900, 1000),
                              UTR5_EXON to Range(1000, 1100)),
                       cds, exons, Strand.MINUS)
    }

    @Test fun annotate() {
        val cds = Range(100, 1000)
        val exons = listOf(Range(10, 30), Range(40, 45),
                           Range(50, 200),
                           Range(300, 400),
                           Range(500, 800),
                           Range(900, 1100),
                           Range(1200, 1300), Range(1400, 1500))

        assertAnnotate(listOf(UTR5_EXON to Range(10, 30), UTR5_EXON to Range(40, 45),
                              UTR5_EXON to Range(50, 100), CDS_EXON to Range(100, 200),
                              CDS_EXON to Range(300, 400),
                              CDS_EXON to Range(500, 800),
                              CDS_EXON to Range(900, 1000), UTR3_EXON to Range(1000, 1100),
                              UTR3_EXON to Range(1200, 1300), UTR3_EXON to Range(1400, 1500)),
                       cds, exons, Strand.PLUS)

        assertAnnotate(listOf(UTR3_EXON to Range(10, 30), UTR3_EXON to Range(40, 45),
                              UTR3_EXON to Range(50, 100), CDS_EXON to Range(100, 200),
                              CDS_EXON to Range(300, 400),
                              CDS_EXON to Range(500, 800),
                              CDS_EXON to Range(900, 1000), UTR5_EXON to Range(1000, 1100),
                              UTR5_EXON to Range(1200, 1300), UTR5_EXON to Range(1400, 1500)),
                       cds, exons, Strand.MINUS)
    }

    fun assertAnnotate(expected: List<Pair<ExonicBlock, Range>>,
                       cds: Range, exons: List<Range>,
                       strand: Strand) {

        val annotations = annotate(
                cds.on(chr).on(strand),
                exons.map { it.on(chr).on(strand) })

        assertEquals(expected, annotations);
    }

    @Test fun findSuitableLine_emptyTable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        assertFindSuitableLine(0 to 0,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_firstAvailable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(50);
        assertFindSuitableLine(0 to 50,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_nextAvailable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(95);
        geneLines[1].add(50);
        assertFindSuitableLine(1 to 50,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_someAvailable() {
        val geneLines = Array<TIntList>(5) { TIntArrayList() };
        geneLines[0].add(105);
        geneLines[1].add(100);
        geneLines[2].add(95);
        geneLines[3].add(50);
        assertFindSuitableLine(3 to 50,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_lastAvailable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(105);
        geneLines[1].add(105);
        geneLines[2].add(105);
        assertFindSuitableLine(3 to null,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_noAvailable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(105);
        geneLines[1].add(105);
        geneLines[2].add(105);
        geneLines[3].add(105);
        assertFindSuitableLine(3 to null,
                               geneLines, 100, 50, 95)
    }

    @Test fun findSuitableLine_middleAvailable() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(105);
        geneLines[1].add(105);
        geneLines[2].add(30);
        geneLines[3].add(105);
        assertFindSuitableLine(2 to 30,
                               geneLines, 100, 50, 95)
    }
    @Test fun findSuitableLine_leftAligned() {
        val geneLines = Array<TIntList>(4) { TIntArrayList() };
        geneLines[0].add(105);
        assertFindSuitableLine(1 to 0,
                               geneLines, 0, -10, 0)
    }

    private fun assertFindSuitableLine(expected: Pair<Int, Int?>, geneLines: Array<TIntList>,
                                       geneStartX: Int, labelPrefStartX: Int,
                                       labelOverlappingStartX: Int) {
        Assert.assertEquals(expected,
                            GenesTrackView.findSuitableLine(geneLines, geneStartX,
                                                            labelPrefStartX, labelOverlappingStartX))
    }

    @Test fun labeledGenePrefStarts_leftAlignedGene() {
        assertEquals(-10 to 0, GenesTrackView.labeledGenePrefStarts(0, 200, 10, Strand.PLUS))
        assertEquals(0 to 0, GenesTrackView.labeledGenePrefStarts(0, 200, 10, Strand.MINUS))
    }

    @Test fun labeledGenePrefStarts_shortLabel() {
        assertEquals(90 to 100, GenesTrackView.labeledGenePrefStarts(100, 200, 10, Strand.PLUS))
        assertEquals(100 to 100, GenesTrackView.labeledGenePrefStarts(100, 200, 10, Strand.MINUS))
    }

    @Test fun labeledGenePrefStarts_longLabel() {
        assertEquals(50 to 100, GenesTrackView.labeledGenePrefStarts(100, 150, 50, Strand.PLUS))
        assertEquals(100 to 100, GenesTrackView.labeledGenePrefStarts(100, 150, 50, Strand.MINUS))

        assertEquals(20 to 70, GenesTrackView.labeledGenePrefStarts(100, 150, 80, Strand.PLUS))
        assertEquals(100 to 100, GenesTrackView.labeledGenePrefStarts(100, 150, 80, Strand.MINUS))
    }

    @Test fun determineLabelStartX_noOverlap() {
        assertEquals(88, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 85, 1000))

        assertEquals(202, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, 85, 1000))
    }

    @Test fun determineLabelStartX_noSpace() {
        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, null, 1000))
        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 195, 1000))
        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 145, 150))

        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, null, 1000))
        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, 196, 205))
        assertEquals(-1, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, 145, 150))
    }

    @Test fun determineLabelStartX_overlap() {
        assertEquals(95, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 95, 1000))
        assertEquals(100, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 100, 1000))
        assertEquals(150, GenesTrackView.determineLabelStartX(Strand.PLUS, 100, 200, 10, 150, 1000))
        assertEquals(0, GenesTrackView.determineLabelStartX(Strand.PLUS, 0, 200, 10, 0, 1000))

        assertEquals(202, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, 150, 1000))
        assertEquals(195, GenesTrackView.determineLabelStartX(Strand.MINUS, 100, 200, 10, 150, 205))
    }

    @Test fun determineLabelBgDim_noOverlap() {
        assertFalse(GenesTrackView.determineLabelBgDim(10, 50, Strand.PLUS, 60, 200))
        assertFalse(GenesTrackView.determineLabelBgDim(200, 50, Strand.MINUS, 60, 200))
    }

    @Test fun determineLabelBgDim_geneBodyOverlap() {
        assertTrue(GenesTrackView.determineLabelBgDim(11, 50, Strand.PLUS, 60, 200))
        assertTrue(GenesTrackView.determineLabelBgDim(60, 50, Strand.PLUS, 60, 200))

        assertTrue(GenesTrackView.determineLabelBgDim(199, 50, Strand.MINUS, 60, 200))
        assertTrue(GenesTrackView.determineLabelBgDim(100, 50, Strand.MINUS, 60, 200))
    }
}
