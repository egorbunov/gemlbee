package org.jetbrains.bio.query.parse

import gnu.trove.list.array.TIntArrayList
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.containers.RangeList
import org.jetbrains.bio.genome.containers.minus
import org.jetbrains.bio.genome.containers.toRangeList
import org.jetbrains.bio.query.containers.SortedRangeList
import org.jetbrains.bio.query.containers.toSortedRangeList
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

abstract class PredicateTrack: GeneratedTrack() {
    /**
     * Important: I suppose that it returns sorted list of ranges without overlapping!
     */
    abstract fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList
}

class NotPredicateTrack(val rhs: PredicateTrack): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is NotPredicateTrack) 1 else rhs.compareTo(other.rhs)
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return (chRange.toRange() - rhs.eval(chRange, binsNum)).toSortedRangeList()
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

class OrPredicateTrack(val lhs: PredicateTrack, val rhs: PredicateTrack): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is OrPredicateTrack) 1 else {
            val res = lhs.compareTo(other.lhs)
            if (res != 0) res else rhs.compareTo(other.rhs)
        }
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return lhs.eval(chRange, binsNum) or rhs.eval(chRange, binsNum)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

class AndPredicateTrack(val lhs: PredicateTrack, val rhs: PredicateTrack): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is AndPredicateTrack) 1 else {
            val res = lhs.compareTo(other.lhs)
            if (res != 0) res else rhs.compareTo(other.rhs)
        }
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return lhs.eval(chRange, binsNum) and rhs.eval(chRange, binsNum)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

class TruePredicateTrack(): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other is TruePredicateTrack) 0 else 1
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return listOf(chRange.toRange()).toSortedRangeList()
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

class FalsePredicateTrack(): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other is FalsePredicateTrack) 0 else 1
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return emptyList<Range>().toSortedRangeList()
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

enum class RelationOp(val str: String) {
    LEQ("<="), GEQ(">="), NEQ("!="), EQ("=="), LE("<"), GE(">");
    companion object {
        fun fromString(str: String): RelationOp? {
            return RelationOp.values().find {
                it.str.equals(str)
            }
        }
    }
}

class RelationPredicateTrack(val op: RelationOp,
                             val lhs: ArithmeticTrack,
                             val rhs: ArithmeticTrack): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is RelationPredicateTrack || op != other.op) 1 else {
            val res = lhs.compareTo(other.lhs)
            if (res != 0) res else rhs.compareTo(other.rhs)
        }
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        val step = (chRange.length() / binsNum).toInt()
        val ranges = ArrayList<Range>()

        var s = chRange.startOffset
        var e = chRange.startOffset
        lhs.eval(chRange, binsNum).zip(rhs.eval(chRange, binsNum)).forEachIndexed { i, p ->
            val res = when (op) {
                RelationOp.EQ  -> p.first == p.second
                RelationOp.LE  -> p.first < p.second
                RelationOp.GE  -> p.first > p.second
                RelationOp.LEQ -> p.first <= p.second
                RelationOp.GEQ -> p.first >= p.second
                RelationOp.NEQ -> p.first != p.second
            }

            if (!res && s < e) ranges.add(Range(s, e))
            e += step
            if (i == binsNum && s < e) ranges.add(Range(s, e))
            if (!res) s = e
        }
        return ranges.toSortedRangeList()
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

class NamedPredicateTrack(val id: String,
                          val ref: PredicateTrack): PredicateTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other !is NamedPredicateTrack || id != other.id) 1 else ref.compareTo(other.ref)
    }

    override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
        return ref.eval(chRange, binsNum)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}