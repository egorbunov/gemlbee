package org.jetbrains.bio.data

import com.esotericsoftware.yamlbeans.YamlReader
import com.esotericsoftware.yamlbeans.YamlWriter
import org.jetbrains.bio.ext.toPath
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.jetbrains.bio.genome.query.GenomeQuery
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import java.util.*


class DataConfig() {
    /**
     * Human-readable identifier of the configuration.
     * This id will be used as a folder name for experiments output.
     */
    @JvmField var id: String? = null
    @JvmField var genome = "<unknown>"

    /**
     * A temporary object for loading weakly-typed YAML data.
     * Must be public due to `yamlbeans` design.
     * IMPORTANT: default values are not serialized by `yamlbeans` design!
     */
    @JvmField var tracks = LinkedHashMap<String, HashMap<String, Any>>()

    /**
     * Additional config for [RawDataExperiment] and [PredicatesExperiment]
     */
    @JvmField var poi: List<String> = DEFAULT_POI

    /**
     * Additional config for [RulesExperiment], etc.
     */
    @JvmField var rules: List<String> = DEFAULT_RULES
    @JvmField var rule_max_complexity: Int = DEFAULT_RULE_MAX_COMPLEXITY
    @JvmField var rule_min_support: Int = DEFAULT_RULE_MIN_SUPPORT
    @JvmField var rule_min_conviction: Double = DEFAULT_RULE_MIN_CONVICTION
    @JvmField var rule_top: Int = DEFAULT_RULE_TOP
    @JvmField var rule_output: Int = DEFAULT_RULE_OUTPUT
    @JvmField var rule_regularizer: Double = DEFAULT_RULE_REGULARIZER

    /** A genome query, which specifies genome build and chromosome restriction. */
    val genomeQuery: GenomeQuery by lazy {
        GenomeQuery.Companion.parse(genome)
    }

    /** Configured tracks. */
    @Suppress("UNCHECKED_CAST")
    val tracksMap: LinkedHashMap<Pair<String, CellId>, Section> by lazy {
        /**
         * Transforms tracks into typed structure for tracks, i.e. Map modification -> Map CellId -> Section
         * TODO: validation and sane error messages.
         */
        val map = LinkedHashMap<Pair<String, CellId>, Section>()
        for ((dataType, inner) in tracks) {
            for ((condition, replicates) in inner.mapKeys { CellId[it.key] }) {
                val section = when (replicates) {
                    is List<*> -> Section.Implicit(replicates.map { it.toString().toPath() })
                    is Map<*, *> -> Section.Labeled(
                            replicates.mapValues { it.value.toString().toPath() } as Map<String, Path>)
                    else -> throw IllegalStateException()
                }

                map.put(dataType to condition, section)
            }
        }
        return@lazy map
    }

    /** Saves configuration in a YAML file. */
    fun save(writer: Writer) {
        writer.append("# This file was generated automatically.\n" +
                FORMAT.lines().map { "# $it" }.joinToString("\n") + "\n")
        // Here we use the fact that YamlBean doesn't save default values.
        val yaml = YamlWriter(writer)
        with(yaml.config) {
            writeConfig.setWriteRootTags(false)
            writeConfig.setWriteRootElementTags(false)
        }
        yaml.write(this)
        yaml.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataConfig) return false

        if (id != other.id) return false
        if (genome != other.genome) return false
        if (tracks != other.tracks) return false
        if (poi != other.poi) return false
        if (rules != other.rules) return false
        if (rule_max_complexity != other.rule_max_complexity) return false
        if (rule_min_support != other.rule_min_support) return false
        if (rule_min_conviction != other.rule_min_conviction) return false
        if (rule_top != other.rule_top) return false
        if (rule_output != other.rule_output) return false
        if (rule_regularizer != other.rule_regularizer) return false

        return true
    }

    fun ruleParams(): String {
        val copy = DataConfig()
        copy.rule_max_complexity = rule_max_complexity
        copy.rule_min_conviction = rule_min_conviction
        copy.rule_min_support = rule_min_support
        copy.rule_top = rule_top
        copy.rule_output = rule_output
        copy.rule_regularizer = rule_regularizer
        val writer = StringWriter()
        val yaml = YamlWriter(writer)
        with(yaml.config) {
            writeConfig.setWriteRootTags(false)
            writeConfig.setWriteRootElementTags(false)
        }
        yaml.write(copy)
        yaml.close()
        return writer.toString().trim().replace("\n", "_").replace(" ", "").replace(":", "_").replace("{}", "")
    }

    override fun hashCode(): Int {
        return Objects.hash(id, genome, tracks,
                poi, rules,
                rule_max_complexity, rule_min_conviction, rule_top, rule_output, rule_regularizer)
    }


    companion object {
        val ALL = "all"
        val POI = "poi"
        val NO = "NO"
        val POI_PLUS = "poi+"
        val GENES_FEATURES = "genes_features"

        val DEFAULT_RULE_MIN_SUPPORT: Int = 1000
        val DEFAULT_RULE_MIN_CONVICTION: Double = 1.0
        val DEFAULT_RULE_MAX_COMPLEXITY: Int = 5
        val DEFAULT_RULE_TOP: Int = 10
        val DEFAULT_RULE_OUTPUT: Int = 1
        val DEFAULT_RULE_REGULARIZER: Double = 2.0
        val DEFAULT_POI: List<String> = arrayListOf(ALL)
        val DEFAULT_RULES: List<String> = arrayListOf("$POI, $GENES_FEATURES => $POI_PLUS")

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
│   ├── hg38.2bit...
"""

        val SUPPORTED_FILE_FORMATS = """Supported file formats:
- *.bed, *.bed.gz, *.bed.zip for ChIP-Seq
- *.bam for BS-Seq
- *.fastq, *.fastq.gz file/folder for transcriptome"""

        val POI_DESCRIPTION = """POI:
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


        val RULES_DESCRIPTION = """rules:
If given configures patterns for rules mining.
Default:
${DEFAULT_RULES.first()}
- $POI          # patterns from $POI section.
- $POI_PLUS     # patterns from $POI section without $NO predicates
- $GENES_FEATURES = genes characteristics, CpG content, ontology, etc.
The semantics is as following:
If there are at least 2 different predicates on the left side, rule mining is performed.
In case of a single clause even with different params, different parameters are evaluated.
Examples:
- poi => H3K4me3[{10,100,50}%][0.5]@tss    # Process rule mining for each target predicate.
- transcription => poi                     # Evaluate all the rules with transcription as condition.
Evaluates all the rules for all parameters combinations:
- H3K4me3[{10,100,50}%][0.5]@tss AND transcription[{0, 100, 10}] => methylation@tss"""

        val RULE_MIN_SUPPORT_DESCRIPTION = """rule_min_support:
If given limits min rule support of generated rules. Default: $DEFAULT_RULE_MIN_SUPPORT"""

        val RULE_MAX_COMPLEXITY_DESCRIPTION = """rule_max_complexity:
If given limits max complexity of generated rules. Default: $DEFAULT_RULE_MAX_COMPLEXITY"""

        val RULE_MIN_CONVICTION_DESCRIPTION = """rule_min_conviction:
If given limits min conviction of generated rules. Default: $DEFAULT_RULE_MIN_CONVICTION"""

        val RULE_TOP_DESCRIPTION = """rule_top:
If given configures rule mining algorithm precision, i.e. guaranteed top for each target. Default: $DEFAULT_RULE_TOP"""

        val RULE_OUT_DESCRIPTION = """rule_output:
If given configures number of rules to output for each target. Default: $DEFAULT_RULE_OUTPUT"""

        val RULE_REGULARIZER_DESCRIPTION = """rule_regularizer:
If given configures regularization coefficient, i.e. fine = coefficient ^ complexity(condition). Default: $DEFAULT_RULE_REGULARIZER"""

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
$POI_DESCRIPTION
---
$RULES_DESCRIPTION
---
$RULE_MIN_CONVICTION_DESCRIPTION
---
$RULE_MIN_SUPPORT_DESCRIPTION
---
$RULE_MAX_COMPLEXITY_DESCRIPTION
---
$RULE_TOP_DESCRIPTION
---
$RULE_OUT_DESCRIPTION
---
$RULE_REGULARIZER_DESCRIPTION"""

        /** Loads configuration from a YAML file. */
        fun load(reader: Reader): DataConfig {
            val yaml = YamlReader(reader)
            return yaml.read(DataConfig::class.java)
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
        for ((key, section) in configuration.tracksMap.entries) {
            val (dataTypeId, condition) = key
            this(dataTypeId, condition, section)
        }
    }

    operator fun invoke(dataTypeId: String, condition: CellId, section: Section) {
    }
}

fun DataSet.toDataConfig() = createDataConfig(this)

fun createDataConfig(dataSet: DataSet): DataConfig {
    val genomeQuery = dataSet.genome.toQuery()
    val config = DataConfig()
    config.id = dataSet.id
    config.genome = genomeQuery.getShortNameWithChromosomes()
    config.tracks = LinkedHashMap<String, HashMap<String, Any>>()
    if (dataSet is ChipSeqDataSet) {
        // Keep consistency, modifications / cell / tracks
        for (target in dataSet.chipSeqTargets) {

            for (cellId in dataSet.getCellIds(DataType.CHIP_SEQ)) {
                val paths = dataSet.getTracks(genomeQuery, cellId, target).map { it.path }
                if (paths.isNotEmpty()) {
                    if (target.name !in config.tracks) {
                        config.tracks[target.name] = hashMapOf()
                    }
                    config.tracks[target.name]!![cellId.name] = paths.map { it.toString() }.toList()
                }
            }
        }
    }

    if (dataSet is TranscriptomeDataSet) {
        val genome = genomeQuery.genome
        for (cellId in dataSet.getCellIds(DataType.TRANSCRIPTION)) {
            val replicates = dataSet.getTranscriptomeReplicates(cellId).map { replicate ->
                val fastqReads = dataSet.getTranscriptomeQuery(genome, cellId, replicate).fastqReads
                val parents = fastqReads.asSequence().map { it.parent }.distinct().toList()
                check(parents.size == 1) { "multi-directory replicates aren't supported" }
                replicate to parents.first()
            }.toMap()

            if (replicates.isNotEmpty()) {
                if (DataType.TRANSCRIPTION.id !in config.tracks) {
                    config.tracks[DataType.TRANSCRIPTION.id] = hashMapOf()
                }
                if (replicates.size == 1) {
                    // Do not use labels in case of single replicate
                    config.tracks[DataType.TRANSCRIPTION.id]!![cellId.name] =
                            replicates.values.map { it.toString() }.toList()
                } else {
                    // User HashMap as YamlBeans can have problems with others
                    config.tracks[DataType.TRANSCRIPTION.id]!![cellId.name] =
                            HashMap(replicates.mapValues { it.value.toString() })
                }
            }
        }
    }

    if (dataSet is MethylomeDataSet) {
        for (cellId in dataSet.getCellIds(DataType.METHYLATION)) {
            val replicates = dataSet.getMethylomeReplicates(cellId).map { replicate ->
                replicate to dataSet.getMethylomePath(genomeQuery, cellId, replicate)
            }.toMap()
            if (replicates.isNotEmpty()) {
                if (DataType.METHYLATION.id !in config.tracks) {
                    config.tracks[DataType.METHYLATION.id] = hashMapOf()
                }
                if (replicates.size == 1) {
                    // Do not use labels in case of single replicate
                    config.tracks[DataType.METHYLATION.id]!![cellId.name] =
                            replicates.values.map { it.toString() }.toList()
                } else {
                    // User HashMap as YamlBeans can have problems with others
                    config.tracks[DataType.METHYLATION.id]!![cellId.name] =
                            HashMap(replicates.mapValues { it.value.toString() })
                }
            }
        }
    }
    return config
}