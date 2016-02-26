package org.jetbrains.bio.browser.headless;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.browser.AbstractGenomeBrowser;
import org.jetbrains.bio.browser.command.Command;
import org.jetbrains.bio.browser.desktop.TrackListComponent;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.LocationReference;
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel;
import org.jetbrains.bio.browser.tasks.CancellableState;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.browser.util.TrackViewRenderer;
import org.jetbrains.bio.ext.ExecutorExtensionsKt;
import org.jetbrains.bio.ext.LoggerExtensionsKt;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Oleg Shpynov
 * @since 25.12.14
 */
public class HeadlessGenomeBrowser implements AbstractGenomeBrowser {
  private static final Logger LOG = Logger.getLogger(HeadlessGenomeBrowser.class);

  public static final int SCREENSHOT_WIDTH = 1600;
  public static final int SCREENSHOT_HEIGHT = 1200;

  private BrowserModel myBrowserModel;
  private final List<TrackView> myTrackViews;
  private final Map<String, Function1<GenomeQuery, List<LocationReference>>> myLocationsMap;


  public HeadlessGenomeBrowser(final BrowserModel browserModel,
                               final List<TrackView> trackViews,
                               final Map<String, Function1<GenomeQuery, List<LocationReference>>> locationsMap) {
    myTrackViews = trackViews;
    myLocationsMap = locationsMap.keySet().stream().collect(Collectors.toMap(String::toLowerCase, locationsMap::get));
    setBrowserModel(browserModel);
  }

  public BrowserModel getBrowserModel() {
    return myBrowserModel;
  }

  public void setBrowserModel(final BrowserModel model) {
    if (myBrowserModel == model) {
      return;
    }

    myBrowserModel = model;
  }

  public List<TrackView> getTrackViews() {
    return myTrackViews;
  }

  @Nullable
  public BufferedImage paint(final int width) throws CancellationException {
    return paint(myBrowserModel, getTrackViews(), width);
  }

  /**
   * @return null in case {@link CancellationException} exception,
   * otherwise returns resulting image of thrown RuntimeException in case of errors
   */
  @Nullable
  public static BufferedImage paint(final BrowserModel model,
                                    final List<TrackView> trackViews,
                                    final int width) throws CancellationException {
    try {
      final Stopwatch stopwatch = Stopwatch.createStarted();

      final Component headerView = AbstractGenomeBrowser.createHeaderView(model);
      final int headerHeight = headerView.getPreferredSize().height;
      final int[] heights = getTrackViewHeights(trackViews);
      final int totalHeight = headerHeight + IntStream.of(heights).sum();
      final BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);

      // Turn AA on
      final Graphics2D g2d = image.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      // paint grid
      TrackListComponent.paintGrid(model, g2d, width, totalHeight);

      // paint header
      final Graphics headerGraphics = g2d.create(0, 0, width, headerHeight);
      headerView.setSize(new Dimension(width, headerHeight));
      headerView.paint(headerGraphics);
      if (model instanceof MultipleLocationsBrowserModel) {
        TrackUIUtil.drawGrid(headerGraphics,
                             width,
                             headerHeight - 35,
                             headerHeight,
                             (MultipleLocationsBrowserModel) model);
      }
      headerGraphics.dispose();

      // Paint tracks in parallel
      final CancellableState cancellableState = CancellableState.Companion.getInstance().reset();
      final List<Callable<Unit>> tasks = Lists.newArrayListWithExpectedSize(trackViews.size());
      int y = headerHeight;
      for (int i = 0; i < trackViews.size(); i++) {
        cancellableState.checkCanceled();
        final TrackView trackView = trackViews.get(i);
        final int trackHeight = heights[i];
        final Graphics trackGraphics = g2d.create(0, y, width, trackHeight);
        y += trackHeight;
        final Callable<Unit> task = () -> {
          cancellableState.checkCanceled();
          LoggerExtensionsKt.time(LOG, Level.DEBUG, "Paint tracks: " + trackView.getTitle(), false,
                                  () -> {
                                    TrackViewRenderer.paintHeadless(model, trackGraphics, trackView,
                                                                    width, trackHeight,
                                                                    cancellableState);

                                    return Unit.INSTANCE;
                                  });

          return Unit.INSTANCE;
        };
        tasks.add(task);
      }
      final ExecutorService executor =
          Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());
      ExecutorExtensionsKt.awaitAll(executor, tasks);
      executor.shutdown();
      stopwatch.stop();
      LOG.debug("Paint tracks in " + stopwatch);
      return image;
    } catch (final CancellationException  e) {
      // Rethrow
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static int[] getTrackViewHeights(final List<TrackView> trackViews) {
    return trackViews.parallelStream()
        .mapToInt(t -> t.getPreferredHeight() + TrackViewRenderer.TITLE_HEIGHT)
        .toArray();
  }

  public Map<String, Function1<GenomeQuery, List<LocationReference>>> getLocationsMap() {
    return myLocationsMap;
  }

  @Override
  public void execute(final Command cmd) {
    // Here we rely on web browser's commands history
    if (cmd != null) {
      cmd.redo();
    }
  }

}
