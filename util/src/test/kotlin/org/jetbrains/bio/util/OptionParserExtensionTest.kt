package org.jetbrains.bio.util

import joptsimple.OptionParser
import org.apache.log4j.Level
import org.jetbrains.bio.ext.parse
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.fail

class OptionParserExtensionTest {
    val OUT = System.out
    val ERR = System.err

    @Before fun setUp() {
        System.setOut(OUT)
        System.setErr(ERR)
        Logs.EXIT_OR_ERROR = true
    }

    @Test fun testUnrecognized() {
        Logs.EXIT_OR_ERROR = false
        val stream = ByteArrayOutputStream()
        System.setErr(PrintStream(stream))
        with(OptionParser()) {
            parse(arrayOf("foo")) { options ->
                Logs.addConsoleAppender(Level.INFO)
            }
        }
        assert("Unrecognized options: [foo]" in stream.toString())
    }

    @Test fun testException() {
        val stream = ByteArrayOutputStream()
        System.setErr(PrintStream(stream))
        try {
            with(OptionParser()) {
                parse(arrayOf()) { options ->
                    throw RuntimeException("Bah!")
                }
            }
        } catch(e: Throwable) {
            assertEquals("Bah!", e.message)
            return
        }
        fail("No exception thrown")
    }
}