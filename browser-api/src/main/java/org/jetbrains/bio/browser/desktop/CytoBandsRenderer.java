package org.jetbrains.bio.browser.desktop;

import kotlin.collections.CollectionsKt;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.CytoBand;
import org.jetbrains.bio.genome.Location;
import org.jetbrains.bio.genome.Range;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.jetbrains.bio.browser.SupportKt.genomeToScreen;
import static org.jetbrains.bio.browser.desktop.SingleLocationHeader.VERTICAL_MARGIN;

/**
 * @author Roman.Chernyatchik
 */
public class CytoBandsRenderer {
  public static final HashMap<String, Color> GIEMSA_COLORS = new LinkedHashMap<>();
  static {
    GIEMSA_COLORS.put("gneg", Color.WHITE);
    GIEMSA_COLORS.put("gpos", Color.GRAY);
    GIEMSA_COLORS.put("gpos25", Color.LIGHT_GRAY);
    GIEMSA_COLORS.put("gpos50", Color.GRAY);
    GIEMSA_COLORS.put("gpos75", Color.DARK_GRAY);
    GIEMSA_COLORS.put("gpos100", Color.BLACK);
    GIEMSA_COLORS.put("acen", new Color(136, 208, 255));
    GIEMSA_COLORS.put("gvar", new Color(111, 188, 112));
    GIEMSA_COLORS.put("stalk", new Color(207, 162, 255));
    GIEMSA_COLORS.put("unknown", new Color(255, 162, 32));
  }

  public static final int IDEOGRAM_HEIGHT = Header.POINTER_HEIGHT - 5;
  public static final int IDEOGRAM_LEGEND_HEIGHT = 8;

  public static void drawBands(final Graphics g, final int y, final int width,
                               final Chromosome chr) {

    final int plotY = y + VERTICAL_MARGIN;

    final java.util.List<CytoBand> bands = CollectionsKt.sorted(chr.getCytoBands());

    final Range chrRange = chr.getRange();

    // Draw border around ideogram
    g.setColor(Color.BLACK);
    g.drawRect(0, plotY, width - 1, IDEOGRAM_HEIGHT - 1);

    // Draw bands
    final Color unknownColor = GIEMSA_COLORS.get("unknown");
    for (final CytoBand band : bands) {
      final Location location = band.getLocation();

      final int startX = genomeToScreen(location.getStartOffset(), width, chrRange);
      final int endX = genomeToScreen(location.getEndOffset(), width, chrRange);

      final int locusLengthX = endX - startX;
      if (locusLengthX > 0) {
        final Color background = GIEMSA_COLORS.getOrDefault(band.getGieStain(), unknownColor);
        g.setColor(background);
        g.fillRect(startX, plotY + 1, locusLengthX, IDEOGRAM_HEIGHT - 2);

        final Rectangle2D bounds = g.getFontMetrics().getStringBounds(band.getName(), g);
        if (bounds.getWidth() <= locusLengthX) {
          g.setColor(foregroundColor(background));
          g.drawString(band.getName(),
                       startX + (locusLengthX -  (int) bounds.getWidth()) / 2,
                       plotY + (int) bounds.getHeight());
        }
      }
    }

    // Draw centromere
    final Range centromere = chr.getCentromere();
    final int centromereLX = genomeToScreen(centromere.getStartOffset(), width, chrRange);
    final int centromereRX = genomeToScreen(centromere.getEndOffset(), width, chrRange);
    final int centromereX = centromereLX + (centromereRX - centromereLX) / 2;
    final int centromereY = plotY + IDEOGRAM_HEIGHT / 2;
    g.setColor(Color.WHITE);
    g.fillPolygon(new Polygon(new int[]{centromereLX, centromereRX, centromereX},
                              new int[]{plotY, plotY, centromereY},
                              3));
    final int ideogramBottomBorderY = plotY + IDEOGRAM_HEIGHT;
    g.fillPolygon(new Polygon(new int[]{centromereLX, centromereX, centromereRX},
                              new int[]{ideogramBottomBorderY, centromereY, ideogramBottomBorderY},
                              3));
    g.setColor(Color.BLACK);
    g.drawLine(centromereLX, plotY, centromereRX, ideogramBottomBorderY - 1);
    g.drawLine(centromereLX, ideogramBottomBorderY - 1, centromereRX, plotY);
    g.setColor(Color.RED);
    g.fillRect(centromereX - 1, plotY + IDEOGRAM_HEIGHT / 2 - 1, 3, 3);

    final Font font = g.getFont();
    drawLegend(g, y + height(font) - legendHeight(font));
  }

  private static Color foregroundColor(final Color background) {
    if (background == Color.BLACK || background == Color.DARK_GRAY) {
      return Color.WHITE;
    } else {
      return Color.BLACK;
    }
  }

  public static int height(final Font font) {
    return VERTICAL_MARGIN + IDEOGRAM_HEIGHT + 10 + legendHeight(font);
  }

  public static int getPointerHandlerY() {
    return VERTICAL_MARGIN - (int) Math.floor((SingleLocationHeader.POINTER_HEIGHT - IDEOGRAM_HEIGHT) / 2) - 1;
  }

  private static void drawLegend(final Graphics g, final int y) {
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int legendImgWidth = 40;
      int legendX = 5;

      for (final Map.Entry<String, Color> tagAndColor : GIEMSA_COLORS.entrySet()) {
        g.setColor(tagAndColor.getValue());
        g.fillRect(legendX, y, legendImgWidth, IDEOGRAM_LEGEND_HEIGHT);
        g.setColor(Color.BLACK);
        g.drawRect(legendX, y, legendImgWidth, IDEOGRAM_LEGEND_HEIGHT);
        legendX += legendImgWidth + 5;
        g.setColor(Color.BLACK);
        final String tag = tagAndColor.getKey();
        g.drawString(tag, legendX, y + IDEOGRAM_LEGEND_HEIGHT);
        legendX += fontMetrics.stringWidth(tag) + 20;
      }
    }

  private static int legendHeight(final Font font) {
    return Math.max(FontDesignMetrics.getMetrics(font).getHeight(), IDEOGRAM_LEGEND_HEIGHT);
  }
}
