package org.jetbrains.bio.browser.tasks

import junit.framework.TestCase
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException

/**
 * @author Oleg Shpynov
 */
class CancellableTaskTest : TestCase() {

    private val intCallable = Callable<Int> {
        for (i in 0..9) {
            CancellableState.instance.checkCanceled()
            Thread.sleep(100)
        }
        42
    }
    private val incorrectCallable = Callable<Int> { throw RuntimeException("WOOT") }

    @Throws(Exception::class)
    fun testNotReady() {
        assertFalse(CancellableTask.of(intCallable).isDone)
    }

    @Throws(Exception::class)
    fun testCancelled() {
        try {
            CancellableTask.of(incorrectCallable).cancel().get()
        } catch (e: CancellationException) {
            return
        }

        fail("CancellationException expected")
    }

    @Throws(Exception::class)
    fun testCancelledWaitAndGet() {
        val task = CancellableTask.of(intCallable)
        Thread(Runnable { task.cancel() }).start()
        assertNull(task.waitAndGet())
    }

    @Throws(Exception::class)
    fun testWaitAndGet() {
        assertEquals(42, CancellableTask.of(intCallable).waitAndGet()!!.toInt())
    }

    @Throws(Exception::class)
    fun testRuntimeException() {
        val cancellableTask = CancellableTask.of(incorrectCallable)
        Thread.sleep(200)
        try {
            cancellableTask.get()
        } catch (e: RuntimeException) {
            return
        }
        fail("exception expected")
    }


    @Throws(Exception::class)
    fun testRuntimeExceptionWait() {
        try {
            CancellableTask.of(incorrectCallable).waitAndGet()
        } catch (e: RuntimeException) {
            return
        }
        fail("exception expected")
    }
}
fun <T> CancellableTask<T>.waitAndGet(): T? {
    while (true) {
        if (cancelled) {
            CancellableTask.LOG.trace("Cancelled $id")
            return null
        }
        if (isDone) {
            CancellableTask.LOG.trace("Loaded task $id")
            return get()
        }
        try {
            Thread.sleep(1000)
        } catch (ex: InterruptedException) {
            // Ignore
        }
    }
}

