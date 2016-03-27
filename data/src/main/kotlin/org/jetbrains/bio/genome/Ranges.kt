package org.jetbrains.bio.genome

import com.google.common.collect.ComparisonChain
import com.google.common.math.IntMath
import com.google.common.primitives.Ints
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.jetbrains.bio.io.BedEntry
import java.math.RoundingMode
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * A semi-closed interval.
 *
 * @author Oleg Shpynov
 * @since 19/11/12
 */
data class Range(
        /** 0-based start offset (inclusive). */
        val startOffset: Int,
        /** 0-based end offset (exclusive). */
        val endOffset: Int) : Comparable<Range> {

    init {
        require(startOffset <= endOffset) { "invalid range $this" }
    }

    fun length() = endOffset - startOffset

    fun isEmpty(): Boolean = length() == 0
    fun isNotEmpty(): Boolean = length() != 0

    infix fun intersects(other: Range): Boolean {
        return other.endOffset > startOffset && endOffset > other.startOffset
    }

    infix fun intersection(other: Range): Range {
        return if (this intersects other) {
            Range(Math.max(startOffset, other.startOffset),
                  Math.min(endOffset, other.endOffset))
        } else {
            EMPTY
        }
    }

    fun on(chromosome: Chromosome): ChromosomeRange {
        return ChromosomeRange(startOffset, endOffset, chromosome)
    }

    operator fun contains(offset: Int) = offset >= startOffset && offset < endOffset

    /**
     * Returns an ordered stream of sub-ranges each having a given
     * `width`, with the last sub-range possibly being an exception.
     */
    fun slice(width: Int): Stream<Range> {
        if (width > length()) {
            return Stream.empty()
        }

        val n = IntMath.divide(length(), width, RoundingMode.CEILING)
        return IntStream.range(0, n).mapToObj { i ->
            Range(startOffset + i * width,
                  Math.min(endOffset, startOffset + (i + 1) * width))
        }
    }

    override fun toString() = "[$startOffset, $endOffset)"

    override fun compareTo(other: Range) = ComparisonChain.start()
            .compare(startOffset, other.startOffset)
            .compare(endOffset, other.endOffset)
            .result()

    companion object {
        /** An empty range. */
        val EMPTY = Range(0, 0)

        /**
         * A type adapter enforcing a more compact encoding for ranges.
         *
         * The notation `[24, 42]` can be confusing, because ranges are
         * semi-closed on the right. If you don't like it, feel free to
         * alter the right bound on serialization/deserialization.
         */
        @JvmStatic val ADAPTER = object : TypeAdapter<Range>() {
            override fun read(`in`: JsonReader) = with(`in`) {
                beginArray()
                val startOffset = nextInt()
                val endOffset = nextInt()
                endArray()
                Range(startOffset, endOffset)
            }

            override fun write(out: JsonWriter, range: Range) {
                out.beginArray()
                        .value(range.startOffset)
                        .value(range.endOffset)
                        .endArray()
            }
        }.nullSafe()
    }
}

data class ScoredRange(val startOffset: Int, val endOffset: Int, val score: Double) :
        Comparable<ScoredRange> {

    init {
        require(startOffset <= endOffset) { "invalid range $this" }
    }

    override fun toString(): String = "$score@$[$startOffset, $endOffset)"

    override fun compareTo(other: ScoredRange) = ComparisonChain.start()
            .compare(startOffset, other.startOffset)
            .compare(endOffset, other.endOffset)
            .compare(score, other.score)
            .result()
}

data class ChromosomeRange(val startOffset: Int, val endOffset: Int,
                           val chromosome: Chromosome) : LocationAware {

    init {
        require(startOffset <= endOffset) { "invalid chromosome range $this" }
    }

    override val location: Location get() = on(Strand.PLUS)

    fun length() = endOffset - startOffset

    fun on(strand: Strand) = Location(startOffset, endOffset, chromosome, strand)

    fun toRange() = Range(startOffset, endOffset)

    override fun toString() = "${chromosome.name}:[$startOffset, $endOffset)"
}

data class Location(val startOffset: Int, val endOffset: Int,
                    val chromosome: Chromosome,
                    val strand: Strand = Strand.PLUS) :
        LocationAware, Comparable<Location> {

    init {
        require(startOffset <= endOffset) { "invalid location $this" }
    }

    override val location: Location get() = this

    val sequence: String get() {
        return chromosome.sequence.substring(startOffset, endOffset, strand)
    }

    fun length() = endOffset - startOffset

    fun opposite() = Location(startOffset, endOffset, chromosome, strand.opposite())

    /**
     * Returns absolute position of 5' bound. Differs from [#getStartOffset]
     * in case of [Strand.MINUS].
     */
    fun get5Bound(): Int = get5BoundOffset(0)

    /**
     * Returns absolute position of 3' bound. Differs from [#getEndOffset]
     * in case of [Strand.MINUS].
     */
    fun get3Bound(): Int = get3BoundOffset(0)

    /**
     * Returns absolute position of offset relative to 5' bound. Differs
     * from `startOffset + relativeOffset` in case of [Strand.MINUS]
     */
    fun get5BoundOffset(relativeOffset: Int): Int {
        return if (strand.isPlus()) {
            startOffset + relativeOffset
        } else {
            endOffset - 1 - relativeOffset
        }
    }

    /**
     * Returns absolute position of offset relative to 3' bound. Differs
     * from `getEndOffset + relativeOffset` in case of [Strand.MINUS].
     */
    fun get3BoundOffset(relativeOffset: Int): Int {
        return if (strand.isPlus()) {
            endOffset - 1 + relativeOffset
        } else {
            startOffset - relativeOffset
        }
    }

    fun toRange() = Range(startOffset, endOffset)

    fun toChromosomeRange() = ChromosomeRange(startOffset, endOffset, chromosome)

    override fun toString() = "${chromosome.name}:$strand[$startOffset, $endOffset)"

    override fun compareTo(other: Location) = ComparisonChain.start()
            .compare(chromosome, other.chromosome)
            .compare(strand, other.strand)
            .compare(startOffset, other.startOffset)
            .compare(endOffset, other.endOffset)
            .result()

    companion object {
        @JvmStatic val ADAPTER = object : TypeAdapter<Location>() {
            override fun read(`in`: JsonReader) = with(`in`) {
                beginArray()
                val startOffset = nextInt()
                val endOffset = nextInt()
                val chromosome = Chromosome.ADAPTER.read(this)
                val strand = nextString().toStrand()
                endArray()
                Location(startOffset, endOffset, chromosome, strand)
            }

            override fun write(out: JsonWriter, location: Location) {
                out.beginArray()
                        .value(location.startOffset)
                        .value(location.endOffset)

                Chromosome.ADAPTER.write(out, location.chromosome)

                out.value(location.strand.char.toString())
                        .endArray()
            }
        }.nullSafe()
    }
}

fun Location.toBedEntry(): BedEntry {
    return BedEntry(chromosome.name, startOffset, endOffset, strand.char)
}

enum class RelativePosition(val id: String) {
    AROUND_START("start") {  // around 5'
        override fun of(location: Location, relativeStartOffset: Int, relativeEndOffset: Int): Location {
            return bracket(location, location.get5BoundOffset(relativeStartOffset),
                           location.get5BoundOffset(relativeEndOffset - 1))
        }
    },

    AROUND_END("end") {  // around 3'
        override fun of(location: Location, relativeStartOffset: Int, relativeEndOffset: Int): Location {
            return bracket(location, location.get3BoundOffset(relativeStartOffset),
                           location.get3BoundOffset(relativeEndOffset - 1))
        }
    },

    AROUND_WHOLE_SEGMENT("whole") {
        override fun of(location: Location, relativeStartOffset: Int, relativeEndOffset: Int): Location {
            return if (relativeStartOffset == 0 && relativeEndOffset == 0) {
                location  // Don't allocate new location in this case
            } else {
                bracket(location, location.get5BoundOffset(relativeStartOffset),
                        location.get3BoundOffset(relativeEndOffset - 1))
            }
        }
    };

    /**
     * Computes location according given relative position and offsets.
     *
     * For more details see: [Location#get5BoundOffset] and [Location#get3BoundOffset]
     */
    abstract fun of(location: Location, relativeStartOffset: Int, relativeEndOffset: Int): Location

    /**
     * Ensures the resulting location is proper, i.e. has correctly ordered
     * endpoints which are bounded by [0, chromosome.length].
     */
    protected fun bracket(location: Location, newStartOffset: Int, newEndOffset: Int): Location {
        return with(location) {
            val boundedStartOffset = Math.min(Math.max(0, Math.min(newStartOffset, newEndOffset)),
                                              chromosome.length)
            // XXX do we even need to use boundedStartOffset here?
            val boundedEndOffset = Math.min(Ints.max(boundedStartOffset, newStartOffset, newEndOffset) + 1,
                                            chromosome.length)
            Location(boundedStartOffset, boundedEndOffset, chromosome, strand)
        }
    }
}

/** A simplistic interface for a "thing" with genomic location. */
interface LocationAware {
    val location: Location
}