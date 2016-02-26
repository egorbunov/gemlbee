package org.jetbrains.bio.data

import com.esotericsoftware.yamlbeans.YamlReader
import com.esotericsoftware.yamlbeans.YamlWriter
import org.jetbrains.bio.ext.toPath
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.query.GenomeQuery
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import java.util.*

class DataConfig(
        /**
         * Human-readable identifier of the configuration.
         *
         * Use `""` if you don't have a meaningful identifier.
         */
        val id: String,
        /** A genome query, which specifies genome build and chromosome restriction. */
        val genomeQuery: GenomeQuery,
        /** Configured tracks. */
        val tracks: LinkedHashMap<Pair<String, CellId>, Section>,
        /** Points of interest */
        val poi: POI = POI(listOf(POI.ALL))) {

    /** Saves configuration in a YAML file. */
    fun save(writer: Writer,
             printComment: Boolean = true,
             printId: Boolean = true,
             printGenome: Boolean = true,
             printPoi: Boolean = true,
             printTracks: Boolean = true) {
        if (printComment) {
            writer.append("# This file was generated automatically.\n" +
                    FORMAT.lines().map { "# $it" }.joinToString("\n") + "\n")
        }
        val proxy = Proxy()
        // Here we use the fact that YamlBean doesn't save default values.
        if (printId) {
            proxy.id = if (id.isEmpty()) null else id
        }
        if (printGenome) {
            proxy.genome = genomeQuery.getShortNameWithChromosomes()
        }
        if (printTracks) {
            proxy.tracks.putAll(tracks.wrap())
        }
        if (printPoi) {
            // Once again Collections#SingletonList issue in `yamlbeans`
            proxy.poi = poi.full(this).toMutableList()
        }

        val yaml = YamlWriter(writer)
        with(yaml.config) {
            writeConfig.setWriteRootTags(false)
            writeConfig.setWriteRootElementTags(false)
        }

        yaml.write(proxy)
        yaml.close()
    }

    @Suppress("unchecked_cast")
    private fun Map<Pair<String, CellId>, Section>.wrap(): Map<String, Map<String, Any>> {
        val acc = HashMap<String, MutableMap<String, Any>>()
        for ((key, section) in entries) {
            val (dataType, condition) = key
            val inner = acc[dataType] ?: HashMap<String, Any>()
            when (section) {
                is Section.Implicit -> inner[condition.name] = section.paths.map { it.toString() }
                is Section.Labeled -> {
                    // YamlBeans doesn't allow to disable tags completely, so
                    // we have to wrap 'LinkedHashMap' used everywhere by Kotlin
                    // into a plain 'HashMap'
                    inner[condition.name] =
                            HashMap(section.replicates.mapValues { it.value.toString() })
                }
            }

            acc[dataType] = inner
        }

        return acc
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as DataConfig
        val writer = StringWriter()
        save(writer)
        val otherWriter = StringWriter()
        other.save(otherWriter)
        return writer.toString() == otherWriter.toString()
    }

    override fun hashCode(): Int {
        val writer = StringWriter()
        save(writer)
        return writer.toString().hashCode()
    }


    companion object {

        val GENOME_DESCRIPTION = """Genome:
See https://genome.ucsc.edu/FAQ/FAQreleases.html
Examples:
- mm10
- hg19[chr1,chr2,chr3]"""

        val MARKUP = """
Path to reference markup folder.
Markup will be downloaded automatically if doesn't exist. Example:
├── hg38
│   ├── cytoBand.txt.gz
│   ├── hg38.2bit
│   ├── ...
"""

        val SUPPORTED_FILE_FORMATS = """Supported file formats:
- *.bed, *.bed.gz, *.bed.zip for ChIP-Seq
- *.bam for BS-Seq
- *.fastq, *.fastq.gz file/folder for transcriptome"""

        val TRACKS_DESCRIPTION = """Tracks:
Each condition is allowed to have multiple replicates. Replicates
can be either implicitly labeled by their position within the
condition or have explicit human-readable labels as in the example below.

With explicit labels:
    <condition>:
        <replicate>: path/to/replicate/data
        <replicate>: path/to/replicate/data

Without labels:
    <condition>:
    - path/to/replicate/data
    - path/to/replicate/data"""

        val POI_DESCRIPTION = """POI:
POI = points of interest, you can use shortcut "all".

poi:
- all                   # All modifications x all regulatory loci + transcription
- H3K4me3@all           # Modification at all regulatory loci
- all@tss[-2000..2000]  # All modifications at given locus
- meth@exons            # Methylation at given locus
- transcription         # Transcription"""

        val FORMAT = """YAML configuration for biological data:
    id: <experiment id>
    genome: <UCSC genome>
    tracks:
        <data type>:
            <condition>:
            - <track>
    poi:
    - <point of interest>

-----
$GENOME_DESCRIPTION

-----
$TRACKS_DESCRIPTION

Supported data types:
* ${DataType.mapper.keys.sorted().joinToString(", ")}

$SUPPORTED_FILE_FORMATS
---
$POI_DESCRIPTION"""

        @JvmStatic fun forDataSet(genomeQuery: GenomeQuery, dataSet: DataSet): DataConfig {
            val tracks = dataSet.collect(genomeQuery)
            check(tracks.isNotEmpty()) { "Failed to collect tracks for $genomeQuery and ${dataSet.id}" }
            return DataConfig(dataSet.id, genomeQuery, tracks)
        }

        /** Loads configuration from a YAML file. */
        fun load(reader: Reader): DataConfig {
            val yaml = YamlReader(reader)
            val proxy = yaml.read(Proxy::class.java)
            return DataConfig(proxy.id ?: "",
                    GenomeQuery.Companion.parse(proxy.genome),
                    proxy.tracks.unwrap(),
                    POI(proxy.poi))
        }

        /**
         * A temporary object for loading weakly-typed YAML data.
         *
         * Turns out it's easier to load a YAML file as a map than to make
         * `yamlbeans` understand how to load our classes.
         *
         * Must be public due to `yamlbeans` design.
         * IMPORTANT: default values are not serialized by `yamlbeans` design!
         */
        class Proxy() {
            @JvmField var id: String? = null
            @JvmField var genome = "<unknown>"
            @JvmField var tracks = LinkedHashMap<String, Map<String, Any>>()
            @JvmField var poi = emptyList<String>()
        }

        // TODO: validation and sane error messages.
        @Suppress("unchecked_cast")
        private fun Map<String, Map<String, Any>>.unwrap(): LinkedHashMap<Pair<String, CellId>, Section> {
            val acc = LinkedHashMap<Pair<String, CellId>, Section>()
            for ((dataType, inner) in this) {
                for ((condition, replicates) in inner.mapKeys { CellId[it.key] }) {
                    val section = when (replicates) {
                        is List<*> -> Section.Implicit(replicates.map { it.toString().toPath() })
                        is Map<*, *> -> Section.Labeled(
                                replicates.mapValues { it.value.toString().toPath() } as Map<String, Path>)
                        else -> throw IllegalStateException()
                    }

                    acc.put(dataType to condition, section)
                }
            }

            return acc
        }
    }
}

/** Replicated data section. */
interface Section {
    /**
     * Each pair here is a labeled replicate, e.g.
     *
     *     "rep1" to "/tmp/data.csv".toPath()
     *
     * Note that the path component can either be a regular file
     * (for ChIP-seq or BS-seq) or a directory (RNA-seq).
     */
    val contents: List<Pair<String, Path>>

    /** Implicitly replicated section. */
    data class Implicit(val paths: List<Path>) : Section {
        override val contents: List<Pair<String, Path>>
            get() = paths.mapIndexed { i, path -> "rep${i + 1}" to path }
    }

    /** A section with per-replicate labels. */
    data class Labeled(val replicates: Map<String, Path>) : Section {
        override val contents: List<Pair<String, Path>>
            get() = replicates.entries.map { it.toPair() }
    }
}

/**
 * An interface for configuring something based on the [DataConfig].
 *
 * This is a dumb name, but I can't think of anything better atm.
 */
interface DataConfigurator {

    operator fun invoke(configuration: DataConfig) {
        for ((key, section) in configuration.tracks.entries) {
            val (dataTypeId, condition) = key
            this(dataTypeId, condition, section)
        }
    }

    operator fun invoke(dataTypeId: String, condition: CellId, section: Section) {
    }
}

private fun DataSet.collect(genomeQuery: GenomeQuery): LinkedHashMap<Pair<String, CellId>, Section> {
    val tracks = LinkedHashMap<Pair<String, CellId>, Section>()
    if (this is ChipSeqDataSet) {
        // Keep consistency, modifications / cell / tracks
        for (target in chipSeqTargets) {
            for (cellId in getCellIds(DataType.CHIP_SEQ)) {
                val paths = getTracks(genomeQuery, cellId, target).map { it.path }
                if (paths.isNotEmpty()) {
                    tracks[target.name to cellId] = Section.Implicit(paths)
                }
            }
        }
    }

    if (this is TranscriptomeDataSet) {
        val genome = genomeQuery.genome
        for (cellId in getCellIds(DataType.TRANSCRIPTOME)) {
            val replicates = getTranscriptomeReplicates(cellId).map { replicate ->
                val fastqReads = getTranscriptomeQuery(genome, cellId, replicate).fastqReads
                val parents = fastqReads.asSequence().map { it.parent }.distinct().toList()
                check(parents.size == 1) { "multi-directory replicates aren't supported" }
                replicate to parents.first()
            }.toMap()
            if (replicates.isNotEmpty()) {
                tracks[DataType.TRANSCRIPTOME.id to cellId] = Section.Labeled(replicates)
            }
        }
    }

    if (this is MethylomeDataSet) {
        for (cellId in getCellIds(DataType.METHYLOME)) {
            val replicates = getMethylomeReplicates(cellId).map { replicate ->
                replicate to getMethylomePath(genomeQuery, cellId, replicate)
            }.toMap()
            if (replicates.isNotEmpty()) {
                tracks[DataType.METHYLOME.id to cellId] = Section.Labeled(replicates)
            }
        }
    }
    return tracks
}