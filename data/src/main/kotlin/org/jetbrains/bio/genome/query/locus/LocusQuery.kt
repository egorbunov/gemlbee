package org.jetbrains.bio.genome.query.locus

import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.LocusType
import org.jetbrains.bio.genome.query.Query

abstract class LocusQuery<Input> protected constructor(protected val locusType: LocusType<*>) :
        Query<Input, Collection<Location>> {

    override val id: String get() = locusType.toString()

    override fun toString() = id

    override fun equals(other: Any?) = when {
        this === other -> true
        other == null || other !is LocusQuery<*> -> false
        else -> locusType == other.locusType
    }

    override fun hashCode() = locusType.hashCode()
}
