package org.jetbrains.bio.browser.web

import com.google.common.cache.CacheBuilder
import com.google.common.collect.Maps
import org.jetbrains.annotations.TestOnly
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.tasks.CancellableTask
import org.jetbrains.bio.browser.tasks.RenderTask
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

object Browsers {

    class InvalidBrowserException(msg: String) : RuntimeException(msg)

    private val browsersCache = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build<Pair<String, String>, CancellableTask<HeadlessGenomeBrowser>>()

    private val tasksCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build<Pair<String, String>, RenderTask>()

    /**
     * See [registerBrowser], [getBrowser] and [getBrowsers]
     */
    private val REGISTERED_BROWSERS: ConcurrentMap<String, Callable<HeadlessGenomeBrowser>> = Maps.newConcurrentMap()

    @JvmStatic fun registerBrowser(id: String, callable: Callable<HeadlessGenomeBrowser>) {
        REGISTERED_BROWSERS.put(id, callable)
        // Clear caches, since some browsers may be overridden
        clearBrowsesCache()
        tasksCache.invalidateAll()
    }

    @TestOnly
    @JvmStatic fun clear() {
        REGISTERED_BROWSERS.clear()
        tasksCache.invalidateAll()
        clearBrowsesCache()
    }

    fun clearBrowsesCache() {
        browsersCache.invalidateAll()
    }

    @JvmStatic fun getBrowsers(): Set<String> = REGISTERED_BROWSERS.keys

    @JvmStatic fun getRenderTasks(sessionId: String, name: String): RenderTask =
            tasksCache[sessionId to name, { RenderTask() }]

    @JvmStatic fun getBrowser(sessionId: String, name: String): HeadlessGenomeBrowser {
        val task = getBrowserInitTask(sessionId, name)
        if (task.cancelled || !task.isDone) {
            throw InvalidBrowserException("$name@$sessionId")
        }
        return task.get()
    }

    // Public for test only
    @JvmStatic fun getBrowserInitTask(sessionId: String, name: String): CancellableTask<HeadlessGenomeBrowser> {
        require(name in REGISTERED_BROWSERS) { "Unknown browser $name@$sessionId" }
        try {
            return browsersCache[sessionId to name, {
                CancellableTask.of(Callable {
                    val browser = REGISTERED_BROWSERS[name]!!.call();
                    val genomeQuery = browser.browserModel.genomeQuery
                    // Init before usage
                    browser.preprocessTracks(browser.trackViews, genomeQuery)
                    browser
                })
            }]
        } finally {
            // In generally it seems that there should only one genome browser per session,
            // so we can invalidate and unload genome browser for the same session to retain resources.
            val keys2Invalidate = browsersCache.asMap().keys.filter {
                it != sessionId to name
            }

            // Cancel all the browsers being processed
            browsersCache.getAllPresent(keys2Invalidate).values.forEach { task ->
                task.cancel()
            }
            browsersCache.invalidateAll(keys2Invalidate)

            // Cancel all the requests
            tasksCache.getAllPresent(keys2Invalidate).values.forEach { tasks ->
                tasks.dispose()
            }
            tasksCache.invalidateAll(keys2Invalidate)
        }
    }


}
