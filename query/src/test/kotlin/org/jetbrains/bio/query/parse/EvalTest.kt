package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.query.containers.SortedRangeList
import org.jetbrains.bio.query.containers.toSortedRangeList
import org.junit.Test
import kotlin.test.assertEquals

/**
 * @author Egor Gorbunov
 * @since 09.05.16
 */

class EvalTest {
    companion object {
        val chromosome = Chromosome.invoke("to1", "chr1")
    }

    class TestPredicateTrack(val supplier: (ChromosomeRange, Int) -> List<Range>): PredicateTrack() {
        override fun eval(chRange: ChromosomeRange, binsNum: Int): SortedRangeList {
            return supplier(chRange, binsNum).toSortedRangeList()
        }
        override fun <T> accept(visitor: TreeVisitor<T>): T {
            throw UnsupportedOperationException()
        }
        override fun compareTo(other: Statement): Int {
            throw UnsupportedOperationException()
        }
    }

    class TestArithmeticTrack(val supplier: (ChromosomeRange, Int) -> List<Double>): ArithmeticTrack() {
        override fun eval(chRange: ChromosomeRange, binsNum: Int): List<Double> {
            return supplier(chRange, binsNum)
        }
        override fun <T> accept(visitor: TreeVisitor<T>): T {
            throw UnsupportedOperationException()
        }
        override fun compareTo(other: Statement): Int {
            throw UnsupportedOperationException()
        }

    }

    @Test fun testTruePredicateEval() {
        val track = LangParser("true", emptyMap(), emptyMap()).parse() as TruePredicateTrack
        val ranges = track.eval(ChromosomeRange(10, 100, chromosome), 10)
        assertEquals(1, ranges.size())
        val rng = ranges.asIterable().first()
        assertEquals(Range(10, 100), rng)
    }

    @Test fun testFalsePredicateEval() {
        val track = LangParser("false", emptyMap(), emptyMap()).parse() as FalsePredicateTrack
        val ranges = track.eval(ChromosomeRange(10, 100, chromosome), 10)
        assertEquals(0, ranges.size())
    }

    @Test fun testAndPredicate() {
        val predicateA = TestPredicateTrack { a, b -> listOf(Range(10, 20), Range(100, 200), Range(500, 800)) }
        val predicateB = TestPredicateTrack { a, b ->
            listOf(Range(15, 40), Range(50, 80), Range(90, 150), Range(650, 900))
        }
        val andPredicate = AndPredicateTrack(predicateA, predicateB)
        val ranges = andPredicate.eval(ChromosomeRange(0, 1000, chromosome), 0)

        arrayOf(Range(15, 20), Range(100, 150), Range(650, 800)).zip(ranges).forEach {
            assertEquals(it.first, it.second)
        }
    }

    @Test fun testOrPredicate() {
        val predicateA = TestPredicateTrack { a, b ->
            listOf(Range(10, 20), Range(100, 200), Range(500, 800))
        }
        val predicateB = TestPredicateTrack { a, b ->
            listOf(Range(15, 40), Range(50, 80), Range(90, 150), Range(650, 900))
        }
        val orPredicate = OrPredicateTrack(predicateA, predicateB)
        val ranges = orPredicate.eval(ChromosomeRange(0, 1000, chromosome), 0)

        arrayOf(Range(10, 40), Range(50, 80), Range(90, 200), Range(500, 900)).zip(ranges).forEach {
            assertEquals(it.first, it.second)
        }
    }

    @Test fun testNotPredicate() {
        val predicate = TestPredicateTrack { a, b -> listOf(Range(10, 20), Range(100, 200), Range(500, 800)) }
        val notPredicate = NotPredicateTrack(predicate)
        val ranges = notPredicate.eval(ChromosomeRange(0, 1000, chromosome), 0)
        arrayOf(Range(0, 10), Range(20, 100), Range(200, 500), Range(800, 1000)).zip(ranges).forEach {
            assertEquals(it.first, it.second)
        }
    }

    @Test fun testRelationPredicate1() {
        val binsNum = 5;
        val track1 = TestArithmeticTrack { a, b -> listOf(1.0, 2.0, 3.0, 4.0, 5.0) }
        val track2 = TestArithmeticTrack { a, b -> listOf(1.0, 2.0, 3.0, 4.0, 5.0) }

        // test EQ
        var ranges = RelationPredicateTrack(RelationOp.EQ, track1, track2).eval(
                ChromosomeRange(0, 100, chromosome), binsNum
        )
        arrayOf(Range(0, 100)).zip(ranges).forEach { assertEquals(it.first, it.second) }
        // test NEQ
        ranges = RelationPredicateTrack(RelationOp.NEQ, track1, track2).eval(
                ChromosomeRange(0, 100, chromosome), binsNum
        )
        assertEquals(0, ranges.size())
        // test LEQ
        ranges = RelationPredicateTrack(RelationOp.LEQ, track1, track2).eval(
                ChromosomeRange(0, 100, chromosome), binsNum
        )
        arrayOf(Range(0, 100)).zip(ranges).forEach { assertEquals(it.first, it.second) }
    }

    @Test fun testRelationPredicate2() {
        val binsNum = 8;
        val track1 = TestArithmeticTrack { a, b ->
            listOf(10.0, 20.0, 30.0, 0.0, 2.0, 3.0, 4.0, 0.0)
        }
        val track2 = TestArithmeticTrack { a, b ->
            listOf(2.0, 10.0, 15.0, 5.0, 2.0, 2.0, 2.0, 100.0)
        }

        // test GEQ
        var ranges = RelationPredicateTrack(RelationOp.GEQ, track1, track2).eval(
                ChromosomeRange(0, 160, chromosome), binsNum
        )
        arrayOf(Range(0, 60), Range(80, 140)).zip(ranges).forEach {
            assertEquals(it.first, it.second)
        }
        // test GE
        ranges = RelationPredicateTrack(RelationOp.GE, track1, track2).eval(
                ChromosomeRange(0, 160, chromosome), binsNum
        )
        arrayOf(Range(0, 60), Range(100, 140)).zip(ranges).forEach {
            assertEquals(it.first, it.second)
        }
    }
}