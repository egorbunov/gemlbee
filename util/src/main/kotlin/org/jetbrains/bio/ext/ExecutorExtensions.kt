package org.jetbrains.bio.ext

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.*

/**
 * Executes tasks re-throwing any exception occurred.
 */
fun ExecutorService.awaitAll(tasks: Iterable<Callable<*>>) {
    val wrapper = MoreExecutors.listeningDecorator(this)
    for (future in Futures.inCompletionOrder(tasks.map { wrapper.submit(it) })) {
        try {
            future.get()
        } catch (e: ExecutionException) {
            shutdownNow()
            throw e.cause ?: e
        }
    }
}

/**
 * A parallel for-loop over ForkJoin pool.
 *
 * Unlike [java.util.stream.IntStream] it doesn't assume a single
 * iteration is cheap and creates a [java.util.concurrent.ForkJoinTask]
 * per iteration.
 *
 * Avoid using [forking] for long blocking operations, e.g. I/O.
 */
inline fun IntRange.forking(crossinline block: (Int) -> Unit) {
    val tasks = Array(endInclusive - start + 1) {
        ForkJoinTask.adapt { block(start + it) }
    }

    ForkJoinTask.invokeAll(*tasks)
}

fun <T> List<Callable<T>>.await(parallel: Boolean): Unit {
    if (parallel) {
        val parallelism = Math.min(size, Runtime.getRuntime().availableProcessors())
        val executor = Executors.newFixedThreadPool(parallelism)
        executor.awaitAll(this)
        executor.shutdown()
    } else {
        forEach { it.call() }
    }
}

