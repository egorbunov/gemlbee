@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package org.jetbrains.bio.genome.query.locus

import org.jetbrains.bio.data.POI
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.containers.locationList
import org.jetbrains.bio.genome.containers.minus
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.Query
import org.jetbrains.bio.util.Lexeme
import org.jetbrains.bio.util.RegexLexeme
import org.jetbrains.bio.util.Tokenizer
import org.jetbrains.bio.util.parseIntOrStep
import java.util.*

/**
 * Marker class for LocusQuery<Gene>
 */
abstract class GeneLocusQuery(locusType: LocusType<Gene>) : LocusQuery<Gene>(locusType)

class CDSQuery : GeneLocusQuery(LocusType.CDS) {
    override fun process(gene: Gene): Collection<Location> {
        val cdsLocation = gene.cds
        return if (cdsLocation == null) emptyList() else listOf(cdsLocation)
    }
}

class ExonsQuery : GeneLocusQuery(LocusType.EXONS) {
    override fun process(gene: Gene) = gene.exons
}

class TranscriptQuery : GeneLocusQuery(LocusType.TRANSCRIPT) {
    override fun process(gene: Gene) = listOf(gene.location)
}

class IntronsQuery : GeneLocusQuery(LocusType.INTRONS) {
    override fun process(gene: Gene) = gene.introns
}

/** Transcription end site query. */
class TesQuery @JvmOverloads constructor(
        val leftBound: Int = -2000,
        val rightBound: Int = 2000) : GeneLocusQuery(LocusType.TES) {

    init {
        check(leftBound < rightBound)
    }

    override fun process(gene: Gene): Collection<Location> {
        return listOf(RelativePosition.AROUND_END.of(
                gene.location, leftBound, rightBound))
    }

    override val id: String get() = "${super.id}[$leftBound..$rightBound]"

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other == null || other !is TesQuery -> false
        else -> leftBound == other.leftBound && rightBound == other.rightBound
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), leftBound, rightBound)
    }
}

/**
 * Transcription start site query.
 * See [ImportantGenesAndLoci] for information on defaults values.
 */
class TssQuery @JvmOverloads constructor(
        val leftBound: Int = -2000,
        val rightBound: Int = 2000) : GeneLocusQuery(LocusType.TSS) {

    init {
        check(leftBound < rightBound)
    }

    override fun process(gene: Gene): Collection<Location> {
        return listOf(RelativePosition.AROUND_START.of(
                gene.location, leftBound, rightBound))
    }

    override val id: String get() = "${super.id}[$leftBound..$rightBound]"

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other == null || other !is TssQuery -> false
        else -> leftBound == other.leftBound && rightBound == other.rightBound
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), leftBound, rightBound)
    }
}

class UTR3Query : GeneLocusQuery(LocusType.UTR3) {
    override fun process(gene: Gene): Collection<Location> {
        val utr3Location = gene.utr3
        return if (utr3Location == null) emptyList() else listOf(utr3Location)
    }
}

class UTR5Query : GeneLocusQuery(LocusType.UTR5) {
    override fun process(gene: Gene): Collection<Location> {
        val utr5Location = gene.utr5
        return if (utr5Location == null) emptyList() else listOf(utr5Location)
    }
}

/** Gene transcript with TSS and TES. */
class TssGeneTesQuery : GeneLocusQuery(LocusType.TSS_GENE_TES) {
    override fun process(gene: Gene): List<Location> {
        return listOf(RelativePosition.AROUND_WHOLE_SEGMENT.of(gene.location, -2000, 2000))
    }
}

class RepeatsQuery(private val repeatClass: String? = null) :
        LocusQuery<Chromosome>(LocusType.REPEATS) {

    override fun process(chromosome: Chromosome): Collection<Location> {
        return chromosome.repeats.asSequence().filter { repeat ->
            repeatClass == null || repeat.repeatClass == repeatClass
        }.map { it.location }.toList()
    }

    override val id: String get() {
        return super.id + if (repeatClass == null) "" else "[$repeatClass]"
    }

    companion object {
        // All the types of repeats available.
        @JvmField val REPEATS_QUERIES: List<LocusQuery<Chromosome>> = listOf(
                RepeatsQuery("line"),
                RepeatsQuery("sine"),
                RepeatsQuery("ltr"),
                RepeatsQuery())
    }
}

class NonRepeatsQuery : LocusQuery<Chromosome>(LocusType.NON_REPEATS) {
    override fun process(chromosome: Chromosome): Collection<Location> {
        val genomeQuery = GenomeQuery(chromosome.genome.build, chromosome.name)
        val repeats = locationList(genomeQuery, chromosome.repeats.map { it.location })

        return Strand.values().flatMap {
            val location = chromosome.range.on(chromosome).on(it)
            location - repeats[chromosome, Strand.PLUS]
        }
    }
}

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

    companion object {
        /**
         * Computes all the Locus for given string, with parameters and steps notation i.e.
         * TSS[500]
         * TSS[-2000..2000]
         * TSS[{-2000,-1000,500}..{0,1000,500}]
         */
        fun parse(pattern: String): List<LocusQuery<Gene>> {
            val delimiter = RegexLexeme("[_;]|\\.\\.")
            val lBracket = Lexeme("[")
            val rBracket = Lexeme("]")
            val KEYWORDS = setOf(delimiter, lBracket, rBracket, Tokenizer.LBRACE, Tokenizer.RBRACE, Tokenizer.COMMA)
            if (pattern == POI.ALL) {
                return ImportantGenesAndLoci.REGULATORY
            }
            // Empty case, no params
            val locusType = if ("genes" == pattern.toLowerCase()) {
                LocusType.TSS_GENE_TES
            } else {
                LocusType.of(pattern.toLowerCase())
            }
            if (locusType is LocusType.GeneType) {
                return listOf(locusType.createQuery())
            }
            // Parse width or params
            if (pattern.startsWith(LocusType.TSS.toString()) || pattern.startsWith(LocusType.TES.toString())) {
                var tokenizer = Tokenizer(pattern.substring(3), KEYWORDS)
                val bracketsFound = tokenizer.fetch() == lBracket
                if (bracketsFound) {
                    tokenizer.next()
                }
                val values = tokenizer.parseIntOrStep()
                var ends: List<Int>? = null
                if (tokenizer.fetch() == delimiter) {
                    tokenizer.next()
                    ends = tokenizer.parseIntOrStep()
                }
                if (bracketsFound) {
                    tokenizer.check(rBracket)
                }
                tokenizer.checkEnd()

                // Only width configured
                if (ends == null) {
                    when (pattern.subSequence(0, 3)) {
                        "tss" -> return values.map { TssQuery(-it, it) }
                        "tes" -> return values.map { TesQuery(-it, it) }
                    }
                } else {
                    when (pattern.subSequence(0, 3)) {
                        "tss" -> return values.flatMap { start ->
                            ends!!.map { end -> TssQuery(start, end) }
                        }
                        "tes" -> return values.flatMap { start ->
                            ends!!.map { end -> TesQuery(start, end) }
                        }
                    }
                }
            }
            POI.LOG.warn("Failed to parse locus: $pattern")
            return emptyList()
        }
    }
}
