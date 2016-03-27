package org.jetbrains.bio.browser.web

import org.jetbrains.bio.browser.*
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import java.awt.image.BufferedImage
import java.util.concurrent.Callable

interface Request {
    val response: Response

    fun process(browser: HeadlessGenomeBrowser,
                params: Map<String, Array<String>>,
                result: MutableMap<String, Any>): Callable<BufferedImage>?

    fun width(params: Map<String, Array<String>>): Int {
        return try {
            params["width"]!![0].toInt()
        } catch (e: Exception) {
            HeadlessGenomeBrowser.SCREENSHOT_WIDTH
        }
    }
}

class SingleLocationRequest(val locRef: LocationReference) : Request {
    override val response: Response get() = Response.Processing

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage> {
        // Navigate
        val model = browser.model as SingleLocationBrowserModel
        browser.execute(model.goTo(locRef))
        return Callable { browser.paint(width(params))!! }
    }
}

class MultipleLocationsRequest : Request {
    override val response: Response get() = Response.Processing

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Navigate
        val model = browser.model
        // Check that model is already configured
        check(model is MultipleLocationsBrowserModel)
        return Callable { browser.paint(width(params))!! }
    }
}

class DragNDropRequest(val start: Int, val end: Int) : Request {
    override val response: Response get() = Response.ChangeLocation

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        val model = browser.model
        browser.execute(model.dragNDrop(start, end))
        result.put("location", model.toString())
        return null
    }
}

class ScrollRequest(val shift: Boolean) : Request {
    override val response: Response get() = Response.ChangeLocation

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Scroll
        val model = browser.model
        browser.execute(model.scroll(shift, false))
        result.put("location", model.toString())
        return null
    }
}

class ZoomInOutRequest(val zoomIn: Boolean) : Request {
    override val response: Response get() = Response.ChangeLocation

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Zoom
        val model = browser.model
        browser.execute(model.zoom(if (zoomIn) 2.0 else 0.5))
        result.put("location", model.toString())
        return null
    }
}

class ZoomAtRequest(val start: Int, val end: Int) : Request {
    override val response: Response get() = Response.ChangeLocation

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Zoom
        val model = browser.model
        val range = model.range
        val l = range.length()
        val newStart = (range.startOffset + .01 * start.toDouble() * l.toDouble()).toInt()
        val newEnd = (range.startOffset + .01 * end.toDouble() * l.toDouble()).toInt()
        browser.execute(model.zoomAt(newStart, newEnd))
        result.put("location", browser.model.toString())
        return null
    }
}


object RequestParser {
    val ZOOM_PATTERN = "zoom (\\d+) (\\d+)".toPattern()
    val DRAG_N_DROP_PATTERN = "dragndrop (\\d+) (\\d+)".toPattern()

    fun parse(browser: HeadlessGenomeBrowser, query: String): Request {
        when (query) {
            "scroll left" -> return ScrollRequest(true)
            "scroll right" -> return ScrollRequest(false)
            "zoom in" -> return ZoomInOutRequest(true)
            "zoom out" -> return ZoomInOutRequest(false)
        }

        val zoomMatcher = ZOOM_PATTERN.matcher(query)
        if (zoomMatcher.matches()) {
            val start = zoomMatcher.group(1).toInt()
            val end = zoomMatcher.group(2).toInt()
            return ZoomAtRequest(start, end)
        }

        val dragNDropMatcher = DRAG_N_DROP_PATTERN.matcher(query)
        if (dragNDropMatcher.matches()) {
            val start = dragNDropMatcher.group(1).toInt()
            val end = dragNDropMatcher.group(2).toInt()
            return DragNDropRequest(start, end)
        }

        if (browser.handleMultipleLocationsModel(query)) {
            return MultipleLocationsRequest()
        }
        val locRef = LociCompletion.parse(query, browser.model.genomeQuery)
        if (locRef != null) {
            return SingleLocationRequest(locRef)
        }
        throw IllegalLocation(query)
    }
}

class IllegalLocation(msg: String): RuntimeException(msg)