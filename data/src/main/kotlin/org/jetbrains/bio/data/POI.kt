package org.jetbrains.bio.data

import org.apache.log4j.Logger
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.jetbrains.bio.genome.query.locus.LocusQuery

/**
 * POI - Points Of Interest, see [DESCRIPTION] for details.
 */
class POI(val list: List<String>) {

    companion object {
        val LOG = Logger.getLogger(POI::class.java)

        val TRANSCRIPTION = "transcription"
        val ALL = "all"
        val AT = '@'

        // Params specific tokens.
        val LBRACKET = "["
        val RBRACKET = "]"
        val PERCENT = "%"

        val DESCRIPTION = """POI:
POI = points of interest and predicates description.

All regulatory loci:
- ${ImportantGenesAndLoci.REGULATORY.map { it.id }.sorted().joinToString(", ")}

poi:
- all                       # All modifications x all regulatory loci + transcription
- H3K4me3@all               # Modification at all regulatory loci
- H3K4me3[80%][0.5]@all     # ChIP-Seq predicate, exist 80% range with >= 0.5 enrichment fraction
- H3K4me3[1000][0.8]@all    # ChIP-Seq predicate, exist range of length = 1000 with >= 0.8 enrichment fraction
- all@tss[-2000..2000]      # All modifications at given locus
- meth@exons                # Methylation at given locus
- meth[10][0.5]@tss         # Methylation predicate at least 0.5 enriched cytosines among at least 10 covered
- transcription             # Transcription, i.e. tpm abundance + 25, 50, 75, 100 percentile discrimination

Step notation is available for any kind of parameters, i.e.
- tss[{-2000,-1000,500}..{1000,2000,500}] # Creates TSS with start at -2000 up to 1000 with step 500, etc.
- H3K4me3[{10,90,10}%][0.5]@all           # Creates predicates with different percentage parameters."""

        fun parameters(poi: String): String {
            if (poi == ALL) {
                return ""
            }
            val beforeAt = poi.substringBefore(AT)
            return if (LBRACKET in beforeAt) LBRACKET + beforeAt.substringAfter(LBRACKET) else ""
        }

        fun locus(poi: String): String {
            if (poi == ALL) {
                return poi
            }
            return poi.substringAfter(AT)
        }

    }

    fun lociForModification(modification: String): List<LocusQuery<Gene>> {
        return filterByModification(modification).flatMap { LocusQuery.parse(it) }.distinct()
    }

    fun filterByModification(modification: String): List<String> {
        return list.filter {
            // Process all OR all@LOCUS
            if (it == ALL || it.startsWith("$ALL$AT")) {
                return@filter true
            }
            // Process predicate with parameters?
            if (modification == it.substringBefore(AT).substringBefore(LBRACKET)) {
                return@filter true
            }
            return@filter false
        }.distinct()
    }

    fun transcription(): Boolean = filterByModification(TRANSCRIPTION).isNotEmpty()
}