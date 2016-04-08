package org.jetbrains.bio.browser.util

import org.apache.commons.math3.util.Precision
import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.asOffset
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.genome.LocationAware
import org.jetbrains.bio.genome.Range
import sun.font.FontDesignMetrics
import java.awt.*
import java.text.DecimalFormat
import java.util.*
import javax.swing.JLabel

/**
 * @author Roman.Chernyatchik
 */
object TrackUIUtil {
    val YAXIS_TICK_SHORT: DecimalFormat = DecimalFormat("#.###")
    val YAXIS_TICK_DETAILED: DecimalFormat = DecimalFormat("0.###E0")

    val LEGEND_COLOR_BOX_HEIGHT: Int = 10
    val LEGEND_COLOR_BOX_WIDTH: Int = 10
    val LEGEND_SPACE_BETWEEN_ITEMS: Int = 10
    val LEGEND_COLOR_PREVIEW_TEXT_SPACER: Int = 2
    val LEGEND_INDENT: Int = 2
    val BG_MASK: Color = Color(255, 255, 255, 170)
    val LEGEND_RIGHT_MARGIN: Int = 20
    const val VERTICAL_SPACER: Int = 5

    @JvmField val DEFAULT_FONT = JLabel().font
    @JvmField val DEFAULT_FONT_HEIGHT = FontDesignMetrics.getMetrics(DEFAULT_FONT).height
    @JvmField val SMALL_FONT = JLabel().font.deriveFont(9f)
    @JvmField val SMALL_FONT_HEIGHT = FontDesignMetrics.getMetrics(SMALL_FONT).height

    val COLOR_WHITE_ALPHA = Color(255, 255, 255, 210)

    /**
     * Draw axis. If realScale is Scale.undefined() than do not show tick marks
     */
    @JvmOverloads @JvmStatic fun drawVerticalAxis(
            g: Graphics,
            yAxisTitle: String,
            visibleScale: TrackView.Scale,
            drawInBG: Boolean,
            trackWidth: Int,
            axisHeight: Int,
            axisScreenY: Int = 0,
            flipVertical: Boolean = false) {

        // Settings
        val tickMarkHalfLength = 2
        val marginX = 3

        // switch to small font
        g.font = SMALL_FONT

        // Collect tick marks info:
        val plotBottomScreenY = axisScreenY + if (!flipVertical) axisHeight - 1 else 0
        val plotTopScreenY = when {
            !flipVertical -> Math.max(axisScreenY, axisScreenY)
            else -> axisScreenY + Math.min(axisHeight, axisHeight) - 1
        }

        val tickValueYTextY = tickMarksInfo(visibleScale,
                axisHeight, axisScreenY, flipVertical, SMALL_FONT_HEIGHT,
                plotTopScreenY, plotBottomScreenY)

        // Bg under axis labels
        g.color = Color(255, 255, 255, 180)
        val longestTickTextPx = tickValueYTextY.map { g.fontMetrics.stringWidth(it.second) }.max()!!
        val titleWidth = SMALL_FONT_HEIGHT + 2
        val axisYWidth = titleWidth + marginX + (2 * tickMarkHalfLength + 2) + longestTickTextPx
        val titleX = if (drawInBG) trackWidth - Math.max(LEGEND_RIGHT_MARGIN, axisYWidth) else marginX
        g.fillRect(titleX - marginX, axisScreenY, axisYWidth, axisHeight)

        // Axis title:
        g.color = Color.BLACK

        // * Transform for painting text 90 angle rotated
        val g2 = g as Graphics2D
        val originalTransform = g2.transform
        val originalAntiAliasingSetting = g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.translate(titleX.toFloat().toDouble(), (if (flipVertical) plotBottomScreenY else plotTopScreenY).toDouble())
        g2.rotate(Math.toRadians(90.0))
        g2.drawString(yAxisTitle, 2, 0)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, originalAntiAliasingSetting)
        g2.transform = originalTransform

        // Vertical line
        val axisScreenX = titleX + titleWidth
        g.drawLine(axisScreenX, plotTopScreenY, axisScreenX, plotBottomScreenY)

        // Draw tick marks axis
        for ((valueY, text, textY) in tickValueYTextY) {
            g.drawLine(axisScreenX - tickMarkHalfLength, valueY,
                       axisScreenX + tickMarkHalfLength, valueY)
            g.drawString(text, axisScreenX + tickMarkHalfLength + 2, textY)
        }
    }

    /**
     * Draws string on the screen on the white background, useful when printing over track
     */
    @JvmStatic fun drawString(g: Graphics, string: String, x: Int, y: Int, color: Color) {
        val bounds = g.fontMetrics.getStringBounds(string, g)
        val width = bounds.height.toInt()
        val height = bounds.width.toInt()

        g.color = Color.WHITE
        g.fillRect(x - 1, y - width + 1, height + 2, width + 2)
        g.color = color
        g.drawString(string, x, y)
    }

    @JvmStatic fun locationsWidths(namedLocations: List<LocationAware>,
                                   screenWidth: Int): List<Int> {
        val cumulativeLength = namedLocations.stream().mapToInt { it.location.length() }.sum()


        var prevWidth = 0
        var currLength = 0.0

        val locWidth = namedLocations.mapIndexed { i, la ->
            // accurate length
            currLength += la.location.length()
            val locWidth = Math.round(screenWidth * currLength / cumulativeLength).toInt() - prevWidth
            prevWidth += locWidth

            locWidth
        }
        return locWidth
    }

    @JvmStatic fun drawGrid(g: Graphics, range: Range, width: Int, height: Int) {
        val stepSize = stepSize(range.length())
        g.color = Color.LIGHT_GRAY

        for (offset in range.startOffset..range.endOffset step stepSize) {
            val x = genomeToScreen(offset, width, range)
            g.drawLine(x, 0, x, height)
        }
    }

    @JvmStatic fun drawGrid(g: Graphics,
                            width: Int,
                            top: Int, bottom: Int,
                            multiModel: MultipleLocationsBrowserModel) {
        // Configure graphics
        val originalStroke = (g as Graphics2D).stroke
        val dashed = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(5f), 0f)
        g.stroke = dashed
        g.color = Color.GRAY

        val locationsWidths = TrackUIUtil.locationsWidths(multiModel.visibleLocations(), width)

        var x = 0
        for (locationsWidth in locationsWidths) {
            if (x > 0) {
                g.drawLine(x, top, x, bottom)
            }
            x += locationsWidth
        }
        g.stroke = originalStroke
    }

    fun stepSize(length: Int): Int {
        // Step: 1, 5, 10, 50, 100, 500, ... bp
        var step = 1
        var times5 = true
        while (length / step > 30) {
            step *= if (times5) 5 else 2
            times5 = !times5
        }
        return step
    }

    @JvmStatic fun drawBoxedLegend(g: Graphics,
                                   trackWidth: Int, trackHeight: Int,
                                   drawInBG: Boolean,
                                   vararg legendItems: Pair<Color, String>) {
        check(legendItems.isNotEmpty())

        g.font = SMALL_FONT
        val fontMetrics = g.fontMetrics

        val legendItemsCount = legendItems.size

        // Legend width
        val legendLabelsSummaryWidth
                = legendItems.asSequence().map { fontMetrics.stringWidth(it.second) }.sum()

        val legendWidth = 2 * LEGEND_INDENT +
                // summary: legend icon width + gap before text
                legendItemsCount * (LEGEND_COLOR_BOX_WIDTH + LEGEND_COLOR_PREVIEW_TEXT_SPACER) +
                // summary: gap before legend items
                legendItemsCount * LEGEND_SPACE_BETWEEN_ITEMS +
                // legend text length
                legendLabelsSummaryWidth

        val legendHeight = Math.max(SMALL_FONT_HEIGHT,
                LEGEND_COLOR_BOX_HEIGHT) + 2 * LEGEND_INDENT
        val legendY = if (drawInBG) LEGEND_INDENT else trackHeight - legendHeight - LEGEND_INDENT
        val legendX = (trackWidth - LEGEND_RIGHT_MARGIN - 4) - legendWidth - 1
        g.color = COLOR_WHITE_ALPHA
        g.fillRect(legendX, legendY, legendWidth, legendHeight)
        g.color = Color.BLACK
        g.drawRect(legendX, legendY, legendWidth, legendHeight)

        val legendTextLeftBottomX = legendX + LEGEND_INDENT
        val legendTextLeftBottomY = legendY + legendHeight - LEGEND_INDENT

        var currX = legendTextLeftBottomX

        for ((color, text) in legendItems) {
            g.color = color
            g.fillRect(currX, legendTextLeftBottomY - LEGEND_COLOR_BOX_HEIGHT,
                    LEGEND_COLOR_BOX_WIDTH, LEGEND_COLOR_BOX_HEIGHT)
            currX += LEGEND_COLOR_BOX_WIDTH + LEGEND_COLOR_PREVIEW_TEXT_SPACER

            g.color = Color.BLACK
            g.drawString(text, currX, legendTextLeftBottomY)
            currX += fontMetrics.stringWidth(text) + LEGEND_SPACE_BETWEEN_ITEMS
        }

        if (drawInBG) {
            val originComposite = (g as Graphics2D).composite
            g.composite = AlphaComposite.SrcOver

            // Decrease BG brightness: draw partly transparent white rectangle
            g.color = BG_MASK
            g.fillRect(legendX, legendY, legendWidth, legendHeight)

            g.composite = originComposite
        }
    }

    @JvmStatic fun drawErrorMessage(g: Graphics, message: String) {
        drawString(g, message, 10, g.fontMetrics.height, Color.RED);
    }


    @JvmStatic fun drawScaleRuler(g: Graphics, range: Range, width: Int, height: Int) {
        val regionLength = range.length()
        val stepSize = stepSize(regionLength)

        g.color = Color(104, 179, 255)
        val margin = 5  // left and right margin
        val scaleLineY = height - 2
        val right = margin + Math.round(stepSize * 1.0 / regionLength * width).toInt()

        // Scale line
        g.drawLine(margin, scaleLineY, right, scaleLineY)
        g.drawLine(margin, scaleLineY - 2, margin, scaleLineY + 2)
        g.drawLine(right, scaleLineY - 2, right, scaleLineY + 2)

        // scale size
        val text = "${stepSize.asOffset()} of ${regionLength.asOffset()} bp"
        g.drawString(text, margin, scaleLineY - 4)

        // show resolution: # bp in on pixel, helps to
        // understand how many bins are merged in on pixel
        val bpInPixel = regionLength / width
        if (bpInPixel > 1) {
            val scaleTextWidth = g.fontMetrics.stringWidth(text)

            g.drawString("1 pixel ~ ${bpInPixel.asOffset()} bp",
                         width - margin - scaleTextWidth, scaleLineY - 4)
        }
    }

    @JvmStatic fun drawOffsets(g: Graphics, range: Range, width: Int) {
        val stepSize = stepSize(range.length())

        g.color = Color.BLACK

        val fontMetrics = g.fontMetrics
        val textBottomLineY = fontMetrics.height + 5

        var prevX = -10
        for (offset in range.startOffset..range.endOffset step stepSize) {
            val x = genomeToScreen(offset, width, range)
            // This is always the case unless 'genomeToScreen' is broken.
            assert(x >= 0)

            if (x - prevX <= 2) {
                continue  // Drop overlaps. But why 2?
            }

            val label = offset.asOffset()
            prevX = x + fontMetrics.stringWidth(label)
            if (prevX > width) {
                break     // Out of screen bounds.
            }

            g.drawString(label, x + 1, textBottomLineY)
        }
    }

    private fun tickMarksInfo(visibleScale: TrackView.Scale,
                              axisHeight: Int, axisScreenY: Int,
                              flipVertical: Boolean, fontHeight: Int,
                              plotTopScreenY: Int, plotBottomScreenY: Int): List<Triple<Int, String, Int>> {

        val format = { value: Double ->
            val absValue = Math.abs(value)
            when {
                absValue == 0.0 -> "0"
                absValue.isNaN() -> "NaN"
                absValue.isInfinite() -> "N/A"
                absValue in 0.001..1000.0 -> YAXIS_TICK_SHORT.format(value)
                else -> YAXIS_TICK_DETAILED.format(value)
            }
        }

        val tickValueYTextY = ArrayList<Triple<Int, String, Int>>()
        val (min, max) = visibleScale;
        if (min.isFinite() && max.isFinite()) {
            // If real scale specified:

            val rangeLength = visibleScale.max - visibleScale.min
            if (!Precision.equals(rangeLength, 0.0)) {
                // Let's assume gap 5 - 20 pixels between axis ticks markers
                val minStepLength = (fontHeight + 2) * rangeLength / axisHeight
                val maxStepLength = (fontHeight + 20) * rangeLength / axisHeight

                // Let's found nice round suitable step length
                var pow = -10
                var d = 1
                var step = 1.0

                while (pow < 10) {
                    step = Math.pow(10.0, pow.toDouble()) * d
                    if (step >= minStepLength && step <= maxStepLength) {
                        break
                    }
                    if (d < 9) {
                        d++
                    } else {
                        d = 1
                        pow++
                    }
                }

                // if step was found - use it for axis
                if (pow < 10) {
                    val stepsCount = Math.floor(rangeLength / step).toInt()
                    if (stepsCount > 2) {
                        // first step - for min value
                        // last step - reserved for max value
                        // {first + 1, .., last - 1} steps - intermediate values

                        // Tick marks
                        for (stepIndex in 0..stepsCount - 1) {

                            val valueAtStep: Double
                            val valueAtStepScreenY: Int
                            val tickTextAtStepScreenY: Int

                            if (stepIndex == stepsCount - 1) {
                                // last step: reserved for max value
                                valueAtStep = visibleScale.max
                                valueAtStepScreenY = axisHeight - 1
                                tickTextAtStepScreenY = valueAtStepScreenY - (if (!flipVertical) fontHeight else 0)
                            } else {
                                valueAtStep = visibleScale.min + stepIndex * step
                                valueAtStepScreenY = Math.round((valueAtStep - visibleScale.min) * axisHeight / rangeLength).toInt()
                                tickTextAtStepScreenY = valueAtStepScreenY + (if (!flipVertical) 0 else fontHeight)
                            }

                            val tickMarkScreenY = plotBottomScreenY - (if (flipVertical) -1 else 1) * valueAtStepScreenY
                            val tickTextScreenY = plotBottomScreenY - (if (flipVertical) -1 else 1) * tickTextAtStepScreenY

                            // hide if out of range
                            if (!flipVertical && (tickMarkScreenY < axisScreenY)) {
                                break
                            } else if (flipVertical && (tickMarkScreenY > plotBottomScreenY + axisHeight)) {
                                break
                            }

                            tickValueYTextY.add(Triple(tickMarkScreenY, format(valueAtStep), tickTextScreenY))
                        }
                    }
                }

            }
        }
        if (tickValueYTextY.isEmpty()) {
            // In case of any difficulties - draw just min/max bounds
            // max
            tickValueYTextY.add(Triple(plotTopScreenY, format(max),
                    plotTopScreenY + if (!flipVertical) fontHeight + 1 else -1))
            // min
            tickValueYTextY.add(Triple(plotBottomScreenY, format(min),
                    plotBottomScreenY + (if (!flipVertical) -1 else fontHeight + 1)))

        }
        return tickValueYTextY
    }
}
