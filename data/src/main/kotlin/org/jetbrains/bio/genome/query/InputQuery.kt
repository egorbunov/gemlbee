package org.jetbrains.bio.genome.query

import org.jetbrains.bio.util.LockManager
import java.lang.ref.SoftReference
import java.util.concurrent.locks.ReentrantLock

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

/** An input query which caches the result of [get] in a [SoftReference]. */
abstract class CachingInputQuery<T> : InputQuery<T> {
    private var cached = SoftReference<T>(null)
    private val lock = ReentrantLock()

    override fun get() = LockManager.synchronized(this) {
        check(lock.holdCount <= 0) { "Attempt to call CachingInputQuery#get recursively" }
        var value = cached.get()
        if (value == null) {
            value = getUncached()
            cached = SoftReference(value)
        }

        value
    }
}