package org.jetbrains.bio.ext

import org.apache.commons.math3.util.Precision
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat

/**
 * @author Sergei Lebedev, Roman.Chernyatchik
 *
 * Various formatting helpers.
 */
fun Long.asFileSize(): String {
    check(this >= 0L)

    if (this == 0L) {
        return "0 b";
    }

    val units = (Math.log(this.toDouble())/Math.log(1024.0)).toInt()
    val value = this / Math.pow(1024.0, units.toDouble())

    return "${Formatter.FILE_SIZE.format(value)} ${Formatter.FILE_SIZE_UNITS[units]}"
}

/**
 * Milliseconds to Date
 */
fun Long.asDate(): String = Formatter.DATE.format(this)


/**
 * Converts a number to a string adding '_' between each three digits.
 * For example, {@code 123456} will be formatted as {@code "123_456"}.
 */
fun Long.asOffset() = Formatter.OFFSET_FORMATTER.format(this)
fun Int.asOffset() = this.toLong().asOffset()

/**
 * Converts a this (value) and given (total) pair of numbers into a human-readable fraction.
 * For example, `3.fraction(4)` yields `&quot;75.0% (3/4)&quot;`.
 */
fun Long.asFractionOf(total: Long): String {
    check(this <= total) { "value > total" }
    return "${asPercentOf(total, 2)} (${asOffset()}/${total.asOffset()})"
}

@JvmOverloads fun Long.asPercentOf(total: Long, digitsAfterDot: Int = 2)
        = "${Precision.round(100.0 * this / total, digitsAfterDot)}%"

object Formatter {
    val DATE = SimpleDateFormat("MM/dd/yyyy HH:mm:ss")

    val FILE_SIZE: DecimalFormat
    val FILE_SIZE_UNITS = arrayOf("b", "kb", "mb", "gb")

    /** Long number formatter, e.g. 123_456. */
    val OFFSET_FORMATTER: DecimalFormat

    init {
        val fss = DecimalFormatSymbols.getInstance()
        fss.decimalSeparator = ','

        FILE_SIZE = DecimalFormat("#,##0.#");
        FILE_SIZE.isGroupingUsed = false
        FILE_SIZE.decimalFormatSymbols = fss


        val off = DecimalFormatSymbols.getInstance()
        off.groupingSeparator = '_'

        OFFSET_FORMATTER = DecimalFormat()
        OFFSET_FORMATTER.isGroupingUsed = true
        OFFSET_FORMATTER.decimalFormatSymbols = off

    }
}