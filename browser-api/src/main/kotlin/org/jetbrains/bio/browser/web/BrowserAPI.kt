package org.jetbrains.bio.browser.web

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.gson.GsonBuilder
import org.apache.log4j.Logger
import org.jetbrains.bio.browser.AbstractGenomeBrowser
import org.jetbrains.bio.browser.desktop.Header
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.util.Logs
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object BrowserAPI {

    val LOG = Logger.getLogger(BrowserAPI::class.java)

    val GSON = GsonBuilder().setPrettyPrinting().create()

    private val lastRequest = AtomicInteger()

    fun process(request: HttpServletRequest, response: HttpServletResponse) {
        try {
            var name = request["name"].trimStart('/').trimEnd('/')
            val session = request.getSession(true).id
            // Force correct JSESSIONID to response, see for more details
            // http://stackoverflow.com/questions/595872/under-what-conditions-is-a-jsessionid-created
            response.addCookie(Cookie("JSESSIONID", session))

            val function = request["function"]
            if ("INITIALIZE" == function) {
                initialize(session, name, request["id"], response, request["query"])
                return
            }
            val browser: HeadlessGenomeBrowser
            try {
                browser = Browsers.getBrowser(session, name)
            } catch (e: Browsers.InvalidBrowserException) {
                initialize(session, name, request["id"], response, request["query"])
                return
            }
            when (function) {
                "REQUEST" -> {
                    request(session, name, browser, response, request.parameterMap, request["query"])
                }
                "CHECK" -> {
                    check(session, name, response, request["id"].toInt())
                }
                "SHOW" -> {
                    show(session, name, response, request["id"].toInt())
                }
                "STATE" -> getState(session, name, response)
                else -> {
                    LOG.error("Illegal function $function")
                    response.error("Illegal function: $function")
                }
            }
        } catch (t: Throwable) {
            LOG.error(t)
            response.error(Logs.getMessage(t))
        }

    }

    fun initialize(sessionId: String,
                   name: String,
                   id: String?,
                   response: HttpServletResponse,
                   r: String?) {
        LOG.debug("INIT $name@$sessionId")
        if (id == null) {
            LOG.info("Loading browser $name...")
        }
        val result = HashMap<String, Any>()
        val browserTask = Browsers.getBrowserInitTask(sessionId, name)
        if (id != null && browserTask.id == Integer.parseInt(id) && !browserTask.cancelled && browserTask.isDone) {
            val browser = browserTask.get()
            result["type"] = Response.Initialized.name
            result["completion"] = browser.locationCompletion
            result["location"] = if (r != null && r.isNotBlank()) r else browser.browserModel.presentableName()
            response.writer.write(GSON.toJson(result))
            return
        }
        result["type"] = Response.Init.name
        result["id"] = browserTask.id
        if (r != null && r.isNotBlank()) {
            result["location"] = r
        }
        response.writer.write(GSON.toJson(result))
    }

    fun request(sessionId: String,
                name: String,
                browser: HeadlessGenomeBrowser,
                response: HttpServletResponse,
                params: Map<String, Array<String>>,
                r: String) {
        LOG.debug("REQUEST $name@$sessionId $r")
        val renderTasks = Browsers.getRenderTasks(sessionId, name)
        val request = RequestParser.parse(Browsers.getBrowser(sessionId, name), r.toLowerCase())
        val result = Maps.newHashMap<String, Any>()
        result["type"] = request.response.name
        val callable = request.process(browser, params, result)
        if (callable != null) {
            val taskId = renderTasks.submit(callable)
            LOG.debug("New task $taskId for request $r")
            result["id"] = taskId
            lastRequest.set(taskId)
        }
        response.writer.write(GSON.toJson(result))
    }

    fun check(sessionId: String,
              name: String,
              response: HttpServletResponse,
              taskId: Int) {
        LOG.debug("CHECK $name@$sessionId $taskId")
        val renderTasks = Browsers.getRenderTasks(sessionId, name)
        val task = renderTasks[taskId]
        // Timeout, NO task for given request
        if (task == null) {
            LOG.debug("Timeout: no request found for $taskId")
            initialize(sessionId, name, null, response, null)
            return
        }
        if (lastRequest.get() > taskId) {
            LOG.error("REQUEST $taskId is already out-of-date, last request ${lastRequest.get()}")
            response.ignored()
            return
        }
        if (!task.isDone) {
            response.processing(taskId)
            return
        }
        try {
            task.get()
            LOG.debug("Ready $taskId")
            // Next request will draw the image
            response.show(taskId)
        } catch (e: CancellationException) {
            LOG.debug("Cancelled $taskId")
            renderTasks.clear();
            response.ignored();
        } catch (t: Throwable) {
            LOG.error(t)
            renderTasks.clear()
            response.error(Logs.getMessage(t))
        }
    }

    fun show(sessionId: String,
             name: String,
             response: HttpServletResponse,
             taskId: Int) {
        LOG.debug("SHOW $name@$sessionId $taskId")
        val renderTasks = Browsers.getRenderTasks(sessionId, name)
        val task = renderTasks[taskId] ?: return
        try {
            // Cancelled
            response.contentType = "image/png;base64"
            val encoded = ByteArrayOutputStream().use { bos ->
                ImageIO.write(task.get(), "png", bos)
                val imageBytes = bos.toByteArray()
                return@use Base64.getEncoder().encodeToString(imageBytes)
            }
            response.writer.write(encoded)
            LOG.debug("SENT result image for $taskId")
        } catch (e: CancellationException) {
            // Ignore
        } finally {
            renderTasks.clear()
        }
    }

    operator fun HttpServletRequest.get(param: String) = getParameter(param)

    fun getState(sessionId: String, name: String, response: HttpServletResponse) {
        LOG.debug("STATE $name@$sessionId")
        response.writer.write(GSON.toJson(State.of(Browsers.getBrowser(sessionId, name))))
    }

    fun HttpServletResponse.error(msg: String) {
        writer.write(GSON.toJson(ImmutableMap.of("error", true, "msg", msg)))
    }

    fun HttpServletResponse.ignored() {
        writer.write(GSON.toJson(ImmutableMap.of("type", Response.Ignored.name)))
    }

    fun HttpServletResponse.processing(taskId: Int) {
        writer.write(GSON.toJson(ImmutableMap.of("id", taskId, "type", Response.Processing.name)))
    }

    fun HttpServletResponse.show(taskId: Int) {
        writer.write(GSON.toJson(ImmutableMap.of("id", taskId, "type", Response.Show.name)))
    }
}

/**
 * Current state of API request processing
 */
enum class Response {
    Init,
    Initialized,
    ChangeLocation,
    Ignored,
    Processing,
    Show
}

@Suppress("unused")
class State(val start: Int,
            val end: Int,
            val length: Int,
            val positionHandlerY: Int,
            val pointerHeight: Int) {

    companion object {
        fun of(genomeBrowser: HeadlessGenomeBrowser): State {
            val browserModel = genomeBrowser.browserModel
            val range = browserModel.range
            val header = AbstractGenomeBrowser.createHeaderView(browserModel)

            return State(
                    range.startOffset,
                    range.endOffset,
                    browserModel.length,
                    header.pointerHandlerY,
                    Header.POINTER_HEIGHT)
        }
    }
}
