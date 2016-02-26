package org.jetbrains.bio.browser.web

import org.apache.log4j.Logger
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.util.Configuration
import org.jetbrains.bio.util.Logs
import java.io.IOException
import java.net.Socket

/**
 * @author Oleg Shpynov
 * @since  5/26/15
 */

object ServerUtil {
    private val LOG = Logger.getLogger(ServerUtil::class.java)

    @JvmStatic fun getWebAppResourceBase(): String {
        // We cannot use FileUtil.getProjectRoot(), because it omits "file:" protocol, which is required for jetty
        // Correct resource example for jar file:  jar:file:<path_to_jar>!/
        val classPath = ServerUtil::class.java.getResource("").toString().replaceFirst("!/.*".toRegex(), "!/")
        if (".jar" in classPath) {
            return classPath
        } else {
            val projectSourceRoot = checkNotNull(Configuration.findSourceRoot()) {
                "Failed to find project source root, probably jar file location under root is violated!"
            }
            return (projectSourceRoot / "browser-api/src/main/resources").toString()
        }
    }

    @JvmStatic fun isPortAvailable(port: Int): Boolean {
        try {
            Socket("localhost", port).use { ignored -> return false }
        } catch (ignored: IOException) {
            return true
        }
    }

    @JvmStatic fun startServer(port: Int, handlers: Handler) {
        val server = org.eclipse.jetty.server.Server()

        val connector = ServerConnector(server)
        Logs.checkOrFail(isPortAvailable(port), "Port $port is not available.")
        connector.port = port

        server.addConnector(connector)
        // Enable gzip compression
        val gzipHandler = GzipHandler()
        gzipHandler.handler = handlers
        server.handler = gzipHandler

        server.start()

        LOG.info("Server started on http://localhost:$port")
        // Cause server to keep running until it receives a Interrupt.
        // Interrupt Signal, or SIGINT (Unix Signal), is typically seen as a result of a kill -TERM {pid} or Ctrl+C
        server.join()

    }

}