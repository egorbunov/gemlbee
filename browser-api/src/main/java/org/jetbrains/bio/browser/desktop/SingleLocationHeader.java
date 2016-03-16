package org.jetbrains.bio.browser.desktop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Gap;
import org.jetbrains.bio.genome.Range;

import java.awt.*;

import static org.jetbrains.bio.browser.util.TrackUIUtil.VERTICAL_SPACER;
import static org.jetbrains.bio.browser.util.TrackUIUtil.genomeToScreen;

/**
 * @author Evgeny.Kurbatsky
 */
public class SingleLocationHeader extends Header {
  public static final int VERTICAL_MARGIN = 4;
  private static final Color GAPS_COLOR = new Color(150, 150, 150, 128);

  private final SingleLocationBrowserModel myModel;

  private final int myGridHeight;

  public SingleLocationHeader(final SingleLocationBrowserModel model) {
    myModel = model;
    // repaint on location changed
    myModel.addModelListener(this::repaint);
    myGridHeight = VERTICAL_MARGIN + 2 * (TrackUIUtil.SMALL_FONT_HEIGHT + VERTICAL_SPACER);
    setPreferredSize(new Dimension(30, // fake width value
                                   myGridHeight + CytoBandsRenderer.height(TrackUIUtil.SMALL_FONT) + VERTICAL_MARGIN));
  }

  @Override
  public int getHeight() {
    return super.getHeight();
  }

  public int getPointerHandlerY() {
    return myGridHeight + CytoBandsRenderer.getPointerHandlerY();
  }

  @Override
  public void paint(@NotNull final Graphics g) {
    final Range range = myModel.getRange();
    final Chromosome chr = myModel.getChromosome();

    final int width = getWidth();
    final int height = getHeight();

    g.setFont(TrackUIUtil.SMALL_FONT);

    // Clear header
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);

    // Draw Grid with ruler
    TrackUIUtil.drawGrid(g, range, width, height);
    TrackUIUtil.drawOffsets(g, range, width);
    TrackUIUtil.drawScaleRuler(g, range, width, myGridHeight);

    // Draw gaps
    drawGaps(g, range, width);

    // Draw bands
    CytoBandsRenderer.drawBands(g, myGridHeight, width, chr);

    // Pointer
    drawPointer(g, width, chr.getRange(), myModel.getRange(), getPointerHandlerY());

    // Draw build
    g.setFont(TrackUIUtil.DEFAULT_FONT);
    TrackUIUtil.drawString(g, String.format("   %s   ", myModel.getGenomeQuery().getShortNameWithChromosomes()),
                           3, 17, Color.BLACK);
  }

  private void drawGaps(final @NotNull Graphics g, final Range range, final int width) {
    final Chromosome chromosome = myModel.getChromosome();
    chromosome.getGaps().stream()
        .filter(gap -> gap.isTelomere() || gap.isHeterochromatin()).map(Gap::getLocation)
        .filter(l -> range.intersects(l.toRange())).forEach(loc -> {
      final int startX = Math.max(0, genomeToScreen(loc.getStartOffset(), width, range));
      final int endX = Math.min(width, genomeToScreen(loc.getEndOffset(), width, range));

      g.setColor(GAPS_COLOR);
      g.fillRect(startX, myGridHeight - 17, endX - startX - 1, 12);
    });
  }


}
