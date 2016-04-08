package org.jetbrains.bio.util

import org.jetbrains.bio.ext.asFractionOf
import org.jetbrains.bio.ext.asOffset
import org.jetbrains.bio.ext.asPercentOf
import org.junit.Test
import kotlin.test.assertEquals

/**
 * @author Roman.Chernyatchik
 */
class FormatterTest {
    @Test fun asOffset() {
        assertEquals("123", 123.asOffset());
        assertEquals("123_456", 123456.asOffset());
        assertEquals("1_234_567_890_123", 1234567890123L.asOffset());
    }

    @Test fun asFractionOf() {
        assertEquals("75.0% (3/4)", 3L.asFractionOf(4L))
        assertEquals("75.0% (30_000/40_000)", 30000L.asFractionOf(40000L))
    }

    @Test fun asPercentOf() {
        assertEquals("75.0%", 3L.asPercentOf(4L))
        assertEquals("75.0%", 30000L.asPercentOf(40000L))
    }
}
