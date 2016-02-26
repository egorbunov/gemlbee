package org.jetbrains.bio.util

import org.jetbrains.bio.ext.awaitAll
import org.jetbrains.bio.ext.forking
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private object OhNoesException : Exception()

class ExecutorExtensionsTest {
    @Test(expected = OhNoesException::class) fun testAwaitAll() {
        val tasks = (1..4).map {
            Callable {
                if (it % 2 == 0) {
                    throw OhNoesException
                }
            }
        }

        val executor = Executors.newCachedThreadPool()
        executor.awaitAll(tasks)
    }

    @Test(expected = OhNoesException::class) fun testForking() {
        (1..4).forking {
            if (it % 2 == 0) {
                throw OhNoesException
            }
        }
    }
}
