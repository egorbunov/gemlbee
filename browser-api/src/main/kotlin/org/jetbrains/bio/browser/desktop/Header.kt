package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.genome.Range
import java.awt.Color
import java.awt.Component
import java.awt.Graphics

abstract class Header : Component() {

    abstract val pointerHandlerY: Int

    companion object {
        val POINTER_FILL_COLOR = Color(255, 0, 0, 64)
        val POINTER_BORDER_COLOR = Color(255, 0, 0).brighter()
        @JvmField val POINTER_HEIGHT = 20

        fun drawPointer(g: Graphics, width: Int,
                        fullRange: Range,
                        visibleRange: Range,
                        yOffset: Int) {
            if (fullRange == visibleRange) {
                return
            }
            val startX = genomeToScreen(visibleRange.startOffset, width, fullRange)
            val endX = genomeToScreen(visibleRange.endOffset, width, fullRange)
            val pointerWidth = Math.max(2, endX - startX)

            // red + alpha
            g.color = POINTER_FILL_COLOR
            g.fillRect(startX, yOffset, pointerWidth, POINTER_HEIGHT)

            // red
            g.color = POINTER_BORDER_COLOR
            g.drawRect(startX, yOffset, pointerWidth, POINTER_HEIGHT)
        }
    }
}
