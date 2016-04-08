package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.LocationAware

interface LocationReference : LocationAware {
    val name: String

    // TODO: what does this actually update?
    fun update(newLoc: Location): LocationReference
}

data class SimpleLocRef(override val location: Location) : LocationReference {
    override val name: String
        get() = ""

    override fun update(newLoc: Location) = SimpleLocRef(newLoc)
}

data class GeneLocRef(val gene: Gene,
                      override val location: Location = gene.location ) :
        LocationReference {

    override val name: String get() = gene.symbol

    override fun update(newLoc: Location) = GeneLocRef(gene, newLoc)
}
