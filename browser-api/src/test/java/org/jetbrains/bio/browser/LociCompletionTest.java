package org.jetbrains.bio.browser;

import junit.framework.TestCase;
import org.jetbrains.bio.browser.util.LociCompletion;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Roman.Chernyatchik
 */
public class LociCompletionTest extends TestCase {
  public static final String[] BOUNDARIES_TESTS = new String[]{
      "tss-500_500", "tss-500_+500", "tss-500;+500", "tss-500,+500",
      "tss-500,500", "tss200 300", "tss200 +300", "tss 200 +300",
      "tss_-200 +300"};

  public void testParseAsChrLocation() {
    final GenomeQuery query = new GenomeQuery("to1", "chr1");

    assertNotNull(LociCompletion.parse("chr1", query));
    assertNotNull(LociCompletion.parse("chr1:1234-2345", query));

    assertNull(LociCompletion.parse("chr1:1aa", query));
    assertNull(LociCompletion.parse("chr1:1234", query));
    assertNull(LociCompletion.parse("chr2:1234-2345", query));

    assertEquals("chr1:+[1234, 4568)",
                 LociCompletion.parse("chr1:1234-4568", query).getLocation().toString());
    assertEquals("chr1:+[2100234, 2100568)",
                 LociCompletion.parse("chr1:2.100.234-2.100.568", query).getLocation().toString());
    assertEquals("chr1:+[2100234, 2100568)",
                 LociCompletion.parse("chr1:2,100,234-2,100,568", query).getLocation().toString());
  }

  public void testParseChrX() {
    final GenomeQuery query = new GenomeQuery("to1", "chr2", "chrX");
    assertNotNull(LociCompletion.parse("chrX", query));
  }

  public void testAbstractLocationPattern() {
    Stream.of(BOUNDARIES_TESTS).forEach(p -> {
      final Pattern pattern = LociCompletion.ABSTRACT_LOCATION_PATTERN;
      assertTrue(pattern.matcher(p).matches());
      assertTrue(pattern.matcher(p + ":1-1000").matches());
    });
  }

  public void testPredicate() {
    assertTrue(LociCompletion.ABSTRACT_LOCATION_PATTERN.matcher("h3K4me3_h1<>huvec").matches());
    assertTrue(LociCompletion.ABSTRACT_LOCATION_PATTERN.matcher("h3K4me3_h1<>huvec:1-1000").matches());
  }

  public void testCompletionResolve() {
    final GenomeQuery genomeQuery = new GenomeQuery("to1");
    LociCompletion.get(genomeQuery)
        .forEach(s -> assertNotNull(LociCompletion.parse(s, genomeQuery)));
  }
}
