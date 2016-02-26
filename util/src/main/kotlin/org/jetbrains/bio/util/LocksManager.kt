package org.jetbrains.bio.util

import com.google.common.cache.CacheBuilder
import org.apache.log4j.Logger
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * @author Roman.Chernyatchik
 */
object LocksManager {
    private val LOG = Logger.getLogger(LocksManager::class.java)

    private val lockMap = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.DAYS)
            .maximumSize(100000)
            .build<Any, Lock>()

    /**
     * Synchronization using look associated with key. Locks are
     * stored in cache with auto-cleanup for locks not accessed
     * within one day.
     *
     * Block - is block object, which can unlock lock if it
     * non needed
     */
    fun synchronized(key: Any, block: (Any, Lock) -> Unit) {
        val lock = lockMap[key, { ReentrantLock() }]
        try {
            LOG.trace("Acquiring lock key={$key} ...")
            ForkJoinPool.managedBlock(ManagedLocker(lock))
            LOG.trace("Done, executing code key={$key} ...")
            block(key, lock)
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

class ManagedLocker(private val lock: Lock) : ForkJoinPool.ManagedBlocker {
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
