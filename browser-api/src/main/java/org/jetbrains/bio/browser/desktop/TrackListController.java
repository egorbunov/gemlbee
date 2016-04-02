package org.jetbrains.bio.browser.desktop;

import kotlin.Pair;
import org.jdesktop.swingx.util.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.browser.Command;
import org.jetbrains.bio.browser.CommandsKt;
import org.jetbrains.bio.browser.History;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.util.Storage;
import org.jetbrains.bio.genome.Range;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.jetbrains.bio.browser.SupportKt.screenToGenome;

/**
 * @author Roman.Chernyatchik
 */
public class TrackListController {
  private static final double ZOOM_MOUSE_PERCENT = 0.1; // 10% zoom

  private final TrackListComponent myTrackListComponent;
  private final DesktopGenomeBrowser myBrowser;

  @Nullable
  private Point myLatestMouseDraggingPoint; // In track list coordinates
  @Nullable
  private Point myLatestRegionSelectionPoint; // In track list coordinates
  @Nullable
  private Point myLatestAimSelectionPoint; // In track list coordinates
  @Nullable
  private Point myLatestDragAndDropPoint; // In track list coordinates
  @Nullable
  private Pair<Integer, Integer> mySelectionRange = null;

  private Point myCurrentTrackScreenshotOffset; // In track list coordinates
  @Nullable
  private BufferedImage myCurrentTrackScreenShot;


  private final History myHistory = new History();

  public TrackListController(final TrackListComponent trackListComponent,
                             final DesktopGenomeBrowser browser) {
    myTrackListComponent = trackListComponent;
    myBrowser = browser;

    // Subscribe on keyboard events. Force track list (this component) to be always focused
    // for correct events processing
    myTrackListComponent.addKeyListener(createKeyboardListener());
  }

  private KeyListener createKeyboardListener() {
    return new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull final KeyEvent e) {
        final int keyCode = e.getKeyCode();

        if (keyCode == KeyEvent.VK_ADD || keyCode == KeyEvent.VK_SUBTRACT) {
          // Numpad '+'/'-' :  zoom in/out
          doZoom2x(keyCode == KeyEvent.VK_ADD);
        } else if (keyCode == KeyEvent.VK_ESCAPE) {
          // ESC: clear selection

          // Clear range
          mySelectionRange = null;
          myLatestRegionSelectionPoint = null;
          myLatestAimSelectionPoint = null;

          // Clear selected tracks
          doClearTracksSelection();

          // Clear selected genes
          final BrowserModel model = myBrowser.getModel();
          if (model instanceof SingleLocationBrowserModel) {
            final SingleLocationBrowserModel sModel = (SingleLocationBrowserModel) model;
            sModel.setChromosomeRange(sModel.getChromosomeRange());
          }

          // repaint
          myTrackListComponent.repaint();
        } else if ((keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT)
                   && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown() && !e.isAltDown()) {
          // LEFT / RIGHT : scroll 20% left / right
          doScrollTrack(keyCode == KeyEvent.VK_LEFT, false);
        }
      }
    };
  }

  public MouseAdapter createMouseListener() {
    return new MouseAdapter() {

      // In track list coordinates
      @Nullable
      private Point myMousedPressedCursorPoint;

      @Nullable
      private TrackViewComponent myTracksSelectionStartTrack;
      @Nullable
      private TrackViewComponent myCurrentTrackComponent;

      @Override
      public void mousePressed(@NotNull final MouseEvent e) {
        final Point point = myTrackListComponent.translateToLocalCoordinates(e);

        // Keyboard events are handled by focused component, let's set focus
        // to track list and handle all events using its keyboard handler
        myTrackListComponent.requestFocus();

        // For track scrolling
        myMousedPressedCursorPoint = point;

        // Control/Meta + Left Button Pressed : For Drag & Drop animation
        // Shift + Left Button Pressed: continuous selection
        if ((isMetaOrControl(e)) && e.getButton() == MouseEvent.BUTTON1) {
          final TrackViewComponent component = myTrackListComponent.getTrackAt(point);
          if (component != null) {
            myCurrentTrackComponent = component;
            myCurrentTrackScreenShot = myCurrentTrackComponent.getImage();
            myCurrentTrackScreenshotOffset = SwingUtilities.convertPoint(myTrackListComponent, point, myCurrentTrackComponent);
            e.consume();
          }
        }

        // Control + Right Button Pressed : Hide track
        if ((e.isControlDown()) && e.getButton() == MouseEvent.BUTTON3) {
          doHideTrack(point);
          e.consume();
        }
      }

      @Override
      public void mouseClicked(@NotNull final MouseEvent e) {
        // Here process only Button1
        if (e.getButton() != MouseEvent.BUTTON1) {
          return;
        }

        final Point point = myTrackListComponent.translateToLocalCoordinates(e);

        if (mySelectionRange != null || myLatestRegionSelectionPoint != null || myLatestAimSelectionPoint != null) {
          mySelectionRange = null;
          myLatestRegionSelectionPoint = null;
          myLatestAimSelectionPoint = null;
          myTrackListComponent.repaint();
        }

        if (!e.isAltDown()) {
          // Use:
          // * click for select one track
          // * control/meta click - to add current or selected tracks to selection
          // * shift click - to select range

          final TrackViewComponent trackViewComp = myTrackListComponent.getTrackAt(point);
          if (trackViewComp != null) {
            final boolean preserveOtherSelection = isMetaOrControl(e);
            final boolean regionSelection = e.isShiftDown();

            if (regionSelection && preserveOtherSelection) {
              // both pressed - not supported
              return;
            }

            final List<TrackViewComponent> selectedTracks = myTrackListComponent.getSelectedTrackViewComponents();
            if (!preserveOtherSelection) {
              final boolean alreadySelected = !regionSelection && selectedTracks.size() == 1 && selectedTracks.get(0).equals(trackViewComp);
              if (!alreadySelected) {
                doClearTracksSelection();
                e.consume();
              }
            }

            if (!regionSelection) {
              // * Just click - select current track / or add to selection / deselect
              // * Ctrl + click - add/remove to selection
              trackViewComp.setSelected(!trackViewComp.isSelected());
              e.consume();
            } else if (myTracksSelectionStartTrack != null) {
              // Range selection
              final List<TrackViewComponent> trackViewComponents = myTrackListComponent.getTrackViewComponents();
              final int selectionStart = trackViewComponents.indexOf(myTracksSelectionStartTrack);
              final int selectionEnd = trackViewComponents.indexOf(trackViewComp);
              final List<TrackViewComponent> tracksInRange =
                  trackViewComponents.subList(Math.min(selectionStart, selectionEnd), Math.max(selectionStart, selectionEnd) + 1);
              for (final TrackViewComponent trackInRange : tracksInRange) {
                trackInRange.setSelected(true);
              }
              e.consume();
            }

            myTrackListComponent.repaint();

            myTracksSelectionStartTrack = trackViewComp;
          }
        }
      }

      @Override
      public void mouseReleased(@NotNull final MouseEvent e) {
        final Point point = myTrackListComponent.translateToLocalCoordinates(e);
        if (myCurrentTrackScreenShot != null) {
          myTrackListComponent.doMoveTrack(point, myCurrentTrackComponent);

          myCurrentTrackScreenShot = null;
          myCurrentTrackComponent = null;
          myTrackListComponent.repaint();
        }

        myMousedPressedCursorPoint = null;
        myLatestMouseDraggingPoint = null;
        myLatestDragAndDropPoint = null;

        if (mySelectionRange != null) {
          doZoomToSelection();
        }
        myLatestRegionSelectionPoint = null;
        mySelectionRange = null;
      }

      @Override
      public void mouseMoved(@NotNull final MouseEvent e) {
        if (e.isShiftDown()) {
          // show & update aim
          myLatestAimSelectionPoint = myTrackListComponent.translateToLocalCoordinates(e);
          myTrackListComponent.repaint();
        }
      }

      @Override
      public void mouseDragged(@NotNull final MouseEvent e) {
        final Point previousPoint = myLatestMouseDraggingPoint;
        myLatestMouseDraggingPoint = myTrackListComponent.translateToLocalCoordinates(e);

        // Shift + Drag : Region selection
        if (e.isShiftDown()) {
          myLatestRegionSelectionPoint = myLatestMouseDraggingPoint;

          //noinspection ConstantConditions
          final int start = Math.min(myMousedPressedCursorPoint.x, myLatestMouseDraggingPoint.x);
          final int end = Math.max(myMousedPressedCursorPoint.x, myLatestMouseDraggingPoint.x);
          mySelectionRange = new Pair<>(Math.max(0, start), Math.min(end, myTrackListComponent.getWidth()));
          myTrackListComponent.repaint();
          // Control/Meta + Drag: Track Move animation
          e.consume();
        } else if (isMetaOrControl(e)) {
          // Track drag
          myLatestDragAndDropPoint = myLatestMouseDraggingPoint;
          myTrackListComponent.repaint();
          e.consume();
        } else {
          // Drag: Scroll with fix start offset
          //noinspection ConstantConditions
          doScrollTrack((previousPoint == null ? myMousedPressedCursorPoint : previousPoint).x
                        - myLatestMouseDraggingPoint.x);
          e.consume();
        }
      }

      @Override
      public void mouseWheelMoved(final MouseWheelEvent e) {
        if (isMetaOrControl(e)) {
          // control + mouse wheel scroll: Scroll current genome region right/left
          doZoom(e.getWheelRotation());
          e.consume();
        } else {
          myTrackListComponent.dispatchEvent(e);
        }
      }
    };
  }

  public static boolean isMetaOrControl(final InputEvent e) {
    return OS.isMacOSX() ? e.isMetaDown() : e.isControlDown();
  }

  ////////////////////////////////////////////////////////

  @Nullable
  public Pair<Integer, Integer> getSelectionRange() {
    return mySelectionRange;
  }

  @Nullable
  public BufferedImage getCurrentTrackScreenShot() {
    return myCurrentTrackScreenShot;
  }

  @Nullable
  public Point getLatestDragAndDropPoint() {
    return myLatestDragAndDropPoint;
  }

  @Nullable
  public Point getLatestRegionSelectionPoint() {
    return myLatestRegionSelectionPoint;
  }

  public Point getCurrentTrackScreenshotOffset() {
    return myCurrentTrackScreenshotOffset;
  }

  @Nullable
  public Point getLatestAimSelectionPoint() {
    return myLatestAimSelectionPoint;
  }

  public TrackListComponent getTrackListComponent() {
    return myTrackListComponent;
  }

  public void doScrollTrack(final boolean scrollLeft, final boolean shiftWholeRegion) {
    execute(CommandsKt.scroll(myBrowser.getModel(), scrollLeft, shiftWholeRegion));
  }

  @SuppressWarnings("MethodOnlyUsedFromInnerClass")
  private void doScrollTrack(final int distancePx) {
    if (distancePx == 0) {
      return;
    }
    final Range region = myBrowser.getModel().getRange();
    final int regionLength = region.length();
    final long dragDistanceNucleotides = distancePx * regionLength / myTrackListComponent.getWidth();
    execute(CommandsKt.scrollBy(myBrowser.getModel(), (int) dragDistanceNucleotides));
  }


  @SuppressWarnings("MethodOnlyUsedFromInnerClass")
  private void doZoom(final int wheelRotation) {
    final boolean zoomIn = OS.isMacOSX() ? wheelRotation >= 0 : wheelRotation < 0;
    final double zoomScale = zoomIn ? 1 + ZOOM_MOUSE_PERCENT : 1 / (1 + ZOOM_MOUSE_PERCENT);
    execute(CommandsKt.zoom(myBrowser.getModel(), zoomScale));
  }


  public void doZoom2x(final boolean zoomIn) {
    if (zoomIn && mySelectionRange != null) {
      // Navigate & Zoom to selection
      doZoomToSelection();
    } else {
      // Just zoom
      final double scale = zoomIn ? 2.0 : 0.5;
      execute(CommandsKt.zoom(myBrowser.getModel(), scale));
    }
  }

  private void doZoomToSelection() {
    if (mySelectionRange != null) {
      final int width = myTrackListComponent.getWidth();

      assert mySelectionRange.getFirst() != null;
      assert mySelectionRange.getSecond() != null;

      final Range range = myBrowser.getModel().getRange();
      final int selectionStartOffset = screenToGenome(mySelectionRange.getFirst(), width, range);
      final int selectionEndOffset = screenToGenome(mySelectionRange.getSecond(), width, range);

      // Clear selection
      mySelectionRange = null;

      execute(CommandsKt.zoomAt(myBrowser.getModel(), selectionStartOffset, selectionEndOffset));
    }
  }

  /////////////////////////////////////////////////////////

  protected void doClearTracksSelection() {
    final List<TrackViewComponent> selectedTrackViewComponents = myTrackListComponent.getSelectedTrackViewComponents();
    for (final TrackViewComponent component : selectedTrackViewComponents) {
      component.setSelected(false);
    }
  }

  /////////////////////////////////////////////////////////

  @SuppressWarnings("MethodOnlyUsedFromInnerClass")
  private void doHideTrack(final Point point) {
    final TrackViewComponent component = myTrackListComponent.getTrackAt(point);
    if (component != null) {
      final TrackView trackView = component.getTrackView();
      myTrackListComponent.removeTrack(trackView, true);
    }
  }

  public void execute(final Command cmd) {
    myHistory.execute(cmd);
    myTrackListComponent.requestFocus();
  }

  public void undo() {
    if (!myHistory.undo()) {
      JOptionPane.showMessageDialog(myTrackListComponent,
                                    "Cannot go back, no more actions in history.",
                                    "Back",
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  public void redo() {
    if (!myHistory.redo()) {
      JOptionPane.showMessageDialog(myTrackListComponent,
                                    "Cannot go forward, no more actions in history.",
                                    "Forward",
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  public void doToggleLegendVisibility() {
    final Storage uiModel = myTrackListComponent.getUIModel();
    uiModel.set(TrackView.SHOW_LEGEND, !uiModel.get(TrackView.SHOW_LEGEND));
  }

  public void doToggleAxisVisibility() {
    final Storage uiModel = myTrackListComponent.getUIModel();
    uiModel.set(TrackView.SHOW_AXIS, !uiModel.get(TrackView.SHOW_AXIS));
  }
}
