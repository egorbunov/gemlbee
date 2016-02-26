package org.jetbrains.bio.browser.tracks;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Marker interface of {@link TrackView} with controls UI
 *
 * @author Oleg Shpynov
 * @since 6/29/15
 */
public interface TrackViewWithControls {
  /**
   * Custom components panel with enabled/disabled events handler. Please disable your
   * controls on process(false) request.
   *
   * @return Pair of panel and panel component enabled(true - enabled, false - disabled) handler.
   */
  @Nullable
  kotlin.Pair<JPanel, Consumer<Boolean>> createTrackControlsPane();

  @Nullable
  default JPanel initTrackControlsPane() {
    final kotlin.Pair<JPanel, Consumer<Boolean>> paneAndConsumer = createTrackControlsPane();
    if (paneAndConsumer == null) {
      return null;
    }
    final JPanel trackControlsPane = paneAndConsumer.getFirst();
    if (trackControlsPane == null || paneAndConsumer.getSecond() == null) {
      throw new IllegalArgumentException("Components pane and enabled handler can't be null");
    }
    return trackControlsPane;
  }

}
