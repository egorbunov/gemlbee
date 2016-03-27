package org.jetbrains.bio.browser

import org.jetbrains.bio.genome.Range

/**
 * Maps genomic coordinate to the corresponding X coordinate on the screen.
 *
 * @param offset 0-based genomic coordinate.
 * @param width screen width in pixels.
 * @param range visible genomic range.
 * @return screen x coordinate.
 */
fun genomeToScreen(offset: Int, width: Int, range: Range): Int {
    // Ideally, we should only allow `offset in range`, but in a lot
    // of places the function is called with `range.endOffset`.
    // So we have to allow `offset == range.endOffset` as an exception.
    require(offset in range || offset == range.endOffset) {
        "offset out of range: $offset not in $range"
    }

    val shift = (offset - range.startOffset).toLong()
    val x = (shift * width / range.length()).toInt()

    // `width` instead of `width - 1` to match the edge-case above.
    return Math.min(Math.max(x, 0), width)
}

/**
 * Returns genomic coordinate for a given X coordinate on the screen.
 *
 * @param screenX screen x coordinate.
 * @param width screen width in pixels.
 * @param range visible genomic range.
 * @return chromosome offset.
 */
fun screenToGenome(screenX: Int, width: Long, range: Range): Int {
    require(screenX in 0..width - 1) {
        "screen X coordinate out of range [0, $width)"
    }

    return ((screenX.toLong()) * range.length() / width + range.startOffset).toInt()
}
