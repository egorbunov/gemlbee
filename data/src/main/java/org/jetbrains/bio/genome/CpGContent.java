package org.jetbrains.bio.genome;

import com.google.common.base.Preconditions;
import org.jetbrains.bio.genome.sequence.Nucleotide;
import org.jetbrains.bio.genome.sequence.NucleotideSequence;
import org.jetbrains.bio.genome.sequence.NucleotideSequenceKt;

/**
 * Originally is used in the article "Genome-wide maps of chromatin state in pluripotent and lineage-committed cells"
 * to divide promoters within HCP, ICP and LCP classes.
 * <p>
 * HCPs contain a  500-bp interval within -0.5 kb to +2 kb with a (G+C)-fraction >=0.55 and a CpG observed to expected ratio (O/E) >=0.6.
 * LCPs contain no 500-bp interval with CpG O/E >=0.4.
 *
 * @author Oleg Shpynov
 * @since 11/5/14
 */
public enum CpGContent {
  HCP, ICP, LCP;
  public static final byte CYTOSINE = Nucleotide.C.getByte();
  public static final byte GUANINE = Nucleotide.G.getByte();
  public static final int MIN_LENGTH = 500;

  static class Counters {
    public int cg;
    public int cpg;

    public Counters(final int cg, final int cpg) {
      this.cg = cg;
      this.cpg = cpg;
    }
  }

  public static CpGContent classify(final Location location) throws IllegalArgumentException {
    final String s = location.getSequence();
    return classify(NucleotideSequenceKt.asNucleotideSequence(s), MIN_LENGTH);
  }

  public static CpGContent classify(final NucleotideSequence sequence, final int l) {
    Preconditions.checkState(sequence.length() >= l, "Cannot classify sequences < %s", l);

    final byte[] buffer = new byte[l];
    for (int i = 0; i < buffer.length; i++) {
      buffer[i] = sequence.byteAt(i);
    }

    // We are going to update these values incrementally
    double maxOE = Double.MIN_VALUE;
    Counters cgcpg = null;
    // We use shift at first so that we start with previous byte, location.startOffset = 0 is quite impossible
    for (int i = l - 1; i < sequence.length(); i++) {
      if (cgcpg == null) {
        cgcpg = new Counters(computeCG(buffer), computeCpG(buffer));
      } else {
        final byte byteOut = buffer[0];
        // Shift i -> i - 1
        System.arraycopy(buffer, 1, buffer, 0, buffer.length - 1);
        final byte byteIn = buffer[buffer.length - 1] = sequence.byteAt(i);
        // Update cg and cpg numbers
        update(buffer, cgcpg, byteOut, byteIn);
      }

      final double observedToExpectedCpG = observedToExpected(cgcpg.cg, cgcpg.cpg);
      final double cgFraction = cgFraction(l, cgcpg.cg);
      if (cgFraction >= 0.55 && observedToExpectedCpG >= 0.6) {
        return CpGContent.HCP;
      }
      maxOE = Math.max(maxOE, observedToExpectedCpG);
    }
    return maxOE < 0.4 ? CpGContent.LCP : CpGContent.ICP;
  }

  private static double cgFraction(final int length, final int cg) {
    return 1. * cg / length;
  }

  /**
   * See wikipedia for more details: http://en.wikipedia.org/wiki/CpG_site
   */
  private static double observedToExpected(final int cg, final int cpg) {
    return cg != 0 ? (2. * cpg / cg) : 0;
  }

  protected static void update(final byte[] buffer,
                               final Counters cgcpg,
                               final byte byteOut,
                               final byte byteIn) {
    int cg = cgcpg.cg;
    int cpg = cgcpg.cpg;
    // Out update
    if (byteOut == CYTOSINE) {
      cg--;
      if (GUANINE == buffer[0]) {
        cpg--;
      }
    }
    if (byteOut == GUANINE) {
      cg--;
      if (CYTOSINE == buffer[0]) {
        cpg--;
      }
    }
    // In update
    if (byteIn == CYTOSINE) {
      cg++;
      if (GUANINE == buffer[buffer.length - 2]) {
        cpg++;
      }
    }
    if (byteIn == GUANINE) {
      cg++;
      if (CYTOSINE == buffer[buffer.length - 2]) {
        cpg++;
      }
    }
    cgcpg.cg = cg;
    cgcpg.cpg = cpg;
  }

  protected static int computeCpG(final byte[] buffer) {
    int cpg = 0;
    for (int i = 0; i < buffer.length - 1; i++) {
      final byte b = buffer[i];
      final byte nextB = buffer[i + 1];
      if (CYTOSINE == b && GUANINE == nextB ||
          GUANINE == b && CYTOSINE == nextB) {
        cpg++;
      }
    }
    return cpg;
  }

  protected static int computeCG(final byte[] buffer) {
    int cg = 0;
    for (final byte nByte : buffer) {
      if (CYTOSINE == nByte || GUANINE == nByte) {
        cg++;
      }
    }
    return cg;
  }
}
