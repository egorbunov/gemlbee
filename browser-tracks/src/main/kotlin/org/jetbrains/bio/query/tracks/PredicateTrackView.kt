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
                         val track: PredicateTrack,
                         val binsNum: Int) : LocationAwareTrackView<Location>(name) {
    override fun getItems(model: SingleLocationBrowserModel): Iterable<Location> {
        return track.eval(model.chromosomeRange, binsNum).map {
            Location(it.startOffset, it.endOffset, model.chromosome)
        }
    }
}