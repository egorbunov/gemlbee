package org.jetbrains.bio.genome.query

import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.GeneClass

class GenesQuery @JvmOverloads constructor(private val genesClass: GeneClass = GeneClass.ALL) :
        Query<GenomeQuery, List<Gene>> {

    override fun process(genomeQuery: GenomeQuery): List<Gene> {
        val query = ChromosomeGenesQuery(genesClass)
        return genomeQuery.get().flatMap { query.process(it) }
    }

    override val id: String get() = genesClass.id

    override val description: String get() = genesClass.description

}
