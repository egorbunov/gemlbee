package org.jetbrains.bio.browser.query.desktop

import org.jetbrains.bio.browser.tracks.TrackView

/**
 * @author Egor Gorbunov
 * @since 06.05.16
 */

/**
 * Listener of newly generated tracks (for example, interpreter may generate some)
 */
interface NewTrackViewListener {
    open fun addNewTrackView(trackView: TrackView)
}
