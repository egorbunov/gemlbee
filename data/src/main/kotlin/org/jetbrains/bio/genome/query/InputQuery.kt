package org.jetbrains.bio.genome.query

/**
 * An annotated and cached version of [java.util.function.Supplier].
 *
 * @author Evgeny Kurbatsky
 * @author Oleg Shpynov
 * @since 21/06/12
 */
interface InputQuery<T> {
    open fun getUncached(): T

    open fun get(): T {
        return getUncached()  // no caching in default implementation
    }

    /**
     * Returns unique identifier suitable for use in a file name.
     */
    val id: String

    /**
     * Returns a human-readable description for this query.
     */
    val description: String get() = id
}

