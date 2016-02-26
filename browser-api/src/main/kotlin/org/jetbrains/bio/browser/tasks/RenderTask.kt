package org.jetbrains.bio.browser.tasks

import com.google.common.base.Stopwatch
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Single task to render with cancellation logic, which waits [DELAY] before being executed.
 * @author Oleg Shpynov
 * @since 21/12/15
 */
class RenderTask {
    companion object {
        /**
         * Delay in milliseconds to wait until executing last callable task
         */
        val DELAY = 300
    }

    private val timer: Timer

    @Volatile private var task: CancellableTask<BufferedImage>? = null
    @Volatile private var stopWatch: Stopwatch? = null

    init {
        timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                synchronized (this@RenderTask) {
                    if (task != null && stopWatch != null) {
                        if (stopWatch!!.elapsed(TimeUnit.MILLISECONDS) > DELAY) {
                            task!!.execute()
                            stopWatch = null
                        }
                    }
                }
            }
        }, 0, DELAY.toLong())
    }

    @Synchronized fun dispose() {
        clear()
        timer.cancel()
    }

    @Synchronized fun submit(callable: Callable<BufferedImage>): Int {
        if (task != null && !task!!.cancelled) {
            task!!.cancel()
            task = null
        }
        task = CancellableTask(callable)
        stopWatch = Stopwatch.createStarted()
        return task!!.id
    }

    @Synchronized operator fun get(taskId: Int): CancellableTask<BufferedImage>? {
        return if (task != null && task!!.id == taskId) task else null
    }

    @Synchronized fun clear() {
        if (task != null && !task!!.cancelled) {
            task!!.cancel()
        }
        stopWatch = null
    }
}