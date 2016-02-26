package org.jetbrains.bio.data

import org.apache.log4j.Logger
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.jetbrains.bio.genome.query.locus.GeneLocusQuery
import org.jetbrains.bio.genome.query.locus.LocusQuery

/**
 * POI - Points Of Interest
 */
class POI(val list: List<String>, val allLoci: List<LocusQuery<Gene>> = ImportantGenesAndLoci.REGULATORY) {

    companion object {
        val LOG = Logger.getLogger(POI::class.java)

        val TRANSCRIPTION = "transcription"
        val ALL = "all"
        val AT = '@'
    }

    operator fun get(modification: String): List<LocusQuery<Gene>> {
        val loci = linkedSetOf<LocusQuery<Gene>>()
        if (list.contains(ALL)) {
            loci.addAll(allLoci)
        }

        // Process all@LOCUS
        list
                .filter { it.startsWith("$ALL$AT") }
                .map { it.substringAfter(AT) }
                .filter { it.isNotBlank() }
                .forEach {
                    val locus = GeneLocusQuery.of(it)
                    if (locus != null) {
                        loci.add(locus)
                    } else {
                        LOG.error("Unknown locus $it")
                    }
                }

        // Process modification@all and modification@LOCUS
        list
                .filter { it.contains(AT) }
                .filter { modification == it.substringBefore(AT) }
                .map { it.substringAfter(AT) }
                .filter { it.isNotBlank() }
                .forEach {
                    if (ALL == it) {
                        loci.addAll(allLoci)
                    } else {
                        val geneLocusQuery = GeneLocusQuery.of(it)
                        if (geneLocusQuery != null) {
                            loci.add(geneLocusQuery)
                        } else {
                            LOG.error("Unknown locus $it")
                        }
                    }
                }
        return loci.toList()
    }

    fun transcription(): Boolean = TRANSCRIPTION in list || ALL in list

    /**
     * Unwraps "all" notations
     */
    fun full(config: DataConfig): List<String> {
        val result = arrayListOf<String>()
        (object: DataConfigurator {
            override fun invoke(dataTypeId: String, condition: CellId, section: Section) {
                when (dataTypeId.toDataType()) {
                    DataType.CHIP_SEQ, DataType.METHYLOME -> {
                        result.addAll(get(dataTypeId).map { "$dataTypeId$AT${it.id}" })
                    }
                    DataType.TRANSCRIPTOME -> {
                        if (transcription()) {
                            result.add(TRANSCRIPTION)
                        }
                    }
                    else -> Unit  // no-op.
                }
            }
        })(config)
        return result.distinct().sorted().toMutableList()
    }

}