package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.ChromosomeRange

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

abstract class ArithmeticTrack : GeneratedTrack() {
    abstract fun eval(range: ChromosomeRange, binsNum: Int): List<Double>
}

class NumericTrack(val value: Double) : ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other is NumericTrack && value == other.value) 0 else 1
    }

    override fun eval(range: ChromosomeRange, binsNum: Int): List<Double> {
        return (1..binsNum).map { value }
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

enum class ArithmeticOp(val str: String) {
    PLUS("+"), MINUS("-"), MUL("*"), DIV("/")
}

class BinaryArithmeticTrack(val op: ArithmeticOp,
                            val lhs: ArithmeticTrack,
                            val rhs: ArithmeticTrack) : ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is BinaryArithmeticTrack || op != other.op) 1 else {
            val res = lhs.compareTo(other.lhs)
            if (res != 0) res else rhs.compareTo(other.rhs)
        }
    }


    override fun eval(range: ChromosomeRange, binsNum: Int): List<Double> {
        return lhs.eval(range, binsNum).zip(rhs.eval(range, binsNum)).map {
            when (op) {
                ArithmeticOp.PLUS -> it.first + it.second
                ArithmeticOp.MINUS -> it.first - it.second
                ArithmeticOp.MUL -> it.first * it.second
                ArithmeticOp.DIV -> it.first / it.second
            }
        }
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

/**
 * if {pred} then {ifTrue} else {ifFalse} statement
 */
class IfStatementTrack(val pred: PredicateTrack,
                       val ifTrue: ArithmeticTrack,
                       val ifFalse: ArithmeticTrack) : ArithmeticTrack() {
    override fun eval(range: ChromosomeRange, binsNum: Int): List<Double> {
        throw UnsupportedOperationException()
//        return pred.eval(range, binsNum).zip(ifTrue.eval(range, binsNum).zip(ifFalse.eval(range, binsNum))).map {
//            if (it.first) it.second.first else it.second.second
//        }
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun compareTo(other: Statement): Int {
        return if (other is IfStatementTrack
                && pred.compareTo(other.pred) == 0
                && ifTrue.compareTo(other.ifTrue) == 0
                && ifFalse.compareTo(other.ifFalse) == 0) 0 else 1
    }
}

class NamedArithmeticTrack(val id: String,
                           val ref: ArithmeticTrack): ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is NamedArithmeticTrack || id != other.id) 1 else ref.compareTo(other.ref)
    }

    override fun eval(range: ChromosomeRange, binsNum: Int): List<Double> {
        return ref.eval(range, binsNum)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}
