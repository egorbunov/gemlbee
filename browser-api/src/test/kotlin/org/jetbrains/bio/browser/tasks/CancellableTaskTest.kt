package org.jetbrains.bio.browser.tasks

import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * @author Oleg Shpynov
 */
class CancellableTaskTest {
    private val intCallable = Callable<Int> {
        for (i in 0..9) {
            CancellableState.current().checkCanceled()
            Thread.sleep(100)
        }

        42
    }

    private val incorrectCallable = Callable<Int> { throw RuntimeException("WOOT") }

    @Test fun notReady() {
        assertFalse(CancellableTask.of(intCallable).isDone)
    }

    @Test(expected = CancellationException::class) fun getAfterCancel() {
        CancellableTask.of(incorrectCallable).cancel().get()
    }

    @Test fun cancelledWaitAndGet() {
        val task = CancellableTask.of(intCallable)
        Thread { task.cancel() }.start()
        assertNull(task.waitAndGet())
    }

    @Test fun testWaitAndGet() {
        assertEquals(42, CancellableTask.of(intCallable).waitAndGet()!!.toInt())
    }

    @Test(expected = RuntimeException::class) fun runtimeExceptionGet() {
        val cancellableTask = CancellableTask.of(incorrectCallable)
        Thread.sleep(200)
        cancellableTask.get()
    }

    @Test(expected = RuntimeException::class) fun runtimeExceptionWait() {
        CancellableTask.of(incorrectCallable).waitAndGet()
    }
}

fun <T> CancellableTask<T>.waitAndGet(): T? {
    while (true) {
        if (cancelled) {
            println("Cancelled $id")
            return null
        }
        if (isDone) {
            println("Loaded task $id")
            return get()
        }

        try {
            Thread.sleep(1000)
        } catch (ignored: InterruptedException) {}
    }
}