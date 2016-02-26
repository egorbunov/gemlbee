package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.GeneAliasType
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.LocationAware

interface LocationReference : LocationAware {
    val metaData: Any?
    val name: String
    fun update(newLoc: Location): LocationReference
}

data class SimpleLocRef(override val location: Location) : LocationReference {
    override val metaData: Any?
        get() = null;

    override val name: String
        get() = ""

    override fun update(newLoc: Location) = SimpleLocRef(newLoc)
}

data class GeneLocRef @JvmOverloads constructor(
        val gene: Gene,
        override val location: Location = gene.location
) : LocationReference {

    override val metaData: Gene
        get() = gene

    override val name: String
        get() = gene.getName(GeneAliasType.GENE_SYMBOL);

    override fun update(newLoc: Location) = GeneLocRef(gene, newLoc)
}