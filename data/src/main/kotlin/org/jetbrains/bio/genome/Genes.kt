package org.jetbrains.bio.genome

import com.google.common.collect.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.ext.bufferedReader
import org.jetbrains.bio.ext.bufferedWriter
import org.jetbrains.bio.ext.checkOrRecalculate
import org.jetbrains.bio.ext.div
import org.jetbrains.bio.genome.containers.minus
import java.nio.file.Path
import java.util.*

/**
 * Useful gene groups (aka classes).
 *
 * @author Sergei Lebedev
 * @since 13/04/15
 */
enum class GeneClass(val id: String, val description: String,
                     private val predicate: (Gene) -> Boolean) {
    CODING("coding", "coding genes", { it.isCoding }),
    NON_CODING("non-coding", "non-coding genes", { !it.isCoding }),
    ALL("all", "all genes", { true });

    operator fun contains(gene: Gene) = predicate(gene)
}

/**
 * Gene identifiers available in UCSC Genome Browser.
 *
 * @author Sergei Lebedev
 * @since 21/05/14
 */
enum class GeneAliasType {
    GENE_SYMBOL,
    REF_SEQ_ID,
    ENSEMBL_ID;   // unique.

    val description: String get() = when (this) {
        GENE_SYMBOL -> "Gene Symbol"
        REF_SEQ_ID  -> "RefSeq mRNA Accession"
        ENSEMBL_ID  -> "Ensembl Transcript ID"
    }
}

/**
 * A zero-overhead [EnumMap] specialized to [GeneAliasType].
 *
 * Note that no container
 *
 * @author Sergei Lebedev
 * @since 07/04/16
 */
class GeneAliasMap internal constructor(private val gene: Gene) : Map<GeneAliasType, String> {
    override val keys: Set<GeneAliasType> get() = setOf(*KEY_UNIVERSE)

    override val values: Collection<String> get() = KEY_UNIVERSE.map { get(it) }

    override val entries: Set<Map.Entry<GeneAliasType, String>> get() {
        return KEY_UNIVERSE.mapTo(LinkedHashSet()) { Maps.immutableEntry(it, get(it)) }
    }

    override fun get(key: GeneAliasType) = when (key) {
        GeneAliasType.GENE_SYMBOL -> gene.symbol
        GeneAliasType.REF_SEQ_ID  -> gene.refSeqId
        GeneAliasType.ENSEMBL_ID  -> gene.ensemblId
    }.toUpperCase()

    override fun containsKey(key: GeneAliasType) = true

    override fun containsValue(value: String) = value in values

    override fun isEmpty() = false

    override val size: Int get() = KEY_UNIVERSE.size

    companion object {
        private val KEY_UNIVERSE = GeneAliasType.values()
    }
}

/** This is actually a transcript, not a gene. */
class Gene(
        /** Ensembl transcript ID. */
        internal val ensemblId: String,
        /** RefSeq mRNA ID. */
        internal val refSeqId: String,
        /** Gene symbol associated with this transcript. */
        val symbol: String,
        /** Human-readable transcript description. */
        val description: String,
        /** Transcript location. */
        override val location: Location,
        /** Coding sequence range or `null` for non-coding transcripts. */
        private val cdsRange: Range?,
        /** A list of exon ranges. Empty for non-coding transcripts. */
        private val exonRanges: List<Range>) : LocationAware {

    init {
        require(symbol.isNotEmpty()) { "missing gene symbol" }
    }

    val names: Map<GeneAliasType, String> get() = GeneAliasMap(this)

    val chromosome: Chromosome get() = location.chromosome
    val strand: Strand get() = location.strand

    val isCoding: Boolean get() = cdsRange != null

    val cds: Location? get() = cdsRange?.on(chromosome)?.on(strand)

    private fun List<Range>.on(chromosome: Chromosome, strand: Strand): List<Location> {
        return map { it.on(chromosome).on(strand) }
    }

    val exons: List<Location> get() = if (strand.isPlus()) {
        exonRanges.on(chromosome, strand)
    } else {
        exonRanges.map { it.on(chromosome).on(strand) }.reversed()
    }

    val introns: List<Location> get() = if (strand.isPlus()) {
        (location.toRange() - exonRanges).on(chromosome, strand)
    } else {
        (location.toRange() - exonRanges).on(chromosome, strand).reversed()
    }

    /**
     * Everything after the TSS but before the CDS.
     */
    val utr5: Location? get() {
        if (!isCoding) {
            return null
        }

        return if (strand.isPlus()) {
            Location(location.startOffset, cds!!.startOffset,
                     chromosome, strand)
        } else {
            Location(cds!!.endOffset, location.endOffset,
                     chromosome, strand)
        }
    }

    /**
     * Everything after the CDS but before the transcription end site.
     */
    val utr3: Location? get() {
        if (!isCoding) {
            return null
        }

        return if (strand.isPlus()) {
            Location(cds!!.endOffset, location.endOffset,
                     location.chromosome, strand)
        } else {
            Location(location.startOffset, cds!!.startOffset,
                     location.chromosome, strand)
        }
    }

    fun length() = location.length()

    override fun toString(): String {
        return "$symbol $location [exons: ${exons.size}, introns: ${introns.size}]"
    }

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Gene -> false
        else -> ensemblId == other.ensemblId
    }

    override fun hashCode() = ensemblId.hashCode()
}

/**
 * Genes, only the genes, nothing but the genes.
 *
 * @author Sergei Lebedev
 * @since 21/05/14
 */
object Genes {
    private val CACHE = cache<Gene>()

    internal fun all(genome: Genome): ListMultimap<Chromosome, Gene> {
        return CACHE.get(genome.build) {
            val genesPath = genome.dataPath / "genes.json.gz"
            genesPath.checkOrRecalculate("Genes") { output ->
                output.let { download(genome.build, it) }
            }

            read(genesPath)
        }
    }

    /** Visible only for [TestOrganismDataGenerator]. */
    @JvmField val GSON = GsonBuilder()
            .registerTypeAdapter(Range::class.java, Range.ADAPTER)
            .registerTypeAdapter(Location::class.java, Location.ADAPTER)
            .create()

    private fun read(genesPath: Path): ListMultimap<Chromosome, Gene> {
        val genes = genesPath.bufferedReader().use {
            GSON.fromJson<List<Gene>>(it, object : TypeToken<List<Gene>>() {}.type)
        }

        return Multimaps.index(genes) { it!!.chromosome }
    }

    fun download(build: String, genesPath: Path) {
        val mart = Mart.forBuild(build)

        val exonMap = ArrayListMultimap.create<String, Range>()
        mart.query(listOf("ensembl_transcript_id",
                          "exon_chrom_start",
                          "exon_chrom_end")) { pipe ->
            for (row in pipe) {
                val exon = Range(row["exon_chrom_start"].toInt() - 1,
                                 row["exon_chrom_end"].toInt())
                exonMap.put(row["ensembl_transcript_id"], exon)
            }
        }

        val aliasMap = HashMap<String, Pair<String, String>>()
        mart.query(listOf("ensembl_transcript_id", "refseq_mrna",
                          "external_gene_name")) { pipe ->
            for (row in pipe) {
                aliasMap[row["ensembl_transcript_id"]] =
                        row["refseq_mrna"] to row["external_gene_name"]
            }
        }

        val chromosomes = chromosomeMap(build)
        val genes = ArrayList<Gene>()
        mart.query(listOf("ensembl_transcript_id", "description",
                          "chromosome_name", "strand",
                          "transcript_start", "transcript_end",
                          "cds_length",
                          "3_utr_start", "3_utr_end",
                          "5_utr_start", "5_utr_end")) { pipe ->
            val block = ArrayList<CSVRecord>(1)
            val it = Iterators.peekingIterator(pipe.iterator())
            while (it.hasNext()) {
                // Even though we query Biomart with `unique = true`, it
                // might sometimes return multiple entries for the same
                // transcript ID. And these entries often contain
                // complementary data. To work this around we combine
                // duplicates into blocks.
                it.nextBlock(block) { it["ensembl_transcript_id"] }

                val ensemblId = block["ensembl_transcript_id"]
                val (refSeqId, geneSymbol) = aliasMap[ensemblId] ?: continue
                val chromosome = chromosomes["chr${block["chromosome_name"]}"] ?: continue
                val strand = block["strand"].toInt().toStrand()
                val location = Location(block["transcript_start"].toInt() - 1,
                                        block["transcript_end"].toInt(),
                                        chromosome, strand)
                val cds = when {
                    block["cds_length"].isEmpty() -> null
                    block["5_utr_start"].isEmpty() || block["3_utr_start"].isEmpty() -> {
                        // Some transcripts don't have UTRs, in this case we use the
                        // whole transcript as CDS. See e.g. ENSMUST00000081985 for mm9.
                        location.toRange()
                    }
                    else -> {
                        when (strand) {
                            Strand.PLUS  -> Range(block["5_utr_end"].toInt() - 1,
                                                  block["3_utr_start"].toInt())
                            Strand.MINUS -> Range(block["3_utr_end"].toInt() - 1,
                                                  block["5_utr_start"].toInt())
                        }
                    }
                }

                genes.add(Gene(ensemblId, refSeqId, geneSymbol, block["description"],
                               location, cds, exonMap[ensemblId].toList()))
            }
        }

        genesPath.bufferedWriter().use { GSON.toJson(genes, it) }
    }
}

private inline fun <T> PeekingIterator<T>.nextBlock(
        into: ArrayList<T>, transform: (T) -> Any) {
    into.clear()
    do {
        into.add(next())
    } while (hasNext() && transform(peek()) == transform(into.first()))
}

private operator fun List<CSVRecord>.get(field: String): String {
    for (row in this) {
        val value = row[field]
        if (value.isNotEmpty()) {
            return value
        }
    }

    return ""
}
