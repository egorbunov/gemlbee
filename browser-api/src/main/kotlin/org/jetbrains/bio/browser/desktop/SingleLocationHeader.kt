package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.model.ModelListener
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.TrackUIUtil
import java.awt.*

class SingleLocationHeader(private val model: SingleLocationBrowserModel) : Header() {
    private val gridHeight = VERTICAL_MARGIN +
                             2 * (TrackUIUtil.SMALL_FONT_HEIGHT + TrackUIUtil.VERTICAL_SPACER)

    init {
        // repaint on location changed
        model.addListener(object : ModelListener {
            override fun modelChanged() {
                repaint()
            }
        })

        preferredSize = Dimension(30, // fake width value
                                  gridHeight
                                  + CytoBandsRenderer.height(TrackUIUtil.SMALL_FONT)
                                  + VERTICAL_MARGIN)
    }

    override val pointerHandlerY: Int get() {
        return gridHeight + CytoBandsRenderer.getPointerHandlerY()
    }

    override fun paint(g: Graphics) {
        (g as Graphics2D).setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.font = TrackUIUtil.SMALL_FONT

        // Clear header
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        TrackUIUtil.drawGrid(g, model.range, width, height)
        TrackUIUtil.drawOffsets(g, model.range, width)
        TrackUIUtil.drawScaleRuler(g, model.range, width, gridHeight)

        CytoBandsRenderer.drawBands(g, gridHeight, width, model.chromosome)
        Header.drawPointer(g, width, model.chromosome.range, model.range, pointerHandlerY)
    }

    companion object {
        @JvmField val VERTICAL_MARGIN = 4
    }
}
