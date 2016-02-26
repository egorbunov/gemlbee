package org.jetbrains.bio.browser.desktop;

import junit.framework.TestCase;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.genome.Range;

/**
 * @author Roman.Chernyatchik
 */
public class TrackListComponentTest extends TestCase {
  public void testGetStepSize() throws Exception {
    assertEquals(100000, TrackUIUtil.INSTANCE.stepSize(new Range(1000000, 4000000).length()));
    assertEquals(50, TrackUIUtil.INSTANCE.stepSize(new Range(100, 1200).length()));
    assertEquals(10, TrackUIUtil.INSTANCE.stepSize(new Range(100, 400).length()));
    assertEquals(5, TrackUIUtil.INSTANCE.stepSize(new Range(100, 200).length()));
    assertEquals(5, TrackUIUtil.INSTANCE.stepSize(new Range(100, 150).length()));
    assertEquals(1, TrackUIUtil.INSTANCE.stepSize(new Range(100, 120).length()));
    assertEquals(1, TrackUIUtil.INSTANCE.stepSize(new Range(100, 105).length()));
  }
}
