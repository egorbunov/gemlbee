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

    operator fun contains(gene: Gene): Boolean = predicate(gene)
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
 * This is actually a transcript, not a gene.
 */
class Gene(
        /** Ensembl transcript ID. */
        ensemblId: String,
        /** RefSeq mRNA ID. */
        refSeqId: String,
        /** Gene symbol associated with this transcript. */
        val symbol: String,
        /** Human-readable transcript description. */
        val description: String,
        /** Transcript location. */
        override val location: Location,
        /** Coding sequence range or `null` for non-coding transcripts. */
        private val cdsRange: Range?,
        /**
         * A list of exon ranges. Empty for non-coding transcripts.
         *
         * ArrayList is to make Gson reflection pickup [Range.ADAPTER].
         * Using plain List fails because in Kotlin it's wildcarded.
         */
        private val exonRanges: ArrayList<Range>) : LocationAware {

    val names: Map<GeneAliasType, String> = ImmutableMap.of(
            GeneAliasType.ENSEMBL_ID, ensemblId.toUpperCase(),
            GeneAliasType.GENE_SYMBOL, symbol.toUpperCase(),
            GeneAliasType.REF_SEQ_ID, refSeqId.toUpperCase())

    val chromosome: Chromosome get() = location.chromosome
    val strand: Strand get() = location.strand
    val sequence: String get() = location.sequence

    init {
        require(symbol.isNotEmpty()) { "missing gene symbol" }
    }

    // Remove this method once we get rid of all the Java callers.
    fun getName(geneAliasType: GeneAliasType): String {
        return names[geneAliasType] ?: ""
    }

    val isCoding: Boolean @JvmName("isCoding") get() = cdsRange != null

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
}

/**
 * Genes, only the genes, nothing but the genes.
 *
 * @author Sergei Lebedev
 * @since 21/05/14
 */
object Genes {
    private val GENES_CACHE = cache<Gene>()

    internal fun all(genome: Genome): ListMultimap<Chromosome, Gene> {
        return GENES_CACHE.get(genome.build) {
            val genesPath = genome.dataPath / "genes.json"

            genesPath.checkOrRecalculate("Genes") { output ->
                output.let { path ->
                    download(genome.build, path)
                }
            }
            read(genesPath)
        }
    }

    /** Visible only for [TestOrganismDataGenerator]. */
    @JvmField val GSON = GsonBuilder()
            .registerTypeAdapter(Range::class.java, Range.ADAPTER)
            .registerTypeAdapter(Chromosome::class.java, Chromosome.ADAPTER)
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

        val chromosomes = ChromosomeNamesMap.create(build)
        val genes = ArrayList<Gene>()
        mart.query(listOf("ensembl_transcript_id", "description",
                          "chromosome_name", "strand",
                          "transcript_start", "transcript_end",
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
                val chromosome = chromosomes[block["chromosome_name"]] ?: continue
                val strand = block["strand"].toInt().toStrand()
                val location = Location(block["transcript_start"].toInt() - 1,
                                        block["transcript_end"].toInt(),
                                        chromosome, strand)
                val cds = if (block["5_utr_start"].isEmpty() ||
                              block["3_utr_start"].isEmpty()) {
                    null
                } else {
                    when (strand) {
                        Strand.PLUS  -> Range(block["5_utr_end"].toInt() - 1,
                                              block["3_utr_start"].toInt())
                        Strand.MINUS -> Range(block["3_utr_end"].toInt() - 1,
                                              block["5_utr_start"].toInt())
                    }
                }

                genes.add(Gene(ensemblId, refSeqId, geneSymbol, block["description"],
                               location, cds,
                               exonMap[ensemblId].toCollection(ArrayList())))
            }
        }

        genesPath.bufferedWriter().use { GSON.toJson(genes, it) }

        chromosomes.report("Genes");
    }
}

private inline fun <T> PeekingIterator<T>.nextBlock(
        into: ArrayList<T>, transform: (T) -> Any) {
    into.clear()
    do {
        into.add(next())
    } while (hasNext() && transform(peek()) == transform(into.first()))
}

/*
  Change visibility to private when https://youtrack.jetbrains.com/issue/KT-9779 will be fixed
 */
operator fun List<CSVRecord>.get(field: String): String {
    for (row in this) {
        val value = row[field]
        if (value.isNotEmpty()) {
            return value
        }
    }

    return ""
}
