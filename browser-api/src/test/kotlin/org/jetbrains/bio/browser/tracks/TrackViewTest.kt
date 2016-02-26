package org.jetbrains.bio.browser.tracks

import junit.framework.TestCase

/**
 * @author Oleg Shpynov
 */
class TrackViewTest : TestCase() {
    fun testScaleSumFinite() {
        val scale1 = TrackView.Scale(-10.0, 10.0)
        val scale2 = TrackView.Scale(-100.0, 100.0)

        TestCase.assertEquals(TrackView.Scale(-100.0, 100.0), scale1.union(scale2))
        TestCase.assertEquals(TrackView.Scale(-100.0, 100.0), scale2.union(scale1))
    }

    fun testScaleSumNans() {
        val naScale = TrackView.Scale.undefined()

        TestCase.assertEquals(naScale, naScale.union(naScale))
    }

    fun testScaleSumWithNan() {
        val scale1 = TrackView.Scale(-10.0, 10.0)
        val naScale = TrackView.Scale.undefined()

        TestCase.assertEquals(TrackView.Scale(-10.0, 10.0), scale1.union(naScale))
        TestCase.assertEquals(TrackView.Scale(-10.0, 10.0), naScale.union(scale1))
    }
}