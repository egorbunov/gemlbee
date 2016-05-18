package org.jetbrains.bio.browser.query.desktop

/**
 * @author Egor Gorbunov
 * *
 * @since 18.05.16
 */
interface TrackNameListener {
    fun addTrackName(name: String)
    fun deleteTrackName(name: String)
}
