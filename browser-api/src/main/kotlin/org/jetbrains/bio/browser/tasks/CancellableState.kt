package org.jetbrains.bio.browser.tasks

import java.util.concurrent.CancellationException

/**
 * Contract:
 * Suppose we have a thread which invokes several [CancellableTask],
 * each task has its own instance of [CancellableState], which can be cancelled from outer thread,
 * Task itself invokes [checkCanceled] which can cause exception, immediately interrupting it.

 * @author Oleg Shpynov
 * @since 11/7/14
 */
class CancellableState private constructor() {
    @Volatile private var cancelled = false

    init {
        reset()
    }

    /**
     * Set up cancelled state, i.e.
     * make [checkCanceled] throw [CancellationException]
     * @return this for chain calls
     */
    fun cancel(): CancellableState {
        cancelled = true
        return this
    }

    /**
     * Set up not cancelled state
     * @return this for chain calls
     */
    fun reset(): CancellableState {
        cancelled = false
        return this
    }

    /**
     * In case of cancelled state, throws [CancellationException]
     */
    fun checkCanceled() {
        if (cancelled) {
            throw CancellationException()
        }
    }

    companion object {

        private val THREAD_LOCALS = ThreadLocal<CancellableState>()

        // No synchronization for thread local required
        val instance: CancellableState
            get() {
                var cancellableState: CancellableState? = THREAD_LOCALS.get()
                if (cancellableState == null) {
                    cancellableState = CancellableState()
                    THREAD_LOCALS.set(cancellableState)
                }
                return cancellableState
            }
    }

}
