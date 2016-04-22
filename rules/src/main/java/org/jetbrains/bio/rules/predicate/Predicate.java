package org.jetbrains.bio.rules.predicate;

import com.google.common.collect.Sets;

import java.lang.ref.WeakReference;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * Predicate, used in rules mining.
 * NOTE It doesn't extend interface {@link java.util.function.Predicate} because of clashes in methods #and, #or, etc
 *
 * @author Oleg Shpynov
 * @since 17/11/14
 */
public abstract class Predicate<T> {

  public static final Comparator<Predicate> NAMES_COMPARATOR = (p1, p2) -> p1.getName().compareTo(p2.getName());

  public static <T> TruePredicate<T> truePredicate() {
    //noinspection unchecked
    return TruePredicate.INSTANCE;
  }

  public static <T> FalsePredicate<T> falsePredicate() {
    //noinspection unchecked
    return FalsePredicate.INSTANCE;
  }

  public static <T> UndefinedPredicate<T> undefinedPredicate() {
    //noinspection unchecked
    return UndefinedPredicate.INSTANCE;
  }

  public abstract boolean test(T item);

  public abstract String getName();

  /**
   * Predicate is defined, in case when all the subpredicates are defined.
   * The only atomic undefined predicate considered to be {@link UndefinedPredicate}
   */
  public boolean isDefined() {
    return true;
  }

  /**
   * Used in {@link NotPredicate} creation, returns NotPredicate if true,
   * and {@link UndefinedPredicate} otherwise
   */
  public boolean canNegate() {
    return true;
  }

  /**
   * Return negotiation of predicate.
   * Important: can return {@link UndefinedPredicate}, use {@link #canNegate()} to check
   */
  public Predicate<T> negate() {
    checkState(canNegate(), "cannot negate");
    return NotPredicate.of(this);
  }

  public Predicate<T> and(final Predicate<T> other) {
    return AndPredicate.of(this, other);
  }

  public Predicate<T> or(final Predicate<T> other) {
    return OrPredicate.of(this, other);
  }

  private volatile List<T> cachedDataBase = null;
  private volatile WeakReference<BitSet> cache = new WeakReference<>(null);

  /**
   * Please use {@link #testUncached(List)} to implement custom behavior.
   * NOTE: we don't use Cache here, because items is the same object, so that cache miss should be quite rare.
   */
  public synchronized BitSet test(final List<T> items) {
    BitSet result;
    if (cachedDataBase != items) {
      result = null;
    } else {
      result = cache.get();
    }
    if (result == null) {
      cachedDataBase = items;
      result = testUncached(items);
      cache = new WeakReference<>(result);
    }
    return result;
  }

  protected BitSet testUncached(final List<T> items) {
    final BitSet result = new BitSet(items.size());
    for (int i = 0; i < items.size(); i++) {
      result.set(i, test(items.get(i)));
    }
    return result;
  }

  public void accept(final PredicateVisitor<T> visitor) {
    visitor.visit(this);
  }

  public Set<Predicate<T>> collectAtomics() {
    final Set<Predicate<T>> atomics = Sets.newHashSet();
    accept(new PredicateVisitor<T>() {
      @Override
      public void visit(final Predicate<T> predicate) {
        atomics.add(predicate);
      }
    });
    return atomics;
  }

  public int complexity() {
    return 1;
  }
}
