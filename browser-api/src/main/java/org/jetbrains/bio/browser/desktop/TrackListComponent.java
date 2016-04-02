package org.jetbrains.bio.browser.desktop;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import kotlin.Pair;
import org.apache.log4j.Logger;
import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.painter.AlphaPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.browser.GenomeBrowser;
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.util.Storage;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.genome.Range;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;
import static org.jetbrains.bio.browser.SupportKt.screenToGenome;
import static org.jetbrains.bio.ext.FormatterKt.asOffset;

/**
 * @author Sergey Dmitriev
 * @author Roman.Chernyatchik
 */
public class TrackListComponent extends JPanel {
  private static final Logger LOG = Logger.getLogger(TrackListComponent.class);

  private static final String DEBUG_FOCUS = "genome.browser.debug.focus";

  private static final int MIN_WIDTH = 15; // track list component min width - just to fix layout behaviour on resize
  private static final int SPACER_MIN_HEIGHT = 20; // min height for spacer component - panel after track list
  private static final String TOP_NODE = "top";

  private static final String BOTTOM_NODE = "bottom";
  public static final int SPLIT_PANE_DIVIDER_SIZE = 5;

  private final Container myTracksViewsContainer;
  private final TrackListController myTrackListController;

  private JScrollPane myContentPane;

  protected final MainPanel myPanel;
  private final DesktopGenomeBrowser myBrowser;

  private final List<TrackView> myTrackViews;
  private final Map<TrackView, TrackViewComponent> myComponentsMap = Maps.newLinkedHashMap();

  private MouseAdapter myMouseListener;
  private final JPanel mySpacer;

  private final Storage myUIModel = new Storage();

  public TrackListComponent(final MainPanel panel,
                            final DesktopGenomeBrowser browser,
                            final List<TrackView> trackViews) {
    myPanel = panel;
    myBrowser = browser;
    myTrackViews = new LinkedList<>(trackViews);
    myTrackListController = new TrackListController(this, myBrowser);

    setFocusable(true);

    // Add children:
    setLayout(new GridLayout(1, 1));
    subscribeFocusDebuggingListener(this);

    // - fake spacer panel in the end of tracks list
    mySpacer = new JPanel();
    subscribeFocusDebuggingListener(mySpacer);
    mySpacer.setPreferredSize(new Dimension(MIN_WIDTH, SPACER_MIN_HEIGHT));
    mySpacer.setBorder(new EmptyBorder(0, 0, 0, 0));
    subscribeOnMouseActions(mySpacer);

    // - add track view container
    myTracksViewsContainer = createVerticalSplitPane();
    add(myTracksViewsContainer);

    // - add tracks
    initComponents();

    // subscribe on genome changed event: it may change visible tracks
    browser.getModel().addListener(this::repaint);

    myUIModel.init(TrackView.SHOW_LEGEND, true);
    myUIModel.init(TrackView.SHOW_AXIS, true);
    myUIModel.addListener((key, value) -> {
      if (key == TrackView.SHOW_LEGEND || key == TrackView.SHOW_AXIS) {
        getTrackViewComponents().stream().forEach(c -> c.getTrackView().fireRepaintRequired());
      }
    });
  }

  public JScrollPane getContentPane() {
    if (myContentPane == null) {
      myContentPane = createContentPane();
    }
    return myContentPane;
  }

  protected JScrollPane createContentPane() {
    final JScrollPane scrollPane = new JScrollPane(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    // header
    scrollPane.setColumnHeaderView(GenomeBrowser.Companion.createHeaderView(myBrowser.getModel()));
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    scrollPane.getVerticalScrollBar().setBlockIncrement(20);

    // tracks
    scrollPane.setViewportView(this);
    return scrollPane;
  }

  /**
   * Subscribes given target to browser general mouse actions (zoom, scroll,..). Mouse events
   * are handled by deepest possible component. All JXSplitPanes register
   * own listener, which by default stop mouse events spreading in split panes child components
   *
   * @param target Component which receives mouse events
   */
  private void subscribeOnMouseActions(final Component target) {
    if (myMouseListener == null) {
      myMouseListener = myTrackListController.createMouseListener();
    }
    target.addMouseListener(myMouseListener);
    target.addMouseMotionListener(myMouseListener);
    target.addMouseWheelListener(myMouseListener);
  }

  public Point translateToLocalCoordinates(@NotNull final MouseEvent e) {
    return SwingUtilities.convertPoint((Component) e.getSource(),
                                       e.getPoint(),
                                       TrackListComponent.this);
  }

  @Nullable
  public TrackViewComponent getTrackAt(final Point point) {
    final Point pointInTrackTreeRoot = SwingUtilities.convertPoint(this, point, myTracksViewsContainer);

    // Due to tree structure of track views list - take deepest component and
    // traverse up to getFirst() track view parent
    Component comp = myTracksViewsContainer.findComponentAt(pointInTrackTreeRoot);
    while (comp != null) {
      if (comp instanceof TrackViewComponent) {
        return (TrackViewComponent) comp;
      }
      comp = comp.getParent();
    }
    return null;
    // It's possible to do this easier - just for each tree node (JXSplitPane) look for children at point
    // but some times we need to cache point and convert it to track list coordinates system
    // thus let's do everything using one way, performance isn't critical here
  }

  private void initComponents() {
    JXMultiSplitPane curSplitPane = (JXMultiSplitPane) myTracksViewsContainer;
    for (int i = 0; i < myTrackViews.size(); i++) {
      final TrackView trackView = myTrackViews.get(i);
      final TrackViewComponent component = new TrackViewComponent(trackView, myBrowser, myUIModel);
      subscribeFocusDebuggingListener(component);
      subscribeOnMouseActions(component);
      myComponentsMap.put(trackView, component);

      // Add split listener
      final MouseAdapter splitterChangedListener = new SplitterListener(this, curSplitPane, mySpacer);
      curSplitPane.addMouseListener(splitterChangedListener);
      curSplitPane.addMouseMotionListener(splitterChangedListener);

      // Add tracks
      if (i != myTrackViews.size() - 1) {
        // new split pane model for next row
        final JXMultiSplitPane newSplitPane = createVerticalSplitPane();

        // add components to current split pane
        curSplitPane.add(component, TOP_NODE);
        curSplitPane.add(newSplitPane, BOTTOM_NODE);

        curSplitPane = newSplitPane;
      } else {
        curSplitPane.add(component, TOP_NODE);
        curSplitPane.add(mySpacer, BOTTOM_NODE);
      }
    }
  }

  public TrackListController getTrackListController() {
    return myTrackListController;
  }

  private JXMultiSplitPane createVerticalSplitPane() {
    final JXMultiSplitPane splitPane = new JXMultiSplitPane();

    subscribeFocusDebuggingListener(splitPane);

    splitPane.setContinuousLayout(true);
    splitPane.setBackgroundPainter(new AlphaPainter());
    splitPane.setLayout(new MultiSplitLayout(createVerticalSplitModel()));
    splitPane.setPaintBorderInsets(false);
    splitPane.setBorder(null);
    splitPane.setBorder(null);
    splitPane.setDividerSize(SPLIT_PANE_DIVIDER_SIZE);
    splitPane.setDividerPainter(new JXMultiSplitPane.DividerPainter() {
      @Override
      protected void doPaint(final Graphics2D g, final MultiSplitLayout.Divider divider, final int width, final int height) {
        final boolean continuousLayout = splitPane.isContinuousLayout();
        final MultiSplitLayout.Divider activeDivider = splitPane.activeDivider();

        if ((divider == activeDivider && !continuousLayout)) {
          // active divider
          g.setColor(Color.black);
          g.fillRect(0, 0, width, height);
        } else {
          g.setColor(mySpacer.getBackground());
          g.fillRect(0, 0, width, height);

          final int screenCenterX = width / 2;
          final int screenCenterY = height / 2;

          g.setColor(Color.DARK_GRAY);
          g.drawRect(screenCenterX - 1, screenCenterY - 1, 2, 1);
          g.drawRect(screenCenterX - 6, screenCenterY - 1, 2, 1);
          g.drawRect(screenCenterX + 4, screenCenterY - 1, 2, 1);
        }
      }
    });

    // Split pane defines it's own mouse handler, so we had to
    // subscribe using our own handler or it will be ignored, because
    // events propagates to deepest component listening events.

    // subscribe on user interaction events
    subscribeOnMouseActions(splitPane);

    return splitPane;
  }

  private void subscribeFocusDebuggingListener(final Component c) {
    if (System.getProperties().getProperty(DEBUG_FOCUS) != null) {
      final FocusListener focusListener = new FocusListener() {

        @Override
        public void focusGained(@NotNull final FocusEvent e) {
          if (e.isTemporary()) return;
          ((JComponent) e.getComponent()).setBorder(new LineBorder(Color.red, 10));
        }

        @Override
        public void focusLost(@NotNull final FocusEvent e) {
          if (e.isTemporary()) return;
          ((JComponent) e.getComponent()).setBorder(new LineBorder(Color.black, 10));

        }
      };
      c.addFocusListener(focusListener);
    }
  }

  @NotNull
  private MultiSplitLayout.Split createVerticalSplitModel() {
    final MultiSplitLayout.ColSplit colSplit = new MultiSplitLayout.ColSplit();
    colSplit.setRowLayout(false);

    final MultiSplitLayout.Leaf rootTop = new MultiSplitLayout.Leaf(TOP_NODE);
    rootTop.setWeight(0);

    final MultiSplitLayout.Leaf rootBottom = new MultiSplitLayout.Leaf(BOTTOM_NODE);
    rootBottom.setWeight(1);

    colSplit.setChildren(rootTop,
                         new MultiSplitLayout.Divider(),
                         rootBottom);
    return colSplit;
  }

  protected boolean resizeOnDividerMoved(final int movingDist, final int spacerHeight,
                                         final int newCompHeight,
                                         final TrackViewComponent changedTrackViewComp) {
    if (movingDist == 0) {
      return false;
    }

    // Save new desired height to track view
    changedTrackViewComp.resizeOnDividerMoved(newCompHeight);

    // If we resize one component other splitters in hierarchy will be affected,
    // thus we had to enlarge scroll pane's view port component to
    // prevent other splitters resizing
    final JViewport viewport = myContentPane.getViewport();

    // virtual height - view port target height inside scroll pane
    final int virtualHeight = viewport.getView().getHeight();

    // real height of tracks list scroll pane
    final int height = viewport.getHeight();

    final int trackListVirtualHeight = virtualHeight - spacerHeight;
    final int desiredTLH = trackListVirtualHeight + movingDist + 3 * SPACER_MIN_HEIGHT;
    if (desiredTLH <= height) {
      // desired track list new height is less than browser main pane real height
      // no scrolling required! => reduce virtual scroll pane to hide scroll bars
      if (virtualHeight > height) {
        setPreferredSize(new Dimension(MIN_WIDTH, height));
        return true;
      }
    } else {
      // let's enlarge to desired size
      setPreferredSize(new Dimension(MIN_WIDTH, desiredTLH));
      return true;
    }
    return false;
  }

  @Override
  public void paint(@NotNull final Graphics g) {

    paintGrid(myBrowser.getModel(), g, getVisibleWidth(), getHeight());

    paintChildren(g);

    if (myTrackListController.getSelectionRange() != null) {
      paintRegionSelection(g);
    }

    paintAimSelection(g);

    if (myTrackListController.getCurrentTrackScreenShot() != null
        && myTrackListController.getLatestDragAndDropPoint() != null) {

      paintDragAndDropAnimation(g, getVisibleWidth(),
                                myTrackListController.getCurrentTrackScreenShot(),
                                myTrackListController.getLatestDragAndDropPoint(),
                                myTrackListController.getCurrentTrackScreenshotOffset());
    }
  }

  private void paintDragAndDropAnimation(final Graphics g, final int width,
                                         final BufferedImage currentTrackScreenShot,
                                         final Point latestDragAndDropPoint,
                                         final Point currTrackScreenshotOffset) {
    // Draw track screenshot over others
    g.drawImage(currentTrackScreenShot,
                latestDragAndDropPoint.x - currTrackScreenshotOffset.x,
                latestDragAndDropPoint.y - currTrackScreenshotOffset.y,
                this);

    // Actually user can notice 2 highlighting lines between components, because splitter
    // has some small heights and this code paints insertion line near current track bounds
    // So this method can take in consideration separator width, but seems it's not critical

    // highlight insertion place
    final TrackViewComponent currentTrackComponent = getTrackAt(latestDragAndDropPoint);
    if (currentTrackComponent != null) {
      final int trackY;
      if (pointIsInUpperPartOf(currentTrackComponent, latestDragAndDropPoint)) {
        trackY = currentTrackComponent.getY();
      } else {
        trackY = currentTrackComponent.getY() + currentTrackComponent.getHeight();
      }
      final int y = (int) SwingUtilities.convertPoint(currentTrackComponent.getParent(), 0, trackY, this).getY();
      g.setColor(Color.RED);
      g.fillRect(0, y - 3, width, 6);
    }

  }

  private void paintRegionSelection(final Graphics g) {
    g.setColor(new Color(0, 0, 250, 70));
    final Pair<Integer, Integer> selectionRange = myTrackListController.getSelectionRange();
    final Integer screenSelectionStartX = selectionRange.getFirst();
    final Integer screenSelectionEndX = selectionRange.getSecond();

    g.fillRect(screenSelectionStartX, 0, screenSelectionEndX - screenSelectionStartX, getHeight());

    final Range range = myBrowser.getModel().getRange();
    final int selectionStartOffset = screenToGenome(screenSelectionStartX, getVisibleWidth(), range);
    final int selectionEndOffset = screenToGenome(screenSelectionEndX, getVisibleWidth(), range);
    final int selectionLength = selectionEndOffset - selectionStartOffset;

    g.setColor(Color.BLACK);
    final Point latestRegionSelectionPoint = myTrackListController.getLatestRegionSelectionPoint();
    final String text = "Region " + asOffset(selectionStartOffset) + '-' + asOffset(selectionEndOffset) + " (" + asOffset(selectionLength) + " bp)";
    TrackUIUtil.drawString(g, text, latestRegionSelectionPoint.x + 1, latestRegionSelectionPoint.y - 2, Color.BLACK);
  }

  private void paintAimSelection(final Graphics g) {
    final Point aimRegionSelectionPoint = myTrackListController.getLatestAimSelectionPoint();

    if (aimRegionSelectionPoint == null) {
      return;
    }
    g.setColor(Color.BLACK);
    final int height = getHeight();
    for (int screenY = 0; screenY < height; screenY += 5) {
      g.drawLine(aimRegionSelectionPoint.x, screenY, aimRegionSelectionPoint.x, screenY + 2);
    }

    final int width = getVisibleWidth();
    for (int screenX = 0; screenX < width; screenX += 5) {
      g.drawLine(screenX, aimRegionSelectionPoint.y, screenX + 2, aimRegionSelectionPoint.y);
    }
    final int currOffset = screenToGenome(aimRegionSelectionPoint.x,
                                                      width,
                                                      myBrowser.getModel().getRange());
    final String text = asOffset(currOffset) + " bp";
    TrackUIUtil.drawString(g, text, 5 + aimRegionSelectionPoint.x, aimRegionSelectionPoint.y - 5, Color.BLACK);
  }

  @Override
  public int getWidth() {
    return isShowing() ? super.getWidth() : HeadlessGenomeBrowser.SCREENSHOT_WIDTH;
  }

  public int getVisibleWidth() {
    return isShowing() ? myContentPane.getViewport().getWidth() : getWidth();
  }

  @Override
  public int getHeight() {
    return isShowing() ? super.getHeight() : HeadlessGenomeBrowser.SCREENSHOT_HEIGHT;
  }

  public static void paintGrid(final BrowserModel browserModel, final Graphics g, final int width, final int height) {
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    TrackUIUtil.drawGrid(g, browserModel.getRange(), width, height);
  }

  private boolean pointIsInUpperPartOf(final Component component, final Point point) {
    final Point pointInComponentsOriginCoordinates = SwingUtilities.convertPoint(this, point, component.getParent());
    return pointInComponentsOriginCoordinates.y - component.getY() < component.getHeight() / 2;
  }

  //////////  Actions ///////////////////////////////////////////////////
  public void doTakeScreenshot(final boolean selectedTracksOnly) {
    Preconditions.checkState(isShowing());
    final BufferedImage image = paintToImage(selectedTracksOnly);
    // save screenshot
    try {
      final File screenShotFile = createScreenShotFile();
      ImageIO.write(image, "png", screenShotFile);
      JOptionPane.showMessageDialog(this,
                                    "Screenshot written to file: " + screenShotFile.getAbsolutePath(),
                                    "Take Screenshot",
                                    JOptionPane.INFORMATION_MESSAGE);
    } catch (final IOException ex) {
      LOG.error(ex);
    }
  }

  public BufferedImage paintToImage(final boolean selectedTracksOnly) {
    Preconditions.checkState(isShowing());

    // Select tracks
    final List<TrackViewComponent> trackViewComponents;
    if (selectedTracksOnly) {
      // get selected tracks
      trackViewComponents = getSelectedTrackViewComponents();
      if (trackViewComponents.isEmpty()) {
        JOptionPane.showMessageDialog(this,
                                      "Cannot capture a track screenshot. Select track at getFirst()",
                                      "Take Screenshot",
                                      JOptionPane.ERROR_MESSAGE);
      }
    } else {
      trackViewComponents = getTrackViewComponents();
    }
    final List<TrackView> trackViews = trackViewComponents.stream()
        .map(TrackViewComponent::getTrackView).collect(Collectors.toList());
    return HeadlessGenomeBrowser.paint(myBrowser.getModel(), trackViews, getWidth());
  }

  private File createScreenShotFile() {
    final String prefix = "screenshot_" + myBrowser.getModel().toString().replace(":", "_");
    int i = 0;
    while (true) {
      final String name = prefix + (i == 0 ? "" : "_" + i) + ".png";
      final File file = new File(name);
      if (!file.exists()) {
        return file;
      }
      i++;
    }
  }

  protected void doMoveTrack(final Point destinationPoint, final TrackViewComponent trackComponentToMove) {
    // target component where current track is needed to be placed
    final TrackViewComponent finalPlaceComponent = getTrackAt(destinationPoint); //e.getSource());

    if (finalPlaceComponent != null) {
      // target is some track
      final TrackView trackView = finalPlaceComponent.getTrackView();

      int newIndex = myTrackViews.indexOf(trackView);
      if (!pointIsInUpperPartOf(finalPlaceComponent, destinationPoint)) {
        newIndex++;
      }
      final int oldIndex = getTrackViewComponents().indexOf(trackComponentToMove);

      final TrackView trackViewToMove = trackComponentToMove.getTrackView();
      if (newIndex < oldIndex) {
        removeTrack(trackViewToMove, false);
        addTrack(trackViewToMove, newIndex, false);
        fireTrackViewsListChanged();
      }
      if (newIndex > oldIndex) {
        removeTrack(trackViewToMove, false);
        addTrack(trackViewToMove, newIndex - 1, false);
        fireTrackViewsListChanged();
      }
    }
  }

  protected void removeTrack(final TrackView trackView, final boolean fireEvent) {
    myTrackViews.remove(trackView);
    myComponentsMap.get(trackView).dispose();
    if (fireEvent) {
      fireTrackViewsListChanged();
    }
  }

  private void addTrack(final TrackView trackView, final int index, final boolean fireEvent) {
    myTrackViews.add(index, trackView);
    if (fireEvent) {
      fireTrackViewsListChanged();
    }
  }


  private void fireTrackViewsListChanged() {
    // 0. filter track view components
    final Map<TrackView, TrackViewComponent> track2ComponentMapping = new HashMap<>();
    collectTrackViews(track2ComponentMapping, myTracksViewsContainer);

    // 1. remove components
    myTracksViewsContainer.removeAll();

    // 2. re-add components, create for missed tracks
    initComponents();

    // 4. re-layout, repaint
    revalidate();
  }

  private void collectTrackViews(final Map<TrackView, TrackViewComponent> track2ComponentMapping,
                                 final Container container) {
    final Component[] components = container.getComponents();
    for (final Component component : components) {
      if (component instanceof TrackViewComponent) {
        final TrackViewComponent trackViewComponent = (TrackViewComponent) component;
        final TrackView trackView = trackViewComponent.getTrackView();
        track2ComponentMapping.put(trackView, trackViewComponent);
      } else if (component instanceof JXMultiSplitPane) {
        collectTrackViews(track2ComponentMapping, (Container) component);
      }
    }
  }

  protected List<TrackViewComponent> getSelectedTrackViewComponents() {
    return getTrackViewComponents().stream().filter(TrackViewComponent::isSelected).collect(Collectors.toList());
  }

  public List<TrackViewComponent> getTrackViewComponents() {
    final List<TrackViewComponent> trackComponents = Lists.newArrayList();
    getTrackViewComponentsRecursive(myTracksViewsContainer, trackComponents);
    return trackComponents;
  }

  public Storage getUIModel() {
    return myUIModel;
  }

  private void getTrackViewComponentsRecursive(final Container container,
                                               final List<TrackViewComponent> trackComponents) {
    for (final Component component : container.getComponents()) {
      if (component instanceof TrackViewComponent) {
        trackComponents.add((TrackViewComponent) component);
      } else if (component instanceof Container) {
        getTrackViewComponentsRecursive((Container) component, trackComponents);
      }
    }
  }

}
