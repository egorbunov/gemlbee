package org.jetbrains.bio.browser.desktop;

import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SplitterListener extends MouseAdapter {
  private final TrackListComponent myTrackListComponent;
  private final JXMultiSplitPane mySplitPane;
  private final JPanel mySpacer;

  @Nullable
  private MultiSplitLayout.Divider myDivider;
  @Nullable
  private Rectangle myDividerPrevBounds;

  private int mySpacerCompPrevHeight;

  public SplitterListener(final TrackListComponent trackListComponent,
                          final JXMultiSplitPane splitPane,
                          final JPanel spacer) {
    myTrackListComponent = trackListComponent;
    mySplitPane = splitPane;
    mySpacer = spacer;
  }

  @Override
  public void mouseDragged(final MouseEvent e) {
    if (mySplitPane.isContinuousLayout()) {
      processDividerMoved();
    }
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if (!mySplitPane.isContinuousLayout()) {
      processDividerMoved();
    }
    myDivider = null;
    myDividerPrevBounds = null;
  }

  @Override
  public void mousePressed(final MouseEvent e) {
    myDivider = mySplitPane.activeDivider();
    myDividerPrevBounds = myDivider == null ? null
                                            : myDivider.getBounds();
    mySpacerCompPrevHeight = mySpacer.getHeight();
  }

  protected void processDividerMoved() {
    if (myDividerPrevBounds != null) {
      @SuppressWarnings("ConstantConditions")
      final Rectangle newBounds = myDivider.getBounds();

      final int distance = newBounds.y - myDividerPrevBounds.y;
      final TrackViewComponent changedTrackView = (TrackViewComponent) mySplitPane.getComponents()[0];
      myTrackListComponent.resizeOnDividerMoved(distance, mySpacerCompPrevHeight, newBounds.y, changedTrackView);

      myDividerPrevBounds = newBounds;
      mySpacerCompPrevHeight = mySpacer.getHeight();
    }
  }

}
