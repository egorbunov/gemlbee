 package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.sequence.Nucleotide
import sun.font.FontDesignMetrics
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import javax.swing.JLabel

class SequenceTrack : TrackView("Chromosome sequence") {
    private val font = JLabel().font.deriveFont(Font.BOLD, 12f)
    private val fontHeight: Int
    private val maxCharWidth: Int

    init {
        val fontMetrics = FontDesignMetrics.getMetrics(font)
        maxCharWidth = fontMetrics.stringWidth("W")
        fontHeight = fontMetrics.height

        preferredHeight = fontHeight + 2 * VERTICAL_SEPARATOR
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        g.font = font

        val currRegion = model.range

        val trackWidth = conf[TrackView.WIDTH]
        val trackHeight = conf[TrackView.HEIGHT]
        val nucleotidesInRegion = currRegion.length()

        val borderRectY = trackHeight - fontHeight - VERTICAL_SEPARATOR - 1
        val borderRectHeight = fontHeight + 1
        val textY = trackHeight - VERTICAL_SEPARATOR - 3

        if (nucleotidesInRegion <= trackWidth) {
            val drawSequence = trackWidth / currRegion.length() > maxCharWidth
            val sequence = model.chromosome.sequence
            for (i in currRegion.startOffset until currRegion.endOffset - 1) {
                val b = sequence.byteAt(i)
                g.color = COLORS[if (b < 0) 4 else b.toInt()]

                val thisStartX = TrackUIUtil.genomeToScreen(i, trackWidth, model.range)
                val nextStartX = TrackUIUtil.genomeToScreen(i + 1, trackWidth, model.range)
                if (drawSequence) {
                    val ch = Nucleotide.getChar(b).toUpperCase()
                    g.drawString(Character.toString(ch), thisStartX + 1, textY)
                } else {
                    g.fillRect(thisStartX, borderRectY, nextStartX - thisStartX, borderRectHeight)
                }
            }
        } else {
            g.color = Color.DARK_GRAY
            g.fillRect(0, borderRectY, trackWidth, borderRectHeight)

            g.color = Color.LIGHT_GRAY
            g.drawString("Chromosome sequence (scale is too small)", 5, textY)
        }
    }

    companion object {
        private const val VERTICAL_SEPARATOR = 1

        private val COLORS = arrayOf(Color.GREEN,       // T
                                     Color.BLUE,        // C
                                     Color.ORANGE,      // A
                                     Color.RED,         // G
                                     Color.LIGHT_GRAY)  // N
    }
}