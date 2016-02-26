package org.jetbrains.bio.util

import com.google.common.primitives.Longs
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.asFractionOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAccumulator
import java.util.function.LongBinaryOperator

/**
 * This class is used to track and report the progress of an arbitrary process.
 * The progress is measured in the number of processed items.
 *
 * @author Dmitry Groshev
 * @author Sergei Lebedev
 * @since 22/08/2014
 */
abstract class Progress(
        private var timeOutNanos: Long,
        protected var percentageDelta: Int,
        private var title: String,
        protected var accumulator: LongAccumulator) {

    private var lastDuration = AtomicLong(-timeOutNanos)
    protected var isDone = false // maybe this should be volatile
    protected val startTime = System.nanoTime()

    /**
     * This is the main "guarding" method for Progress. Its purpose is to
     * avoid excessive progress reporting (using [timeOutNanos]). It is
     * called for every update and therefore should be fast.
     *
     * The gist of it is an atomic CAS. It goes like this.
     *
     *   1. Calculate elapsed time from the creation of this [Progress].
     *   2. Get current value of [lastDuration] field that encodes the
     *      last reporting time. It's a *volatile* read, therefore it's
     *      visible across threads.
     *   3. Compare calculated duration with a threshold and return -1
     *      if it's too early to report progress.
     *   4. This is the tricky part. It's probable that more than one
     *      thread read lastDuration and now they are racing to set
     *      `snapshot`. We are using CAS here to gently serialize this
     *      scenario. If some other thread already changed [lastDuration],
     *      it means that other thread successfully reported and this
     *      thread shouldn't do this.
     *
     * Here is a sketchy proof that there this will work:
     * - atomics use volatiles
     * - therefore, reads and writes to [lastDuration] are strictly
     *   ordered by happens-before relation, and [System.nanoTime] calls
     *   are strictly ordered as well. It's guaranteed by JMM that the
     *   ordering will be like this:
     *     - thread1 calls nanoTime;
     *     - thread1 writes [lastDuration];
     *     - thread2 reads [lastDuration];
     *     - thread2 calls nanoTime.
     * - we can show that duration is always strictly larger than `snapshot` on CAS:
     * - it's either larger because [System.nanoTime] returns larger
     *   value than previous one;
     * - or, if it returns the same value as in a different thread, duration
     *   will be equal to `snapshot` and "duration < timeoutNanos + snapshot"
     *   will be true and this method will return -1;
     * - therefore, *every* CAS will increase value of [lastDuration];
     * - therefore, there is no possibility that two threads will
     *   successfully update `snapshot` at the same time.
     *
     * Regarding return value: [System.nanoTime] is not free, therefore
     * it's best to avoid extra calls, therefore this method performs checks
     * AND returns duration.
     */
    protected fun getDuration() : Long {
        val lastDurationValue = lastDuration.get()
        val duration = System.nanoTime() - startTime
        if (duration < timeOutNanos + lastDurationValue) {
            return -1
        }
        if (!lastDuration.compareAndSet(lastDurationValue, duration)) {
            return -1
        }
        return duration
    }

    protected abstract fun format(duration: Long, processed: Long): String

    protected fun tell(message: String) = LOG.info("$title: $message")

    protected fun done(message: String, totalItems: Long) {
        if (!isDone) {
            isDone = true
            val processed = totalItems
            val duration = System.nanoTime() - startTime
            tell(format(duration, processed) + " [done]")
            if (message.isNotEmpty()) {
                tell(message)
            }
        }
    }

    interface Common {
        /**
         * Marks progress as done. No further updates would be accumulated.
         */
        fun done(message: String = "")
    }

    /**
     * Incremental progress increments the internal accumulator on update.
     */
    interface Incremental : Common {
        fun report(update: Long)

        fun report() = report(1)
    }

    /**
     * Monotonic progress accumulates the maximum reported update.
     */
    interface Monotonic : Common {
        fun report(update: Long)
    }

    class Builder {
        protected var timeOutNanos = TimeUnit.SECONDS.toNanos(1)
        protected var percentageDelta = 1
        protected var title: String? = null

        fun period(period: Long, timeUnit: TimeUnit): Builder {
            timeOutNanos = timeUnit.toNanos(period)
            return this
        }

        fun percentageDelta(percentageDelta: Int): Builder {
            this.percentageDelta = percentageDelta
            return this
        }

        fun title(title: String): Builder {
            this.title = title
            return this
        }

        fun monotonic(totalItems: Long): Monotonic {
            return Bounded(totalItems, timeOutNanos, percentageDelta, title,
                           LongAccumulator(MAX, 0))
        }

        fun monotonic(): Monotonic {
            return Unbounded(timeOutNanos, percentageDelta, title,
                             LongAccumulator(MAX, 0))
        }

        fun incremental(totalItems: Long): Incremental {
            return Bounded(totalItems, timeOutNanos, percentageDelta, title,
                           LongAccumulator(SUM, 0))
        }

        fun incremental(): Incremental {
            return Unbounded(timeOutNanos, percentageDelta, title,
                             LongAccumulator(SUM, 0))
        }

        companion object {
            private val MAX = LongBinaryOperator { a, b -> Longs.max(a, b) }
            private val SUM = LongBinaryOperator { a, b -> a + b }
        }
    }

    companion object {
        val LOG = Logger.getLogger(Progress::class.java)

        // XXX switch to Groovy-style builder after there're no Java
        //     callers.
        @JvmStatic fun builder() = Builder()
    }
}

class Bounded(private val totalItems: Long,
              timeOutNanos: Long,
              percentageDelta: Int, title: String?,
              accumulator: LongAccumulator)
:
        Progress(timeOutNanos, percentageDelta, title ?: "Progress", accumulator),
        Progress.Monotonic, Progress.Incremental {

    @Volatile private var progressSoFar: Long = -1

    override fun report(update: Long) {
        check(!isDone) { "this progress is done" }
        accumulator.accumulate(update)

        val duration = getDuration()
        if (duration == -1L) {
            return
        }

        val processed = accumulator.get()
        val currentProgress = (100.0 * processed * percentageDelta / totalItems).toLong()
        if (currentProgress > progressSoFar) {
            progressSoFar = currentProgress
            if (processed < totalItems) {
                tell(format(duration, processed))
            } else {
                check(processed == totalItems) {
                    "progress overflow, expected $totalItems, got $processed"
                }
                done()
            }
        }
    }

    override fun format(duration: Long, processed: Long): String {
        val throughputPart = if (processed > 1 && processed < totalItems) {
            val ETA = asTime((duration / (processed.toDouble() / totalItems)).toLong() - duration)
            val throughput = asThroughput(processed, duration)
            ", Throughput: $throughput, ETA: $ETA"
        } else {
            ""
        }

        return ("${processed.asFractionOf(totalItems)}, Elapsed time: "
                + asTime(duration) + throughputPart)
    }

    override fun done(message: String) = done(message, totalItems)
}

class Unbounded(timeOutNanos: Long, percentageDelta: Int,
                title: String?, accumulator: LongAccumulator)
:
        Progress(timeOutNanos, percentageDelta, title ?: "Processed items", accumulator),
        Progress.Monotonic, Progress.Incremental {

    override fun report(update: Long) {
        check(!isDone) { "this progress is done" }
        accumulator.accumulate(update)

        val duration = getDuration()
        if (duration == -1L) {
            return
        }

        val processed = accumulator.get()
        tell(format(duration, processed))
    }

    override fun format(duration: Long, processed: Long): String {
        return if (processed > 1) {
            val avgThroughput = asThroughput(processed, duration)
            "$processed, Elapsed time: ${asTime(duration)}, Throughput: $avgThroughput"
        } else {
            "$processed, Elapsed time: ${asTime(duration)}"
        }
    }

    override fun done(message: String) = done(message, accumulator.get())
}

private fun Long.chooseUnit(): TimeUnit {
    return when {
        DAYS.convert(this, NANOSECONDS) > 0 -> DAYS
        HOURS.convert(this, NANOSECONDS) > 0 -> HOURS
        MINUTES.convert(this, NANOSECONDS) > 0 -> MINUTES
        SECONDS.convert(this, NANOSECONDS) > 0 -> SECONDS
        MILLISECONDS.convert(this, NANOSECONDS) > 0 -> MILLISECONDS
        MICROSECONDS.convert(this, NANOSECONDS) > 0 -> MICROSECONDS
        else -> NANOSECONDS
    }
}

private fun TimeUnit.abbreviate(): String {
    return when (this) {
        NANOSECONDS -> "ns"
        MICROSECONDS -> "\u03bcs" // μs
        MILLISECONDS -> "ms"
        SECONDS -> "s"
        MINUTES -> "min"
        HOURS -> "h"
        DAYS -> "d"
        else -> throw AssertionError()
    }
}

fun asThroughput(items: Long, nanos: Long) : String {
    val digits = 10  // <digits> items/unit.
    val unit = (nanos * digits / items).chooseUnit()
    val amount = items * NANOSECONDS.convert(1, unit) / nanos
    return if (amount == 0L) {
        "${unit.convert(nanos, NANOSECONDS) / items} ${unit.abbreviate()}/item"
    } else {
        "$amount items/${unit.abbreviate()}"
    }
}

fun asTime(nanos: Long) : String {
    val unit = nanos.chooseUnit()
    val duration = nanos.toDouble() / NANOSECONDS.convert(1, unit)
    return "%.4g %s".format(duration, unit.abbreviate())
}