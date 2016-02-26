package org.jetbrains.bio.browser;

import com.google.common.collect.Lists;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.bio.browser.command.Command;
import org.jetbrains.bio.browser.command.Commands;
import org.jetbrains.bio.browser.desktop.Header;
import org.jetbrains.bio.browser.desktop.MultipleLocationsHeader;
import org.jetbrains.bio.browser.desktop.SingleLocationHeader;
import org.jetbrains.bio.browser.model.*;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.util.LociCompletion;
import org.jetbrains.bio.ext.LoggerExtensionsKt;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.LocusType;
import org.jetbrains.bio.genome.query.GenomeLocusQuery;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Oleg Shpynov
 * @since 01/02/15
 * <p>
 * The AbstractGenomeBrowser is a graphical interface designed for browsing various track data,
 * e.g. gene composition (exons, introns, UTR, LTR and so on), ChIP-seq tag density,
 * and so on. The track views are the main part of interface; each view describes
 * the graphical representation of the underlying data.
 * There are numerous track views
 * for standard data formats, but one can always write their own implementation.
 */
public interface AbstractGenomeBrowser {

  BrowserModel getBrowserModel();

  void setBrowserModel(BrowserModel model);

  List<TrackView> getTrackViews();

  Map<String, Function1<GenomeQuery, List<LocationReference>>> getLocationsMap();

  void execute(final Command cmd);

  default List<String> getLocationCompletion() {
    final List<String> result = Lists.newArrayList();
    result.addAll(LociCompletion.get(getBrowserModel().getGenomeQuery()));

    // Add completion for Loci and preconfigured locations
    result.addAll(getLocationsMap().keySet());
    for (final LocusType locusType : LocusType.values()) {
      result.add(locusType == LocusType.WHOLE_GENE ? "genes" : locusType.toString());
    }
    IntStream.of(1000).forEach(i -> {
      result.add("tss" + i);
      result.add("tss-" + i + ',' + i);
      result.add("tes" + i);
    });
    return result.stream().map(String::toLowerCase).sorted().distinct().collect(Collectors.toList());
  }

  /**
   * Tries to process given text as {@link MultipleLocationsBrowserModel}
   *
   * @return true if successful and text was recognized as multiple locations model
   */
  default boolean handleMultipleLocationsModel(final String text) {
    BrowserModel model = getBrowserModel();
    final Map<String, Function1<GenomeQuery, List<LocationReference>>> locationsMap = getLocationsMap();

    // Process particular location
    final Matcher matcher = LociCompletion.ABSTRACT_LOCATION_PATTERN.matcher(text);
    if (matcher.matches()) {
      // Key is before range
      final String key = matcher.group(1);
      if (!(model instanceof MultipleLocationsBrowserModel) || !key.equals(((MultipleLocationsBrowserModel) model).getId())) {
        final Function1<GenomeQuery, List<LocationReference>> lf = locationsMap.get(key);
        if (lf != null) {
          model = MultipleLocationsBrowserModel.create(key, lf, getOriginalModel(model));
          setBrowserModel(model);
        } else {
          final GenomeLocusQuery<Chromosome, ?> query = GenomeLocusQuery.of(key);
          if (query != null) {
            model = LocusQueryBrowserModel.create(key, query, getOriginalModel(model));
            setBrowserModel(model);
          } else {
            setBrowserModel(getOriginalModel(model));
            return false;
          }
        }
      }

      // Goto range if required or whole model range
      final String startGroup = matcher.groupCount() >= 3 ? matcher.group(3) : null;
      final String endGroup = matcher.groupCount() >= 4 ? matcher.group(4) : null;
      if (startGroup != null && endGroup != null) {
        final int start = Integer.parseInt(startGroup.replaceAll("\\.|,", ""));
        final int end = Integer.parseInt(endGroup.replaceAll("\\.|,", ""));
        execute(Commands.createZoomToRegionCommand(model, start, end - start));
      }
      return true;
    }

    // Otherwise restore original model
    setBrowserModel(getOriginalModel(model));
    return false;
  }

  /**
   * Preprocess tracks before rendering, ensure that necessary data is loaded/cached for future
   * track fast rendering.
   */
  default void preprocessTracks(final List<TrackView> trackViews, final GenomeQuery genomeQuery) {
    // Do it sequentially, preprocessing is expected to be CPU and MEM consuming
    for (final TrackView trackView : trackViews) {
      LoggerExtensionsKt.time(Logger.getRootLogger(), Level.INFO,
                              "Preprocess track: '" + trackView.getTitle() + '\'', true,
                              () -> {
                                trackView.preprocess(genomeQuery);
                                return Unit.INSTANCE;
                              });
    }
  }

  static BrowserModel getOriginalModel(final BrowserModel model) {
    return model instanceof MultipleLocationsBrowserModel
           ? ((MultipleLocationsBrowserModel) model).getOriginalModel()
           : model;
  }

  static Header createHeaderView(final BrowserModel model) {
    return model instanceof MultipleLocationsBrowserModel
           ? new MultipleLocationsHeader((MultipleLocationsBrowserModel) model)
           : new SingleLocationHeader((SingleLocationBrowserModel) model);
  }
}
