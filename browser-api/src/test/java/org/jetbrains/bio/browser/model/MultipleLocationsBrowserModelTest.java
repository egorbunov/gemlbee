package org.jetbrains.bio.browser.model;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.jetbrains.bio.genome.*;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static org.jetbrains.bio.genome.Strand.PLUS;

//TEST
public class MultipleLocationsBrowserModelTest extends TestCase {

  private Location myL1;
  private Location myL2;
  private MultipleLocationsBrowserModel myMultipleLocationsBrowserModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final GenomeQuery genomeQuery = new GenomeQuery("to1", "chr1");
    final Chromosome chromosome = genomeQuery.get().get(0);
    myL1 = new Location(0, 100, chromosome, PLUS);
    myL2 = new Location(200, 300, chromosome, PLUS);
    final ImmutableList<LocationReference> list = of(new SimpleLocRef(myL1), new SimpleLocRef(myL2));
    myMultipleLocationsBrowserModel
        = MultipleLocationsBrowserModel.create("test",
                                               gq -> list,
                                               new SingleLocationBrowserModel(genomeQuery));
  }

  public void testVisibleFull() throws Exception {
    myMultipleLocationsBrowserModel.setRange(new Range(0, 200));
    final List<LocationReference> visibleLocations = myMultipleLocationsBrowserModel.visibleLocations();
    
    assertEquals(2, visibleLocations.size());
    assertEquals(myL1, visibleLocations.get(0).getLocation());
    assertEquals(myL2, visibleLocations.get(1).getLocation());
  }

  public void testVisibleFirst() throws Exception {
    myMultipleLocationsBrowserModel.setRange(new Range(0, 100));
    final List<LocationReference> visibleLocations = myMultipleLocationsBrowserModel.visibleLocations();
    
    assertEquals(1, visibleLocations.size());
    assertEquals(myL1, visibleLocations.get(0).getLocation());
  }

  public void testVisibleLast() throws Exception {
    myMultipleLocationsBrowserModel.setRange(new Range(100, 200));
    final List<LocationReference> visibleLocations = myMultipleLocationsBrowserModel.visibleLocations();
    assertEquals(1, visibleLocations.size());
    assertEquals(myL2, visibleLocations.get(0).getLocation());
  }

  public void testVisible_Cropped() throws Exception {
    final GenomeQuery genomeQuery = new GenomeQuery("to1", "chr1");
    final Chromosome chr = Chromosome.get("to1", "chr1");

    final Gene gene1 = chr.getGenes().get(0);
    final Gene gene2 = chr.getGenes().get(1);
    final LocationReference locRef1 = new GeneLocRef(gene1, new Location(0, 100, chr, PLUS));
    final LocationReference locRef2  = new GeneLocRef(gene2, new Location(200, 300, chr, PLUS));
    final ImmutableList<LocationReference> list = of(locRef1, locRef2);

    myMultipleLocationsBrowserModel
        = MultipleLocationsBrowserModel.create("test",
                                               gq -> list,
                                               new SingleLocationBrowserModel(genomeQuery));

    myMultipleLocationsBrowserModel.setRange(new Range(50, 150));
    final List<LocationReference> visibleLocations = myMultipleLocationsBrowserModel.visibleLocations();

    assertEquals(2, visibleLocations.size());
    assertEquals(50, visibleLocations.get(0).getLocation().getStartOffset());
    assertEquals(100, visibleLocations.get(0).getLocation().getEndOffset());
    assertEquals(gene1, visibleLocations.get(0).getMetaData());
    assertEquals(gene1.getName(GeneAliasType.GENE_SYMBOL), visibleLocations.get(0).getName());
    assertEquals(200, visibleLocations.get(1).getLocation().getStartOffset());
    assertEquals(250, visibleLocations.get(1).getLocation().getEndOffset());
    assertEquals(gene2.getName(GeneAliasType.GENE_SYMBOL), visibleLocations.get(1).getName());
  }

  public void testCopy() throws Exception {
    final Range range = new Range(0, 100);
    myMultipleLocationsBrowserModel.setRange(range);
    final BrowserModel copy = myMultipleLocationsBrowserModel.copy();
    copy.setRange(new Range(0, 200));
    assertEquals(range, myMultipleLocationsBrowserModel.getRange());
  }
}