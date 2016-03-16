package org.jetbrains.bio.browser.web

import org.jetbrains.bio.browser.command.Commands
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.LociCompletion
import java.awt.image.BufferedImage
import java.util.concurrent.Callable
import java.util.regex.Pattern

interface Request {
    fun process(browser: HeadlessGenomeBrowser,
                params: Map<String, Array<String>>,
                result: MutableMap<String, Any>): Callable<BufferedImage>?

    val response: Response
}

class DragNDropRequest(val start: Int, val end: Int) : Request {

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        val browserModel = browser.browserModel
        browser.execute(Commands.createDragNDropCommand(browserModel, start, end))
        result.put("location", browserModel.presentableName())
        return null
    }

    override val response: Response
        get() = Response.ChangeLocation
}

class MultipleLocationsRequest : Request {
    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Navigate
        val model = browser.browserModel
        // Check that model is already configured
        check(model is MultipleLocationsBrowserModel)
        return Callable { browser.paint(width(params))!! }
    }

    override val response: Response
        get() = Response.Processing
}

class ScrollRequest(val shift: Boolean) : Request {

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Scroll
        val browserModel = browser.browserModel
        browser.execute(Commands.createScrollCommand(browserModel, shift, false))
        result.put("location", browserModel.presentableName())
        return null
    }

    override val response: Response
        get() = Response.ChangeLocation

}

class SingleLocationRequest(val locRef: LocationReference) : Request {

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage> {
        // Navigate
        val browserModel = browser.browserModel as SingleLocationBrowserModel
        browser.execute(Commands.createGoToLocationCommand(browserModel, locRef))
        return Callable { browser.paint(width(params))!! }
    }

    override val response: Response
        get() = Response.Processing
}

class ZoomInOutRequest(val zoom: Boolean) : Request {

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Zoom
        val browserModel = browser.browserModel
        browser.execute(Commands.createZoomGenomeRegionCommand(browserModel, if (zoom) 2.0 else 0.5))
        result.put("location", browserModel.presentableName())
        return null
    }

    override val response: Response
        get() = Response.ChangeLocation
}

class ZoomRequest(val start: Int, val end: Int) : Request {

    override fun process(browser: HeadlessGenomeBrowser,
                         params: Map<String, Array<String>>,
                         result: MutableMap<String, Any>): Callable<BufferedImage>? {
        // Zoom
        val browserModel = browser.browserModel
        val range = browserModel.range
        val l = range.length()
        val newStart = (range.startOffset + .01 * start.toDouble() * l.toDouble()).toInt()
        val newEnd = (range.startOffset + .01 * end.toDouble() * l.toDouble()).toInt()
        browser.execute(Commands.createZoomToRegionCommand(browserModel, newStart, newEnd - newStart))
        result.put("location", browser.browserModel.presentableName())
        return null
    }

    override val response: Response
        get() = Response.ChangeLocation
}

fun width(params: Map<String, Array<String>>): Int {
    try {
        return params["width"]!![0].toInt()
    } catch (e: Exception) {
        return HeadlessGenomeBrowser.SCREENSHOT_WIDTH
    }
}


class IllegalLocation(msg: String): RuntimeException(msg)

object RequestParser {
    val ZOOM_PATTERN = Pattern.compile("zoom (\\d+) (\\d+)")
    val DRAG_N_DROP_PATTERN = Pattern.compile("dragndrop (\\d+) (\\d+)")


    fun parse(browser: HeadlessGenomeBrowser, query: String): Request {
        when (query) {
            "scroll left" -> return ScrollRequest(true)
            "scroll right" -> return ScrollRequest(false)
            "zoom in" -> return ZoomInOutRequest(true)
            "zoom out" -> return ZoomInOutRequest(false)
        }

        // Try to zoom percentage 1, percentage 2.
        val zoomMatcher = ZOOM_PATTERN.matcher(query)
        if (zoomMatcher.matches()) {
            val start = Integer.valueOf(zoomMatcher.group(1))!!
            val end = Integer.valueOf(zoomMatcher.group(2))!!
            return ZoomRequest(start, end)
        }

        // Drag'n'drop
        val dragNDropMatcher = DRAG_N_DROP_PATTERN.matcher(query)
        if (dragNDropMatcher.matches()) {
            val start = Integer.valueOf(dragNDropMatcher.group(1))!!
            val end = Integer.valueOf(dragNDropMatcher.group(2))!!
            return DragNDropRequest(start, end)
        }

        if (browser.handleMultipleLocationsModel(query)) {
            return MultipleLocationsRequest()
        }
        val locRef = LociCompletion.parse(query, browser.browserModel.genomeQuery)
        if (locRef != null) {
            return SingleLocationRequest(locRef)
        }
        throw IllegalLocation(query)
    }
}