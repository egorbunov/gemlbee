package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.LocationAwareTrackView
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.query.parse.PredicateTrack
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class PredicateTrackView(name: String,
                         val track: PredicateTrack) : LocationAwareTrackView<Location>(name) {

    private val cache = HashMap<String, List<Location>>()

    private fun calc(model: SingleLocationBrowserModel): List<Location> {
        // TODO: fix it
        val wholeRange = ChromosomeRange(model.chromosome.range.startOffset,
                model.chromosome.range.endOffset, model.chromosome)
        return cache.getOrPut(model.chromosome.name) {
            track.eval(wholeRange, wholeRange.length()).map {
                Location(it.startOffset, it.endOffset, model.chromosome)
            }
        }
    }

    override fun getItems(model: SingleLocationBrowserModel): Iterable<Location> {
        return track.eval(model.chromosomeRange, model.chromosomeRange.length()).map {
            Location(it.startOffset, it.endOffset, model.chromosome)
        }
    }
}