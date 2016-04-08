package org.jetbrains.bio.genome.query

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Genome
import java.util.*

/**
 * Genome query for all chromosomes (somatic & autosomal) except
 * unlocalized and unmapped fragments and the mitochondrial
 * chromosome.
 *
 * @author Oleg Shpynov
 * @since 06/9/12
 */
class GenomeQuery(val build: String, vararg names: String) :
        CachingInputQuery<ArrayList<Chromosome>>() {
    //  ^^^ ArrayList is here to avoid excessive wildcards in Java.

    /** A genome to query. */
    val genome = Genome(build)

    /** A subset of chromosomes to be considered or empty list for all chromosomes. */
    val restriction = names.toList()

    override fun getUncached(): ArrayList<Chromosome> {
        return if (restriction.isEmpty()) {
            genome.chromosomes.asSequence().filter {
                !it.isMitochondrial && it.name.matches("chr[0-9]*[XYM]?".toRegex())
            }.toCollection(ArrayList())
        } else {
            restriction.map { Chromosome(build, it) }.toCollection(ArrayList())
        }
    }

    override val id: String get() = build

    // TODO: this shouldn't be here.
    fun getShortNameWithChromosomes(): String {
        return build + if (restriction.isNotEmpty()) "[${restriction.joinToString(",")}]" else ""
    }

    override val description: String get() {
        val contents = if (restriction.isNotEmpty()) {
            restriction.joinToString(", ")
        } else {
            "all chromosomes"
        }
        return "${genome.description} $build [$contents]"
    }

    override fun toString() = description

    override fun equals(other: Any?): Boolean = when {
        other === this -> true
        other == null || other !is GenomeQuery -> false
        else -> build == other.build &&
                restriction == other.restriction
    }

    override fun hashCode(): Int {
        // XXX redundant 'false' here is to keep the computed results
        //     from 'ModelFitExperiment. See discussion in 2b9811.
        return Objects.hash(build, false, restriction)
    }

    companion object {
        /**
         * Restores genome query from [.getShortNameWithChromosomes] format
         */
        fun parse(text: String): GenomeQuery {
            val index = text.indexOf('[')
            if (index == -1) {
                return GenomeQuery(text)
            }

            val build = text.substring(0, index)
            val names = text.substring(index).replace("[\\[\\]]".toRegex(), "")
                    .split(',').toTypedArray()
            return GenomeQuery(build, *names)
        }
    }
}
