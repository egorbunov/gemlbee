package org.jetbrains.bio.rules.predicate;

import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
* @author Oleg Shpynov
* @since 16.12.14
*/
public class ParenthesesPredicate<T> extends Predicate<T> {
  private final Predicate<T> myPredicate;

  ParenthesesPredicate(final Predicate<T> predicate) {
    myPredicate = predicate;
  }

  public boolean canNegate() {
    return myPredicate.canNegate();
  }

  @Override
  public Predicate<T> negate() {
    checkState(canNegate(), "cannot negate");
    return ParenthesesPredicate.of(myPredicate.negate());
  }

  @Override
  public boolean test(@NotNull final T item) {
    return myPredicate.test(item);
  }

  @Override
  public BitSet testUncached(final List<T> items) {
    return myPredicate.test(items);
  }

  public static <T> Predicate<T> of(final Predicate<T> predicate) {
    if (!predicate.isDefined()) {
      return Predicate.undefinedPredicate();
    }
    // Optimize complexity
    return predicate instanceof AndPredicate || predicate instanceof OrPredicate ? new ParenthesesPredicate<>(predicate) : predicate;
  }

  @Override
  public String getName() {
    return '(' + myPredicate.getName() + ')';
  }

  @Override
  public String toString() {
    return getName();
  }

  public void accept(final PredicateVisitor<T> visitor) {
    visitor.visitParenthesisPredicate(this);
  }

  public Predicate<T> getOperand() {
    return myPredicate;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ParenthesesPredicate)) return false;
    final ParenthesesPredicate<?> that = (ParenthesesPredicate<?>) o;
    return Objects.equals(myPredicate, that.myPredicate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myPredicate);
  }

  @Override
  public int complexity() {
    return myPredicate.complexity();
  }
}
