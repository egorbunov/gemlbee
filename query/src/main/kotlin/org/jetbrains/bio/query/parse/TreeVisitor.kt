package org.jetbrains.bio.query.parse

/**
 * @author Egor Gorbunov
 * @since 02.05.16
 */

interface TreeVisitor {
    fun visit(node: BinaryArithmeticTrack)
    fun visit(node: NumericTrack)
    fun visit(node: AssignStatement)
    fun visit(node: ShowTrackStatement)
    fun visit(node: IfStatementTrack)
    fun visit(node: NotPredicateTrack)
    fun visit(node: OrPredicateTrack)
    fun visit(node: AndPredicateTrack)
    fun visit(node: FalsePredicateTrack)
    fun visit(node: TruePredicateTrack)
    fun visit(node: RelationPredicateTrack)
    fun visit(node: BigBedFileTrack)

    fun visit(node: Statement) {
        throw UnsupportedOperationException()
    }

    fun visit(node: GeneratedTrack) {
        throw UnsupportedOperationException()
    }


}