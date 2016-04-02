package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.tracks.TrackView.Scale
import org.junit.Test
import kotlin.test.assertEquals

class ScaleTest {
    @Test fun unionFinite() {
        val scale1 = Scale(80.0, 120.0)
        val scale2 = Scale(-100.0, 100.0)

        assertEquals(Scale(-100.0, 120.0), scale1 union scale2)
        assertEquals(Scale(-100.0, 120.0), scale2 union scale1)
    }

    @Test fun unionUndefined() {
        val naScale = Scale.undefined()
        assertEquals(naScale, naScale union naScale)
    }

    @Test fun unionWithUndefined() {
        val scale1 = Scale(-10.0, 10.0)
        val naScale = Scale.undefined()

        assertEquals(scale1, scale1 union naScale)
        assertEquals(scale1, naScale union scale1)
    }
}
