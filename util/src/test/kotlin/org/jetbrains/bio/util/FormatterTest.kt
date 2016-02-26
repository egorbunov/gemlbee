package org.jetbrains.bio.util

import org.jetbrains.bio.ext.asFileSize
import org.jetbrains.bio.ext.asFractionOf
import org.jetbrains.bio.ext.asOffset
import org.jetbrains.bio.ext.asPercentOf
import org.junit.Test
import kotlin.test.assertEquals

/**
 * @author Roman.Chernyatchik
 */
class FormatterTest {
    @Test fun asFileSize() {
        assertEquals("0 b", 0L.asFileSize())
        assertEquals("10 b", 10L.asFileSize())
        assertEquals("100 b", 100L.asFileSize())
        assertEquals("1020 b", 1020L.asFileSize())
        assertEquals("1 kb", 1050L.asFileSize())
        assertEquals("10,3 kb", 10500L.asFileSize())
        assertEquals("102,5 kb", 105000L.asFileSize())
        assertEquals("1 mb", 1050000L.asFileSize())
        assertEquals("10 mb", 10500000L.asFileSize())
        assertEquals("100,1 mb", 105000000L.asFileSize())
        assertEquals("1001,4 mb", 1050000000L.asFileSize())
        assertEquals("1,9 gb", 2050000000L.asFileSize())
    }

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