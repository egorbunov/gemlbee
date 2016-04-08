package org.jetbrains.bio.genome.containers

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.jetbrains.bio.data.BitterSet
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.io.BedFormat
import java.io.IOException
import java.nio.file.Path
import java.util.*

private fun <T> GenomeStrandMap<T>.merge(
        other: GenomeStrandMap<T>, op: (T, T) -> T): GenomeStrandMap<T> {
    require(genomeQuery == other.genomeQuery)
    return genomeStrandMap(genomeQuery) { chromosome, strand ->
        op(get(chromosome, strand), other[chromosome, strand])
    }
}

/**
 * A location-based friend of [.RangeList].
 *
 * @author Oleg Shpynov
 * @author Sergei Lebedev
 * @since 25/12/12
 */
class LocationList private constructor(
        private val rangeLists: GenomeStrandMap<RangeList>) : Iterable<Location> {

    val genomeQuery: GenomeQuery get() = rangeLists.genomeQuery

    /**
     * Performs element-wise union on locations in the two lists.
     */
    fun or(other: LocationList): LocationList {
        return LocationList(rangeLists.merge(other.rangeLists, { x, y -> x or y }))
    }

    /**
     * Performs element-wise intersection on locations in the two lists.
     */
    fun and(other: LocationList): LocationList {
        return LocationList(rangeLists.merge(other.rangeLists, { x, y -> x and y }))
    }

    operator fun get(chromosome: Chromosome, strand: Strand): List<Location> {
        val result = Lists.newArrayList<Location>()
        for ((startOffset, endOffset) in rangeLists[chromosome, strand]) {
            result.add(Location(startOffset, endOffset,
                                chromosome, strand))
        }
        return result
    }

    operator fun contains(location: Location): Boolean {
        return location.toRange() in rangeLists[location.chromosome, location.strand]
    }

    fun intersects(location: Location): Boolean {
        return intersectionLength(location) > 0
    }

    /**
     * Returns the length of intersection.
     */
    fun intersectionLength(location: Location): Int {
        val rangeList = rangeLists[location.chromosome, location.strand]
        return rangeList.intersectionLength(location.toRange())
    }

    fun size(): Long {
        var result: Long = 0
        for (chromosome in genomeQuery.get()) {
            result += rangeLists[chromosome, Strand.PLUS].size()
            result += rangeLists[chromosome, Strand.MINUS].size()
        }
        return result
    }

    fun length(): Long {
        var result: Long = 0
        for (chromosome in genomeQuery.get()) {
            result += rangeLists[chromosome, Strand.PLUS].length()
            result += rangeLists[chromosome, Strand.MINUS].length()
        }
        return result
    }

    @Throws(IOException::class)
    fun save(path: Path) = BedFormat.SIMPLE.print(path).use { printer ->
        forEach { printer.print(it.toBedEntry()) }
    }

    override fun iterator(): Iterator<Location> {
        return Iterables.concat(genomeQuery.get().map {
            Iterables.concat(get(it, Strand.PLUS), get(it, Strand.MINUS))
        }).iterator()
    }

    class Builder(private val genomeQuery: GenomeQuery) {
        private val ranges: GenomeStrandMap<ArrayList<Range>> =
                genomeStrandMap(genomeQuery) { _c, _s -> arrayListOf<Range>() }

        fun add(location: Location): Builder {
            ranges[location.chromosome, location.strand].add(location.toRange())
            return this
        }

        fun build(): LocationList {
            val rangeLists = genomeStrandMap(genomeQuery) { chromosome, strand ->
                ranges[chromosome, strand].toRangeList()
            }

            return LocationList(rangeLists)
        }
    }

    companion object {
        @JvmStatic fun builder(genomeQuery: GenomeQuery) = Builder(genomeQuery)

        @JvmStatic fun create(genomeQuery: GenomeQuery,
                              locations: Iterable<Location>): LocationList {
            val builder = Builder(genomeQuery)
            locations.forEach { builder.add(it) }
            return builder.build()
        }

        @JvmStatic fun load(genomeQuery: GenomeQuery, path: Path,
                            format: BedFormat = BedFormat.auto(path)): LocationList {
            val chromosomes = ChromosomeNamesMap.create(genomeQuery)
            val locations = format.parse(path).map {
                val chromosome = chromosomes[it.chromosome]
                if (chromosome == null) {
                    null
                } else {
                    Location(it.chromStart, it.chromEnd, chromosome,
                             it.strand.toStrand())
                }
            }.filterNotNull()

            return create(genomeQuery, locations)
        }
    }
}

/**
 * Constructs a list of complementary locations.
 *
 * Input locations may be in any order, on any strand, and are allowed
 * to overlap. Input locations on a different chromosome or on an
 * opposite strand are ignored.
 *
 * @see Range.minus for details.
 */
operator fun Location.minus(locations: List<Location>): List<Location> {
    val ranges = locations.asSequence()
            .filter { it.strand == strand && it.chromosome == chromosome }
            .map { it.toRange() }
            .toList()

    return if (ranges.isEmpty()) {
        listOf(this)
    } else {
        (toRange() - ranges).map { it.on(chromosome).on(strand) }
    }
}

fun locationList(genomeQuery: GenomeQuery, vararg locations: Location): LocationList {
    return locationList(genomeQuery, locations.asList())
}

fun locationList(genomeQuery: GenomeQuery, locations: Iterable<Location>): LocationList {
    return LocationList.create(genomeQuery, locations)
}