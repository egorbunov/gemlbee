package org.jetbrains.bio.browser.model

import gnu.trove.list.array.TDoubleArrayList
import org.jetbrains.bio.ext.parallelStream
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.query.GenomeLocusQuery
import org.jetbrains.bio.genome.query.Query
import org.jetbrains.bio.genome.query.locus.GeneLocusQuery
import java.util.stream.Stream

public class LocusQueryBrowserModel private constructor(
        id: String,
        originalModel: BrowserModel,
        locationReferences: List<LocationReference>,
        public val query: GenomeLocusQuery<Chromosome, *>)
: MultipleLocationsBrowserModel(id, originalModel, locationReferences) {

    companion object {
        @JvmStatic public fun create(id: String,
                                     query: GenomeLocusQuery<Chromosome, *>,
                                     model: BrowserModel): LocusQueryBrowserModel {
            val gq = model.genomeQuery
            val lengths = TDoubleArrayList()
            val locations = MultipleLocationsBrowserModel.filter(
                    gq.get().parallelStream().flatMap { process(it, query, lengths)},
                    gq)
            return LocusQueryBrowserModel(id, model, locations, query)
        }

        private fun process(chr: Chromosome,
                            query: GenomeLocusQuery<Chromosome, *>,
                            lengths: TDoubleArrayList): Stream<LocationReference> {

            val locusQuery = query.locusQuery
            if (locusQuery is GeneLocusQuery) {
                return (query.inputQuery as Query<Chromosome, MutableCollection<Any?>>)
                        .process(chr).stream().flatMap { item ->
                    val gene = item as Gene
                    val genLocations = locusQuery.process(gene).filterNotNull();

                    // Add summary locations length as a single record for gene
                    val summary = genLocations.asSequence().map { it.length().toDouble() }.sum()
                    synchronized (lengths) {
                        lengths.add(summary)
                    }

                    genLocations.stream().map { GeneLocRef(gene, it) }
                }
            }

            // Otherwise just use original process method
            val ls = query.process(chr)
            ls.forEach {
                synchronized(lengths) {
                    lengths.add(it.length().toDouble())
                }
            }
            return ls.stream().map { SimpleLocRef(it) }

        }
    }

    init {
        require(!query.isModified) {
            "Query with post modifications is not supported ${query.description}"
        }
    }
}