package org.jetbrains.bio.browser.tasks

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.MoreObjects
import org.apache.log4j.Logger
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class CancellableTask<T> internal constructor(private val callable: Callable<T>) {
    /** Process-unique task ID. */
    val id = TASK_COUNTER.incrementAndGet()

    @Volatile var cancelled = false
        private set

    @Volatile private var cancellableState: CancellableState? = null

    @Volatile private var task: Future<T>? = null

    val isDone: Boolean get() = task != null && task!!.isDone

    fun cancel(): CancellableTask<T> {
        LOG.trace("Cancelled $id from ${Thread.currentThread()}")
        cancelled = true
        if (cancellableState != null) {
            cancellableState!!.cancel()
        }

        if (task != null && !task!!.isDone) {
            task!!.cancel(true)
        }

        return this
    }

    fun execute() {
        if (cancelled) {
            return
        }
        LOG.trace("Executed $id from ${Thread.currentThread()}")
        task = EXECUTOR.submit<T> {
            if (cancelled) {
                // Do not start task if it is marked as cancelled.
                return@submit null
            }

            cancellableState = CancellableState.current().apply { reset() }
            callable.call()
        }
    }

    /**
     * @return result of callable.
     * @throws CancellationException in case when process was cancelled, see [.cancel]
     */
    @Throws(CancellationException::class)
    fun get(): T {
        if (cancelled) {
            throw CancellationException()
        }
        check(task != null) { "Task not started: $id" }
        check(task!!.isDone) { "Task not ready: $id" }
        try {
            return task!!.get()
        } catch (e: InterruptedException) {
            throw CancellationException(e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExecutionException) {
            // Process inner task exceptions
            val cause = e.cause
            if (cause is CancellationException) {
                // XXX use 'cause' instead of 'e.message'?
                throw CancellationException(e.message)
            }

            throw RuntimeException(e)
        }
    }

    override fun toString() = MoreObjects.toStringHelper(this)
            .addValue(id).toString()

    companion object {
        private val LOG = Logger.getLogger(CancellableTask::class.java)

        private val EXECUTOR = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())

        fun <T> of(callable: Callable<T>): CancellableTask<T> {
            val task = CancellableTask(callable)
            task.execute()
            return task
        }

        private val TASK_COUNTER = AtomicInteger(0)

        @VisibleForTesting fun resetCounter() = TASK_COUNTER.set(0)
    }
}
