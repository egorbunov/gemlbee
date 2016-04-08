package org.jetbrains.bio.browser.tracks

import com.google.common.collect.Iterables
import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.CpGIsland
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.LocationAware
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.containers.LocationList
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Color
import java.awt.Graphics
import java.util.*

/**
 * A track view for "things" with genomic locations. The current
 * implementation ignores `strand` value.
 *
 * @author Sergei Lebedev
 * @since 10/06/15
 */
abstract class LocationAwareTrackView<T : LocationAware> protected constructor(title: String) :
        TrackView(title) {

    init {
        preferredHeight = 10
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        paintItems(g, model, conf, getItems(model))
    }

    protected open fun paintItems(g: Graphics, model: SingleLocationBrowserModel,
                                  configuration: Storage, items: Iterable<T>) {
        val strands = items.mapTo(HashSet<Strand>()) { it.location.strand }
        val trackWidth = configuration[TrackView.WIDTH]
        val trackHeight = configuration[TrackView.HEIGHT]
        val chromosome = model.chromosome
        for (item in items) {
            val location = item.location
            assert(location.chromosome == chromosome) {
                "The impossible happened: current chromosome = $chromosome, " +
                "item chromosome = ${location.chromosome}, item = $item."
            }

            val (startOffset, endOffset) = location.toRange() intersection model.range
            if (startOffset == endOffset) {
                continue  // Empty intersection.
            }

            val startX = genomeToScreen(startOffset, trackWidth, model.range)
            val endX = genomeToScreen(endOffset, trackWidth, model.range)

            // XXX use y-shift to differentiate between the items on
            // both strands.
            val shift = when {
                strands.size == 1 -> 0
                location.strand.isPlus() -> trackHeight / 2
                else -> -trackHeight / 2
            }

            g.color = item.color
            if (endX == startX) {
                // draw 3 x height: rectangle
                val width = 3
                g.fillRect(startX - 1, shift, width, trackHeight)
            } else {
                val width = endX - startX
                g.fillRect(startX, shift, width, trackHeight)
            }
        }
    }

    protected abstract fun getItems(model: SingleLocationBrowserModel): Iterable<T>

    protected open val T.color: Color get() = Color.DARK_GRAY
}

class LocationsTrackView(private val locations: LocationList, title: String) :
        LocationAwareTrackView<Location>(title) {
    override fun getItems(model: SingleLocationBrowserModel): Iterable<Location> {
        return Iterables.concat(locations[model.chromosome, Strand.PLUS],
                                locations[model.chromosome, Strand.MINUS])
    }
}

class CpGIslandsTrackView : LocationAwareTrackView<CpGIsland>("CpG islands") {
    override fun preprocess(genomeQuery: GenomeQuery) {
        for (chromosome in genomeQuery.get()) {
            chromosome.cpgIslands
        }
    }

    override fun getItems(model: SingleLocationBrowserModel) = model.chromosome.cpgIslands
}

