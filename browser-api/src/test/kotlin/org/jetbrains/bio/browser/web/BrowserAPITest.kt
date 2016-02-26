package org.jetbrains.bio.browser.web

import com.google.common.collect.ImmutableMap
import junit.framework.TestCase
import org.apache.log4j.Logger
import org.apache.log4j.WriterAppender
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tasks.CancellableTask
import org.jetbrains.bio.browser.tasks.waitAndGet
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.util.Logs
import java.awt.Graphics
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import javax.servlet.ServletOutputStream
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletResponse

/**
 * @author Oleg Shpynov
 * @since 6/8/15
 */
class BrowserAPITest : TestCase() {
    private val session = "session"
    private val completion = "completion888"
    private var writer: StringWriter = StringWriter()
    private var response: HttpServletResponse = createResponse()
    private var browser: HeadlessGenomeBrowser = createBrowser()
    private var browserName: String = "browser"

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        CancellableTask.resetCounter()
        Browsers.clear()

        browser = createBrowser()
        Browsers.registerBrowser(browserName, Callable { browser })
        writer = StringWriter()
        response = createResponse()
    }

    private fun createBrowser(): HeadlessGenomeBrowser {
        return HeadlessGenomeBrowser(
                SingleLocationBrowserModel(GenomeQuery("to1")),
                listOf(object : TrackView("test") {
                    @Throws(CancellationException::class)
                    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
                        try {
                            Thread.sleep(300)
                        } catch (e: InterruptedException) {
                            // Ignore
                        }

                    }
                }),
                ImmutableMap.of(completion,
                        { gq -> listOf(SimpleLocRef(Location(0, 888, Chromosome["to1", "chr1"]))) }))
    }

    private fun createResponse(): HttpServletResponse {
        return object : HttpServletResponse {
            override fun addCookie(cookie: Cookie) {
            }

            override fun containsHeader(name: String): Boolean {
                return false
            }

            override fun encodeURL(url: String): String? {
                return null
            }

            override fun encodeRedirectURL(url: String): String? {
                return null
            }

            override fun encodeUrl(url: String): String? {
                return null
            }

            override fun encodeRedirectUrl(url: String): String? {
                return null
            }

            @Throws(IOException::class)
            override fun sendError(sc: Int, msg: String) {
            }

            @Throws(IOException::class)
            override fun sendError(sc: Int) {
            }

            @Throws(IOException::class)
            override fun sendRedirect(location: String) {
            }

            override fun setDateHeader(name: String, date: Long) {
            }

            override fun addDateHeader(name: String, date: Long) {
            }

            override fun setHeader(name: String, value: String) {
            }

            override fun addHeader(name: String, value: String) {
            }

            override fun setIntHeader(name: String, value: Int) {
            }

            override fun addIntHeader(name: String, value: Int) {
            }

            override fun setStatus(sc: Int) {
            }

            override fun setStatus(sc: Int, sm: String) {
            }

            override fun getStatus(): Int {
                return 0
            }

            override fun getHeader(name: String): String? {
                return null
            }

            override fun getHeaders(name: String): Collection<String>? {
                return null
            }

            override fun getHeaderNames(): Collection<String>? {
                return null
            }

            override fun getCharacterEncoding(): String? {
                return null
            }

            override fun getContentType(): String? {
                return null
            }

            @Throws(IOException::class)
            override fun getOutputStream(): ServletOutputStream? {
                return null
            }

            @Throws(IOException::class)
            override fun getWriter(): PrintWriter {
                return PrintWriter(this@BrowserAPITest.writer)
            }

            override fun setCharacterEncoding(charset: String) {
            }

            override fun setContentLength(len: Int) {
            }

            override fun setContentLengthLong(len: Long) {
            }

            override fun setContentType(type: String) {
            }

            override fun setBufferSize(size: Int) {
            }

            override fun getBufferSize(): Int {
                return 0
            }

            @Throws(IOException::class)
            override fun flushBuffer() {
            }

            override fun resetBuffer() {
            }

            override fun isCommitted(): Boolean {
                return false
            }

            override fun reset() {
            }

            override fun setLocale(loc: Locale) {
            }

            override fun getLocale(): Locale? {
                return null
            }
        }
    }

    fun testInit() {
        writer = StringWriter()
        BrowserAPI.initialize(session, browserName, null, response, null)
        assertEquals("""{
  "id": 1,
  "type": "Init"
}""", writer.toString())
        writer = StringWriter()
        // Wait until browser init task is finished
        Browsers.getBrowserInitTask(session, browserName).waitAndGet()
        BrowserAPI.initialize(session, browserName, "1", response, null)
        assertTrue(writer.toString().startsWith("""{
  "completion": [
    "cds",
    "chr1",
    "chr2",
    "chr3","""))
    }

    fun testInitBlank() {
        writer = StringWriter()
        // Test blank init line
        BrowserAPI.initialize(session, browserName, null, response, "")
        assertEquals("""{
  "id": 1,
  "type": "Init"
}""", writer.toString())
        Browsers.getBrowserInitTask(session, browserName).waitAndGet()
    }

    fun testInitCompletionCompletion() {
        writer = StringWriter()
        BrowserAPI.initialize(session, browserName, null, response, completion)
        assertEquals("""{
  "location": "completion888",
  "id": 1,
  "type": "Init"
}""", writer.toString())
        // Wait until browser init task is finished
        Browsers.getBrowserInitTask(session, browserName).waitAndGet()
    }

    fun testGenomeBrowserState() {
        init()
        writer = StringWriter()
        BrowserAPI.getState(session, browserName, response)
        assertEquals(
                """{
  "start": 0,
  "end": 10000000,
  "length": 10000000,
  "positionHandlerY": 37,
  "pointerHeight": 20
}""", writer.toString())
    }

    private fun init() {
        BrowserAPI.initialize(session, browserName, null, response, null)
        // Wait until browser init task is finished
        Browsers.getBrowserInitTask(session, browserName).waitAndGet()
    }

    fun testIllegalLocation() {
        try {
            Browsers.registerBrowser(browserName, Callable { browser })
            init()
            val map = HashMap<String, Array<String>>()
            BrowserAPI.request(session, browserName, browser, response, map, "to be or not to be?")
        } catch(e: Exception) {
            assertEquals("ERROR: IllegalLocation to be or not to be?", Logs.getMessage(e))
            return
        }
        fail("No exception")
    }

    fun testRendering() {
        init()
        writer = StringWriter()
        val map = HashMap<String, Array<String>>()
        BrowserAPI.request(session, browserName, browser, response, map, "chr1:0-10000")
        assertEquals("""{
  "id": 2,
  "type": "Processing"
}""", writer.toString())

        writer = StringWriter()
        val task = Browsers.getRenderTasks(session, browserName)[2]
        // Wait until finished
        task!!.waitAndGet()
        BrowserAPI.check(session, browserName, response, 2)
        assertEquals("""{
  "id": 2,
  "type": "Show"
}""", writer.toString())
        writer = StringWriter()
        BrowserAPI.show(session, browserName, response, 2)
        assertFalse(writer.toString().startsWith("{"))
    }

    fun testNavigateCompletion() {
        init()
        val map = HashMap<String, Array<String>>()
        BrowserAPI.request(session, browserName, browser, response, map, completion)
        assertEquals("completion888:0-888", browser.browserModel.presentableName())
        BrowserAPI.request(session, browserName, browser, response, map, completion.toUpperCase())
        assertEquals("completion888:0-888", browser.browserModel.presentableName())
    }

    fun testMultipleRequest() {
        val log = StringWriter()
        val logger = WriterAppender(Logs.LAYOUT, log)
        Logger.getRootLogger().addAppender(logger)
        init()
        val map = HashMap<String, Array<String>>()
        for (i in 1..3) {
            val r = "chr1:0-${i * 1000}"
            BrowserAPI.request(session, browserName, browser, response, map, r)
            assertEquals(r, browser.browserModel.presentableName())
            writer.toString()
            writer.append("\n")
        }
        assertEquals("""{
  "id": 1,
  "type": "Init"
}{
  "id": 2,
  "type": "Processing"
}
{
  "id": 3,
  "type": "Processing"
}
{
  "id": 4,
  "type": "Processing"
}
""", writer.toString())
        writer = StringWriter()
        for (i in 1..3) {
            BrowserAPI.check(session, browserName, response, i + 1)
        }

        // Everything is cancelled before the last one
        assertEquals("""{
  "id": 1,
  "type": "Init"
}{
  "id": 1,
  "type": "Init"
}{
  "id": 4,
  "type": "Processing"
}""", writer.toString())
        Logger.getRootLogger().removeAppender(logger)
    }

    fun testScroll() {
        init()
        val map = HashMap<String, Array<String>>()
        // Edge cases
        BrowserAPI.request(session, browserName, browser, response, map, "scroll left")
        assertEquals("chr1:0-8000000", browser.browserModel.presentableName())
        BrowserAPI.request(session, browserName, browser, response, map, "scroll right")
        assertEquals("chr1:1600000-9600000", browser.browserModel.presentableName())

        BrowserAPI.request(session, browserName, browser, response, map, "chr1:20000-30000")
        BrowserAPI.request(session, browserName, browser, response, map, "scroll left")
        assertEquals("chr1:18000-28000", browser.browserModel.presentableName())
        BrowserAPI.request(session, browserName, browser, response, map, "scroll right")
        assertEquals("chr1:20000-30000", browser.browserModel.presentableName())
    }

    fun testZoom() {
        init()
        val map = HashMap<String, Array<String>>()
        // Edge cases
        BrowserAPI.request(session, browserName, browser, response, map, "zoom out")
        assertEquals("chr1:0-10000000", browser.browserModel.presentableName())

        BrowserAPI.request(session, browserName, browser, response, map, "zoom in")
        assertEquals("chr1:2500000-7500000", browser.browserModel.presentableName())
        BrowserAPI.request(session, browserName, browser, response, map, "zoom out")
        assertEquals("chr1:0-10000000", browser.browserModel.presentableName())

        BrowserAPI.request(session, browserName, browser, response, map, "zoom 10 90")
        assertEquals("chr1:1000000-9000000", browser.browserModel.presentableName())
    }

    fun testDragNDrop() {
        init()
        val map = HashMap<String, Array<String>>()
        // Edge cases
        BrowserAPI.request(session, browserName, browser, response, map, "chr1:20000-30000")
        BrowserAPI.request(session, browserName, browser, response, map, "dragndrop 25000 35000")
        assertEquals("chr1:25000-35000", browser.browserModel.presentableName())
    }


}
