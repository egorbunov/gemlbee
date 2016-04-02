package org.jetbrains.bio.rules.predicate;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("unchecked")
public class PredicateTest extends TestCase {

  private List<Predicate<Integer>> myPredicates;
  private Function<String, Predicate<Integer>> myPredicateFunction;

  public static List<Predicate<Integer>> namedRangePredicates(final int n, final int database) {
    return IntStream.range(0, n).mapToObj(i -> new Predicate<Integer>() {
      @Override
      public boolean test(@NotNull final Integer item) {
        return database * i / n <= item && item < database * (i + 1) / n;
      }

      @Override
      public String toString() {
        return String.valueOf(i);
      }

      @Override
      public String getName() {
        return String.valueOf(i);
      }
    }).collect(Collectors.toList());
  }

  public static List<Integer> rangeDataBase(final int size) {
    return IntStream.range(0, size).mapToObj(Integer::valueOf).collect(Collectors.toList());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPredicates = Lists.newArrayList(namedRangePredicates(10, 10000));
    myPredicates.add(Predicate.truePredicate());
    myPredicates.add(Predicate.falsePredicate());
    myPredicateFunction = PredicateParser.namesFunction(myPredicates);
  }

  public void testCollectAtomicFormulas() throws Exception {
    final List<Predicate<Integer>> predicates = namedRangePredicates(4, 10000);
    final Set<Predicate<Integer>> set =
        ParenthesesPredicate.of(OrPredicate.of(NotPredicate.of(predicates.get(0)),
                                               AndPredicate.of(predicates.get(1), predicates.get(2)))).collectAtomics();
    assertTrue(set.contains(predicates.get(0)));
    assertTrue(set.contains(predicates.get(1)));
    assertTrue(set.contains(predicates.get(2)));
    assertFalse(set.contains(predicates.get(3)));
  }

  private Predicate<Integer> p(final String text) {
    return PredicateParser.parse(text, myPredicateFunction);
  }

  public void testOr() throws Exception {
    assertEquals("0 OR 1", p("0").or(p("1")).toString());
    assertEquals("0 OR 1 OR 2", p("0 OR 1").or(p("2")).toString());
    assertEquals("0 OR 1 OR 2", p("1").or(p("0 OR 2")).toString());
    assertEquals("0 OR 1 OR 2 OR 3", p("0 OR 2").or(p("1 OR 3")).toString());
    assertEquals("0 OR 1 OR 2 AND 3", p("0 OR 1").or(p("2 AND 3")).toString());
    assertEquals("0 OR 1 OR 2", p("0 OR 1").or(p("(2)")).toString());
    assertEquals(p("1"), OrPredicate.of(p("1")));
    assertEquals(p("0"), OrPredicate.of(p("FALSE"), p("0")));
    assertEquals(p("TRUE"), OrPredicate.of(p("TRUE"), p("0")));
  }

  public void testAnd() throws Exception {
    assertEquals("0 AND 1", p("0").and(p("1")).toString());
    assertEquals("0 AND 1 AND 2", p("0 AND 1").and(p("2")).toString());
    assertEquals("0 AND 1 AND 2", p("1").and(p("0 AND 2")).toString());
    assertEquals("0 AND 1 AND 2 AND 3", p("0 AND 2").and(p("1 AND 3")).toString());
    assertEquals("(2 OR 3) AND 0 AND 1", p("0 AND 1").and(p("2 OR 3")).toString());
    assertEquals(p("1"), AndPredicate.of(p("1")));
    assertEquals(p("0"), AndPredicate.of(p("TRUE"), p("0")));
    assertEquals(p("FALSE"), AndPredicate.of(p("FALSE"), p("0")));
  }

  public void testRemoveOr() throws Exception {
    assertEquals("0 OR 2", ((OrPredicate) p("0 OR 1 OR 2")).remove(1).toString());
  }

  public void testRemoveOrSingle() throws Exception {
    assertEquals("0", ((OrPredicate) p("0 OR 1")).remove(1).getName());
  }

  public void testRemoveAnd() throws Exception {
    assertEquals("0 AND 2", ((AndPredicate) p("0 AND 1 AND 2")).remove(1).toString());
  }

  public void testRemoveAndSingle() throws Exception {
    assertEquals("0", ((AndPredicate) p("0 AND 1")).remove(1).toString());
  }

  public void testNotNegate() throws Exception {
    assertEquals("0", NotPredicate.of(myPredicates.get(0)).negate().getName());
    assertFalse(NotPredicate.of(Predicate.undefinedPredicate()).negate().isDefined());
  }

  public void testParentsNegate() throws Exception {
    assertEquals("0", p("(NOT 0)").negate().getName());
    assertEquals("0", p("NOT (0)").negate().getName());
    assertFalse(ParenthesesPredicate.of(Predicate.undefinedPredicate()).negate().isDefined());
  }


  public void testOrNegate() throws Exception {
    assertEquals("NOT (0 OR 1 OR 2)", p("0 OR 1 OR 2").negate().getName());
    assertFalse(OrPredicate.of(p("0"), Predicate.undefinedPredicate()).negate().isDefined());
  }

  public void testAndNegate() throws Exception {
    assertEquals("NOT (0 AND 1 AND 2)", p("0 AND 1 AND 2").negate().getName());
    assertFalse(AndPredicate.of(p("0"), Predicate.undefinedPredicate()).negate().isDefined());
  }

  public void testGetNameOrder() throws Exception {
    final List<Predicate<Integer>> predicates = namedRangePredicates(4, 10000);
    assertEquals("0 OR 1 OR 2", OrPredicate.of(predicates.get(1), predicates.get(0), predicates.get(2)).getName());
    assertEquals("0 AND 1 AND 2", AndPredicate.of(predicates.get(1), predicates.get(0), predicates.get(2)).getName());
  }

  public void testEquals() {
    final List<Predicate<Integer>> predicates = namedRangePredicates(3, 10000);

    assertEquals(Predicate.truePredicate(), Predicate.truePredicate());
    assertEquals(Predicate.falsePredicate(), Predicate.falsePredicate());
    assertEquals(Predicate.undefinedPredicate(), Predicate.undefinedPredicate());

    assertEquals(Predicate.truePredicate(), Predicate.falsePredicate().negate());
    assertEquals(Predicate.falsePredicate(), Predicate.truePredicate().negate());
    assertEquals(Predicate.undefinedPredicate(), Predicate.undefinedPredicate().negate());


    assertEquals(OrPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)),
                 OrPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)));
    // Check operands get sorted
    assertEquals(OrPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)),
                 OrPredicate.of(predicates.get(1), predicates.get(2), predicates.get(0)));
    assertNotSame(OrPredicate.of(predicates.get(0), predicates.get(1)),
                  OrPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)));

    assertEquals(AndPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)),
                 AndPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)));
    // Check operands get sorted
    assertEquals(AndPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)),
                 AndPredicate.of(predicates.get(1), predicates.get(0), predicates.get(2)));
    assertNotSame(AndPredicate.of(predicates.get(0), predicates.get(1)),
                  AndPredicate.of(predicates.get(0), predicates.get(1), predicates.get(2)));

    assertEquals(predicates.get(0).negate(), predicates.get(0).negate());
    assertEquals(ParenthesesPredicate.of(predicates.get(0)), ParenthesesPredicate.of(predicates.get(0)));
  }


  public void testUndefined() {
    final List<Predicate<Integer>> predicates = namedRangePredicates(4, 10000);
    assertFalse(Predicate.undefinedPredicate().isDefined());
    assertFalse(OrPredicate.of(predicates.get(1),
                               AndPredicate.of(predicates.get(0), Predicate.undefinedPredicate())).isDefined());
  }


  public void testComplexity() throws Exception {
    assertEquals(1, p("0").complexity());
    assertEquals(2, p("NOT 0").complexity());
    assertEquals(4, p("0 OR 1 OR 2").complexity());
    assertEquals(4, p("0 AND 1 AND 2").complexity());
    assertEquals(5, p("0 AND 1 OR 2").complexity());
    assertEquals(5, p("0 AND (1 OR 2)").complexity());
    assertTrue(p("NOT 0 AND NOT 1").complexity() > p("NOT (0 OR 1)").complexity());
    assertTrue(p("NOT 0 OR NOT 1").complexity() > p("NOT (0 AND 1)").complexity());
  }
}