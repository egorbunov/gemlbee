package org.jetbrains.bio.util;

import gnu.trove.set.hash.TIntHashSet;
import junit.framework.TestCase;

import java.util.Arrays;

/**
 * @author Roman.Chernyatchik
 */
public class EHashSetTestCase extends TestCase {
  public void testRetainAllSideEffect() throws Exception {
    final int[] data = {5, 4, 3};

    final TIntHashSet seen = new TIntHashSet(new int[]{1, 2, 3});

    // Precondition
    assertEquals("[5, 4, 3]", Arrays.toString(data));

    seen.retainAll(data);

    // Test
    //assertEquals("[5, 4, 3]", Arrays.toString(data));
    if (Arrays.toString(data).equals("[5, 4, 3]")) {
      // https://bitbucket.org/robeden/trove/issue/53/_e_hashsettemplate-retainall-hidden-side
      fail("Issue #53 already fixed, time to remove explicit array.clone in DataFrame Column.intersect()");
    } else {
      // current side effect demo:
      assertEquals("[3, 4, 5]", Arrays.toString(data));
    }
  }
}
