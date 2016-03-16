package org.jetbrains.bio.browser.tracks

import gnu.trove.list.TIntList
import gnu.trove.list.array.TIntArrayList
import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.ExonicBlock.*
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.browser.util.TrackUIUtil.genomeToScreen
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.*

/**
 * @author Roman Chernyatchik
 */
class GenesTrackView : TrackView(GeneClass.ALL.description) {
    init {
        preferredHeight = LINES_PREFERRED_COUNT * LINE_HEIGHT_PX
    }

    override fun preprocess(genomeQuery: GenomeQuery) {
        val build = genomeQuery.build

        val url = Mart.Companion.forBuild(build).host
        title = "${GeneClass.ALL.description} ($url)"
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val trackHeight = conf[TrackView.HEIGHT]
        val trackWidth = conf[TrackView.WIDTH]

        // Create drawing lines - as much as track height allow
        val linesCount = trackHeight / LINE_HEIGHT_PX
        if (linesCount == 0) {
            // height is too small
            TrackUIUtil.drawErrorMessage(g, "Track height is to small, enlarge it please.")
            return
        }

        val chromosome = model.chromosome
        val range = model.range

        // Genes to show
        val genes = chromosome.genes.asSequence()
                .filter { it.location.toRange() intersects range }
                // for better layout so genes as ranges, i.e ignoring strand
                .sortedWith(Comparator.comparing<Gene, Range> { it.location.toRange() })
                .toList()

        val metaInf = model.rangeMetaInf
        val selectedGene = if (metaInf is GeneLocRef) metaInf.gene else null

        val geneLines = Array<TIntList>(linesCount) { TIntArrayList() };
        val elseLine = linesCount - 1
        val descriptionSecondLine = elseLine - 1

        for (gene in genes) {
            // Gene label:
            val label = gene.symbol
            val labelWidthPx = g.fontMetrics.stringWidth(label)

            val geneLoc = gene.location
            val strand = geneLoc.strand

            // If gene starts in invisible area - trunk it to visible one
            val geneStartX = Math.max(0, genomeToScreen(geneLoc.startOffset, trackWidth, range))
            val geneEndX = Math.min(trackWidth, genomeToScreen(geneLoc.endOffset, trackWidth, range))

            // labeled gene preferred and less preferred starts
            val (prefStartX, overlappingStartX)
                    = labeledGenePrefStarts(geneStartX, geneEndX, labelWidthPx, strand)

            val (line, labelLeftBoundX)
                    = findSuitableLine(geneLines, geneStartX, prefStartX, overlappingStartX)

            geneLines[line].add(geneEndX + strand.choose(0, labelWidthPx + LABEL_SPACER_PX)
                    + LABEL_LEFT_SPACER_PX)

            // If gene is selected we will write it's description in 2 last lines
            // so let's free it of other genes, they are grayed and not important
            if (selectedGene == null || line < descriptionSecondLine) {
                drawGene(gene, isGeneIgnored(gene, selectedGene), geneStartX, geneEndX,
                         label, labelLeftBoundX, line, linesCount,
                         g, model, genes.size, trackWidth)
            }

        }

        // Draw gene description if
        // a) gene Selected
        // b) only 1 gene is visible
        if (genes.size > 1 && selectedGene != null) {
            drawGeneDescription(g, elseLine, selectedGene, trackWidth);
        }

        drawOthersLineSeparator(g, linesCount, trackWidth)
    }

    /**
     * Draws gene in given 'line'
     */
    private fun drawGene(gene: Gene,
                         ignored: Boolean,
                         geneStartX: Int, geneEndX: Int,
                         label: String, labelLeftBoundX: Int?,
                         line: Int, linesCount: Int,
                         g: Graphics, model: SingleLocationBrowserModel,
                         genesCount: Int, trackWidth: Int) {

        val strand = gene.strand

        // Gene transcript
        val color = transcriptColor(gene, ignored)
        drawGeneTranscript(g, color, geneStartX, geneEndX, strand, line)

        // Exons/CDS/UTR etc
        drawGeneRegions(g, gene, ignored, line, model, trackWidth);

        // Gene label
        val labelWidth = g.getFontMetrics(TrackUIUtil.SMALL_FONT).stringWidth(label)
        if (genesCount < MAX_SHOWN_GENES_WITH_LABELS) {
            val labelX = determineLabelStartX(strand, geneStartX, geneEndX,
                    labelWidth, labelLeftBoundX, trackWidth)
            if (labelX != -1) {
                drawGeneLabel(g, label,
                        if (ignored) COLOR_LABEL_IGNORED else color,
                        labelX, line,
                        determineLabelBgDim(labelX, labelWidth, strand, geneStartX, geneEndX))
            }

            if (genesCount == 1) {
                drawGeneDescription(g, linesCount, gene, trackWidth);
            }
        }
    }

    private fun transcriptColor(gene: Gene, ignored: Boolean) = when {
        ignored -> COLOR_GENE_IGNORED
        gene.isCoding -> COLOR_CODING
        else -> COLOR_NON_CODING
    }

    private fun drawGeneTranscript(g: Graphics, color: Color,
                                   geneStartX: Int, geneEndX: Int,
                                   strand: Strand,
                                   line: Int) {
        val centerY = getLineCenterY(line);

        g.color = color
        g.drawLine(geneStartX, centerY, geneEndX, centerY);

        val arrowLen = 2;
        val shift1 = strand.choose(0, arrowLen)
        val shift2 = strand.choose(arrowLen, 0)
        for (i in (geneStartX + arrowLen)..(geneEndX - arrowLen) step arrowLen * 3) {
            g.drawLine(i - shift1, centerY, i - shift2, centerY - arrowLen);
            g.drawLine(i - shift1, centerY, i - shift2, centerY + arrowLen);
        }
    }

    private fun getLineCenterY(line: Int) = line * LINE_HEIGHT_PX + LINE_HEIGHT_PX / 2

    private fun drawRect(g: Graphics,
                         x: Int, y: Int, width: Int, height: Int,
                         trackWidth: Int) {
        // accepts rect large that track X axis bounds
        var xFixed = x;
        var widthFixed = width;

        if (xFixed < 0) {
            widthFixed += xFixed;
            xFixed = 0;
        }
        if (widthFixed > trackWidth) {
            widthFixed = trackWidth;
        }

        if (widthFixed == 1) {
            g.drawLine(xFixed, y, xFixed, y + height);
        } else {
            g.fillRect(xFixed, y, widthFixed, height);
        }
    }


    private fun drawOthersLineSeparator(g: Graphics, linesCount: Int, trackWidth: Int) {
        // if more than one line, separate single-gene lines from "all other" line
        if (linesCount > 1) {
            g.color = Color.LIGHT_GRAY;
            // dotted line
            for (screenX in 0..trackWidth step 9) {
                val lastLineY = (linesCount - 1) * LINE_HEIGHT_PX;
                g.drawLine(screenX, lastLineY, screenX + 3, lastLineY);
            }
        }
    }

    private fun isGeneIgnored(gene: Gene, preSelectedGene: Gene?)
            = preSelectedGene != null && preSelectedGene !== gene

    private fun drawGeneDescription(g: Graphics, linesCount: Int,
                                    gene: Gene,
                                    trackWidth: Int) {

        val desc = "${gene.description} (e:${gene.exons.size}, i:${gene.introns.size})"
        val names = gene.names.values.filter { it.isNotEmpty() }.joinToString { it };

        val fm = g.getFontMetrics(TrackUIUtil.SMALL_FONT);
        g.font = TrackUIUtil.SMALL_FONT
        if (trackWidth >= Math.max(fm.stringWidth(names), fm.stringWidth(desc))) {
            //Full gene desc:
            drawGeneLabel(g, desc, Color.GRAY, 0, linesCount - 2, true);
            drawGeneLabel(g, names, Color.GRAY, 0, linesCount - 1, true);
        } else {
            // Short desc
            val geneId = gene.getName(GeneAliasType.ENSEMBL_ID);
            if (trackWidth >= fm.stringWidth(geneId)) {
                drawGeneLabel(g, geneId, Color.GRAY, 0, linesCount - 1, true);
            }
            // else: no place for additional info
        }

    }

    private fun drawGeneLabel(g: Graphics, label: String, color: Color,
                              x: Int, line: Int,
                              dimBG: Boolean) {
        val fm = g.fontMetrics
        val centerY = getLineCenterY(line);

        if (dimBG) {
            g.color = TrackUIUtil.COLOR_WHITE_ALPHA;
            g.fillRect(x - 3, centerY - LINE_HEIGHT_PX / 2, fm.stringWidth(label) + 6, fm.height);
        }

        g.color = color;
        g.font = TrackUIUtil.SMALL_FONT
        (g as Graphics2D).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.drawString(label, x, centerY + fm.descent + 2);
    }

    private fun drawGeneRegions(g: Graphics,
                                gene: Gene, ignored: Boolean,
                                line: Int,
                                model: BrowserModel, trackWidth: Int) {

        // Paint exons
        val visibleRange = model.range
        val visibleExons = gene.exons.filter { visibleRange.intersects(it.toRange()) }

        val centerY = getLineCenterY(line);

        if (!gene.isCoding) {
            // just draw as is
            g.color = if (ignored) COLOR_LOCUS_IGNORED else COLOR_NON_CODING_EXON
            for (exonLocation in visibleExons) {
                drawRange(g, centerY, NON_CODING_EXON_HEIGHT, exonLocation.toRange(), model, trackWidth)
            }
        } else {

            val cds = gene.cds!!

            // draw CDS
            g.color = when {
                ignored -> COLOR_LOCUS_IGNORED
                else -> cds.strand.choose(COLOR_CDS_PLUS, COLOR_CDS_MINUS)
            }
            drawRange(g, centerY, CODING_EXON_CDS_HEIGHT, cds.toRange(), model, trackWidth)

            // draw UTRs and exons
            g.color = if (ignored) COLOR_LOCUS_IGNORED else COLOR_CODING_EXON
            for ((block, range) in annotate(cds, visibleExons)) {
                when (block) {
                    UTR5_EXON, UTR3_EXON -> {
                        drawRange(g, centerY, CODING_EXON_UTR_HEIGHT, range, model, trackWidth)
                    }
                    CDS_EXON -> {

                        drawRange(g, centerY, CODING_EXON_CDS_HEIGHT, range, model, trackWidth)
                    }
                    else -> check(false) { "Unsupported annotation $block for $range" }
                }
            }
        }
    }

    private fun drawRange(g: Graphics,
                          centerY: Int, rectHeight: Int,
                          range: Range, model: BrowserModel,
                          trackWidth: Int) {

        if (range.isNotEmpty()) {
            val startX = genomeToScreen(range.startOffset, trackWidth, model.range)
            val endX = genomeToScreen(range.endOffset, trackWidth, model.range)

            drawRect(g,
                    startX, centerY - rectHeight / 2,
                    Math.max(1, endX - startX), rectHeight,
                    trackWidth)
        }
    }


    override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
        TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                COLOR_CODING to "coding",
                COLOR_NON_CODING to "non coding",
                COLOR_CDS_PLUS to "+ CDS",
                COLOR_CDS_MINUS to "- CDS")
    }

    companion object {
        // Genes lines count, this tuning designed for headless mode
        private val LINES_PREFERRED_COUNT = 14
        private val LINE_HEIGHT_PX = 15
        private val LABEL_SPACER_PX = 2
        private val LABEL_LEFT_SPACER_PX = 5 + LABEL_SPACER_PX
        private val NON_CODING_EXON_HEIGHT = 6
        private val CODING_EXON_CDS_HEIGHT = 12
        private val CODING_EXON_UTR_HEIGHT = 6


        private val COLOR_CODING = Color(43, 208, 50)
        private val COLOR_CODING_EXON = Color(43, 208, 50, 130)
        private val COLOR_NON_CODING = Color(243, 69, 77)
        private val COLOR_NON_CODING_EXON = Color(243, 69, 77, 100)
        private val COLOR_CDS_PLUS = Color(43, 208, 50, 50)
        private val COLOR_CDS_MINUS = Color(16, 28, 255, 30)

        private val COLOR_LOCUS_IGNORED = Color(192, 192, 192, 80)
        private val COLOR_GENE_IGNORED = Color.LIGHT_GRAY
        private val COLOR_LABEL_IGNORED = Color(152, 152, 152)

        private val MAX_SHOWN_GENES_WITH_LABELS = 1000

        /**
         * For each label mode - available line index and 1st rightmost free offset null if no info
         */
        fun findSuitableLine(geneLines: Array<TIntList>,
                             geneStartX: Int,
                             genePrefStartX: Int,
                             geneOverlappedLabelStartX: Int): Pair<Int, Int?> {

            // by default: last line
            val defaultLine = geneLines.size - 1;

            // Find first max suitable line among all line except last one
            // last line is for all genes with not enough space

            // prefLabelStart: 'true' -> best, 'false' -> overlapping label, 'null' -> no label (worst)
            var prefLabelStart: Boolean? = null;
            var bestLine = defaultLine;
            var labelLeftBoundX: Int? = null;

            for (line in 0..defaultLine - 1) {
                val genEndXs = geneLines[line];

                // Genes are sorted by start & end, so for next gene
                // it is enough to compare it with latest gene at line
                val lastGeneEndX = when {
                    genEndXs.isEmpty -> 0
                    else -> genEndXs[genEndXs.size() - 1];
                }

                // preferred:
                if (lastGeneEndX <= genePrefStartX) {
                    bestLine = line
                    labelLeftBoundX = lastGeneEndX;

                    // if preferred found => OK, stop searching
                    break;
                }

                // try to find better candidate
                if (prefLabelStart == null) {
                    // with some label
                    if (lastGeneEndX <= geneOverlappedLabelStartX) {
                        prefLabelStart = false;
                        bestLine = line;
                        labelLeftBoundX = lastGeneEndX;
                    }

                    // or at least define line
                    if (bestLine == defaultLine && lastGeneEndX <= geneStartX) {
                        bestLine = line
                    }
                }
            }
            return bestLine to labelLeftBoundX;
        }

        fun labeledGenePrefStarts(geneStartX: Int, geneEndX: Int, geneNameWidthPx: Int,
                                  strand: Strand): Pair<Int, Int> {
            // Preferred label start '+' before gene, '-' after gene
            // Here gene start, not label start:
            val prefStartX = geneStartX - strand.choose(geneNameWidthPx, 0)

            // Less preferred start: overlaps gene name on plus strand (ignored on minus)
            val overlappingStartX = strand.choose(Math.min(geneStartX, geneEndX - geneNameWidthPx),
                                                  geneStartX)

            return prefStartX to overlappingStartX
        }

        fun determineLabelStartX(strand: Strand, geneStartX: Int, geneEndX: Int,
                                 labelWidthPx: Int,
                                 labelLeftBoundX: Int?,
                                 trackWidth: Int): Int {
            if (labelLeftBoundX == null) {
                return -1
            }

            val labelX = if (strand.isPlus()) {
                val labelPrefX = Math.max(labelLeftBoundX, geneStartX - labelWidthPx - LABEL_SPACER_PX)
                if (labelPrefX + labelWidthPx <= Math.min(geneEndX, trackWidth)) labelPrefX else -1
            } else {
                val labelPrefX = Math.min(trackWidth - labelWidthPx, geneEndX + LABEL_SPACER_PX);
                if (labelPrefX < labelLeftBoundX) -1 else labelPrefX
            }
            return labelX
        }

        fun determineLabelBgDim(labelX: Int, labelWidth: Int, strand: Strand, geneStartX: Int, geneEndX: Int): Boolean {
            return if (strand.isPlus()) {
                labelX + labelWidth > geneStartX
            } else {
                labelX < geneEndX
            }
        }
    }
}


fun annotate(cds: Location, exons: List<Location>): List<Pair<ExonicBlock, Range>> {
    if (cds.length() == 0 || exons.isEmpty()) {
        //TODO NonCoding genes support
        return Collections.emptyList();
    }

    val strand = cds.strand;
    val (cdsStart, cdsEnd) = cds;

    val blocks = ArrayList<Pair<ExonicBlock, Range>>();

    // Left/right on screen, doesn't depend on gene strand
    for (exon in exons) {
        val (exonStart, exonEnd) = exon

        when {
            exonEnd <= cdsStart -> {
                // Before CDS
                blocks.add(strand.choose(UTR5_EXON, UTR3_EXON) to exon.toRange());
            }

            exonStart < cdsStart && exonEnd > cdsEnd -> {
                // Covers CDS:
                blocks.add(strand.choose(UTR5_EXON, UTR3_EXON) to Range(exonStart, cdsStart));
                blocks.add(CDS_EXON to cds.toRange());
                blocks.add(strand.choose(UTR3_EXON, UTR5_EXON) to Range(cdsEnd, exonEnd));
            }

            exonStart < cdsStart && exonEnd <= cdsEnd && exonEnd > cdsStart -> {
                // Intersects with left CDS bound
                blocks.add(strand.choose(UTR5_EXON, UTR3_EXON) to Range(exonStart, cdsStart));
                blocks.add(CDS_EXON to Range(cdsStart, exonEnd));
            }

            exonStart >= cdsStart && exonEnd <= cdsEnd -> {
                // Inside CDS
                blocks.add(CDS_EXON to exon.toRange());
            }

            exonStart < cdsEnd && exonStart >= cdsStart && exonEnd > cdsEnd -> {
                // Intersects with right CDS bound
                blocks.add(CDS_EXON to Range(exonStart, cdsEnd));
                // UTR part:
                blocks.add(strand.choose(UTR3_EXON, UTR5_EXON) to Range(cdsEnd, exonEnd));

            }

            exonStart >= cdsEnd -> {
                blocks.add(strand.choose(UTR3_EXON, UTR5_EXON) to exon.toRange());
            }

            else -> check(false) { "Unexpected case: cd=$cds, exon=$exon" }
        }
    }

    return blocks;
}

/**
 * Is used to annotate exon splitting by CDS
 */
enum class ExonicBlock {
    UTR5_EXON,
    CDS_EXON,
    UTR3_EXON
}
