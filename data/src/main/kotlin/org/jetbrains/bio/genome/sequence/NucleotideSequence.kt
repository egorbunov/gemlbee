package org.jetbrains.bio.genome.sequence

import com.google.common.base.Preconditions.checkPositionIndexes
import org.jetbrains.bio.genome.Strand

/**
 * An immutable container for nucleotide sequence.
 *
 * @author Sergei Lebedev
 * @since 02/07/14
 */
interface NucleotideSequence {
    /**
     * Returns the lowercase nucleotide at specific position.
     */
    fun charAt(pos: Int): Char = Nucleotide.getChar(byteAt(pos))

    /**
     * Returns the lowercase nucleotide at specific position and strand.
     */
    fun charAt(pos: Int, strand: Strand) = Nucleotide.getChar(byteAt(pos, strand))

    fun byteAt(pos: Int) = Nucleotide.getByte(charAt(pos))

    fun byteAt(pos: Int, strand: Strand): Byte {
        val b = byteAt(pos)
        return if (strand.isPlus()) b else Nucleotide.complement(b)
    }

    fun substring(from: Int, to: Int) = substring(from, to, Strand.PLUS)

    fun substring(from: Int, to: Int, strand: Strand): String {
        checkPositionIndexes(from, to, length())
        val acc = CharArray(to - from)
        if (strand.isPlus()) {
            for (offset in 0..acc.size - 1) {
                val b = byteAt(from + offset)
                acc[offset] = Nucleotide.getChar(b)
            }
        } else {
            for (offset in 0..acc.size - 1) {
                acc[acc.size - 1 - offset] =
                        Nucleotide.getChar(Nucleotide.complement(byteAt(from + offset)))
            }
        }

        return String(acc)
    }

    fun length(): Int
}

fun CharSequence.asNucleotideSequence(): NucleotideSequence {
    return WrappedCharSequence(this)
}
