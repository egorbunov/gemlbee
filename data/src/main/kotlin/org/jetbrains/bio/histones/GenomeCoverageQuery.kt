package org.jetbrains.bio.histones

import org.apache.log4j.Logger
import org.jetbrains.bio.ext.deleteIfExists
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.ext.readOrRecalculate
import org.jetbrains.bio.genome.query.CachingInputQuery
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.io.BedEntry
import org.jetbrains.bio.util.Configuration

class GenomeCoverageQuery private constructor(
        private val genomeQuery: GenomeQuery,
        private val bedQuery: InputQuery<Iterable<BedEntry>>,
        private val uniqueOnly: Boolean) : CachingInputQuery<GenomeCoverage>() {

    override fun getUncached(): GenomeCoverage {
        val path = Configuration.cachePath / "coverage" / "$id.npz"
        return path.readOrRecalculate(
                { GenomeCoverage(genomeQuery).load(path) },
                { output ->
                    // Recalculate for all the chromosomes at once.
                    val coverage = GenomeCoverage.compute(genomeQuery, bedQuery, uniqueOnly)

                    if (genomeQuery.restriction.isEmpty()) {
                        output.let { path -> coverage.save(path) }
                    } else {
                        output.let { path -> path.deleteIfExists() }
                        LOG.warn("ChipSeq coverage $id wasn't serialized to $path because " +
                                 "genome query isn't complete ${genomeQuery.description}")
                    }
                    output to coverage
                }, "GenomeCoverage for $bedQuery")
    }

    override val id: String get() = bedQuery.id + (if (uniqueOnly) "_unique" else "")

    override val description: String get() {
        return (if (uniqueOnly) "Unique tags coverage for " else "Coverage for") +
                bedQuery.description
    }

    companion object {
        private val LOG = Logger.getLogger(GenomeCoverageQuery::class.java)

        @JvmStatic @JvmOverloads fun of(bedQuery: BedTrackQuery,
                                        uniqueOnly: Boolean = true): GenomeCoverageQuery {
            return of(bedQuery.genomeQuery, bedQuery, uniqueOnly)
        }

        @JvmStatic @JvmOverloads fun of(genomeQuery: GenomeQuery,
                                        bedQuery: InputQuery<Iterable<BedEntry>>,
                                        uniqueOnly: Boolean = true): GenomeCoverageQuery {
            return GenomeCoverageQuery(genomeQuery, bedQuery, uniqueOnly)
        }
    }
}