package org.jetbrains.bio.data

import org.jetbrains.bio.ext.div
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.Genome
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.histones.BedTrackQuery
import org.jetbrains.bio.methylome.MethylomeQuery
import org.jetbrains.bio.transcriptome.KallistoQuery
import org.jetbrains.bio.util.Configuration
import java.nio.file.Path
import java.util.*

enum class DataType(val id: String) {
    METHYLOME("meth"),
    CHIP_SEQ("chip-seq"),
    TRANSCRIPTOME("rna-seq");

    companion object {
        internal val mapper: Map<String, DataType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val acc = HashMap<String, DataType>()
            ChipSeqTarget.values().forEach { acc[it.name.toLowerCase()] = CHIP_SEQ }
            acc[METHYLOME.id] = METHYLOME
            acc[TRANSCRIPTOME.id] = TRANSCRIPTOME
            acc
        }
    }
}

fun String.toDataType(): DataType {
    return DataType.mapper[toLowerCase()]
           ?: error("Unsupported data type $this, available: " +
                    DataType.mapper.keys.sorted().joinToString(", "))
}

abstract class DataSet protected constructor(
        /**
         * Experiment unique id, e.g. GEO acc. number. If not given
         * class simple name will be used.
         */
        id: String?,
        /** Supported genomes. */
        genome: Genome, vararg rest: Genome) {

    val genomes = listOf(genome, *rest)

    val id = id ?: javaClass.simpleName
    var description = "<no description>"

    /** Absolution path to data root folder. */
    abstract val dataPath: Path

    /** Returns cells available for a given [dataType]. */
    abstract fun getCellIds(dataType: DataType): Collection<CellId>

    override fun toString() = "$id: $description"

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is DataSet -> false
            else -> id == other.id && description == other.description &&
                    genomes == other.genomes
        }
    }

    override fun hashCode() = Objects.hash(id, description, genomes)
}

/**
 * Subclasses are expected to be named as the corresponding GSE
 * accession number. E.g. if the class wraps data from GSE16256
 * it must be named `GSE16256`.
 */
abstract class GeoDataSet protected constructor(genome: Genome, vararg rest: Genome) :
        DataSet(null, genome, *rest) {

    init {
        check(id.matches("GSE[0-9]+".toRegex())) {
            "Subclasses must be named after the GSE accession number."
        }

        description = "http://www.ncbi.nlm.nih.gov/geo/query/acc.cgi?acc=$id"
    }

    val accession: String get() = id

    override val dataPath = Configuration.geoSamplesPath / accession
}

/**
 * An interface for forward-declarations of [DataSet] fields.
 *
 * This might seem like a hack, and it is, but we do need the
 * [id] field in order to get reasonable output from e.g.
 * [MethylomeDataSet].
 */
interface DataSetForwarder {
    val id: String
}

/** An interface for RNA-seq data. */
interface TranscriptomeDataSet : DataSetForwarder {
    /**
     * Returns biological replicates available for a given condition.
     *
     * If there's only a single replicate present, returns `listOf("")`.
     * Note that `""` matches the default value in [getTranscriptomeQuery].
     */
    fun getTranscriptomeReplicates(cellId: CellId): Collection<String> {
        return listOf("")
    }

    fun getTranscriptomeQuery(genome: Genome, cellId: CellId,
                              replicate: String = ""): KallistoQuery
}

/**
 * An interface for BS-seq data.
 *
 * In theory we could've also wrapped other methylome analysis protocols,
 * but in practice we only work with whole-genome BS-seq.
 */
interface MethylomeDataSet : DataSetForwarder {
    /**
     * Returns biological replicates available for a given condition.
     *
     * If there's only a single replicate present, returns `listOf("")`.
     * Note that `""` matches the default value in [getMethylomeQuery].
     */
    fun getMethylomeReplicates(cellId: CellId): Collection<String> {
        return listOf("")
    }

    /** Returns a path to the binary version of methylome. */
    fun getMethylomePath(genomeQuery: GenomeQuery, cellId: CellId,
                         replicate: String = ""): Path = TODO()

    fun getMethylomeQuery(genomeQuery: GenomeQuery, cellId: CellId,
                          replicate: String = ""): MethylomeQuery {
        return MethylomeQuery.forFile(genomeQuery, cellId.toString(),
                                      getMethylomePath(genomeQuery, cellId, replicate),
                                      id)
    }

    @Deprecated("Please update to the replicate-only API.")
    fun getMethylomeQueryLegacy(genomeQuery: GenomeQuery, cellId: CellId,
                                vararg rest: String): MethylomeQuery {
        throw UnsupportedOperationException()
    }
}

/** A target in the ChIP-seq protocol. */
enum class ChipSeqTarget(val isInput: Boolean = false) {
    // Histones
    H2AZ(),

    H3K18ac(),
    H3K4me1(), H3K4me2(), H3K4me3(),
    H3K9me3(), H3K9ac(),
    H3K27me3(), H3K27ac(),
    H3K36me3(),

    H4K12ac(),
    H4K20me1(),

    // Other
    RNA(),
    PolII(),
    CTCF(),
    DNAse(),
    PpargAb1(),
    PpargAb2(),

    // Input
    Input(true),
    Input_MNAse(true),
    Input_Sonicated(true)
}

/**
 * An interface for ChIP-seq data.
 *
 * At the moment all implementors use BED as the data format, however,
 * this restriction is not imposed by the interface.
 */
interface ChipSeqDataSet : DataSetForwarder {
    val chipSeqTargets: List<ChipSeqTarget>

    val chipSeqTargetsWithoutInput: List<ChipSeqTarget>
        get() = chipSeqTargets.filterNot { it.isInput }

    fun getTracks(genomeQuery: GenomeQuery, cellId: CellId,
                  target: ChipSeqTarget): List<BedTrackQuery>

    open fun getTrack(genomeQuery: GenomeQuery, cellId: CellId,
                      target: ChipSeqTarget): BedTrackQuery {
        val tracks = getTracks(genomeQuery, cellId, target)
        check(tracks.size == 1) {
            "Expected a single track for " +
            "${genomeQuery.build}/$cellId/$target, got: $tracks"
        }

        return tracks.first()
    }

    fun getTrack(genomeQuery: GenomeQuery, cellId: CellId,
                 target: ChipSeqTarget, replicate: String): BedTrackQuery {
        val tracks = getTracks(genomeQuery, cellId, target)
                .filter({ track -> replicate in track.path.toString() })
        check(tracks.size == 1) {
            "Expected a single track for " +
            "${genomeQuery.build}/$cellId ($replicate)/$target, got: $tracks"
        }

        return tracks.first()
    }
}
