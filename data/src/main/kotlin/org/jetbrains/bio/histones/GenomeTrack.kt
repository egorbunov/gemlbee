package org.jetbrains.bio.histones

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.ScoredRange
import org.jetbrains.bio.genome.containers.GenomeMap
import org.jetbrains.bio.genome.containers.genomeMap
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*

/**
 * A track which maps ranges aka bins on the chromosome to some real number,
 * stored in [ScoredRange].
 */
class GenomeTrack(genome : GenomeQuery) {
    val ranges : GenomeMap<ArrayList<ScoredRange>> =
            genomeMap(genome) { ArrayList<ScoredRange>() }

    fun add(chromosome : Chromosome, range: ScoredRange) {
        ranges[chromosome].add(range)
    }

    fun add(chromosome : Chromosome, start: Int, end: Int, score: Double) {
        add(chromosome, ScoredRange(start, end, score))
    }

    operator fun get(chromosome : Chromosome, from: Int, to : Int) : List<ScoredRange> {
        return ranges[chromosome].filter { it.startOffset < to && it.endOffset > from }
    }

    operator fun get(chromosome : Chromosome) : List<ScoredRange> {
        return ranges[chromosome]
    }

}
