package org.jetbrains.bio.query.parse

/**
 * @author Egor Gorbunov
 * @since 02.05.16
 */

interface TreeVisitor<T> {
    fun visit(node: BinaryArithmeticTrack): T
    fun visit(node: NumericTrack): T
    fun visit(node: AssignStatement): T
    fun visit(node: ShowTrackStatement): T
    fun visit(node: IfStatementTrack): T
    fun visit(node: NotPredicateTrack): T
    fun visit(node: OrPredicateTrack): T
    fun visit(node: AndPredicateTrack): T
    fun visit(node: FalsePredicateTrack): T
    fun visit(node: TruePredicateTrack): T
    fun visit(node: RelationPredicateTrack): T
    fun visit(node: BigBedFileTrack): T
    fun visit(node: NamedArithmeticTrack): T
    fun visit(node: NamedPredicateTrack): T
}