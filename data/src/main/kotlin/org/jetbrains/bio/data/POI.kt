package org.jetbrains.bio.data

import org.apache.log4j.Logger
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.jetbrains.bio.genome.query.locus.LocusQuery

/**
 * POI - Points Of Interest, see [DESCRIPTION] for details.
 */
class POI(val patterns: List<String>) {
    companion object {
        val LOG = Logger.getLogger(POI::class.java)

        val TRANSCRIPTION = DataType.TRANSCRIPTION.id
        val NO = "NO "
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
- methylation@exons         # Methylation at given locus
- methylation[10][0.5]@tss  # Methylation predicate at least 0.5 enriched cytosines among at least 10 covered
- transcription             # Transcription, i.e. tpm abundance separated by threshold.

Step notation is available for any kind of parameters, i.e.
- tss[{-2000,-1000,500}..{1000,2000,500}] # Creates TSS with start at -2000 up to 1000 with step 500, etc.
- H3K4me3[{10,90,10}%][0.5]@all           # Creates predicates with different percentage parameters.
- transcription[0.001]                    # Creates transcription predicate with threshold = 0.001"""

        /**
         * Extract modification out of predicate or NO predicate.
         * NOTE: predicate doesn't contain all or {start,end,step}.
         */
        fun modification(predicate: String): String {
            check(!(predicate == ALL || predicate.startsWith("$ALL$AT"))) {
                "$ALL is not allowed as predicate name!"
            }
            return predicate
                    .substringAfter(NO)
                    .substringBefore(POI.AT)
                    .substringBefore(POI.LBRACKET)
        }

        /**
         * Extract parameters from pattern.
         */
        fun parameters(pattern: String): String {
            if (pattern == ALL) {
                return ""
            }
            val beforeAt = pattern.substringBefore(AT)
            return if (LBRACKET in beforeAt) LBRACKET + beforeAt.substringAfter(LBRACKET) else ""
        }

        /**
         * Return locus part for pattern.
         * Locus can be transformed to list of [LocusQuery] with [LocusQuery.parse] call.
         */
        fun locus(pattern: String): String {
            if (pattern == ALL) {
                return pattern
            }
            return pattern.substringAfter(AT)
        }

        /**
         * Returns true if poi is applicable for given modification.
         * See [modification] for details.
         */
        fun isApplicable(modification: String, pattern: String): Boolean {
            // Process all OR all@LOCUS
            if (pattern == ALL || pattern.startsWith("$ALL$AT")) {
                return true
            }
            val patternModification = modification(pattern)
            if (patternModification == modification) {
                return true
            }
            return false
        }
    }

    fun lociForModification(modification: String): List<LocusQuery<Gene>> {
        return patternsForModification(modification)
                .map { it.substringAfter(POI.AT) }
                .filter { it.isNotEmpty() }
                .flatMap { LocusQuery.parse(it) }.distinct()
    }

    fun patternsForModification(modification: String): List<String> {
        return patterns.filter { isApplicable(modification, it) }.distinct()
    }

    fun transcription(): Boolean = patternsForModification(TRANSCRIPTION).isNotEmpty()
}