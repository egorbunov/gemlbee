package org.jetbrains.bio.browser.desktop;

import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.genome.Range;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
* @author Roman.Chernyatchik
*/
public class ScrollBarModel implements BoundedRangeModel {

  private BrowserModel myBrowserModel;
  private final List<ChangeListener> myChangedListeners = new ArrayList<>();
  private boolean myAdjusting;

  public ScrollBarModel(final BrowserModel browserModel) {
    setBrowserModel(browserModel);
    myBrowserModel.addListener(() -> {
        final ChangeEvent changeEvent = new ChangeEvent(myBrowserModel);
        for (final ChangeListener listener : myChangedListeners) {
          listener.stateChanged(changeEvent);
        }
    });
  }

  public void setBrowserModel(final BrowserModel browserModel) {
    myBrowserModel = browserModel;
  }

  @Override
  public int getMinimum() {
    return 0;
  }

  @Override
  public void setMinimum(final int newMinimum) {
    throw new UnsupportedOperationException("N/A");
  }

  @Override
  public int getMaximum() {
    // show scroll bar for whole chromosome (real or multi-loc cumulative)
    return myBrowserModel.getLength();
  }

  @Override
  public void setMaximum(final int newMaximum) {
    throw new UnsupportedOperationException("N/A");
  }

  @Override
  public int getValue() {
    return myBrowserModel.getRange().getStartOffset();
  }

  @Override
  public void setValue(final int newValue) {
    final Range currentRange = myBrowserModel.getRange();

    // new range with changed start offset not allowed to be out of chromosome bounds
    final int validatedStartOffset = Math.max(0, Math.min(newValue, getMaximum() - currentRange.length()));

    myBrowserModel.setRange(new Range(validatedStartOffset, validatedStartOffset + currentRange.length()));
  }

  @Override
  public void setValueIsAdjusting(final boolean isAdjusting) {
    myAdjusting = isAdjusting;
  }

  @Override
  public boolean getValueIsAdjusting() {
    return myAdjusting;
  }

  @Override
  public int getExtent() {
    return myBrowserModel.getRange().length();
  }

  @Override
  public void setExtent(final int newExtent) {
    final Range currentRange = myBrowserModel.getRange();

    // new range with changed length not allowed to be out of chromosome bounds
    final int validatedLength = Math.max(0, Math.min(newExtent, getMaximum() - currentRange.getStartOffset()));

    myBrowserModel.setRange(new Range(currentRange.getStartOffset(), currentRange.getStartOffset() + validatedLength));
  }

  @Override
  public void setRangeProperties(final int value, final int extent,
                                 final int min, final int max, final boolean adjusting) {

    final int validatedStOffset = Math.min(Math.max(0, value), getMaximum() - 1);

    // new range with changed length not allowed to be out of chromosome bounds
    final int validatedLength = Math.max(0, Math.min(extent, getMaximum() - validatedStOffset));

    myBrowserModel.setRange(new Range(validatedStOffset, validatedStOffset + validatedLength));

    // other properties not supported
  }

  @Override
  public void addChangeListener(final ChangeListener x) {
    myChangedListeners.add(x);
  }

  @Override
  public void removeChangeListener(final ChangeListener x) {
    myChangedListeners.remove(x);
  }
}
