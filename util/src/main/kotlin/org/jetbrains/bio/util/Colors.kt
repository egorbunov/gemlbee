package org.jetbrains.bio.util

import java.awt.Color
import java.util.*

/**
 * @author Roman.Chernyatchik
 */
object Colors {
    // Set1 palette, a better option would be to parse the CSV, available
    // on the official website. See:
    // http://www.personal.psu.edu/cab38/ColorBrewer/ColorBrewer_RGB.html
    val COLOR_BREWER_SET1: Array<Color> = arrayOf(
            Color(55, 126, 184),
            Color(77, 175, 74),
            Color(228, 26, 28),
            Color(152, 78, 163),
            Color(255, 127, 0),
            Color(255, 255, 51),
            Color(166, 86, 40),
            Color(247, 129, 191))

    /**
     * Converts a [0, 1] value to an RGB color.
     *
     * See http://stackoverflow.com/questions/470690/how-to-automatically-generate-n-distinct-colors.
     */
    private fun generateColor(x: Float, alpha: Int): Color {
        require(x >= 0 && x <= 1) { "x must be in range [0, 1]" }

        val r: Float
        val g: Float
        val b: Float
        when {
            x >= 0.0f && x < 0.2f -> {
                r = 0.0f
                g = x / 0.2f
                b = 1.0f
            }
            x >= 0.2f && x < 0.4f -> {
                r = 0.0f
                g = 1.0f
                b = 1.0f - (x - 0.2f) / 0.2f
            }
            x >= 0.4f && x < 0.6f -> {
                r = (x - 0.4f) / 0.2f
                g = 1.0f
                b = 0.0f
            }
            x >= 0.6f && x < 0.8f -> {
                r = 1.0f
                g = 1.0f - (x - 0.6f) / 0.2f
                b = 0.0f
            }
            x >= 0.8f && x <= 1.0f -> {
                r = 1.0f
                g = 0.0f
                b = (x - 0.8f) / 0.2f
            }
            else -> {
                r = 0.0f
                g = 0.0f
                b = 1.0f
            }
        }

        return Color(r, g, b, alpha.toFloat() / 255.0f)
    }

    /**
     * Creates a palette of a given [size].
     *
     * @param size desired number of colors.
     * @param alpha (optional) color opacity, defaults to `255`.
     * @return a list of colors.
     */
    @JvmStatic @JvmOverloads fun palette(size: Int, alpha: Int = 255): List<Color> {
        return when {
            size == 0 -> emptyList()
            size <= COLOR_BREWER_SET1.size ->
                COLOR_BREWER_SET1.asList().subList(0, size)
            else -> {
                val palette = ArrayList<Color>()
                val dx = 1.0f / (size - 1)
                for (i in 0..size - 1) {
                    palette.add(generateColor(i * dx, alpha))
                }
                palette
            }
        }
    }
}
