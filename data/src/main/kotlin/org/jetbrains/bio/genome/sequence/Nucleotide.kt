package org.jetbrains.bio.genome.sequence


import com.google.common.base.Preconditions.checkElementIndex

/**
 * DNA nucleotide.
 */
enum class Nucleotide private constructor(
        /**
         * Two bit coding as defined by the 2bit format.
         *
         * See http://genome.ucsc.edu/FAQ/FAQformat.html#format7.
         */
        val byte: Byte) {

    // Please keep 'em in bytes order
    T(0b00),
    C(0b01),
    A(0b10),
    G(0b11);

    val char: Char get() = getChar(byte)

    companion object {
        // Use values() here until https://youtrack.jetbrains.com/issue/KT-9776
        @JvmField val ALPHABET = CharArray(values().size)

        init {
            for (nucleotide in values()) {
                ALPHABET[nucleotide.byte.toInt()] = nucleotide.toString().first().toLowerCase()
            }
        }

        // NOTE[oleg] Some SA implementations work only with positive numbers
        @JvmField val ILLEGAL_NUCLEOTIDE_BYTE: Byte = 0b100
        @JvmField val ANY_NUCLEOTIDE_BYTE: Byte = 0b101
        @JvmField val N = 'n'

        @JvmStatic fun getByte(c: Char): Byte = when (c) {
            // Don't use fromChar() and toUpperCase() for performance reasons
            'T', 't' -> 0
            'C', 'c' -> 1
            'A', 'a' -> 2
            'G', 'g' -> 3
            else -> ILLEGAL_NUCLEOTIDE_BYTE
        }

        @JvmStatic fun getChar(b: Byte) = when (b) {
            ILLEGAL_NUCLEOTIDE_BYTE, ANY_NUCLEOTIDE_BYTE -> N
            else -> {
                checkElementIndex(b.toInt(), 4, "invalid nucleotide byte")
                ALPHABET[b.toInt()]
            }
        }

        @JvmStatic fun fromChar(c: Char) = fromByte(getByte(c).toInt())

        @JvmStatic fun complement(b: Byte): Byte {
            // Note(lebedev): leave exceptional bytes (any nt, illegal nt) as is.
            if (b > 3) {
                return b
            }

            return when (fromByte(b.toInt())) {
                A -> T.byte
                T -> A.byte
                C -> G.byte
                G -> C.byte
                else -> throw IllegalArgumentException()
            }
        }

        @JvmStatic fun fromByte(bits: Int): Nucleotide {
            checkElementIndex(bits, 4, "invalid nucleotide byte")
            return values()[bits]
        }
    }
}
