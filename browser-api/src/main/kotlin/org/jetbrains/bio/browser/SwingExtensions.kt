package org.jetbrains.bio.browser

import sun.font.FontDesignMetrics
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** Creates [Graphics2D] with antialiasing enabled. */
fun BufferedImage.createAAGraphics() = createGraphics().apply {
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                     RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
}
