package org.jetbrains.bio.rules.predicate;

import org.jetbrains.annotations.NotNull;

/**
 * Predicate to mark undefined behavior in case logical system is Constructivism
 * http://en.wikipedia.org/wiki/Constructivism_(mathematics)
 * <p>
 * I.E. A or not A is not TRUE
 *
 * @author Oleg Shpynov
 * @since 27.4.15
 */
public class UndefinedPredicate<T> implements Predicate<T> {

  /**
   * Use {@link Predicate#undefinedPredicate()}
   */
  final static UndefinedPredicate INSTANCE = new UndefinedPredicate<>();

  private UndefinedPredicate() {
  }

  @Override
  public String getName() {
    return "Undefined";
  }

  @Override
  public boolean test(@NotNull final T item) {
    throw new IllegalStateException("#result is undefined");
  }

  public boolean isDefined() {
    return false;
  }

  public boolean canNegate() {
    return false;
  }

  @Override
  public Predicate<T> negate() {
    return this;
  }

  @Override
  public String toString() {
    return getName();
  }

}
