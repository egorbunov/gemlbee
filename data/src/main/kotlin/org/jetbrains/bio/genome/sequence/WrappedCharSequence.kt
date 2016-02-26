package org.jetbrains.bio.genome.sequence

import com.google.common.base.Preconditions.checkElementIndex

/**
 * A [NucleotideSequence] adapter for [java.lang.CharSequence].
 *
 * @author Sergei Lebedev
 * @since 02/07/14
 */
internal data class WrappedCharSequence(val wrapped: CharSequence) : NucleotideSequence {
    override fun charAt(pos: Int): Char {
        checkElementIndex(pos, length(), "pos")
        return wrapped[pos].toLowerCase()
    }

    override fun length() = wrapped.length

    override fun toString() = wrapped.toString()
}