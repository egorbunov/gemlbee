package org.jetbrains.bio.rules.predicate;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ConstantConditions")
public class PredicateParserTest extends TestCase {

  private static Predicate<Object> testPredicate(final String name) {
    return new Predicate<Object>() {
      @Override
      public boolean test(@NotNull final Object item) {
        throw new UnsupportedOperationException("#test");
      }

      @Override
      public String getName() {
        return name;
      }
    };
  }

  public void testNamesFunction() {
    assertEquals("PREDICATE",
                 PredicateParser.parse("PREDICATE",
                                       PredicateParser.namesFunction(ImmutableList.of(testPredicate("PREDICATE")))).getName());
  }

  public void testLookahead() {
    assertEquals("[0;20)",
                 PredicateParser.parse("[0;20)",
                                       PredicateParser.namesFunction(ImmutableList.of(testPredicate("[0;20)")))).getName());
  }

  public void testParseNotRule() throws Exception {
    assertEquals("NOT p1", PredicateParser.parse("NOT p1", PredicateParserTest::testPredicate).getName());
  }

  public void testParseParenthesisRule() throws Exception {
    assertEquals("(p1)", PredicateParser.parse("(p1)", PredicateParserTest::testPredicate).getName());
  }

  public void testParseRuleAnd() throws Exception {
    assertEquals("p1 AND p2", PredicateParser.parse("p1 AND p2",
                                                    PredicateParserTest::testPredicate).getName());
  }

  public void testParseRuleAnd3() throws Exception {
    assertEquals("p1 AND p2 AND NOT p3",
                 PredicateParser.parse("p1 AND p2 AND NOT p3",
                                       PredicateParserTest::testPredicate).getName());
  }

  public void testParseRuleOr() throws Exception {
    assertEquals("p1 OR p2", PredicateParser.parse("p1 OR p2",
                                                   PredicateParserTest::testPredicate).getName());
  }

  public void testParseRuleOr3() throws Exception {
    assertEquals("p1 OR p2 OR NOT p3",
                 PredicateParser.parse("p1 OR p2 OR NOT p3",
                                       PredicateParserTest::testPredicate).getName());
  }

  public void testParseComplex() throws Exception {
    assertEquals("NOT ICP AND NOT LCP AND NOT exons_except_first_H3K36me3",
                 PredicateParser.parse("NOT ICP AND NOT LCP AND NOT exons_except_first_H3K36me3",
                                       PredicateParserTest::testPredicate).getName());
  }

  public void testParseComplexOr() throws Exception {
    assertEquals("Insulator AND exons_except_first_H3K36me3",
                 PredicateParser.parse("Insulator AND exons_except_first_H3K36me3",
                                       PredicateParserTest::testPredicate).getName());
  }

  public void testParseTrueFalse() throws Exception {
    assertTrue(PredicateParser.parse("TRUE", p -> null) instanceof TruePredicate);
    assertTrue(PredicateParser.parse("FALSE", p -> null) instanceof FalsePredicate);
  }

  public void testParseNoPredicate() throws Exception {
    assertEquals("NO meth",
                 PredicateParser.parse("NO meth",
                                       PredicateParser.namesFunction(ImmutableList.of(testPredicate("NO meth")))).getName());
  }


}