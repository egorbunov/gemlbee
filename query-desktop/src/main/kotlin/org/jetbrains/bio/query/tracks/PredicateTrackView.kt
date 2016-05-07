package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.LocationAwareTrackView
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.query.parse.PredicateTrack

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

/**
 * TODO: implement me (PredicateTrackView) =)
 */
class PredicateTrackView(name: String,
                         val track: PredicateTrack) : LocationAwareTrackView<Location>(name) {

    override fun getItems(model: SingleLocationBrowserModel): Iterable<Location> {
        throw UnsupportedOperationException()
    }
}