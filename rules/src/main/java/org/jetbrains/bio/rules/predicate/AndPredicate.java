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
public class AndPredicate<T> extends Predicate<T> {
  private final List<Predicate<T>> myOperands;

  /**
   * Invariant: all operands are defined.
   */
  AndPredicate(final List<Predicate<T>> operands) {
    myOperands = operands;
  }

  public static <T> Predicate<T> of(final List<Predicate<T>> operands) {
    Preconditions.checkArgument(operands.size() >= 1);
    if (!operands.stream().allMatch(Predicate::isDefined)) {
      return Predicate.undefinedPredicate();
    }
    final List<Predicate<T>> processedOperands = operands
        .stream()
        // Insert parenthesis within Or operands
        .map(o -> o instanceof OrPredicate ? ParenthesesPredicate.of(o) : o)
        // Remove unnecessary ()
        .map(o -> o instanceof ParenthesesPredicate && !(((ParenthesesPredicate) o).getOperand() instanceof OrPredicate)
                  ? ((ParenthesesPredicate<T>) o).getOperand()
                  : o)
        // Open underlying And predicates
        .flatMap(o -> o instanceof AndPredicate ? ((AndPredicate<T>) o).getOperands().stream() : Stream.of(o))
        // Filter TRUE operands
        .filter(o -> o != Predicate.<T>truePredicate())
        .sorted(NAMES_COMPARATOR)
        .collect(Collectors.toList());
    // Check FALSE inside operands
    if (processedOperands.parallelStream().anyMatch(o -> o == Predicate.<T>falsePredicate())) {
      return Predicate.falsePredicate();
    }
    return processedOperands.size() == 1 ? processedOperands.get(0) : new AndPredicate<>(processedOperands);
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
    return myOperands.parallelStream().allMatch(o -> o.test(item));
  }


  @Override
  public String getName() {
    return myOperands.stream().map(Predicate::getName).collect(Collectors.joining(' ' + PredicateParser.AND.toString() + ' '));
  }

  public List<Predicate<T>> getOperands() {
    return myOperands;
  }

  public int length() {
    return myOperands.size();
  }

  public Predicate<T> remove(final int index) {
    final List<Predicate<T>> operands = Lists.newArrayList(myOperands);
    operands.remove(index);
    return AndPredicate.of(operands);
  }

  public BitSet testUncached(final List<T> items) {
    final BitSet result = (BitSet) myOperands.get(0).test(items).clone();
    myOperands.stream().skip(0).forEach(o -> result.and(o.test(items)));
    return result;
  }

  @Override
  public void accept(final PredicateVisitor<T> visitor) {
    visitor.visitAndPredicate(this);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof AndPredicate)) return false;
    final AndPredicate<?> that = (AndPredicate<?>) o;
    return Objects.equals(myOperands, that.myOperands);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myOperands);
  }

  /**
   * Returns {@link AndPredicate} if all operands are defined and the
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
