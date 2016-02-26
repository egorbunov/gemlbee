package org.jetbrains.bio.util

import org.apache.log4j.WriterAppender
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressFormattingTest {
    @Test fun testFormatTime() {
        assertEquals("1.234 \u03bcs", asTime(1234))
        assertEquals("10.00 s", asTime(TimeUnit.SECONDS.toNanos(10)))
    }

    @Test fun testFormatThroughput() {
        assertEquals("1 items/s", asThroughput(10, TimeUnit.SECONDS.toNanos(10)))
        assertEquals("2 items/s", asThroughput(20, TimeUnit.SECONDS.toNanos(10)))
        assertEquals("2 items/ms", asThroughput(25442, TimeUnit.SECONDS.toNanos(10)))
        assertEquals("2544 items/ns", asThroughput(25442, 10))
    }
}

data class ProgressPart(val percentCompleted: Double,
                        val itemsDone: Int, val itemsOverall: Int,
                        val elapsedSeconds: Int) {
    companion object {
        private val PAT_BOUNDED =
                ("Progress: (\\d+\\.\\d+)% \\((\\d+)/(\\d+)\\), " +
                 "Elapsed time: (\\d+)\\.\\d+ ((?:\\w|\u03bc)+)").toRegex()
        private val PAT_UNBOUNDED =
                ("Processed items: (\\d+), " +
                 "Elapsed time: (\\d+)\\.\\d+ ((?:\\w|\u03bc)+)").toRegex()

        /** Converts (duration, unit) pair to seconds. */
        private fun Pair<Int, String>.toSeconds(): Int {
            val unit = when (second) {
                "\u03bcs" -> TimeUnit.MICROSECONDS
                "ms" -> TimeUnit.MILLISECONDS
                "s"  -> TimeUnit.SECONDS
                else -> throw AssertionError("'$second'")
            }

            return unit.toSeconds(first.toLong()).toInt()
        }

        fun fromBoundedProgressString(str: String): ProgressPart {
            assertTrue(PAT_BOUNDED in str, "This should match regexp: '$str'")
            val match = PAT_BOUNDED.find(str)!!.groups
            return ProgressPart(match[1]!!.value.toDouble(),
                                match[2]!!.value.toInt(),
                                match[3]!!.value.toInt(),
                                (match[4]!!.value.toInt() to match[5]!!.value).toSeconds())
        }

        fun fromUnboundedProgressString(str: String): ProgressPart {
            assertTrue(PAT_UNBOUNDED in str, "This should match regexp: '$str'")
            val match = PAT_UNBOUNDED.find(str)!!.groups
            return ProgressPart(-1.0, match[1]!!.value.toInt(), -1,
                                (match[2]!!.value.toInt() to match[3]!!.value).toSeconds())
        }
    }
}

abstract class ProgressTest {
    protected var logContent: ByteArrayOutputStream by Delegates.notNull()

    /** A list of log output lines. */
    protected val strings: List<String> get() = logContent.toString().trim().split('\n')
    /** A list of reports from progress. */
    protected abstract val parts: List<ProgressPart>

    @Before fun setUp() {
        logContent = ByteArrayOutputStream()
        val appender = WriterAppender(Logs.LAYOUT, logContent)
        appender.name = "appender for ProgressTest"
        Progress.LOG.addAppender(appender)
    }

    @After fun tearDown() {
        Progress.LOG.removeAppender("appender for ProgressTest")
    }
}

class SequentialProgressTest : ProgressTest() {
    override val parts: List<ProgressPart> get() = strings.map {
        ProgressPart.fromBoundedProgressString(it)
    }

    @Test fun testBasic() {
        val progress = Progress.builder().percentageDelta(10).monotonic(100)
        for (i in 0..100) {
            progress.report(i.toLong())
            Thread.sleep(100)
        }
        progress.done()
        progress.done() // to ensure that this doesn't make any difference

        assertEquals(11, strings.size)
        assertEquals(0.0, parts[0].percentCompleted)
        assertEquals(0, parts[0].elapsedSeconds);
        assertEquals(0, parts[0].itemsDone)
        assertEquals(100.0, parts.last().percentCompleted)
        assertEquals(10, parts.last().elapsedSeconds)
        assertEquals(100, parts.last().itemsDone)
        assertTrue("[done]" in strings.last())
    }

    @Test fun testBasicIncremental() {
        val progress = Progress.builder().incremental(100)
        for (i in 0..99) {
            progress.report()
            Thread.sleep(100)
        }
        progress.done()
        progress.done() // to ensure that this doesn't make any difference

        assertEquals(11, strings.size)
        assertEquals(1.0, parts[0].percentCompleted)
        // can be <1s, e.g. 258ms.
        // assertEquals(0, parts.get(0).elapsed);
        assertEquals(1, parts[0].itemsDone)
        assertEquals(100.0, parts.last().percentCompleted)
        assertEquals(10, parts.last().elapsedSeconds)
        assertEquals(100, parts.last().itemsDone)
        assertTrue("[done]" in strings.last())
    }
}

class ParallelProgressTest : ProgressTest() {
    override val parts: List<ProgressPart> get() = strings.map {
        ProgressPart.fromUnboundedProgressString(it)
    }

    @Test fun testParallel() {
        val THREADS = 8
        // we use 9900 ms here instead of just one second because otherwise there
        // is a race on 10th second (10th second will or will not be reported)
        val TEST_DURATION = TimeUnit.MILLISECONDS.toNanos(9900)
        val WAITNANOS = 10

        val progress = Progress.builder().monotonic()
        val pool = Executors.newFixedThreadPool(THREADS)
        val adder = LongAdder()

        val endTime = System.nanoTime() + TEST_DURATION
        val callables = (0..THREADS).map {
            Callable<Void> {
                var lastTime = System.nanoTime()
                while (lastTime < endTime) {
                    // busy wait here, woo-hoo
                    while (System.nanoTime() - lastTime < WAITNANOS) {
                    }
                    adder.add(1)
                    progress.report(adder.sum())
                    lastTime = System.nanoTime()
                }

                null
            }
        }

        pool.invokeAll(callables)
        progress.done()
        progress.done() // to ensure that this doesn't make any difference

        val uniqueSeconds = parts.asSequence().map { it.elapsedSeconds }
                .distinct().count()
        val allSeconds = parts.size
        assertEquals(allSeconds, uniqueSeconds + 1,
                     "shouldn't report progress more often than once in second, except [done]")
        assertTrue("[done]" in strings[strings.size - 1])
        assertEquals(adder.sum(), parts[parts.size - 1].itemsDone.toLong(),
                     "should report all progress")
    }

    @Test fun testParallelIncremental() {
        val THREADS = 8
        val TEST_DURATION = TimeUnit.MILLISECONDS.toNanos(9900)
        val WAITNANOS = 10

        val progress = Progress.builder().incremental()
        val pool = Executors.newFixedThreadPool(THREADS)
        val adder = LongAdder()

        val endTime = System.nanoTime() + TEST_DURATION
        val callables = (0..THREADS).map {
            Callable<Void> {
                var lastTime = System.nanoTime();
                while (lastTime < endTime) {
                    // busy wait here, woo-hoo
                    while (System.nanoTime() - lastTime < WAITNANOS) {}
                    adder.add(1);
                    progress.report();
                    lastTime = System.nanoTime();
                }

                null
            }
        }

        pool.invokeAll(callables)
        progress.done()
        progress.done() // to ensure that this doesn't make any difference

        val uniqueSeconds = parts.asSequence().map { it.elapsedSeconds }
                .distinct().count()
        assertEquals(parts.size, uniqueSeconds + 1,
                     "shouldn't report progress more often than once in second, except [done]")
        assertTrue("[done]" in strings[strings.size - 1])
        assertEquals(adder.sum(), parts.last().itemsDone.toLong(),
                     "should report all progress")
    }
}
