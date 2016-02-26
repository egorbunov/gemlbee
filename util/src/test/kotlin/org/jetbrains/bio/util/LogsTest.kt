package org.jetbrains.bio.util

import junit.framework.TestCase
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

class LogsTest : TestCase() {

    fun testGetMessageRuntime() {
        val original = RuntimeException("ORIGINAL")
        assertEquals("ERROR: RuntimeException ORIGINAL", Logs.getMessage(RuntimeException(original)))
    }

    fun testGetMessageExecution() {
        val original = RuntimeException("ORIGINAL")
        assertEquals("ERROR: RuntimeException ORIGINAL", Logs.getMessage(ExecutionException(original)))
    }

    fun testGetMessageInvocationTarget() {
        val original = RuntimeException("ORIGINAL")
        assertEquals("ERROR: RuntimeException ORIGINAL", Logs.getMessage(InvocationTargetException(original)))
    }

    fun testNull() {
        assertEquals("ERROR: NullPointerException", Logs.getMessage(NullPointerException()))
    }

    fun testQuite() {
        Logs.addConsoleAppender(Level.ALL)
        val bytes = ByteArrayOutputStream()
        System.setOut(PrintStream(bytes))
        System.setErr(PrintStream(bytes))
        Logs.quiet()
        val log = Logger.getLogger(LogsTest::class.java)
        log.error("Foo")
        log.info("Foo")
        println("Bar")
        System.err?.println("Baz")
        assertTrue(String(bytes.toByteArray()).isEmpty())
    }
}