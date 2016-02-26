package org.jetbrains.bio.browser.desktop;

import java.awt.*;

public class Border implements javax.swing.border.Border {
  private final boolean myIsSelected;

  public Border(final boolean isSelected) {
    myIsSelected = isSelected;
  }

  @Override
  public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
    g.setColor(myIsSelected ? Color.BLUE : Color.LIGHT_GRAY);

    g.drawRect(0, 0, width - 1, height - 1);
    if (myIsSelected) {
      // if selected - draw thicker rectangle
      g.drawRect(1, 1, width - 3, height - 3);
    }
  }

  @Override
  public Insets getBorderInsets(final Component c) {
    return new Insets(2, 2, 2, 2);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
