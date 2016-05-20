package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.ChromosomeRange
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

abstract class ArithmeticTrack : GeneratedTrack() {
    abstract fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double>
}

class NumericTrack(val value: Double) : ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other is NumericTrack && value == other.value) 0 else 1
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double> {
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


    override fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double> {
        return lhs.eval(chRange, binsNum).zip(rhs.eval(chRange, binsNum)).map {
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
 * if {cond} then {ifTrue} else {ifFalse} statement
 */
class IfStatementTrack(val cond: PredicateTrack,
                       val ifTrue: ArithmeticTrack,
                       val ifFalse: ArithmeticTrack) : ArithmeticTrack() {
    override fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double> {
        val step = chRange.length() / binsNum
        val rangeList = cond.eval(chRange, binsNum)
        val ans = ArrayList(ifFalse.eval(chRange, binsNum))
        val trueRes = ifTrue.eval(chRange, binsNum)
        rangeList.map { Pair(((it.startOffset - chRange.startOffset) / step).toInt(),
                ((it.endOffset - chRange.startOffset) / step).toInt()) }.forEach {

            for (i in it.first..it.second - 1) {
                ans[i] = trueRes[i]
            }
        }
        return ans;
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun compareTo(other: Statement): Int {
        return if (other is IfStatementTrack
                && cond.compareTo(other.cond) == 0
                && ifTrue.compareTo(other.ifTrue) == 0
                && ifFalse.compareTo(other.ifFalse) == 0) 0 else 1
    }
}

class NamedArithmeticTrack(val id: String,
                           val ref: ArithmeticTrack): ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is NamedArithmeticTrack || id != other.id) 1 else ref.compareTo(other.ref)
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double> {
        return ref.eval(chRange, binsNum)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}
