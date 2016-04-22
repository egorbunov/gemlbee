package org.jetbrains.bio.rules.predicate;

import org.jetbrains.annotations.NotNull;

/**
 * @author Oleg Shpynov
 * @since 18.2.15
 */
public class TruePredicate<T> extends Predicate<T> {
  /**
   * Use {@link Predicate#truePredicate()}
   */
  final static TruePredicate INSTANCE = new TruePredicate<>();

  private TruePredicate() {
  }

  @Override
  public boolean test(@NotNull final T item) {
    return true;
  }

  @Override
  public String getName() {
    return PredicateParser.TRUE.toString();
  }

  @Override
  public Predicate<T> negate() {
    return Predicate.falsePredicate();
  }

  @Override
  public String toString() {
    return getName();
  }
}
