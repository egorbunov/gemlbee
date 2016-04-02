package org.jetbrains.bio.rules.predicate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import kotlin.Pair;
import kotlin.Unit;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.ext.ExecutorExtensionsKt;

import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkState;

/**
 * Predicate, used in rules mining.
 * NOTE It doesn't extend interface {@link java.util.function.Predicate} because of clashes in methods #and, #or, etc
 *
 * @author Oleg Shpynov
 * @since 17/11/14
 */
public interface Predicate<T> {
  Logger LOG = Logger.getLogger(Predicate.class);

  Comparator<Predicate> NAMES_COMPARATOR = (p1, p2) -> p1.getName().compareTo(p2.getName());

  static <T> TruePredicate<T> truePredicate() {
    //noinspection unchecked
    return TruePredicate.INSTANCE;
  }

  static <T> FalsePredicate<T> falsePredicate() {
    //noinspection unchecked
    return FalsePredicate.INSTANCE;
  }

  static <T> UndefinedPredicate<T> undefinedPredicate() {
    //noinspection unchecked
    return UndefinedPredicate.INSTANCE;
  }

  boolean test(@NotNull T item);

  String getName();

  /**
   * Predicate is defined, in case when all the subpredicates are defined.
   * The only atomic undefined predicate considered to be {@link UndefinedPredicate}
   */
  default boolean isDefined() {
    return true;
  }

  /**
   * Used in {@link NotPredicate} creation, returns NotPredicate if true,
   * and {@link UndefinedPredicate} otherwise
   */
  default boolean canNegate() {
    return true;
  }

  /**
   * Return negotiation of predicate.
   * Important: can return {@link UndefinedPredicate}, use {@link #canNegate()} to check
   */
  default Predicate<T> negate() {
    checkState(canNegate(), "cannot negate");
    return NotPredicate.of(this);
  }

  default Predicate<T> and(final Predicate<T> other) {
    return AndPredicate.of(this, other);
  }

  default Predicate<T> or(final Predicate<T> other) {
    return OrPredicate.of(this, other);
  }

  Cache<Pair<Predicate, List>, BitSet> ourPredicatesCache = CacheBuilder.newBuilder().softValues().build();
  ExecutorService executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

  /**
   * Please use {@link #testUncached(List)} to implement custom behavior
   */
  default BitSet test(final List<T> items) {
    try {
      return ourPredicatesCache.get(new Pair<>(this, items), () -> testUncached(items));
    } catch (final ExecutionException e) {
      LOG.error(e);
      throw new RuntimeException(e);
    }
  }

  default BitSet testUncached(final List<T> items) {
    final BitSet result = new BitSet(items.size());
    final List<Callable<Unit>> tasks = Lists.newArrayListWithExpectedSize(items.size());
    for (int i = 0; i < items.size(); i++) {
      final int I = i;
      tasks.add(() -> {
        final boolean test = test(items.get(I));
        if (test) {
          synchronized (result) {
            result.set(I);
          }
        }
        return null;
      });
    }
    ExecutorExtensionsKt.awaitAll(executor, tasks);
    return result;
  }

  default void accept(final PredicateVisitor<T> visitor) {
    visitor.visit(this);
  }

  default Set<Predicate<T>> collectAtomics() {
    final Set<Predicate<T>> atomics = Sets.newHashSet();
    accept(new PredicateVisitor<T>() {
      @Override
      public void visit(final Predicate<T> predicate) {
        atomics.add(predicate);
      }
    });
    return atomics;
  }

  // Size of atomics inside + processing not, or, and
  default int complexity() {
    return 1;
  }
}
