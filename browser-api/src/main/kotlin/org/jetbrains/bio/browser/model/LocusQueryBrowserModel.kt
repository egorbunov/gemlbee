package org.jetbrains.bio.browser.model

import org.jetbrains.bio.ext.parallelStream
import org.jetbrains.bio.ext.stream
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.query.GenomeLocusQuery
import org.jetbrains.bio.genome.query.locus.GeneLocusQuery
import java.util.stream.Stream

class LocusQueryBrowserModel private constructor(
        id: String,
        originalModel: BrowserModel,
        locationReferences: List<LocationReference>,
        val query: GenomeLocusQuery<Chromosome, *>)
: MultipleLocationsBrowserModel(id, originalModel, locationReferences) {

    init {
        require(!query.isModified) {
            "Query with post modifications is not supported ${query.description}"
        }
    }

    companion object {
        @JvmStatic fun create(id: String,
                              query: GenomeLocusQuery<Chromosome, *>,
                              model: BrowserModel): LocusQueryBrowserModel {
            val gq = model.genomeQuery
            val locations = MultipleLocationsBrowserModel.filter(
                    gq.get().parallelStream().flatMap { process(it, query)},
                    gq)
            return LocusQueryBrowserModel(id, model, locations, query)
        }

        private fun process(chr: Chromosome,
                            query: GenomeLocusQuery<Chromosome, *>): Stream<LocationReference> {
            val locusQuery = query.locusQuery
            return if (locusQuery is GeneLocusQuery) {
                query.inputQuery.process(chr).stream().flatMap { item ->
                    val gene = item as Gene
                    locusQuery.process(gene).stream()
                            .filter { it != null }
                            .map { GeneLocRef(gene, it) }
                }
            } else {
                query.process(chr).stream().map { SimpleLocRef(it) }
            }
        }
    }
}