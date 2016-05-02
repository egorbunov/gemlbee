package org.jetbrains.bio.query.parse

import org.jetbrains.bio.big.BigBedFile
import org.jetbrains.bio.genome.ChromosomeRange

/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class BigBedFileTrack(val id: String, val bbf: BigBedFile): ArithmeticTrack() {
    override fun compareTo(other: Statement): Int {
        return if (other is BigBedFileTrack && id == other.id) 0 else 1 // TODO: Is that enough?
    }


    override fun eval(range: ChromosomeRange, binsNum: Int): List<Double> {
        return bbf.summarize(range.chromosome.name,
                range.startOffset,
                range.endOffset,
                binsNum).map { it.sum }
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}
