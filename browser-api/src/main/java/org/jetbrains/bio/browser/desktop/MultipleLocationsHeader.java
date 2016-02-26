package org.jetbrains.bio.browser.desktop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.LocationReference;
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.genome.Location;
import org.jetbrains.bio.genome.Range;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.util.List;

import static org.jetbrains.bio.ext.FormatterKt.asOffset;

/**
 * @author Oleg Shpynov
 * @since 29.12.14
 */
public class MultipleLocationsHeader extends Header {

  private final MultipleLocationsBrowserModel myModel;

  public MultipleLocationsHeader(final MultipleLocationsBrowserModel model) {
    myModel = model;
    // repaint on location changed
    myModel.addModelListener(this::repaint);
    setPreferredSize(new Dimension(30, getPointerHandlerY() + POINTER_HEIGHT + 1));
  }

  public int getPointerHandlerY() {
    return 5 + 2 * (TrackUIUtil.SMALL_FONT_HEIGHT + TrackUIUtil.VERTICAL_SPACER);
  }

  @Override
  public void paint(@NotNull final Graphics g) {
    g.setFont(TrackUIUtil.SMALL_FONT);
    final int width = getWidth();
    final int height = getHeight();


    // Clear header
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);

    final Range range = myModel.getRange();
    final int locations = myModel.getLocationReferences().size();

    // Draw grid
    TrackUIUtil.drawGrid(g, width, getPointerHandlerY(), height, myModel);

    // Draw labels
    final List<LocationReference> visibleLocations = myModel.visibleLocations();
    final List<Integer> locationsWidths = TrackUIUtil.locationsWidths(visibleLocations, width);

    final FontDesignMetrics fontMetrics = FontDesignMetrics.getMetrics(TrackUIUtil.SMALL_FONT);
    int x = 0;
    for (int i = 0; i < visibleLocations.size(); i++) {
      final LocationReference locRef = visibleLocations.get(i);
      final int locationWidth = locationsWidths.get(i);
      final Location l = locRef.getLocation();
      final String name = locRef.getName();
      final String label = String.format("%s%s: %s [%s]",
                                         name.isEmpty() ? "" : name +  " = ",
                                         l.getChromosome().getName(),
                                         asOffset(l.getStartOffset()),
                                         asOffset(l.length()));
      if (fontMetrics.stringWidth(label) + 10 < locationWidth) {
        TrackUIUtil.drawString(g, label, x + 5, getHeight() - 4 - 1, Color.BLACK);
      }
      x += locationWidth;
    }

    // Ruler & pointer
    TrackUIUtil.drawScaleRuler(g, range, width, getPointerHandlerY());

    drawPointer(g, width, new Range(0, myModel.getLength()), range, getPointerHandlerY());

    // Show locations info
    g.setFont(TrackUIUtil.DEFAULT_FONT);
    TrackUIUtil.drawString(
        g, String.format("   %s: %s; Locations: %s; Length: %sbp   ",
                         myModel.getGenomeQuery().getShortNameWithChromosomes(),
                         myModel.getId(),
                         locations < MultipleLocationsBrowserModel.MAX_LOCATIONS ?
                         locations : MultipleLocationsBrowserModel.MAX_LOCATIONS + '+',
                         asOffset(myModel.getLength())),
        3, 17, Color.BLACK);
  }
}
