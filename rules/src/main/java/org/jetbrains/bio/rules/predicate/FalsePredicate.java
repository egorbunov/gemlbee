package org.jetbrains.bio.rules.predicate;

import org.jetbrains.annotations.NotNull;

/**
* @author Oleg Shpynov
* @since 18.2.15
*/
public class FalsePredicate<T> implements Predicate<T> {

  /**
   * Use {@link Predicate#falsePredicate()}
   */
  final static FalsePredicate INSTANCE = new FalsePredicate<>();

  private FalsePredicate() {
  }

  @Override
  public boolean test(@NotNull final T item) {
    return false;
  }

  @Override
  public String getName() {
    return PredicateParser.FALSE.toString();
  }

  @Override
  public Predicate<T> negate() {
    return Predicate.truePredicate();
  }

  @Override
  public String toString() {
    return getName();
  }
}
