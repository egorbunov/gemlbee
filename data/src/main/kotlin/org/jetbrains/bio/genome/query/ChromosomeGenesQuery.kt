package org.jetbrains.bio.genome.query

import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.GeneClass

class ChromosomeGenesQuery(private val genesClass: GeneClass) :
        Query<Chromosome, Collection<Gene>> {

    override fun process(chromosome: Chromosome): Collection<Gene> {
        return if (genesClass === GeneClass.ALL) {
            chromosome.genes
        } else {
            chromosome.genes.filter { it in genesClass }
        }
    }

    override val id: String get() = genesClass.id

    override val description: String get() {
        return "Chromosome genes ${genesClass.description}"
    }
}
