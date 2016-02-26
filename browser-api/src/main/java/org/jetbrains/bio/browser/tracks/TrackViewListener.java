package org.jetbrains.bio.browser.tracks;


public interface TrackViewListener {
  void repaintRequired();
  void relayoutRequired();
}
