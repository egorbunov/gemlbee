package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import java.awt.Graphics

@Suppress("unused")
/**
 * @author Oleg Shpynov
 */
class Separator: TrackView("") {

    init {
        preferredHeight = 0
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        // Do nothing here
    }

}
