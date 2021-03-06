package org.jetbrains.bio.util

import com.google.common.cache.CacheBuilder
import org.apache.log4j.Logger
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Fork/Join pool requires all blocking operations to be mediated by
 * a [ForkJoinPool.ManagedBlocker] instance. This class abstracts this detail away
 * by implementing Fork/Join-friendly [synchronized].
 *
 * @author Roman Chernyatchik
 */
object LockManager {
    private val LOG = Logger.getLogger(LockManager::class.java)

    private val lockMap = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build<Any, ReentrantLock>()

    /**
     * Fork/Join-friendly [synchronized].
     *
     * Locks are invalidated after 1 day.
     *
     * @param key an object to synchronize on.
     * @param block a callback which is allowed (but not required)
     *              to unlock the lock once it is not needed any more.
     */
    fun <R> synchronized(key: Any, block: (ReentrantLock) -> R): R {
        val lock = lockMap[key, ::ReentrantLock]
        try {
            LOG.trace("Acquiring lock key={$key} ...")
            ForkJoinPool.managedBlock(ManagedLocker(lock))
            LOG.trace("Done, executing code key={$key} ...")
            return block(lock)
        } finally {
            try {
                lock.unlock()
                LOG.trace("Released lock key={$key}")
            } catch (e: IllegalMonitorStateException) {
                // Ignore, already released by block
            }
        }
    }
}

private class ManagedLocker(private val lock: Lock) : ForkJoinPool.ManagedBlocker {
    private var hasLock = false

    override fun block(): Boolean {
        if (!hasLock) {
            lock.lock()
        }
        return true
    }

    override fun isReleasable(): Boolean {
        if (!hasLock) {
            hasLock = lock.tryLock()
        }

        return hasLock
    }
}
