package org.jetbrains.bio.util

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Throwables
import com.google.common.io.ByteStreams
import org.apache.log4j.*
import org.apache.log4j.spi.LoggingEvent
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException

object Logs {
    val CONSOLE_APPENDER = "CONSOLE_APPENDER"

    val DATE_FORMAT = SimpleDateFormat("Y-MM-dd HH:mm:ss")

    val LAYOUT: Layout = object : Layout() {
        override fun activateOptions() {
            // Ignore
        }

        override fun format(event: LoggingEvent): String {
            val throwableInformation = event.throwableInformation
            val sb = StringBuilder().append('[').append(DATE_FORMAT.format(Date())).append("] ")
            if (event.getLevel() !== Level.INFO) {
                sb.append(simpleName(event)).append(" [").append(event.getLevel()).append("] ")
            }

            val eventMessage = event.message
            if (eventMessage != null) {
                sb.append(eventMessage).append('\n')
            }

            if (throwableInformation != null) {
                sb.append("Caused by: ")
                sb.append(Throwables.getStackTraceAsString(throwableInformation.throwable))
            } else if (eventMessage is Throwable) {
                sb.append(Throwables.getStackTraceAsString(eventMessage as Throwable?))
            }

            return sb.toString()
        }

        override fun ignoresThrowable(): Boolean {
            return false
        }
    }

    val LAYOUT_SHORT: Layout = object : Layout() {
        override fun activateOptions() {
            // Ignore
        }

        override fun format(event: LoggingEvent): String {
            val message = event.message
            return if (message is Throwable) Logs.getMessage(message) else message.toString();
        }

        override fun ignoresThrowable(): Boolean {
            return false
        }
    }

    private fun simpleName(event: LoggingEvent): String {
        val fqnName = event.loggerName
        val classNameIdx = fqnName.lastIndexOf('.')
        return if ((classNameIdx == -1))
            fqnName
        else
            fqnName.substring(classNameIdx + 1)
    }

    fun addConsoleAppender(level: Level) {
        // We want only single console appender with given level
        if (Logger.getRootLogger().getAppender(CONSOLE_APPENDER) == null) {
            Logger.getRootLogger().level = level
            val consoleAppender = ConsoleAppender(Logs.LAYOUT)
            consoleAppender.name = CONSOLE_APPENDER
            Logger.getRootLogger().addAppender(consoleAppender)
        }
    }

    /**
     * Unwraps [RuntimeException], [ExecutionException], [InvocationTargetException]
     * to get original message
     */
    fun getMessage(throwable: Throwable): String {
        var t: Throwable = throwable
        while (true) {
            if (t is RuntimeException || t is ExecutionException || t is InvocationTargetException) {
                val cause = t.cause
                if (cause != null) {
                    t = cause
                    continue
                }
            }
            val message = t.message
            return "ERROR: ${t.javaClass.simpleName}${if (message != null) " $message" else ""}"
        }
    }

    @VisibleForTesting
    var EXIT_OR_ERROR = true

    fun checkOrFail(condition: Boolean, message: String) {
        if (!condition) {
            System.err.println("ERROR: $message")
            if (EXIT_OR_ERROR) {
                System.exit(1)
            }
        }
    }

    fun quiet() {
        val loggers = Collections.list(LogManager.getCurrentLoggers())
        loggers.add(LogManager.getRootLogger())
        loggers.forEach { (it as Logger).level = Level.OFF }

        val nullPrintStream = PrintStream(ByteStreams.nullOutputStream())
        System.setOut(nullPrintStream)
        System.setErr(nullPrintStream)
    }
}
