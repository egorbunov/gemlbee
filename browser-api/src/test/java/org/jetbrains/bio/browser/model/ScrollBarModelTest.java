package org.jetbrains.bio.browser.model;

import junit.framework.TestCase;
import org.jetbrains.bio.browser.desktop.ScrollBarModel;
import org.jetbrains.bio.genome.Chromosome;
import org.jetbrains.bio.genome.Range;
import org.jetbrains.bio.genome.query.GenomeQuery;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Roman.Chernyatchik
 */
@SuppressWarnings("ConstantConditions")
public class ScrollBarModelTest extends TestCase {
  private SingleLocationBrowserModel myBrowserModel;
  private BoundedRangeModel myBoundedRangeModelAdaptor;
  private Chromosome myChr1;
  private Chromosome myChr2;
  private final AtomicReference<Boolean> myStateChangedMarker = new AtomicReference<>();
  private ChangeListener myChangeListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myChr1 = Chromosome.get("to1", "chr1");
    myChr2 = Chromosome.get("to1", "chr2");
    final GenomeQuery genomeQuery = new GenomeQuery("to1");
    myBrowserModel = new SingleLocationBrowserModel(genomeQuery);
    myBrowserModel.setRange(new Range(0, 10));
    myBoundedRangeModelAdaptor = new ScrollBarModel(myBrowserModel);
    myChangeListener = e -> myStateChangedMarker.set(true);
    myBoundedRangeModelAdaptor.addChangeListener(myChangeListener);
    myStateChangedMarker.set(false);
  }

  public void testGetMinimumInitial() throws Exception {
    assertEquals(0, myBoundedRangeModelAdaptor.getMinimum());
  }

  public void testGetMinimumOnLocationChanged() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(5, 100).on(myChr2));
    assertEquals(0, myBoundedRangeModelAdaptor.getMinimum());
  }

  public void testGetMaximumInitial() throws Exception {
    assertEquals(myChr1.getLength(), myBoundedRangeModelAdaptor.getMaximum());
  }

  public void testGetMaximumOnLocationChanged() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(5, 100).on(myChr2));
    assertEquals(myChr2.getLength(), myBoundedRangeModelAdaptor.getMaximum());
  }

  public void testGetValueInitial() throws Exception {
    assertEquals(0, myBoundedRangeModelAdaptor.getValue());
  }

  public void testGetValueRangeChanged() throws Exception {
    myBrowserModel.setRange(new Range(65, 110));
    assertEquals(65, myBoundedRangeModelAdaptor.getValue());
  }

  public void testGetValueChrChanged() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(65, 110).on(myChr2));
    assertEquals(65, myBoundedRangeModelAdaptor.getValue());
  }

  public void testGetValueLocChanged() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(110, 210).on(myChr2));
    assertEquals(110, myBoundedRangeModelAdaptor.getValue());
  }

  public void testSetValue() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(50);
    assertEquals(new Range(50, 150), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueAfterRange() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(150);
    assertEquals(new Range(150, 250), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueBeforeRange() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(0);
    assertEquals(new Range(0, 100), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueNegative() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(-1);
    assertEquals(new Range(0, 100), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueOutOfChr() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(myChr1.getLength());
    assertEquals(new Range(myChr1.getLength() - 100, myChr1.getLength()), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueIntersectingChrEnd1() throws Exception {
    myBrowserModel.setRange(new Range(5, 5 + 100));

    myBoundedRangeModelAdaptor.setValue(myChr1.getLength() - 1);
    assertEquals(new Range(myChr1.getLength() - 100, myChr1.getLength()), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueIntersectingChrEnd2() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(myChr1.getLength() - 100);
    assertEquals(new Range(myChr1.getLength() - 100, myChr1.getLength()), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetValueNearChrEnd() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setValue(myChr1.getLength() - 101);
    assertEquals(new Range(myChr1.getLength() - 101, myChr1.getLength() - 1),
                 myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testGetValueIsAdjusting() throws Exception {
    assertFalse(myBoundedRangeModelAdaptor.getValueIsAdjusting());
  }

  public void testGetExtentInitial() throws Exception {
    assertEquals(10, myBoundedRangeModelAdaptor.getExtent());
  }

  public void testGetExtentRangeChanged() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));
    assertEquals(100, myBoundedRangeModelAdaptor.getExtent());
  }

  public void testGetExtentChrChanged() throws Exception {
    myBrowserModel.setChromosomeRange(myChr2.getRange().on(myChr2));
    assertEquals(myChr2.getLength(), myBoundedRangeModelAdaptor.getExtent());
  }

  public void testGetExtentLocChanged() throws Exception {
    myBrowserModel.setChromosomeRange(new Range(60, 110).on(myChr2));
    assertEquals(50, myBoundedRangeModelAdaptor.getExtent());
  }

  public void testSetExtent() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(50);
    assertEquals(new Range(5, 55), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetLargerExtent() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(200);
    assertEquals(new Range(5, 205), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentIntersectingChrEnd1() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(myChr1.getLength());
    assertEquals(new Range(5, myChr1.getLength()), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentIntersectingChrEnd2() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(myChr1.getLength() - 1);
    assertEquals(new Range(5, myChr1.getLength()), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentNearChrEnd() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(myChr1.getLength() - 100);
    assertEquals(new Range(5, myChr1.getLength() - 95), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentNegative() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(-1);
    // FIXME(lebedev): this range is empty [5, 5).
    assertEquals(new Range(5, 5), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentZero() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    myBoundedRangeModelAdaptor.setExtent(0);
    assertEquals(new Range(5, 5), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetExtentLess10() throws Exception {
    myBrowserModel.setRange(new Range(5, 105));

    // length >= 10 limitation - only in user zoom events handlers
    myBoundedRangeModelAdaptor.setExtent(9);
    assertEquals(new Range(5, 14), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetRangeProperties() throws Exception {
    myBoundedRangeModelAdaptor.setRangeProperties(50, 1000, 10, 2000, true);
    assertEquals(0, myBoundedRangeModelAdaptor.getMinimum());
    assertEquals(myChr1.getLength(), myBoundedRangeModelAdaptor.getMaximum());
    assertFalse(myBoundedRangeModelAdaptor.getValueIsAdjusting());
    assertEquals(new Range(50, 1050), myBrowserModel.getRange());
    assertEquals(myChr1, myBrowserModel.getChromosome());
  }

  public void testSetRangePropertiesLess10() throws Exception {
    // length >= 10 limitation - only in user zoom events handlers
    myBoundedRangeModelAdaptor.setValue(6);
    myBoundedRangeModelAdaptor.setExtent(100);
    myBoundedRangeModelAdaptor.setRangeProperties(5, 9, 1, 400, true);
    assertEquals(0, myBoundedRangeModelAdaptor.getMinimum());
    assertEquals(myChr1.getLength(), myBoundedRangeModelAdaptor.getMaximum());
    assertFalse(myBoundedRangeModelAdaptor.getValueIsAdjusting());
    assertEquals(new Range(5, 14), myBrowserModel.getRange());
  }
         //----------

  public void testAddChangeListenerChangedAdaptorValue() throws Exception {
    // prerequisite
    myBoundedRangeModelAdaptor.setValue(2);
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.setValue(1);
    assertTrue(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedAdaptorSameValueTwice() throws Exception {
    // prerequisite
    myBoundedRangeModelAdaptor.setValue(2);
    myStateChangedMarker.set(false);

    // - same value twice
    myBoundedRangeModelAdaptor.setValue(2);
    assertFalse(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedAdaptorExtent() throws Exception {
    // prerequisite
    myBoundedRangeModelAdaptor.setExtent(200);
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.setExtent(100);
    assertTrue(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedAdaptorSameExtentTwice() throws Exception {
    // prerequisite
    myBoundedRangeModelAdaptor.setExtent(200);
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.setExtent(200);
    assertFalse(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedAdaptorProperties() throws Exception {
    // prerequisite
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.setRangeProperties(5, 100, 1, 200, true);
    assertTrue(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedModelChr() throws Exception {
    final Range range = myBrowserModel.getRange();
    // prerequisite
    myBrowserModel.setChromosomeRange(range.on(myChr2));
    myStateChangedMarker.set(false);

    myBrowserModel.setChromosomeRange(range.on(myChr1));
    assertTrue(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedModelSameChrTwice() throws Exception {
    final Range range = myBrowserModel.getRange();
    // prerequisite
    myBrowserModel.setChromosomeRange(range.on(myChr2));
    myStateChangedMarker.set(false);

    myBrowserModel.setChromosomeRange(range.on(myChr2));
    assertFalse(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedModelRange() throws Exception {
    // prerequisite
    myBrowserModel.setRange(new Range(100, 150));
    myStateChangedMarker.set(false);

    myBrowserModel.setRange(new Range(100, 200));
    assertTrue(myStateChangedMarker.get());
  }

  public void testAddChangeListenerChangedModelSameRangeTwice() throws Exception {
    // prerequisite
    myBrowserModel.setRange(new Range(100, 150));
    myStateChangedMarker.set(false);

    myBrowserModel.setRange(new Range(100, 150));
    assertFalse(myStateChangedMarker.get());
  }

  public void testRemoveChangeListenerAdapterChanged() throws Exception {
    // prerequisite
    myBoundedRangeModelAdaptor.setValue(10);
    myBoundedRangeModelAdaptor.setExtent(200);
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.removeChangeListener(myChangeListener);
    myBoundedRangeModelAdaptor.setValue(20);
    myBoundedRangeModelAdaptor.setExtent(300);
    assertFalse(myStateChangedMarker.get());
  }

  public void testRemoveChangeListenerModelChanged() throws Exception {
    // prerequisite
    myBrowserModel.setChromosomeRange(new Range(1, 2).on(myChr1));
    myStateChangedMarker.set(false);

    myBoundedRangeModelAdaptor.removeChangeListener(myChangeListener);
    myBrowserModel.setChromosomeRange(new Range(100, 200).on(myChr2));
    assertFalse(myStateChangedMarker.get());
  }
}
