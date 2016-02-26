package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.LocationAware
import java.awt.Color
import java.awt.Graphics

/**
 * A track view for "things" with genomic locations. The current
 * implementation ignores `strand` value.
 *
 * @author Sergei Lebedev
 * @since 10/06/15
 */
abstract class LocationAwareTrackView<T : LocationAware> protected constructor(title: String)
: TrackView(title) {

    init {
        preferredHeight = 10
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val items = getItems(model)
        if (items.isEmpty()) {
            return
        }

        paintItems(g, model, conf, items)
    }

    protected open fun paintItems(g: Graphics, model: SingleLocationBrowserModel, configuration: Storage, items: List<T>) {
        val strands = items.asSequence().map { it.location.strand }.toSet()
        val trackWidth = configuration[TrackView.WIDTH]
        val trackHeight = configuration[TrackView.HEIGHT]
        val chromosome = model.chromosome
        for (item in items) {
            val location = item.location
            assert(location.chromosome == chromosome) {
                "The impossible happened: current chromosome = $chromosome, " +
                "item chromosome = ${location.chromosome}, item = $item."
            }

            val startOffset = TrackUIUtil.genomeToScreen(
                    location.startOffset, trackWidth, model.range)
            val endOffset = TrackUIUtil.genomeToScreen(
                    location.endOffset, trackWidth, model.range)

            // XXX use y-shift to differentiate between the items on
            // both strands.
            val shift = when {
                strands.size == 1 -> 0
                location.strand.isPlus() -> trackHeight / 2
                else -> -trackHeight / 2
            }

            g.color = getItemColor(item)
            if (endOffset == startOffset) {
                // draw 3 x height: rectangle
                val width = 3
                g.fillRect(startOffset - 1, shift, width, trackHeight)
            } else {
                val width = endOffset - startOffset
                g.fillRect(startOffset, shift, width, trackHeight)
            }
        }
    }

    protected abstract fun getItems(model: SingleLocationBrowserModel): List<T>

    protected open fun getItemColor(item: T): Color = Color.DARK_GRAY
}