package org.jetbrains.bio.genome.containers

import org.jetbrains.annotations.TestOnly
import org.jetbrains.bio.ext.await
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Creates a new genome map from a given [init] function.
 *
 * If [parallel] is `false`, initialization is performed
 * sequentially otherwise for each chromosome [init] is
 * called in a separate thread.
 */
fun <T> genomeMap(genomeQuery: GenomeQuery, parallel: Boolean = false,
                  init: (Chromosome) -> T): GenomeMap<T> {
    return ConcurrentGenomeMap(genomeQuery, parallel, init)
}

fun <T> genomeStrandMap(genomeQuery: GenomeQuery, parallel: Boolean = false,
                        init: (Chromosome, Strand) -> T): GenomeStrandMap<T> {
    return ConcurrentGenomeStrandMap(genomeQuery, parallel, init)
}

/**
 * A map with a fixed set of keys, defined by
 * [org.jetbrains.bio.genome.query.GenomeQuery].
 *
 * @author Oleg Shpynov
 * @author Sergei Lebedev
 */
interface GenomeMap<T> {
    val genomeQuery: GenomeQuery

    operator fun get(chromosome: Chromosome): T

    operator fun set(chromosome: Chromosome, value: T)

    fun <R> map(parallel: Boolean, f: (T) -> R): GenomeMap<R> {
        return genomeMap(genomeQuery, parallel, { f(get(it)) })
    }
}

/**
 * A genome map which supports concurrent updates.
 *
 * @author Sergei Lebedev
 * @since 14/08/14
 */
private open class ConcurrentGenomeMap<T>(override val genomeQuery: GenomeQuery,
                                          parallel: Boolean,
                                          f: (Chromosome) -> T) : GenomeMap<T> {
    private val mask: BooleanArray  // allows us to store nulls.
    private val data: AtomicReferenceArray<T>

    init {
        val numChromosomes = genomeQuery.genome.chromosomes.size
        mask = BooleanArray(numChromosomes)
        data = AtomicReferenceArray<T>(numChromosomes)
        genomeQuery.get().map { chromosome ->
            Callable {
                val key = chromosome.id
                val value = f(chromosome)
                mask[key] = true
                data[key] = value
            }
        }.await(parallel)
    }

    override fun get(chromosome: Chromosome): T {
        val key = chromosome.id
        if (mask[key] && chromosome.genome.build == genomeQuery.build) {
            return data[key]
        }

        throw NoSuchElementException(chromosome.toString())
    }

    override fun set(chromosome: Chromosome, value: T) {
        val key = chromosome.id
        if (mask[key] && chromosome.genome.build == genomeQuery.build) {
            data.getAndUpdate(key) { value }
        } else {
            throw NoSuchElementException(chromosome.toString())
        }
    }
}

/**
 * A [org.jetbrains.bio.genome.containers.GenomeMap] where each chromosome
 * is allowed to have per [org.jetbrains.bio.genome.Strand] values.
 *
 * @author Oleg Shpynov
 * @author Sergei Lebedev
 */
interface GenomeStrandMap<T> {
    operator fun get(chromosome: Chromosome, strand: Strand): T

    operator fun set(chromosome: Chromosome, strand: Strand, value: T)

    val genomeQuery: GenomeQuery
}

/**
 * A genome map which supports concurrent updates for (chromosome, strand)
 * pairs.
 *
 * @author Sergei Lebedev
 * @since 14/08/14
 */
open class ConcurrentGenomeStrandMap<T>(
        override val genomeQuery: GenomeQuery,
        parallel: Boolean,
        f: (Chromosome, Strand) -> T) : GenomeStrandMap<T> {

    /** Number of items stored on a single strand. */
    private val strandCapacity = genomeQuery.genome.names.size

    private val mask: BooleanArray
    private val data: AtomicReferenceArray<T>

    init {
        mask = BooleanArray(2 * strandCapacity)
        data = AtomicReferenceArray<T>(2 * strandCapacity)

        val tasks = arrayListOf<Callable<Unit>>()
        for (chromosome in genomeQuery.get()) {
            for (strand in Strand.values()) {
                tasks.add(Callable {
                    val key = keyFor(chromosome, strand)
                    val value = f(chromosome, strand)
                    mask[key] = true
                    data[key] = value
                })
            }
        }

        tasks.await(parallel)
    }

    private fun keyFor(chromosome: Chromosome, strand: Strand): Int {
        return chromosome.id + strandCapacity * strand.ordinal
    }

    override fun get(chromosome: Chromosome, strand: Strand): T {
        val key = keyFor(chromosome, strand)
        if (mask[key] && chromosome.genome.build == genomeQuery.build) {
            return data[key]
        }

        throw NoSuchElementException((chromosome to strand).toString())
    }

    override fun set(chromosome: Chromosome, strand: Strand, value: T) {
        val key = keyFor(chromosome, strand)
        if (mask[key] && chromosome.genome.build == genomeQuery.build) {
            data.getAndUpdate(key) { value }
        } else {
            throw NoSuchElementException((chromosome to strand).toString())
        }
    }

    @TestOnly fun snapshot() = (0..data.length() - 1).map { data[it] }
}

