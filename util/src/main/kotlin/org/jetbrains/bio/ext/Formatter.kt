package org.jetbrains.bio.ext

import org.apache.commons.math3.util.Precision
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Various formatting helpers.
 */

/**
 * Converts a number to a string adding '_' between each three digits.
 * For example, {@code 123456} will be formatted as {@code "123_456"}.
 */
fun Long.asOffset() = Formatter.OFFSET_FORMATTER.format(this)
fun Int.asOffset() = toLong().asOffset()

/**
 * Converts a this (value) and given (total) pair of numbers into a human-readable fraction.
 * For example, `3.fraction(4)` yields `"75.0% (3/4)"`.
 */
fun Long.asFractionOf(total: Long): String {
    check(this <= total) { "value > total" }
    return "${asPercentOf(total, 2)} (${asOffset()}/${total.asOffset()})"
}

fun Long.asPercentOf(total: Long, digitsAfterDot: Int = 2): String {
    return "${Precision.round(100.0 * this / total, digitsAfterDot)}%"
}

object Formatter {
    /** Long number formatter, e.g. 123_456. */
    val OFFSET_FORMATTER: DecimalFormat

    init {
        val off = DecimalFormatSymbols.getInstance()
        off.groupingSeparator = '_'

        OFFSET_FORMATTER = DecimalFormat()
        OFFSET_FORMATTER.isGroupingUsed = true
        OFFSET_FORMATTER.decimalFormatSymbols = off
    }
}
