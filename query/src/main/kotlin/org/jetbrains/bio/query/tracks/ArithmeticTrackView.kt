package org.jetbrains.bio.query.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.query.parse.ArithmeticTrack
import java.awt.Graphics

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class ArithmeticTrackView(name: String, val track: ArithmeticTrack): TrackView(name) {
    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        throw UnsupportedOperationException()
    }
}