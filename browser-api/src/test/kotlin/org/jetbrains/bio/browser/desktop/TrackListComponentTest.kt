package org.jetbrains.bio.browser.desktop

import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.Range
import org.junit.Test
import kotlin.test.assertEquals

class TrackListComponentTest {
    @Test fun testGetStepSize() {
        assertEquals(100000, TrackUIUtil.stepSize(Range(1000000, 4000000).length()))
        assertEquals(50, TrackUIUtil.stepSize(Range(100, 1200).length()))
        assertEquals(10, TrackUIUtil.stepSize(Range(100, 400).length()))
        assertEquals(5, TrackUIUtil.stepSize(Range(100, 200).length()))
        assertEquals(5, TrackUIUtil.stepSize(Range(100, 150).length()))
        assertEquals(1, TrackUIUtil.stepSize(Range(100, 120).length()))
        assertEquals(1, TrackUIUtil.stepSize(Range(100, 105).length()))
    }
}