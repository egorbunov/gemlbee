package org.jetbrains.bio.genome.query

import org.jetbrains.bio.util.ManagedLocker
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.locks.ReentrantLock

/**
 * @author Roman.Chernyatchik
 */
abstract class CachingInputQuery<T> : InputQuery<T> {
    private var cachedValueRef: Reference<T> = SoftReference(null)
    private val lock = ReentrantLock()

    override fun get(): T {
        check(lock.holdCount <= 0) { "Attempt to call CachingInputQuery#get recursively" }
        try {
            ForkJoinPool.managedBlock(ManagedLocker(lock))
            var value = cachedValueRef.get()
            if (value == null) {
                value = getUncached()
                cachedValueRef = SoftReference(value)
            }
            return value
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } finally {
            lock.unlock()
        }
    }
}