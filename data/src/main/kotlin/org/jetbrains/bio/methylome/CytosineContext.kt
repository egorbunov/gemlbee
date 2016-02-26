package org.jetbrains.bio.methylome

import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.sequence.Nucleotide
import org.jetbrains.bio.genome.sequence.NucleotideSequence

/**
 * Cytosine context.
 *
 * Each cytosine can be assigned a context depending on the following
 * two nucleotides. See below for possible values. Here `&#39;H&#39;`
 * means A, T or C.
 *
 * @author Roman Chernyatchik
 * @since 28/05/12
 */
enum class CytosineContext private constructor(@JvmField val tag: Byte) {
    CG(0.toByte()),
    CHH(1.toByte()),
    CHG(2.toByte());

    companion object {

        /**
         * For historical reasons we use `null` to represent either
         * any cytosine context (used for filtering) or undefined cytosine
         * context.

         * Cytosine has undefined context when it is located on the chromosome
         * boundaries or its neighbours are Ns.
         */
        @JvmField val ANY: CytosineContext? = null

        @JvmStatic fun determine(sequence: NucleotideSequence,
                                 offset: Int, strand: Strand): CytosineContext? {
            val seqLength = sequence.length()
            val direction = if (strand === Strand.PLUS) 1 else -1

            // 1-st nucleotide
            val nucleotide1 = sequence.byteAt(offset, strand)
            if (nucleotide1 != Nucleotide.C.byte) {
                return ANY
            }

            // 2-nd nucleotide
            val nucleotide2Offset = offset + direction
            if (nucleotide2Offset < 0 || nucleotide2Offset >= seqLength) {
                return ANY
            }
            val nucleotide2 = sequence.byteAt(nucleotide2Offset, strand)
            if (nucleotide2 == Nucleotide.G.byte) {
                return CG
            }

            // 3-rd nucleotide
            val nucleotide3Offset = nucleotide2Offset + direction
            if (nucleotide3Offset < 0 || nucleotide3Offset >= seqLength) {
                return ANY
            }
            val nucleotide3 = sequence.byteAt(nucleotide3Offset, strand)
            // CHG
            if (nucleotide3 == Nucleotide.G.byte) {
                // H - unknown (N)
                return if (nucleotide2 == Nucleotide.ANY_NUCLEOTIDE_BYTE) ANY else CHG
            }

            // CHH
            if (nucleotide2 == Nucleotide.ANY_NUCLEOTIDE_BYTE ||
                nucleotide3 == Nucleotide.ANY_NUCLEOTIDE_BYTE) {
                return ANY
            }

            return CHH
        }
    }
}