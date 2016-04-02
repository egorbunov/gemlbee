package org.jetbrains.bio.ext

import com.google.common.base.Stopwatch
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *  Measures the running time of a given possibly impure [block].
 */
inline fun <R> Logger.time(level: Level = Level.DEBUG,
                           message: String = "",
                           block: () -> R): R {
    log(level, "$message...")
    val stopwatch = Stopwatch.createStarted()

    val res = try {
        block()
    } catch (e: Exception) {
        stopwatch.stop()
        log(level, "$message: [FAILED] after $stopwatch", e)
        throw e
    }
    stopwatch.stop()
    log(level, "$message: done in $stopwatch")
    return res
}