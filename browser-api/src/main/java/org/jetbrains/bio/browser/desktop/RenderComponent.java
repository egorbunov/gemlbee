package org.jetbrains.bio.browser.desktop;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.tasks.CancellableState;
import org.jetbrains.bio.browser.tasks.CancellableTask;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.tracks.TrackViewListener;
import org.jetbrains.bio.browser.util.Storage;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.browser.util.TrackViewRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;

/**
 * Component use to draw {@link TrackView} or progress asynchronously
 *
 * @author Roman.Chernyatchik
 * @author Oleg Shpynov
 */
public class RenderComponent extends JComponent implements TrackViewListener {
  private static final Logger LOG = Logger.getLogger(RenderComponent.class);

  // Same as server version
  public static final float MAX_ALPHA = 0.5f;
  private volatile float myAlpha = 0f;

  private final TrackView myTrackView;
  private final DesktopGenomeBrowser myBrowser;
  private final Storage myUiModel;

  private final Timer myProgressTimer;

  private volatile CancellableTask<BufferedImage> myTask;
  private volatile BufferedImage myImage;

  public RenderComponent(final TrackView trackView,
                         final DesktopGenomeBrowser browser,
                         final Storage uiModel) {
    myTrackView = trackView;
    myBrowser = browser;
    myUiModel = uiModel;
    trackView.addEventsListener(this);
    myProgressTimer = new Timer(50, e -> repaint());
  }

  private void restart() {
    CancellableTask<BufferedImage> task = myTask;
    if (task != null) {
      LOG.trace("[" + myTrackView.getTitle() + "] Cancel task " + task.getId());
      task.cancel();
    }
    task = CancellableTask.Companion.of(this::paintToBuffer);
    LOG.trace("[" + myTrackView.getTitle() + "] Submitted task " + task.getId());
    myTask = task;
  }

  @Override
  public void repaintRequired() {
    LOG.trace("[" + myTrackView.getTitle() + "] Repaint required");
    restart();
    SwingUtilities.invokeLater(RenderComponent.this::repaint);
  }

  @Override
  public void relayoutRequired() {
    LOG.trace("[" + myTrackView.getTitle() + "] Relayout required");
    restart();
    SwingUtilities.invokeLater(() -> {
      // Invalidate me and all the parents
      Component c = this;
      while (c != null) {
        c.invalidate();
        c.validate();
        c = c.getParent();
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(10, myTrackView.getPreferredHeight());
  }

  public void setPreferredHeight(final int height) {
    myTrackView.setPreferredHeight(height);
    relayoutRequired();
  }

  @Override
  public void paint(@NotNull final Graphics g) {
    LOG.trace("[" + myTrackView.getTitle() + "] Paint");
    final CancellableTask<BufferedImage> task = myTask;
    if (task == null) {
      restart();
      progress(g);
      return;
    }
    if (!task.isDone()) {
      progress(g);
      return;
    }
    try {
      final BufferedImage image = task.get();
      if (task == myTask) {
        LOG.trace("[" + myTrackView.getTitle() + "] Done task " + task.getId());
        g.drawImage(image, 0, 0, null);
        timerStop();
        myAlpha = 0f;
        myImage = image;
      }
    } catch (CancellationException e) {
      restart();
      progress(g);
    }
  }

  private void progress(final Graphics g) {
    LOG.trace("[" + myTrackView.getTitle() + "] Progress");
    final BufferedImage image = myImage;
    if (image != null) {
      g.drawImage(image, 0, 0, null);
    }

    // Draw fade + progress
    ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    myAlpha = Math.min(MAX_ALPHA, myAlpha + 0.01f);
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, getWidth(), getHeight());
    // draw progress line
    for (int i = 0; i < getWidth(); i++) {
      if ((i + System.currentTimeMillis() / 10) % 20 < 10) {
        g.setColor(Color.BLUE);
      } else {
        g.setColor(Color.LIGHT_GRAY);
      }
      g.drawLine(i, 2, i - 5, 8);
    }
    timerStart();

  }

  @NotNull
  public TrackView getTrackView() {
    return myTrackView;
  }

  private BufferedImage paintToBuffer() {
    final int width = getWidth();
    final int height = getHeight();

    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    try {
      TrackViewRenderer.paintToImage(image,
                                     myBrowser.getBrowserModel().copy(), width, height,
                                     myTrackView,
                                     CancellableState.Companion.getInstance(),
                                     true, myUiModel);
    } catch (final CancellationException e) {
      // Rethrow
      throw e;
    } catch (final Throwable e) {
      LOG.error(e);
      final Graphics2D g2d = image.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      TrackUIUtil.drawErrorMessage(g2d, "Exception occurred: " + e.getClass().getName() + " - " + e.getMessage() + " (see details in log)");
      g2d.dispose();
    }
    return image;
  }

  private void timerStart() {
    // Start repaint timer to show a progress bar while buffered image is calculating
    if (!myProgressTimer.isRunning()) {
      myProgressTimer.start();
    }
  }

  private void timerStop() {
    if (myProgressTimer.isRunning()) {
      myProgressTimer.stop();
    }
  }

  public void dispose() {
    timerStop();
    myTrackView.removeEventsListener(this);
  }
}