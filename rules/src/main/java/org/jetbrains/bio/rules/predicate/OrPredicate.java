package org.jetbrains.bio.rules.predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Oleg Shpynov
 * @since 20/11/14
 */
public class OrPredicate<T> implements Predicate<T> {
  private final List<Predicate<T>> myOperands;

  /**
   * Invariant: all operands are defined.
   */
  protected OrPredicate(final List<Predicate<T>> operands) {
    myOperands = operands;
  }

  public static <T> Predicate<T> of(final List<Predicate<T>> operands) {
    Preconditions.checkArgument(operands.size() >= 1);
    if (!operands.stream().allMatch(Predicate::isDefined)) {
      return Predicate.undefinedPredicate();
    }
    final List<Predicate<T>> processedOperands = operands
        .stream()
        // Remove unnecessary ()
        .map(o -> o instanceof ParenthesesPredicate ? ((ParenthesesPredicate<T>) o).getOperand() : o)
        // Open underlying Or operands
        .flatMap(o -> o instanceof OrPredicate ? ((OrPredicate<T>) o).getOperands().stream() : Stream.of(o))
        // Filter FALSE operands
        .filter(o -> o != Predicate.<T>falsePredicate())
        .sorted(NAMES_COMPARATOR)
        .collect(Collectors.toList());
    if (processedOperands.parallelStream().anyMatch(o -> o == Predicate.<T>truePredicate())) {
      return Predicate.truePredicate();
    }
    return processedOperands.size() == 1 ? processedOperands.get(0) : new OrPredicate<>(processedOperands);
  }

  public boolean canNegate() {
    return myOperands.parallelStream().allMatch(Predicate::canNegate);
  }

  @NotNull
  @Override
  public Predicate<T> negate() {
    return NotPredicate.of(ParenthesesPredicate.of(this));
  }

  @Override
  public boolean test(@NotNull final T item) {
    return myOperands.parallelStream().anyMatch(o -> o.test(item));
  }

  @Override
  public String getName() {
    return myOperands.stream().map(Predicate::getName)
        .collect(Collectors.joining(' ' + PredicateParser.OR.toString() + ' '));
  }

  public int length() {
    return myOperands.size();
  }

  public Predicate<T> remove(final int index) {
    final List<Predicate<T>> operands = Lists.newArrayList(myOperands);
    operands.remove(index);
    return OrPredicate.of(operands);
  }

  public List<Predicate<T>> getOperands() {
    return myOperands;
  }

  @Override
  public BitSet testUncached(final List<T> items) {
    final BitSet result = (BitSet) myOperands.get(0).test(items).clone();
    myOperands.stream().skip(0).forEach(o -> result.or(o.test(items)));
    return result;
  }

  public void accept(final PredicateVisitor<T> visitor) {
    visitor.visitOrPredicate(this);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof OrPredicate)) return false;
    final OrPredicate<?> that = (OrPredicate<?>) o;
    return Objects.equals(myOperands, that.myOperands);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myOperands);
  }

  /**
   * Returns {@link OrPredicate} if all operands are defined and the
   * number of operands >= 2. If there's only one defined operand,
   * returns it unchanged. Otherwise returns {@link UndefinedPredicate}.
   */
  @SafeVarargs
  public static <T> Predicate<T> of(final Predicate<T>... operands) {
    return of(Arrays.asList(operands));
  }

  @Override
  public int complexity() {
    return 1 + myOperands.stream().mapToInt(Predicate::complexity).sum();
  }
}
