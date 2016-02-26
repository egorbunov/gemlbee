package org.jetbrains.bio.genome;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.jetbrains.bio.genome.query.locus.*;

import java.util.Map;

public class LocusType<T> {

  private static final Map<String, LocusType> OUR_INSTANCES = Maps.newConcurrentMap();

  public static final GeneType INTRONS = new GeneType("introns", IntronsQuery.class);
  public static final GeneType INTRONS_EXCEPT_FIRST = new GeneType("introns_except_first", IntronsExceptFirstQuery.class);
  public static final GeneType FIRST_INTRON = new GeneType("first_intron", FirstIntronQuery.class);
  public static final GeneType LAST_INTRON = new GeneType("last_intron", LastIntronQuery.class);

  public static final GeneType EXONS = new GeneType("exons", ExonsQuery.class);
  public static final GeneType EXONS_EXCEPT_FIRST = new GeneType("exons_except_first", ExonsExceptFirstQuery.class);
  public static final GeneType FIRST_EXON = new GeneType("first_exon", FirstExonQuery.class);

  public static final GeneType TSS = new GeneType("tss", TssQuery.class);
  public static final GeneType TES = new GeneType("tes", TesQuery.class);

  public static final GeneType TRANSCRIPT = new GeneType("transcript", GeneTranscriptQuery.class);
  public static final GeneType WHOLE_GENE = new GeneType("whole_gene", WholeGeneQuery.class);
  public static final GeneType CDS = new GeneType("cds", CDSQuery.class);

  public static final GeneType UTR5 = new GeneType("utr5", UTR5Query.class);
  public static final GeneType UTR3 = new GeneType("utr3", UTR3Query.class);

  public static final ChromosomeType REPEATS = new ChromosomeType("repeats", RepeatsQuery.class);
  public static final ChromosomeType NON_REPEATS = new ChromosomeType("non_repeats", NonRepeatsQuery.class);

  private final String myName;
  private final Class<LocusQuery<T>> myQueryClass;

  private LocusType(final String name, final Class<LocusQuery<T>> queryClass) {
    Preconditions.checkState(name.toLowerCase().equals(name), "Name %s should be lowercase!", name);
    myName = name;
    myQueryClass = queryClass;
    OUR_INSTANCES.put(name.toLowerCase(), this);
  }

  public String toString() {
    return myName;
  }

  public LocusQuery<T> createQuery() {
    Preconditions.checkState(myQueryClass != null, "#createQuery is not supported for " + myName);
    try {
      return myQueryClass.newInstance();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static LocusType[] values() {
    return OUR_INSTANCES.values().toArray(new LocusType[OUR_INSTANCES.size()]);
  }

  public static LocusType of(final String name) {
    return OUR_INSTANCES.get(name.toLowerCase());
  }

  public static class ChromosomeType extends LocusType<Chromosome> {
    private ChromosomeType(final String name, final Class queryClass) {
      super(name, queryClass);
    }
  }

  public static class GeneType extends LocusType<Gene> {
    private GeneType(final String name, final Class queryClass) {
      super(name, queryClass);
    }
  }
}
