package org.jetbrains.bio.browser;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser;
import org.jetbrains.bio.browser.model.LocationReference;
import org.jetbrains.bio.browser.model.MultipleLocationsBrowserModel;
import org.jetbrains.bio.browser.model.SimpleLocRef;
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Location;
import org.jetbrains.bio.genome.Strand;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.Collections;

import static com.google.common.collect.ImmutableMap.of;

public class AbstractGenomeBrowserTest extends TestCase {

  public void testHandleMultipleLocModel_Empty() throws Exception {
    final GenomeQuery gq = new GenomeQuery("to1");
    final HeadlessGenomeBrowser browser
        = new HeadlessGenomeBrowser(new SingleLocationBrowserModel(gq),
                                    Collections.emptyList(),
                                    Collections.emptyMap());
    assertFalse(browser.getBrowserModel() instanceof MultipleLocationsBrowserModel);
    assertFalse(browser.handleMultipleLocationsModel("housekeeping"));
  }

  public void testHandleMultipleLocModel() throws Exception {
    final GenomeQuery gq = new GenomeQuery("to1");
    final Chromosome chr = Chromosome.get("to1", "chr1");
    final LocationReference locRef
        = new SimpleLocRef(new Location(0, 1000, chr, Strand.PLUS));

    final HeadlessGenomeBrowser browser =
        new HeadlessGenomeBrowser(new SingleLocationBrowserModel(gq),
                                  Collections.emptyList(),
                                  of("housekeeping", qg -> ImmutableList.of(locRef)));
    assertTrue(browser.handleMultipleLocationsModel("housekeeping"));
    assertTrue(browser.getBrowserModel() instanceof MultipleLocationsBrowserModel);
    assertEquals(0, browser.getBrowserModel().getRange().getStartOffset());
    assertEquals(1000, browser.getBrowserModel().getRange().getEndOffset());
    assertTrue(browser.handleMultipleLocationsModel("housekeeping"));
  }

  public void testHandleMultipleLocModel_RangeFilter() throws Exception {
    final GenomeQuery gq = new GenomeQuery("to1");
    final Chromosome chr = Chromosome.get("to1", "chr1");
    final LocationReference locRef
        = new SimpleLocRef(new Location(0, 1000, chr, Strand.PLUS));

    final HeadlessGenomeBrowser browser =
        new HeadlessGenomeBrowser(new SingleLocationBrowserModel(gq),
                                  Collections.emptyList(),
                                  of("housekeeping", qg -> ImmutableList.of(locRef)));
    assertTrue(browser.handleMultipleLocationsModel("housekeeping"));
    assertEquals(0, browser.getBrowserModel().getRange().getStartOffset());
    assertEquals(1000, browser.getBrowserModel().getRange().getEndOffset());
    assertTrue(browser.handleMultipleLocationsModel("housekeeping:100-200"));
    assertEquals(100, browser.getBrowserModel().getRange().getStartOffset());
    assertEquals(200, browser.getBrowserModel().getRange().getEndOffset());
  }
}