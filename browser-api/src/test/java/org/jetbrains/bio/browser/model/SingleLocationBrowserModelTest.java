package org.jetbrains.bio.browser.model;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Location;
import org.jetbrains.bio.genome.Range;
import org.jetbrains.bio.genome.Strand;
import org.jetbrains.bio.genome.query.GenomeQuery;

import java.util.concurrent.atomic.AtomicReference;

import static org.jetbrains.bio.genome.Chromosome.get;

/**
 * @author Roman.Chernyatchik
 */
@SuppressWarnings("ConstantConditions")
public class SingleLocationBrowserModelTest extends TestCase {
  private SingleLocationBrowserModel myBrowserModel;
  private Chromosome myChr1;
  private Chromosome myChr2;
  private AtomicReference<Boolean> myChrOrRangeChangedMarker;
  private ModelListener myListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myChr1 = Chromosome.get("to1", "chr1");
    myChr2 = Chromosome.get("to1", "chr2");
    final GenomeQuery genomeQuery = new GenomeQuery("to1");
    myBrowserModel = new SingleLocationBrowserModel(genomeQuery);
    myBrowserModel.setChromosomeRange(new Range(0, 10).on(myChr1));

    myChrOrRangeChangedMarker = new AtomicReference<>();

    myListener = () -> myChrOrRangeChangedMarker.set(true);
    myBrowserModel.addModelListener(myListener);
  }

  public void testListenerRangeChangedSame() {
    myBrowserModel.setRange(new Range(100, 200));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setRange(new Range(100, 200));
    assertFalse(myChrOrRangeChangedMarker.get());
  }

  public void testListenerRangeChanged() {
    myBrowserModel.setRange(new Range(100, 150));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setRange(new Range(100, 200));
    assertTrue(myChrOrRangeChangedMarker.get());
  }

  public void testListenerChromosomeChangedSame() {
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr1));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr1));
    assertFalse(myChrOrRangeChangedMarker.get());
  }

  public void testListenerChromosomeChangedOtherRange() {
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr1));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr1));
    assertTrue(myChrOrRangeChangedMarker.get());
  }

  public void testListenerChromosomeChanged() {
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr2));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr1));
    assertTrue(myChrOrRangeChangedMarker.get());
  }

  public void testRemoveModelListener() throws Exception {
    // prerequisites
    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr2));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr1));
    assertTrue(myChrOrRangeChangedMarker.get());

    // unsubscribe:
    myBrowserModel.removeModelListener(myListener);

    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr2));
    myChrOrRangeChangedMarker.set(false);
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr1));
    assertFalse(myChrOrRangeChangedMarker.get());
  }


  public void testGetChromosome() throws Exception {
    myBrowserModel = new SingleLocationBrowserModel(new GenomeQuery("to1"));
    assertNotNull(myBrowserModel.getChromosome());
    assertEquals(Chromosome.get("to1", "chr1"),
                 myBrowserModel.getChromosome());

    myBrowserModel.setChromosomeRange(new Range(100, 150).on(myChr2));
    assertSame(myChr2, myBrowserModel.getChromosome());
    assertEquals(new Range(100, 150), myBrowserModel.getRange());
  }

  public void testSetChromosomeSame() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr1));
    assertSame(myChr1, myBrowserModel.getChromosome());
    assertEquals(new Range(100, 200), myBrowserModel.getRange());
  }


  public void testSetChromosome() throws Exception {
    final Range range = myBrowserModel.getRange();
    myBrowserModel.setChromosomeRange(range.on(myChr2));
    assertSame(myChr2, myBrowserModel.getChromosome());
    assertEquals(range, myBrowserModel.getRange());
  }


  public void testGetRange() throws Exception {
    myBrowserModel = new SingleLocationBrowserModel(new GenomeQuery("to1"));
    assertNotNull(myBrowserModel.getChromosome());
    assertEquals(new Range(0, get("to1", "chr1").getLength()),
                 myBrowserModel.getRange());

    myBrowserModel.setChromosomeRange(new Range(10, 20).on(myChr1));
    assertEquals(10, myBrowserModel.getRange().getStartOffset());
    assertEquals(20, myBrowserModel.getRange().getEndOffset());
  }

  public void testSetRange() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr1));
    myBrowserModel.setRange(myChr1.getRange());
    assertEquals(0, myBrowserModel.getRange().getStartOffset());
    assertEquals(myChr1.getLength(), myBrowserModel.getRange().getEndOffset());

    myBrowserModel.setRange(new Range(10, 20));
    assertEquals(10, myBrowserModel.getRange().getStartOffset());
    assertEquals(20, myBrowserModel.getRange().getEndOffset());
    assertEquals("[10, 20)", myBrowserModel.getRange().toString());
  }

  public void testCopy_Independent() throws Exception {
    myBrowserModel = new SingleLocationBrowserModel(new GenomeQuery("to1", "chr1"));
    final Range range = new Range(0, 100);
    myBrowserModel.setRange(range);

    myBrowserModel.copy().setChromosomeRange(new Range(0, 200).on(myChr2));
    // not affected by copy
    assertEquals(range, myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testCopy() throws Exception {
    myBrowserModel = new SingleLocationBrowserModel(new GenomeQuery("to1", "chr1"));
    final SimpleLocRef metaInf = new SimpleLocRef(new Location(99, 199, myChr2, Strand.PLUS));
    myBrowserModel.setChromosomeRange(new Range(0, 200).on(myChr2), metaInf);

    final SingleLocationBrowserModel copy = myBrowserModel.copy();
    assertEquals(new Range(0, 200), copy.getRange());
    assertEquals(myChr2, copy.getChromosome());
    assertEquals(metaInf, copy.getRangeMetaInf());
  }

  public void testMetaInf() throws Exception {
    myBrowserModel = new SingleLocationBrowserModel(new GenomeQuery("to1", "chr1"));
    myBrowserModel.setChromosomeRange(new Range(0, 200).on(myChr2),
                                      new LocationReference() {
                                        @Nullable
                                        @Override
                                        public Object getMetaData() {
                                          return 1;
                                        }

                                        @NotNull
                                        @Override
                                        public String getName() {
                                          return "foo";
                                        }

                                        @NotNull
                                        @Override
                                        public LocationReference update(@NotNull final Location newLoc) {
                                          return null;
                                        }

                                        @NotNull
                                        @Override
                                        public Location getLocation() {
                                          return new Location(99, 199, myChr2, Strand.PLUS);
                                        }
                                      });

    // custom metainf
    assertNotNull(myBrowserModel.getRangeMetaInf());
    assertEquals("foo", myBrowserModel.getRangeMetaInf().getName());
    assertEquals(1, myBrowserModel.getRangeMetaInf().getMetaData());

    // change
    final SimpleLocRef metaInf = new SimpleLocRef(new Location(99, 199, myChr2, Strand.PLUS));
    myBrowserModel.setChromosomeRange(new Range(0, 10).on(myChr2), metaInf);
    assertEquals(metaInf, myBrowserModel.getRangeMetaInf());
    assertEquals("", myBrowserModel.getRangeMetaInf().getName());

    // clear
    myBrowserModel.setChromosomeRange(new Range(0, 10).on(myChr2));
    assertNull(myBrowserModel.getRangeMetaInf());
  }

  public void testGetCurrentPositionPresentableName() {
    final GenomeQuery genomeQuery = new GenomeQuery("to1");
    assertEquals("chr1:0-10000000",
                 new SingleLocationBrowserModel(genomeQuery).presentableName());
    myBrowserModel.setChromosomeRange(new Range(10, 400).on(myChr1));
    assertEquals("chr1:10-400", myBrowserModel.presentableName());
  }
}
