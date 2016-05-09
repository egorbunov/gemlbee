package org.jetbrains.bio.query.parse

/**
 * @author Egor Gorbunov
 * @since 02.05.16
 */

class ToStringVisitor: TreeVisitor<Unit> {
    var sb = StringBuilder()

    fun getString(): String {
        return sb.toString()
    }

    private fun doesLhsNeedParen(parent: BinaryArithmeticTrack, lhs: ArithmeticTrack): Boolean {
        if (lhs is IfStatementTrack) {
            return true
        }
        if (lhs !is BinaryArithmeticTrack) {
            return false
        }
        if (lhs.op in setOf(ArithmeticOp.MINUS, ArithmeticOp.PLUS)
                && parent.op in setOf(ArithmeticOp.MUL, ArithmeticOp.DIV)) {
            return true
        }
        return false
    }

    private fun doesRhsNeedParen(parent: BinaryArithmeticTrack, rhs: ArithmeticTrack): Boolean {
        if (rhs is IfStatementTrack) {
            return true
        }
        if (rhs !is BinaryArithmeticTrack) {
            return false
        }
        return doesLhsNeedParen(parent, rhs)
                || (rhs.op == ArithmeticOp.PLUS && parent.op == ArithmeticOp.MINUS)
    }

    override fun visit(node: BinaryArithmeticTrack) {
        var needParen = doesLhsNeedParen(node, node.lhs)
        if (needParen) sb.append("(")
        node.lhs.accept(this)
        if (needParen) sb.append(")")
        sb.append(node.op.str)

        needParen = doesRhsNeedParen(node, node.rhs)
        if (needParen) sb.append("(")
        node.rhs.accept(this)
        if (needParen) sb.append(")")
    }

    override fun visit(node: NumericTrack) {
        sb.append(node.value)
    }

    override fun visit(node: AssignStatement) {
        sb.append(node.id)
        sb.append(" := ")
        node.track.accept(this)
    }

    override fun visit(node: ShowTrackStatement) {
        sb.append("show ")
        node.track.accept(this)
    }

    override fun visit(node: IfStatementTrack) {
        sb.append("if ")
        node.cond.accept(this)
        sb.append(" then ")
        node.ifTrue.accept(this)
        sb.append(" else ")
        node.ifFalse.accept(this)
    }

    override fun visit(node: NotPredicateTrack) {
        sb.append("NOT (")
        node.rhs.accept(this)
        sb.append(")")
    }

    override fun visit(node: AndPredicateTrack) {
        var np = node.lhs is OrPredicateTrack
        if (np) sb.append("(")
        node.lhs.accept(this)
        if (np) sb.append(")")

        sb.append(" AND ")

        np = node.rhs is OrPredicateTrack
        if (np) sb.append("(")
        node.rhs.accept(this)
        if (np) sb.append(")")
    }

    override fun visit(node: OrPredicateTrack) {
        node.lhs.accept(this)
        sb.append(" OR ")
        node.rhs.accept(this)
    }

    override fun visit(node: RelationPredicateTrack) {
        if (node.lhs is IfStatementTrack) sb.append("(")
        node.lhs.accept(this)
        if (node.lhs is IfStatementTrack) sb.append(")")

        sb.append(" ${node.op.str} ")

        if (node.rhs is IfStatementTrack) sb.append("(")
        node.rhs.accept(this)
        if (node.rhs is IfStatementTrack) sb.append(")")
    }

    override fun visit(node: FalsePredicateTrack) {
        sb.append("false")
    }

    override fun visit(node: TruePredicateTrack) {
        sb.append("true")
    }

    override fun visit(node: BigBedFileTrack) {
        sb.append(node.id)
    }

    override fun visit(node: NamedArithmeticTrack) {
        sb.append(node.id)
    }
    override fun visit(node: NamedPredicateTrack) {
        sb.append(node.id)
    }
}
