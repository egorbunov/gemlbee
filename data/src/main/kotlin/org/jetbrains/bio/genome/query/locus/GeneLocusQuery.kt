package org.jetbrains.bio.genome.query.locus

import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.containers.locationList
import org.jetbrains.bio.genome.containers.minus
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*

abstract class GeneLocusQuery protected constructor(locusType: LocusType.GeneType) :
        LocusQuery<Gene>(locusType) {

    companion object {
        private val WIDTH_PATTERN = "(tss|tes)([-_ ]?)([\\d]+)".toPattern()
        private val BOUNDS_PATTERN =
                "(tss|tes)([_ \\(\\[]?)([+-]?[\\d]+)(([_;, ]?)|\\.\\.)([+-]?[\\d]+)([\\)\\]]?)".toPattern()

        @JvmStatic fun of(text: String): LocusQuery<Gene>? {
            val locusType = if ("genes" == text.toLowerCase()) {
                LocusType.WHOLE_GENE
            } else {
                LocusType.of(text.toLowerCase())
            }

            if (locusType is LocusType.GeneType) {
                return locusType.createQuery()
            }
            val locusWidthMatcher = WIDTH_PATTERN.matcher(text.toLowerCase())
            if (locusWidthMatcher.matches()) {
                val size = Integer.valueOf(locusWidthMatcher.group(3).trim { it <= ' ' })!!
                when (locusWidthMatcher.group(1)) {
                    "tss" -> return TssQuery(-size, size)
                    "tes" -> return TesQuery(-size, size)
                }
            }

            val locusBoundsMatcher = BOUNDS_PATTERN.matcher(text.toLowerCase())
            if (locusBoundsMatcher.matches()) {
                val left = locusBoundsMatcher.group(3).trim().toInt()
                var right = locusBoundsMatcher.group(6).trim().toInt()
                if (left >= 0 && right < 0) {
                    right = -right
                }
                if ((left < 0) and (right < left)) {
                    right = -right
                }

                when (locusBoundsMatcher.group(1)) {
                    "tss" -> return TssQuery(left, right)
                    "tes" -> return TesQuery(left, right)
                }
            }
            return null
        }
    }
}

class CDSQuery : GeneLocusQuery(LocusType.CDS) {
    override fun process(gene: Gene): Collection<Location> {
        val cdsLocation = gene.cds
        return if (cdsLocation == null) emptyList() else listOf(cdsLocation)
    }
}

class ExonsExceptFirstQuery : GeneLocusQuery(LocusType.EXONS_EXCEPT_FIRST) {
    override fun process(gene: Gene): Collection<Location> {
        val exons = gene.exons
        return if (exons.isEmpty()) exons else exons.drop(1)
    }
}

class ExonsQuery : GeneLocusQuery(LocusType.EXONS) {
    override fun process(gene: Gene) = gene.exons
}

class FirstExonQuery : GeneLocusQuery(LocusType.FIRST_EXON) {
    override fun process(gene: Gene) = gene.exons.take(1)
}

class FirstIntronQuery : GeneLocusQuery(LocusType.FIRST_INTRON) {
    override fun process(gene: Gene) = gene.introns.take(1)
}

class GeneTranscriptQuery : GeneLocusQuery(LocusType.TRANSCRIPT) {
    override fun process(gene: Gene) = listOf(gene.location)
}

class IntronsExceptFirstQuery : GeneLocusQuery(LocusType.INTRONS_EXCEPT_FIRST) {
    override fun process(gene: Gene) = gene.introns.drop(1)
}

class IntronsQuery : GeneLocusQuery(LocusType.INTRONS) {
    override fun process(gene: Gene) = gene.introns
}

class LastIntronQuery : GeneLocusQuery(LocusType.LAST_INTRON) {
    override fun process(gene: Gene): Collection<Location> {
        val introns = gene.introns
        return if (introns.isEmpty()) emptyList() else listOf(introns.last())
    }
}

class NonRepeatsQuery : LocusQuery<Chromosome>(LocusType.NON_REPEATS) {
    override fun process(chromosome: Chromosome): Collection<Location> {
        val genomeQuery = GenomeQuery(chromosome.genome.build)
        val repeats = locationList(genomeQuery, chromosome.repeats.map { it.location })

        return Strand.values().flatMap {
            val location = chromosome.range.on(chromosome).on(it)
            location - repeats[chromosome, Strand.PLUS]
        }
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

/** Transcription end site query. */
class TesQuery @JvmOverloads constructor(
        val leftBound: Int = -2000,
        val rightBound: Int = 2000): GeneLocusQuery(LocusType.TES) {

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
 *
 * See [org.jetbrains.bio.genome.ImportantGenesAndLoci] for information
 * on defaults values.
 */
class TssQuery @JvmOverloads constructor(
        val leftBound: Int = -2000,
        val rightBound: Int = 2000): GeneLocusQuery(LocusType.TSS) {

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
class WholeGeneQuery : GeneLocusQuery(LocusType.WHOLE_GENE) {
    override fun process(gene: Gene): List<Location> {
        return listOf(RelativePosition.AROUND_WHOLE_SEGMENT.of(
                gene.location, -2000, 2000))
    }
}
