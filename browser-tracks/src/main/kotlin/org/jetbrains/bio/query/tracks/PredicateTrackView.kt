package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.LocationAwareTrackView
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.query.parse.PredicateTrack
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

/**
 * TODO: implement me (PredicateTrackView) =)
 */
class PredicateTrackView(name: String,
                         val track: PredicateTrack) : LocationAwareTrackView<Location>(name) {

    private val cache = HashMap<String, List<Location>>()

    private fun calc(model: SingleLocationBrowserModel): List<Location> {
        val start = model.chromosomeRange.startOffset
        val lst = track.eval(model.chromosomeRange, model.chromosomeRange.length())
        return emptyList()
    }


    override fun getItems(model: SingleLocationBrowserModel): Iterable<Location> {
        throw UnsupportedOperationException()
    }
}