package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.model.ModelListener
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.ext.asOffset
import org.jetbrains.bio.genome.Range
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics

/**
 * @author Oleg Shpynov
 * @since 29.12.14
 */
class MultipleLocationsHeader(private val model: MultipleLocationsBrowserModel) : Header() {

    init {
        // repaint on location changed
        model.addListener(object : ModelListener {
            override fun modelChanged() = repaint()
        })

        preferredSize = Dimension(30, pointerHandlerY + POINTER_HEIGHT + 1)
    }

    override val pointerHandlerY: Int
        get() = 5 + 2 * (TrackUIUtil.SMALL_FONT_HEIGHT + TrackUIUtil.VERTICAL_SPACER)

    override fun paint(g: Graphics) {
        g.font = TrackUIUtil.SMALL_FONT


        // Clear header
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        val locations = model.locationReferences.size

        // Draw grid
        TrackUIUtil.drawGrid(g, width, pointerHandlerY, height, model)

        // Draw labels
        val visibleLocations = model.visibleLocations()
        val locationsWidths = TrackUIUtil.locationsWidths(visibleLocations, width)

        var x = 0
        for (i in visibleLocations.indices) {
            val locRef = visibleLocations[i]
            val locationWidth = locationsWidths[i]
            val l = locRef.location
            val name = locRef.name
            val label = String.format("%s%s: %s [%s]",
                                      if (name.isEmpty()) "" else name + " = ",
                                      l.chromosome.name,
                                      l.startOffset.asOffset(),
                                      l.length().asOffset())
            if (g.fontMetrics.stringWidth(label) + 10 < locationWidth) {
                TrackUIUtil.drawString(g, label, x + 5, height - 4 - 1, Color.BLACK)
            }
            x += locationWidth
        }

        TrackUIUtil.drawScaleRuler(g, model.range, width, pointerHandlerY)
        drawPointer(g, width, Range(0, model.length), model.range, pointerHandlerY)

        // Show locations info
        g.font = TrackUIUtil.DEFAULT_FONT
        TrackUIUtil.drawString(
                g, String.format("   %s: %s; Locations: %s; Length: %sbp   ",
                                 model.genomeQuery.getShortNameWithChromosomes(),
                                 model.id,
                                 if (locations < MultipleLocationsBrowserModel.MAX_LOCATIONS)
                                     locations
                                 else
                                     "${MultipleLocationsBrowserModel.MAX_LOCATIONS}+",
                                 model.length.asOffset()),
                3, 17, Color.BLACK)
    }
}