package org.jetbrains.bio.browser.query.desktop

/**
 * @author Egor Gorbunov
 * @since 17.05.16
 */
interface DesktopInterpreter {
    fun isParseable(query: String): Boolean
    fun interpret(query: String): String
    fun addNewTrackNameListener(listener: TrackNameListener)
    fun removeNewTrackNameListener(listener: TrackNameListener)
    fun addNewTrackViewListener(listener: NewTrackViewListener)
    fun removeNewTrackViewListener(listener: NewTrackViewListener)
    fun getAvailableNamedTracks(): List<String>
}