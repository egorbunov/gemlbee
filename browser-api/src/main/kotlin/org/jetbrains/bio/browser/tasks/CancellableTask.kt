package org.jetbrains.bio.browser.tasks

import org.apache.log4j.Logger
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class CancellableTask<T>(private val callable: Callable<T>) {

    val id: Int

    @Volatile var cancelled = false
        private set

    @Volatile private var cancellableState: CancellableState? = null

    @Volatile private var task: Future<T>? = null

    init {
        id = ourTasksCounter.incrementAndGet()
    }

    fun cancel(): CancellableTask<T> {
        LOG.trace("Cancel $id")
        cancelled = true
        if (cancellableState != null) {
            cancellableState!!.cancel()
        }
        if (task != null && !task!!.isDone && !task!!.isCancelled) {
            task!!.cancel(true)
        }
        return this
    }

    fun execute() {
        if (cancelled) {
            return
        }
        LOG.trace("Execute %d".format(id))
        task = executor.submit<T> { // Do not start task if cancelled
            if (cancelled) {
                return@submit null
            }
            cancellableState = CancellableState.instance.reset()
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
        check(task != null) { "Task not stated: $id" }
        check(task!!.isDone) { "Task not ready: $id" }
        try {
            return task!!.get()
        } catch (e: InterruptedException) {
            throw CancellationException(e.message)
        } catch (e: CancellationException) {
            throw CancellationException(e.message)
        } catch (e: ExecutionException) {
            // Process inner task exceptions
            val cause = e.cause
            if (cause is CancellationException) {
                throw CancellationException(e.message)
            }
            throw RuntimeException(e)
        }
    }

    val isDone: Boolean
        get() = task != null && task!!.isDone && !task!!.isCancelled

    companion object {
        val LOG = Logger.getLogger(CancellableTask::class.java)
        private val ourTasksCounter = AtomicInteger(0)

        private val executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())

        fun <T> of(callable: Callable<T>): CancellableTask<T> {
            val task = CancellableTask(callable)
            task.execute()
            return task
        }

        @TestOnly
        fun resetCounter() {
            ourTasksCounter.set(0)
        }
    }
}
