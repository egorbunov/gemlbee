package org.jetbrains.bio.methylome;

import gnu.trove.list.TByteList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.data.frame.DataFrame;
import org.jetbrains.bio.data.frame.DataFrameBuilder;
import org.jetbrains.bio.data.frame.DataFramePredicatesKt;
import org.jetbrains.bio.data.frame.DataFrameSpec;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Location;
import org.jetbrains.bio.genome.Range;
import org.jetbrains.bio.genome.Strand;
import org.jetbrains.bio.genome.sequence.Nucleotide;
import org.jetbrains.bio.genome.sequence.NucleotideSequence;
import org.jetbrains.bio.genome.sequence.NucleotideSequenceKt;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.jetbrains.bio.methylome.MethylomeDfPredicatesKt.controlFdr;
import static org.jetbrains.bio.methylome.MethylomeDfPredicatesKt.covered;

/**
 * @author Roman.Chernyatchik
 *         <p>
 *         ///TODO as Chromosome o data frame kotlin extensions?
 */
public class MethylomeStats {

  public static DataFrame cytosinesDf(final Chromosome chr, final Strand strand) {
    return cytosinesDf(chr, strand, null);
  }

  public static DataFrame cytosinesDf(final Chromosome chr, final Strand strand,
                                      @Nullable final CytosineContext patternFilter) {
    return cytosinesDf(NucleotideSequenceKt.asNucleotideSequence(chr.getSequence().toString()),
                       strand, patternFilter);
  }

  /**
   * Data frame columns:
   * - offset : int[] offset in chr
   * - tag : byte[] - Methylation pattern tag (0 - CG, 1 - CHH, 2 - CHG). If unknown => cytosine ignored
   * - strand : byte[] - Strand tag (0 - '+', 1 - '-')
   *
   * @param chr Chromosome
   * @return data frame
   */
  public static DataFrame cytosinesDf(final Chromosome chr) {
    final NucleotideSequence wrapSeq = NucleotideSequenceKt
        .asNucleotideSequence(chr.getSequence().toString());

    final DataFrame dfPlus = cytosinesDf(wrapSeq, Strand.PLUS, null);
    final DataFrame dfMinus = cytosinesDf(wrapSeq, Strand.MINUS, null);

    return DataFrame.rowBind(dfPlus.with("strand", (byte) Strand.PLUS.ordinal()),
                             dfMinus.with("strand", (byte) Strand.MINUS.ordinal()))
        .reorder("offset");
  }

  public static DataFrame completeMethylome(final MethylomeQuery methylomeQuery,
                                            final Chromosome chromosome,
                                            final Strand strand,
                                            final @Nullable CytosineContext patternFilter) {
    return completeMethylome(methylomeQuery.get(), chromosome, strand, patternFilter);
  }

  public static DataFrame completeMethylome(final Methylome methylome,
                                            final Chromosome chromosome,
                                            final Strand strand) {
    return completeMethylome(methylome, chromosome, strand, null);
  }

  public static DataFrame completeMethylome(final Methylome methylome,
                                            final Chromosome chromosome,
                                            final Strand strand,
                                            final @Nullable CytosineContext patternFilter) {
    return completeMethylome(cytosinesDf(chromosome, strand, patternFilter),
                             methylome, chromosome, strand, patternFilter);
  }

  /**
   * DF containing info for each cytosine. Columns:
   * - tag : byte[] - Methylation pattern tag (0 - CG, 1 - CHH, 2 - CHG)
   * - level : double[] - Methylation level
   * - k : short[] - Covered as methylated cytosine
   * - n : short[] - Cytosine reads coverage
   * - d : int[] - Distance to previous cytosine
   * - offset : int[] - Genome offset
   *
   * @param cytosinesDf   Cytosines data frame
   * @param methylome     Methylome
   * @param chromosome    Chromosome
   * @param strand        Strand
   * @param patternFilter Methylation pattern (null - any)
   * @return df
   */
  public static DataFrame completeMethylome(final DataFrame cytosinesDf,
                                            final Methylome methylome,
                                            final Chromosome chromosome,
                                            final Strand strand,
                                            final @Nullable CytosineContext patternFilter) {
    // Cytosines and methylome both contains 'tag' row. Cytosines is supposed to include
    // methylome offsets => we need to omit methylome.tag column to avoid loose values
    final DataFrame df = coveredMethylome(methylome, chromosome, strand, patternFilter);
    return DataFrame.mergeOuter("offset", cytosinesDf, df.omit("tag"));
  }

  /**
   * DF containing info for each covered cytosine. Columns:
   * - tag : byte[] - Methylation pattern tag (0 - CG, 1 - CHH, 2 - CHG)
   * - level : double[] - Methylation level
   * - k : short[] - Covered as methylated cytosine
   * - n : short[] - Cytosine reads coverage
   * - d : int[] - Distance to previous cytosine
   * - offset : int[] - Genome offset
   *
   * @param methylome     Methylome
   * @param chromosome    Chromosome
   * @param strand        Strand
   * @param patternFilter Methylation pattern (null - any)
   * @return df
   */
  public static DataFrame coveredMethylome(final Methylome methylome,
                                           final Chromosome chromosome,
                                           final Strand strand,
                                           final @Nullable CytosineContext patternFilter) {
    return MethylomeToDataFrame
        .create(strand.asFilter(), patternFilter)
        .apply(methylome, chromosome);
  }


  /**
   * Data frame columns:
   * - offset : int[] offset in chr
   * - tag : byte[] - Methylation pattern tag (0 - CG, 1 - CHH, 2 - CHG). If unknown => cytosine ignored
   *
   * @param wrappedSeq    Wrapped sequence with fast charAt() access
   * @param strand        Strand
   * @param patternFilter Optional tag (methylation pattern) filter
   * @return data frame
   */
  public static DataFrame cytosinesDf(final NucleotideSequence wrappedSeq, final Strand strand,
                                      @Nullable final CytosineContext patternFilter) {
    final TIntList offsets = new TIntArrayList();
    final TByteList tags = new TByteArrayList();

    final char cChar = strand.isPlus() ? Nucleotide.C.getChar()
                                       : Nucleotide.G.getChar();

    final int chrLength = wrappedSeq.length();
    for (int offset = 0; offset < chrLength; offset++) {
      if (wrappedSeq.charAt(offset) == cChar) {
        final CytosineContext pattern = CytosineContext.determine(wrappedSeq, offset, strand);
        //NOTE: skip undefined patterns, it is smth near NNN
        if (pattern != null && (patternFilter == null || pattern == patternFilter)) {
          offsets.add(offset);
          tags.add(pattern.tag);
        }
      }
    }

    return new DataFrame()
            .with("offset", offsets.toArray())
            .with("tag", tags.toArray());
  }

  static LocationsMethylationFun mC2CRatio(final double fdr) {
    return (locDf, edf, locMcStartRow, locMcEndRow, pattern) -> {

      // Calc stats
      final double[] mcCRatio = IntStream.range(0, locDf.getRowsNumber()).mapToDouble(locRow -> {
        final int startOffsetRow = locMcStartRow[locRow];
        final int endOffsetRow = locMcEndRow[locRow];
        if (startOffsetRow == endOffsetRow) {
          return Double.NaN;
        }
        final int cCovCount = edf.count(covered(), startOffsetRow, endOffsetRow);
        final int mcCount = edf.count(DataFramePredicatesKt.all(covered(), controlFdr(fdr)),
                                      startOffsetRow, endOffsetRow);
        return ((double) mcCount) / (cCovCount);
      }).toArray();


      return locDf.with("mc_c_" + pattern.name(), mcCRatio);
    };
  }

  static LocationsMethylationFun cCoverage() {
    return (locDf, edf, locMcStartRow, locMcEndRow, pattern) -> {

      final double[] avgCov = IntStream.range(0, locDf.getRowsNumber()).mapToDouble(locRow -> {
        final int startOffsetRow = locMcStartRow[locRow];
        final int endOffsetRow = locMcEndRow[locRow];
        if (startOffsetRow == endOffsetRow) {
          return Double.NaN;
        }
        final int cCovCount = edf.count(covered(), startOffsetRow, endOffsetRow);
        return ((double) cCovCount) / (endOffsetRow - startOffsetRow);
      }).toArray();

      return locDf.with(("cCov_" + pattern.name()).intern(), avgCov);
    };
  }

  private static <T> DataFrame createLocationsDf(final List<T> items,
                                                 final Function<T, Location> item2Loc,
                                                 @Nullable final Function<T, String> item2Id) {

    final DataFrameBuilder builder = new DataFrameSpec().ints("start_offset", "end_offset").builder();
    items.stream().map(item2Loc).forEach(loc -> builder.add(new Object[]{loc.getStartOffset(), loc.getEndOffset()}));
    DataFrame df = builder.build();

    // add ids column
    if (item2Id != null) {
      final String[] ids = items.stream().map(item2Id).toArray(String[]::new);
      df = df.with("id", ids);
    }

    return df.reorder("start_offset", "end_offset");
  }

  /**
   * Process all states in offsets range
   *
   * @param chrEnrichment  Enrichment (with 'offset' and 'level' columns)
   * @param range          Offsets range
   * @param stateProcessor User processor for states in range
   */
  public static void processLocusEnrichment(final DataFrame chrEnrichment,
                                            final Range range,
                                            final IntConsumer stateProcessor) {
    // Same as count some predicate on (df, range)
    final int[] offsets = chrEnrichment.sliceAsInt("offset");
    final float[] levels = chrEnrichment.sliceAsFloat("level");

    int ptr = Arrays.binarySearch(offsets, range.getStartOffset());
    if (ptr < 0) {
      ptr = Math.max(~ptr - 1, 0);
    }

    final int rangeEndOffset = range.getEndOffset();
    while (ptr < chrEnrichment.getRowsNumber() && offsets[ptr] <= rangeEndOffset) {
      if (!Float.isNaN(levels[ptr])) {
        stateProcessor.accept(ptr);
      }

      ptr++;
    }
  }

  /**
   * @param startOffset start offset inclusive
   * @param endOffset   end offset exclusive
   * @param sequence    Wrapped or normal sequence
   * @param strand      Strand
   * @param pattern     Cytosine context or null if any
   * @return cytosines count of given context
   */
  public static int countCytosines(final int startOffset, final int endOffset,
                                   final NucleotideSequence sequence,
                                   final Strand strand,
                                   @Nullable final CytosineContext pattern) {
    final byte cytosine = Nucleotide.C.getByte();

    int cCount = 0;
    for (int pos = startOffset; pos < endOffset; pos++) {
      if (pattern != CytosineContext.ANY) {
        final CytosineContext actualPattern = CytosineContext.determine(sequence, pos, strand);
        if (actualPattern == pattern) {
          cCount++;
        }
      } else {
        if (sequence.byteAt(pos, strand) == cytosine) {
          cCount++;
        }
      }
    }
    return cCount;
  }

  @FunctionalInterface
  public interface LocationsMethylationFun {
    DataFrame apply(DataFrame locationsDf,
                    DataFrame mcEnrichmentDf,
                    int[] locMcStartRow,
                    int[] locMcEndRow,
                    @NotNull CytosineContext pattern);
  }

  public static Range binarySearch(final DataFrame mcDf,
                                   final int startOffset,
                                   final int endOffset) {
    final int[] offsets = mcDf.sliceAsInt("offset");
    // 'Arrays#binarySearch' has undefined behaviour for arrays with
    // equal elements, thus we should explicitly check if we were lucky
    // enough to get lower/upper bound.
    final int lowerBound = Arrays.binarySearch(offsets, startOffset);
    final int upperBound = Arrays.binarySearch(offsets, endOffset);

    return new Range(lowerBound < 0 ? ~lowerBound : lowerBound,
                     upperBound < 0 ? ~upperBound : upperBound);
  }

}
