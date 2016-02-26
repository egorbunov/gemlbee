package org.jetbrains.bio.ext

import java.util.stream.Collector
import java.util.stream.Stream

/**
 * @author Roman.Chernyatchik
 */

/**
 * Returns parallel stream method to kotlin Collection interface.
 *
 * Please remove it after KT-5175 (https://youtrack.jetbrains.com/issue/KT-5175) is fixed
 */
fun <T> Collection<T>.parallelStream(): java.util.stream.Stream<T> {
    return (this as java.util.Collection<T>).parallelStream()
}
fun <T> Collection<T>.stream(): java.util.stream.Stream<T> {
    return (this as java.util.Collection<T>).stream()
}

// Yes. This is a simple alias. However, M13 type system cannot infer 'T'
// when using direct `#collect` call.
fun <T, R> Stream<T>.collectHack(collector: Collector<in T, *, R>) = collect(collector)
