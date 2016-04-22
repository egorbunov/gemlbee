package org.jetbrains.bio.genome.query

import com.google.common.base.Joiner
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.locus.LocusQuery

/**
 * Locus query for fetching genome loci for given [GenomeQuery]
 *
 * See [LocusType] for details on available loci.
 *
 * @author Roman Chernyatchik
 * @author Oleg Shpynov
 */
class GenomeLocusQuery<Input, Item> private constructor(
        protected val relativeStartOffset: Int = 0,
        protected val relativeEndOffset: Int = 0,
        protected val relativePosition: RelativePosition = RelativePosition.AROUND_WHOLE_SEGMENT,
        val inputQuery: Query<Input, Collection<Item>>,
        val locusQuery: LocusQuery<Item>)
:
        Query<Input, Collection<Location>> {

    override val id: String get() {
        return if (isModified) {
            Joiner.on('_').join(locusQuery.id,
                                relativePosition.id,
                                relativeStartOffset, relativeEndOffset)
        } else {
            Joiner.on('_').skipNulls().join(locusQuery.id, inputQuery.id)
        }
    }

    override val description: String = if (isModified) {
        "${locusQuery.description}:${relativePosition.id}[$relativeStartOffset $relativeEndOffset)"
    } else {
        locusQuery.description
    }

    /**
     * @return true is location are shifted, moved according to [.myRelativePosition] and bounds,
     * false in case of id
     */
    val isModified: Boolean get() {
        return !(relativeStartOffset == 0 && relativeEndOffset == 0 &&
                relativePosition === RelativePosition.AROUND_WHOLE_SEGMENT)
    }

    /**
     * Creates a composition of [.myInputQuery] o [.myLocusQuery]
     * and shifts the result using [.myRelativePosition]
     */
    override fun process(input: Input): Collection<Location> {
        return inputQuery.process(input).asSequence()
                .flatMap { item -> locusQuery.process(item).asSequence() }
                .filterNotNull()
                .map { relativePosition.of(it, relativeStartOffset, relativeEndOffset) }
                .toList()
    }

    override fun toString() = description

    companion object {
        @JvmStatic fun of(locusQuery: LocusQuery<Gene>,
                          geneClass: GeneClass): GenomeLocusQuery<Chromosome, Gene> {
            return GenomeLocusQuery(inputQuery = ChromosomeGenesQuery(geneClass),
                                    locusQuery = locusQuery)
        }

        @JvmStatic fun of(locusQuery: LocusQuery<Chromosome>): GenomeLocusQuery<Chromosome, Chromosome> {
            return GenomeLocusQuery(inputQuery = object : Query<Chromosome, Collection<Chromosome>> {
                override fun process(input: Chromosome) = listOf(input)

                override val id: String get() = "unknown"
            }, locusQuery = locusQuery)
        }
    }
}
