package org.jetbrains.bio.genome

/**
 * DNA strand.
 *
 * @author Roman Chernyatchik
 * @since 28/02/12
 */
enum class Strand(val char: Char) {
    PLUS('+'), MINUS('-');

    fun asFilter() = when (this) {
        PLUS  -> StrandFilter.PLUS
        MINUS -> StrandFilter.MINUS
    }

    fun isPlus() = this === PLUS

    fun isMinus() = this === MINUS

    fun opposite() = choose(MINUS, PLUS)

    @Suppress("NOTHING_TO_INLINE")  // Otherwise the arguments are boxed.
    inline fun <T> choose(ifPlus: T, ifMinus: T) = if (isPlus()) ifPlus else ifMinus

    override fun toString() = char.toString()
}

fun Int.toStrand() = if (this > 0) Strand.PLUS else Strand.MINUS

fun String.toStrand() = single().toStrand()

fun Char.toStrand() = when (this) {
    '+'  -> Strand.PLUS
    '-'  -> Strand.MINUS
    else -> throw IllegalArgumentException(toString())
}

enum class StrandFilter(private val char: Char) {
    BOTH('='), PLUS('+'), MINUS('-');

    fun accepts(strand: Strand): Boolean = when (this) {
        BOTH -> true
        PLUS -> strand.isPlus()
        MINUS -> strand.isMinus()
    }

    override fun toString()  = char.toString()
}
