package org.jetbrains.bio.query.parse

import org.jetbrains.bio.big.BigSummary
import java.util.*

/**
 * Extensions (not only as Kotlin extensions) for BigSummary
 *
 * @author Egor Gorbunov
 * @since 01.05.16
 */

/**
 * Comparator for `BigSummary` by `sum` field
 */
class BigSummaryComparatorBySum: Comparator<BigSummary> {
    override fun compare(a: BigSummary?, b: BigSummary?): Int {
        if (a == null || b == null) {
            throw IllegalArgumentException()
        }
        return a.sum.compareTo(b.sum)
    }
}

fun BigSummary.minus(other: BigSummary) {
    // TODO: that smells...
    this.sum -= other.sum
    this.sumSquares -= other.sumSquares
}