package org.jetbrains.bio.rules.predicate;

/**
 * Basic recursive predicates visitor
 *
 * @author Oleg Shpynov
 * @since 12/1/14
 */
public class PredicateVisitor<T> {

  public void visitNot(final NotPredicate<T> predicate) {
    predicate.getOperand().accept(this);
  }

  public void visitParenthesisPredicate(final ParenthesesPredicate<T> predicate) {
    predicate.getOperand().accept(this);
  }

  public void visitAndPredicate(final AndPredicate<T> predicate) {
    predicate.getOperands().stream().forEach(p -> p.accept(this));
  }

  public void visitOrPredicate(final OrPredicate<T> predicate) {
    predicate.getOperands().stream().forEach(p -> p.accept(this));
  }

  public void visit(final Predicate<T> predicate) {
  }
}
