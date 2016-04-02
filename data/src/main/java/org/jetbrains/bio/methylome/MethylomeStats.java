package org.jetbrains.bio.methylome;

import com.google.common.base.Preconditions;
import gnu.trove.list.TByteList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.data.frame.DataFrame;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Range;
import org.jetbrains.bio.genome.Strand;
import org.jetbrains.bio.genome.sequence.Nucleotide;
import org.jetbrains.bio.genome.sequence.NucleotideSequence;
import org.jetbrains.bio.genome.sequence.NucleotideSequenceKt;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.jetbrains.bio.methylome.MethylomeDfPredicatesKt.controlFdr;
import static org.jetbrains.bio.methylome.MethylomeDfPredicatesKt.covered;

/**
 * @author Roman.Chernyatchik
 *         <p>
 *         ///TODO as Chromosome o data frame kotlin extensions?
 */
public class MethylomeStats {

  public static DataFrame cytosinesDf(final Chromosome chr, final Strand strand,
                                      @Nullable final CytosineContext patternFilter) {
    return cytosinesDf(NucleotideSequenceKt.asNucleotideSequence(chr.getSequence().toString()),
                       strand, patternFilter);
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


  public static LocationsMethylationFun cCoverage() {
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
