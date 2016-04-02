package org.jetbrains.bio.rules.predicate;

import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * @author Oleg Shpynov
 * @since 20.11.14
 */
public class NotPredicate<T> implements Predicate<T> {
  private final Predicate<T> myPredicate;

  /**
   * Invariant: predicate is defined and can be negated.
   */
  protected NotPredicate(final Predicate<T> predicate) {
    myPredicate = predicate;
  }

  /**
   * Use {@link Predicate#negate()} in general case, because it can have specific implementation,
   * i.e. CODING_GENE.negate -> NON_CODING_GENE and vise versa
   * @return NotPredicate in case when predicate is defined,
   * P if predicate is NOT(P)
   * Undefined in case predicate is undefined
   */
  protected static <T> Predicate<T> of(final Predicate<T> predicate) {
    if (predicate instanceof NotPredicate) {
      return ((NotPredicate<T>) predicate).getOperand();
    }

    return predicate.canNegate() ? new NotPredicate<>(predicate)
                                 : Predicate.undefinedPredicate();
  }

  @NotNull
  @Override
  public Predicate<T> negate() {
    return myPredicate instanceof ParenthesesPredicate
           ? ((ParenthesesPredicate) myPredicate).getOperand()
           : myPredicate;
  }

  @Override
  public boolean test(@NotNull final T item) {
    return !myPredicate.test(item);
  }

  @Override
  public String getName() {
    return PredicateParser.NOT.toString() + ' ' + myPredicate.getName();
  }

  public Predicate<T> getOperand() {
    return myPredicate;
  }

  @Override
  public BitSet testUncached(final List<T> items) {
    final BitSet result = (BitSet) myPredicate.test(items).clone();
    result.flip(0, items.size());
    return result;
  }

  public void accept(final PredicateVisitor<T> visitor) {
    visitor.visitNot(this);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof NotPredicate)) return false;
    final NotPredicate<?> that = (NotPredicate<?>) o;
    return Objects.equals(myPredicate, that.myPredicate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myPredicate);
  }

  @Override
  public int complexity() {
    return 1 + myPredicate.complexity();
  }
}
