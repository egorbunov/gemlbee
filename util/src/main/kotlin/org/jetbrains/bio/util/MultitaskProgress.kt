package org.jetbrains.bio.util

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.asFractionOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * @author Alexey Dievsky
 * @date 19.05.2015
 */

/**
 * This object tracks progress of multiple tasks. Each task should have a unique identifier (of Any type),
 * which it uses to register itself [addTask], report progress [reportTask] and finish tracking [finishTask].
 * These methods are thread-safe.
 *
 * Note that reporting and finishing tasks that haven't been added isn't an error.
 */
object MultitaskProgress {
    private val LOG = Logger.getLogger(MultitaskProgress::class.java)

    var startTime: Long = 0

    val timeOut: Long = TimeUnit.SECONDS.toNanos(5)

    val totalItemsByTasks: MutableMap<Any, Long> = ConcurrentHashMap()
    val processedItemsByTasks: MutableMap<Any, LongAdder> = ConcurrentHashMap()
    val totalItems: AtomicLong = AtomicLong()
    val processedItems: LongAdder = LongAdder()
    val lastDuration: AtomicLong = AtomicLong(-timeOut)
    var progressSoFar: Long = 0

    val runningTasks: Int
        get() = totalItemsByTasks.size

    fun tell(s: String): Unit = LOG.info("Running $runningTasks task(s): $s")

    /**
     * Restarts the progress tracker. It is assumed that no tasks are running when this method is called.
     * Not thread-safe, requires external synchronization.
     */
    fun reset() {
        startTime = System.nanoTime()
        totalItems.set(0)
        processedItems.reset()
        progressSoFar = 0
        lastDuration.set(-timeOut)
    }

    /**
     * Add a task to be tracked. Provide a reasonable estimate of iterations needed for the task to complete;
     * this estimate doesn't need to be the upper bound. If no tasks were running prior to the invocation,
     * the tracker is reset.
     * Tries to print progress by calling [reportIfNecessary].
     */
    @JvmStatic fun addTask(taskID: Any, items: Long) {
        synchronized(taskID, {
            if (totalItemsByTasks.isEmpty()) {
                reset()
            }
            if (totalItemsByTasks.containsKey(taskID)) {
                tell("Trying to add task $taskID that is already running")
                return
            }
            totalItemsByTasks.put(taskID, items)
            processedItemsByTasks.put(taskID, LongAdder())
            totalItems.addAndGet(items)
            reportIfNecessary()
        })
    }

    /**
     * Finish tracking a task. The unused iterations reserved by the task are subtracted from the total pool.
     * Tries to print progress by calling [reportIfNecessary].
     */
    @JvmStatic fun finishTask(taskID: Any) {
        synchronized(taskID, {
            if (!totalItemsByTasks.containsKey(taskID)) {
                return
            }
            totalItems.addAndGet(processedItemsByTasks.get(taskID)!!.toLong() - totalItemsByTasks.get(taskID)!!)
            totalItemsByTasks.remove(taskID)
            processedItemsByTasks.remove(taskID)
            reportIfNecessary()
        })
    }

    /**
     * Report that a task completed an iteration. If this exceeds the allotted number of iterations,
     * the number is doubled. Tries to print progress by calling [reportIfNecessary].
     */
    @JvmStatic fun reportTask(taskID: Any) {
        if (!totalItemsByTasks.containsKey(taskID)) {
            return
        }
        val processedItemsForTask = processedItemsByTasks.get(taskID)!!
        val totalItemsForTask = totalItemsByTasks.get(taskID)!!
        processedItemsForTask.increment()
        processedItems.increment()
        if (processedItemsForTask.toLong() > totalItemsForTask) {
            synchronized(taskID) {
                if (processedItemsForTask.toLong() > totalItemsForTask) {
                    totalItemsByTasks.set(taskID, totalItemsForTask * 2)
                    totalItems.addAndGet(totalItemsForTask)
                }
            }
        }
        reportIfNecessary()
    }

    /**
     * Reports progress iff the two conditions are met:
     * 1. no less than [timeOut] nanoseconds have elapsed since the last report
     * OR there were no reports since the last reset
     * 2. the progress value (percentage rounded to the integer) differs from the last reported one
     * OR there were no reports since the last reset and the progress value is not zero
     *
     */
    fun reportIfNecessary() {
        val duration = getDuration()
        if (duration == -1L) {
            return
        }

        synchronized(this, {
            val processed = processedItems.toLong()

            val total = totalItems.get()
            val currentProgress = (processed * 100.0 / total).toLong()
            if (currentProgress != progressSoFar) {
                progressSoFar = currentProgress
                if (processed < total) {
                    val throughputPart: String
                    if (processed > 0) {
                        val ETA = asTime((duration.toDouble() * total / processed).toLong() - duration)
                        val avgThroughput = asThroughput(processed, duration)
                        throughputPart = ", Throughput: $avgThroughput, ETA: $ETA"
                    } else {
                        throughputPart = ""
                    }

                    tell(processed.asFractionOf(total) + ", Elapsed time: " + asTime(duration) + throughputPart)
                }
            }
        })
    }

    fun getDuration() : Long {
        val lastDurationValue = lastDuration.get()
        val duration = System.nanoTime() - startTime;
        if (duration < timeOut + lastDurationValue) {
            return -1;
        }
        if (!lastDuration.compareAndSet(lastDurationValue, duration)) {
            return -1;
        }
        // Not very precise, but not a big deal
        return duration
    }
}
