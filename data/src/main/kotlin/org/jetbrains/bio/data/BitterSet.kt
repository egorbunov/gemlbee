package org.jetbrains.bio.data

import java.util.*

/**
 * A sibling of [java.util.BitSet] which is aware of the universe cardinality.
 *
 * A more descriptive name would be `BitList` or `BitVector`.
 *
 * @author Sergei Lebedev
 * @since 08/07/15
 */
class BitterSet(private val universe: Int) : BitSet() {
    fun peel() = BitSet.valueOf(toLongArray())

    fun toBooleanArray() = BooleanArray(universe) { get(it) }

    operator fun plus(other: BitterSet): BitterSet {
        val acc = peel()
        var ptr = other.nextSetBit(0)
        while (ptr >= 0) {
            acc.set(universe + ptr)
            ptr = other.nextSetBit(ptr + 1)
        }

        return of(universe + other.universe, acc)
    }

    /**
     * Unlike its relative [BitterSet] returns the universe cardinality.
     *
     *     val bs = BitSet()
     *     bs.set(1)
     *     bs.size()   # == 64 == Long.SIZE
     *
     *     val bts = BitterSet.of(3, bs)
     *     bts.size()  # == 3
     *     bts.set(1)
     *     bts.size()  # == 3
     *
     * Why? Because, concatenation.
     */
    override fun size() = universe

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is BitterSet -> false
        else -> super.equals(other) && universe == other.universe
    }

    override fun hashCode() = Objects.hash(super.hashCode(), universe)

    companion object {
        @JvmStatic fun of(universe: Int, wrapped: BitSet): BitterSet {
            require(wrapped.cardinality() <= universe)
            val bs = BitterSet(universe)
            bs.or(wrapped)
            return bs
        }

        @JvmStatic inline fun of(universe: Int, block: (Int) -> Boolean): BitterSet {
            val bs = BitSet(universe)
            for (i in 0..universe - 1) {
                if (block(i)) {
                    bs.set(i)
                }
            }

            return of(universe, bs)
        }
    }
}