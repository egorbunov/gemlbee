package org.jetbrains.bio.browser.web

import com.google.gson.GsonBuilder
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.FileAppender
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.webapp.WebAppContext
import org.jetbrains.bio.ext.deleteIfExists
import org.jetbrains.bio.ext.name
import org.jetbrains.bio.util.Logs
import java.io.File
import java.io.IOException
import java.util.concurrent.LinkedBlockingDeque
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * @author Oleg Shpynov
 * @since  5/27/15
 */
object Handlers {
    private val LOG = Logger.getLogger(Handlers::class.java)
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    /**
     * Creates simple single file handler, which is supposed to serve static log file
     * Context: /log
     */
    fun createFullLogHandler(): WebAppContext {
        val tempLogFile = File.createTempFile("server", ".log").toPath()
        try {
            // Recreate log file on start
            tempLogFile.deleteIfExists()
            Logger.getRootLogger().addAppender(FileAppender(Logs.LAYOUT, tempLogFile.toString()))
        } catch (e: IOException) {
            LOG.error("Failed to create log file: " + tempLogFile.toString(), e)
        }
        val handler = WebAppContext()
        handler.contextPath = "/log"
        handler.resourceBase = tempLogFile.parent.toString()
        handler.welcomeFiles = arrayOf(tempLogFile.name)
        return handler
    }

    /**
     * Creates web log handler, which works in pair with reactapi.js and weblog.js
     * Context: /weblog
     */
    fun createWebLogHandler(): Handler = object : ContextHandler() {

        private val Appender = object : AppenderSkeleton() {

            val records = object : LinkedBlockingDeque<String>(10) {
                fun addMessage(e: String): Boolean {
                    e.split('\n').forEach { offer(it) }
                    return true;
                }

                override @Synchronized fun offer(e: String?): Boolean {
                    if (0 == remainingCapacity()) {
                        poll();
                    }
                    return super.offer(e)
                }
            }

            override @Synchronized fun append(event: LoggingEvent) {
                records.addMessage(Logs.LAYOUT_SHORT.format(event).trim())
            }

            override fun close() {
                Logger.getRootLogger().removeAppender(this)
            }

            override fun requiresLayout(): Boolean = false;
        }

        val CONTEXT = "/weblog"

        init {
            Logger.getRootLogger().addAppender(Appender)
            contextPath = CONTEXT
        }

        override fun doScope(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
            if (CONTEXT == target) {
                response?.contentType = "application/json";
                response?.status = HttpServletResponse.SC_OK;
                response?.writer?.write(GSON.toJson(Appender.records.joinToString("\n")));
                baseRequest?.isHandled = true
                return
            }
            super.doScope(target, baseRequest, request, response)
        }
    }


    /**
     * Creates handler to serve /browserName/ contexts.
     * See [BrowsersManager.getBrowsers()] for more details
     */
    fun createBrowsersHandler(): WebAppContext = object : WebAppContext() {
        val CONTEXT = "/"

        init {
            contextPath = CONTEXT
            val webAppResourceBase = ServerUtil.getWebAppResourceBase()
            LOG.debug("Starting browsers web app: $webAppResourceBase")
            resourceBase = webAppResourceBase
        }

        override fun doScope(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
            if (CONTEXT.equals(target)) {
                return
            }
            if (target == null) {
                super.doScope(target, baseRequest, request, response)
                return
            }
            val contextPath = contextPath
            for (b in Browsers.getBrowsers()) {
                val newCp = "/$b"
                if (newCp in target) {
                    if (newCp != contextPath) {
                        setContextPath(newCp)
                    }
                    super.doScope(target, baseRequest, request, response)
                    return
                }
            }
            if (CONTEXT != contextPath) {
                setContextPath(CONTEXT);
            }
            super.doScope(target, baseRequest, request, response)
        }
    }


    /**
     * Creates handler for /api
     * Used by web browser
     */
    fun createAPIHandler(): SessionHandler = object : SessionHandler() {
        val CONTEXT = "/api"
        override fun doHandle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            if (CONTEXT != target) {
                super.doHandle(target, baseRequest, request, response)
                return
            }
            try {
                BrowserAPI.process(baseRequest, response)
            } finally {
                baseRequest.isHandled = true
            }
        }
    }

}
