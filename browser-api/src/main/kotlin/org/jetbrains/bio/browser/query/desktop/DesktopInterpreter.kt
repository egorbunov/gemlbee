package org.jetbrains.bio.browser.query.desktop

/**
 * @author Egor Gorbunov
 * @since 17.05.16
 */
interface DesktopInterpreter {
    fun interpret(query: String): String
    fun addNewTrackListener(listener: NewTrackViewListener)
    fun removeNewTrackListener(listener: NewTrackViewListener)
}