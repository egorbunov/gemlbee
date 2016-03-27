package org.jetbrains.bio.browser.desktop;

import net.miginfocom.swing.MigLayout;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.tracks.TrackViewWithControls;
import org.jetbrains.bio.browser.util.Storage;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Evgeny.Kurbatsky
 */
public class TrackViewComponent extends JComponent {
  private static final Border SELECTED_BORDER = new Border(true);
  private static final Border NOT_SELECTED_BORDER = new Border(false);

  private final RenderComponent myRenderComponent;
  private final List<Component> myMouseEventsTargets = new ArrayList<>();
  private boolean isSelected;

  public TrackViewComponent(final TrackView trackView,
                            final DesktopGenomeBrowser browser,
                            final Storage uiModel) {
    final JPanel trackControlsPanel = trackView instanceof TrackViewWithControls
                                      ? ((TrackViewWithControls) trackView).initTrackControlsPane() : null;
    final boolean debug = false;
    setLayout(new MigLayout(// layout const
                            (debug ? "debug, " : "") + "ins 0, hidemode 2, wrap 1, gapy 0",
                            // column const
                            "[grow, fill]",
                            // row const
                            (trackControlsPanel == null ? "" : "[]") + "[][][grow, fill]"));
    setSelected(false);

    // track title
    final JLabel titleLabel = new JLabel();
    titleLabel.setText(trackView.getTitle());
    add(titleLabel);
    myMouseEventsTargets.add(titleLabel);

    // Controls Pane
    if (trackControlsPanel != null) {
      add(trackControlsPanel);
    }

    // track drawing
    myRenderComponent = new RenderComponent(trackView, browser, uiModel);
    add(myRenderComponent);
    myMouseEventsTargets.add(myRenderComponent);
  }

  public List<Component> getMouseEventsTargets() {
    return myMouseEventsTargets;
  }

  public void resizeOnDividerMoved(final int newHeight) {
    final int preferredHeight = super.getPreferredSize().height;
    final int drawingAreaPreferredHeight = myRenderComponent.getPreferredSize().height;
    final int otherComponentsPreferredHeight = preferredHeight - drawingAreaPreferredHeight;
    final int newDrawingAreaPreferredHeight = newHeight - otherComponentsPreferredHeight;
    myRenderComponent.setPreferredHeight(newDrawingAreaPreferredHeight);
  }

  public boolean isSelected() {
    return isSelected;
  }

  public synchronized void setSelected(final boolean selected) {
    isSelected = selected;

    if (selected) {
      setBorder(SELECTED_BORDER);
    } else {
      setBorder(NOT_SELECTED_BORDER);
    }
  }

  public TrackView getTrackView() {
    return myRenderComponent.getTrackView();
  }

  public BufferedImage getImage() {
    final BufferedImage result = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    final Graphics graphics = result.getGraphics();
    paint(graphics);
    return result;
  }

  public void dispose() {
    myRenderComponent.dispose();
  }

}
