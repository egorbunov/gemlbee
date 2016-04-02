package org.jetbrains.bio.rules.predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.util.Lexeme;
import org.jetbrains.bio.util.Tokenizer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Oleg Shpynov
 * @since 30.1.15
 */
public class PredicateParser {

  /**
   * NOTE we operate with small rules, so that we don't really need Finite Automate based lexer
   */
  public static final Lexeme IMPL = new Lexeme("=>");

  static final Lexeme NOT = new Lexeme("NOT");
  static final Lexeme AND = new Lexeme("AND");
  static final Lexeme OR = new Lexeme("OR");
  private static final Lexeme LPAR = new Lexeme("(");
  private static final Lexeme RPAR = new Lexeme(")");
  static final Lexeme TRUE = new Lexeme("TRUE");
  static final Lexeme FALSE = new Lexeme("FALSE");

  public static final Set<Lexeme> KEYWORDS = ImmutableSet.of(NOT, AND, OR, LPAR, RPAR, IMPL, TRUE, FALSE);

  public static <T> Predicate<T> parse(String text, final Function<String, Predicate<T>> factory) {
    text = text.replaceAll(" ", "");
    final Tokenizer tokenizer = new Tokenizer(text, KEYWORDS);
    return parse(tokenizer, factory);
  }

  public static <T> Predicate<T> parse(final Tokenizer tokenizer, final Function<String, Predicate<T>> factory) {
    final Lexeme lexeme = tokenizer.fetch();
    if (lexeme == null) {
      return null;
    }
    return parseOr(tokenizer, factory);
  }

  private static <T> Predicate<T> parseOr(final Tokenizer tokenizer, final Function<String, Predicate<T>> factory) {
    final Predicate<T> operand1 = parseAnd(tokenizer, factory);
    Preconditions.checkNotNull(operand1, error(tokenizer));

    Lexeme lexeme = tokenizer.fetch();
    if (lexeme != OR) {
      return operand1;
    }
    final List<Predicate<T>> operands = Lists.newArrayList(operand1);
    while (lexeme == OR) {
      tokenizer.next();
      final Predicate<T> nextOperand = parseAnd(tokenizer, factory);
      Preconditions.checkNotNull(nextOperand, error(tokenizer));
      operands.add(nextOperand);
      lexeme = tokenizer.fetch();
    }

    return new OrPredicate<>(operands);
  }

  private static <T> Predicate<T> parseAnd(final Tokenizer tokenizer, final Function<String, Predicate<T>> factory) {
    final Predicate<T> operand1 = parseTerm(tokenizer, factory);
    Preconditions.checkNotNull(operand1, error(tokenizer));

    Lexeme lexeme = tokenizer.fetch();
    if (lexeme != AND) {
      return operand1;
    }
    final List<Predicate<T>> operands = Lists.newArrayList(operand1);
    while (lexeme == AND) {
      tokenizer.next();
      final Predicate<T> nextOperand = parseTerm(tokenizer, factory);
      Preconditions.checkNotNull(nextOperand, error(tokenizer));
      operands.add(nextOperand);
      lexeme = tokenizer.fetch();
    }

    return new AndPredicate<>(operands);
  }

  private static <T> Predicate<T> parseTerm(final Tokenizer tokenizer, final Function<String, Predicate<T>> factory) {
    final Lexeme lexeme = tokenizer.fetch();
    if (lexeme == null) {
      return null;
    }
    if (lexeme == TRUE) {
      tokenizer.next();
      return Predicate.truePredicate();
    }
    if (lexeme == FALSE) {
      tokenizer.next();
      return Predicate.falsePredicate();
    }
    if (lexeme == LPAR) {
      tokenizer.next();
      final Predicate<T> p = parse(tokenizer, factory);
      Preconditions.checkNotNull(p);
      Preconditions.checkState(RPAR == tokenizer.fetch());
      // Use direct constructor, because of method can perform complexity transformations
      return new ParenthesesPredicate<>(p);
    }
    if (lexeme == NOT) {
      tokenizer.next();
      final Predicate<T> p = parseTerm(tokenizer, factory);
      Preconditions.checkNotNull(p);
      return new NotPredicate<>(p);
    }

    if (!KEYWORDS.contains(lexeme)) {
      // Lookahead
      for (int l = tokenizer.matchLength();
           tokenizer.getTokenOffset() + tokenizer.matchLength() <= tokenizer.getText().length(); l++) {
        final Predicate<T> predicate =
            factory.apply(tokenizer.getText().substring(tokenizer.getTokenOffset(), tokenizer.getTokenOffset() + l));
        if (predicate != null) {
          tokenizer.next();
          return predicate;
        }
      }
    }
    throw new IllegalStateException(error(tokenizer));
  }

  @NotNull
  private static String error(final Tokenizer tokenizer) {
    return "Failed to parse predicate: " + tokenizer;
  }

  /**
   * Transforms collection of predicates to map for parsing.
   * NOTE it removes all the spaces, which is important for parsing
   */
  public static <T> Function<String, Predicate<T>> namesFunction(final Collection<Predicate<T>> predicates) {
    return predicates.parallelStream().collect(Collectors.toMap(p -> p.getName().replaceAll(" ", ""), p -> p))::get;
  }

}
