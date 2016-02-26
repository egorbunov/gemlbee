package org.jetbrains.bio.browser.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.browser.model.GeneLocRef;
import org.jetbrains.bio.browser.model.LocationReference;
import org.jetbrains.bio.browser.model.SimpleLocRef;
import org.jetbrains.bio.genome.*;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jetbrains.bio.genome.ImportantGenesAndLoci.getDevelopmentalGenes;
import static org.jetbrains.bio.genome.ImportantGenesAndLoci.getHouseKeepingGenes2013Short;

public class LociCompletion {

  public static final Map<String, Function1<GenomeQuery, List<LocationReference>>> DEFAULT_COMPLETION =
      ImmutableMap.of(
          "housekeeping", (Function1<GenomeQuery, List<LocationReference>>) gq ->
              GeneResolver.resolve(gq.getBuild(), getHouseKeepingGenes2013Short(gq)).stream()
                          .map(GeneLocRef::new)
                          .collect(Collectors.toList()),
          "development", (Function1<GenomeQuery, List<LocationReference>>) gq ->
              GeneResolver.resolve(gq.getBuild(), getDevelopmentalGenes(gq)).stream()
                          .map(GeneLocRef::new)
                          .collect(Collectors.toList()),

          "cg_islands", (Function1<GenomeQuery, List<LocationReference>>) gq ->
              gq.get().stream()
                  .flatMap(chromosome -> chromosome.getCpgIslands().stream())
                  .map(cgi -> new SimpleLocRef(cgi.getLocation()))
                  .collect(Collectors.toList()));

  public static final Pattern ABSTRACT_LOCATION_PATTERN = Pattern.compile("([^:]+)(:([\\d\\.,]+)-([\\d\\.,]+))?");

  /**
   * See {@link #parse(String, GenomeQuery)}
   */
  public static Set<String> get(final GenomeQuery genomeQuery) {
    final Set<String> result = Sets.newHashSet();
    result.addAll(genomeQuery.get().stream().map(Chromosome::getName).collect(Collectors.toList()));
    (genomeQuery.getRestriction().isEmpty()
     ? genomeQuery.getGenome().getGenes().stream()
     : genomeQuery.get().stream().flatMap(c -> c.getGenes().stream()))
        .flatMap(g -> g.getNames().values().stream()).forEach(result::add);
    return result;
  }

  /**
   * See {@link #get(GenomeQuery)}
   */
  @Nullable
  public static LocationReference parse(final String text, final GenomeQuery genomeQuery) {
    final String name = text.toLowerCase().trim();
    final Matcher matcher = ABSTRACT_LOCATION_PATTERN.matcher(name);
    if (matcher.matches()) {
      final String chromosomeName = matcher.group(1);
      final Chromosome chromosome = ChromosomeNamesMap.create(genomeQuery).get(chromosomeName);
      if (chromosome != null) {
        final String startGroup = matcher.groupCount() >= 3 ? matcher.group(3) : null;
        final String endGroup = matcher.groupCount() >= 4 ? matcher.group(4) : null;
        if (startGroup != null && endGroup != null) {
          final Location loc = new Location(Integer.parseInt(startGroup.replaceAll("\\.|,", "")),
                                            Integer.parseInt(endGroup.replaceAll("\\.|,", "")),
                                            chromosome,
                                            Strand.PLUS);
          return new SimpleLocRef(loc);
        }
        return new SimpleLocRef(new Location(0, chromosome.getLength(), chromosome, Strand.PLUS));
      }
    }
    final Gene gene = GeneResolver.getAny(genomeQuery.getBuild(), name);
    if (gene == null) {
      return null;
    }
    // Relative
    return new GeneLocRef(gene, RelativePosition.AROUND_WHOLE_SEGMENT.of(gene.getLocation(),
                                                                         -gene.length() / 2, gene.length() / 2));
  }
}
