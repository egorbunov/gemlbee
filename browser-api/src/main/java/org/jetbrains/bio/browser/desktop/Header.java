package org.jetbrains.bio.browser.desktop;

import org.jetbrains.bio.genome.Range;

import java.awt.*;

import static org.jetbrains.bio.browser.util.TrackUIUtil.genomeToScreen;

/**
 * @author Roman.Chernyatchik
 */
public abstract class Header extends Component {
  public static Color POINTER_FILL_COLOR = new Color(255, 0, 0, 64);
  public static Color POINTER_BORDER_COLOR = new Color(255, 0, 0);
  public static int POINTER_HEIGHT = 20;

  public abstract int getPointerHandlerY();

  public static void drawPointer(final Graphics g, final int width,
                                 final Range fullRange,
                                 final Range visibleRange,
                                 final int yOffset) {
    if (fullRange.equals(visibleRange)) {
      return;
    }
    final int startX = Math.max(0, genomeToScreen(visibleRange.getStartOffset(), width, fullRange));
    final int endX = Math.min(width, genomeToScreen(visibleRange.getEndOffset(), width, fullRange));
    final int pointerWidth = Math.max(2, endX - startX);

    // red + alpha
    g.setColor(POINTER_FILL_COLOR);
    g.fillRect(startX, yOffset, pointerWidth, POINTER_HEIGHT);

    // red
    g.setColor(POINTER_BORDER_COLOR);
    g.drawRect(startX, yOffset, pointerWidth, POINTER_HEIGHT);
  }
}
