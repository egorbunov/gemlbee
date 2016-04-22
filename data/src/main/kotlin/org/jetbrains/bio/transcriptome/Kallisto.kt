package org.jetbrains.bio.transcriptome

import gnu.trove.map.hash.TObjectDoubleHashMap
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.io.FastaRecord
import org.jetbrains.bio.io.write
import org.jetbrains.bio.util.Configuration
import org.jetbrains.bio.util.run
import java.nio.file.Path
import java.util.*

/**
 * Kallisto is a tool for RNA-seq quantification.
 *
 * See https://pachterlab.github.io/kallisto.
 *
 * @author Sergei Lebedev
 * @since 08/09/15
 */
class Kallisto(private val executable: Path = "kallisto".toPath()) {
    /**
     * Runs Kallisto on a given set of possibly paired reads.
     *
     * @param genome which index to use.
     * @param fastqReads an array of reads to process.
     * @param outputPath directory to write output to, will be created
     *                   if doesn't exist.
     * @param bootstrap number of bootstrap samples to use.
     * @param threads number of threads to use for bootstrapping.
     */
    fun run(genome: Genome, fastqReads: Array<Path>,
            outputPath: Path,
            bootstrap: Int = 0, // Defaults: https://pachterlab.github.io/kallisto/manual.html
            threads: Int = 1): Result {
        outputPath.createDirectories()

        val abundancePath = outputPath / "abundance.tsv"
        if (abundancePath.notExists) {
            val indexPath = index(genome)

            val (fastqReads1, fastqReads2) = fastqReads.map { it.toString() }
                    .partition { "_1" in it }
            val extra = if (fastqReads1.isEmpty() || fastqReads2.isEmpty()) {
                // For single reads kallisto requires fragment length mean
                // and SD. We use "reasonable" hardcoded values. See mailing list
                // discussion here:
                // https://groups.google.com/d/msg/kallisto-sleuth-users/h5LeAlWS33w/NzRUaCw8iqcJ
                arrayOf("--single", "-l", "200", "-s", "600")
            } else {
                check(fastqReads1.size == fastqReads2.size)
                emptyArray<String>()
            }

            executable.run(
                    "quant",
                    "-i", indexPath, "-o", outputPath,
                    "-b", bootstrap.toString(),
                    "-t", threads.toString(),
                    *extra, *fastqReads,
                    log = true)
        }

        return Result(genome, outputPath)
    }

    fun index(genome: Genome): Path {
        val transcriptomePath = genome.dataPath / "${genome.build}.cdna.all.fa.gz"
        transcriptomePath.checkOrRecalculate("CDNA sequences") { output ->
            output.let { path ->
                genome.genes.asSequence().map {
                    // See https://www.biostars.org/p/47022.
                    val cds = it.exons.map { it.sequence }.joinToString("")
                    FastaRecord(it.names[GeneAliasType.ENSEMBL_ID]!!, cds)
                }.asIterable().write(path)
            }
        }

        val indexPath = transcriptomePath.withExtension("idx")
        indexPath.checkOrRecalculate("Kallisto index") { output ->
            output.let { executable.run("index", "-i", it, transcriptomePath) }
        }

        return indexPath
    }

    data class Result(val genome: Genome, val outputPath: Path) {

        val abundances: List<TranscriptAbundance> get() {
            val abundancePath = outputPath / "abundance.tsv"
            val acc = ArrayList<TranscriptAbundance>()
            for (row in FORMAT.parse(abundancePath.bufferedReader())) {
                val alias = row["target_id"]
                val gene = GeneResolver.getAny(genome.build, alias, GeneAliasType.ENSEMBL_ID)
                if (gene != null) {
                    acc.add(TranscriptAbundance(gene, row["tpm"].toDouble()))
                } else {
                    LOG.warn("Failed to load gene: $alias")
                }

            }
            return acc
        }

        companion object {
            private val LOG = Logger.getLogger(Kallisto::class.java)
            private val FORMAT = CSVFormat.TDF
                    .withHeader("target_id", "length", "eff_length", "est_counts", "tpm")
                    .withSkipHeaderRecord()
        }
    }
}

// A good review of RNA-seq expression units is available here:
// https://haroldpimentel.wordpress.com/2014/05/08/what-the-fpkm-a-review-rna-seq-expression-units
data class TranscriptAbundance(
        /** Transcript. */
        val transcript: Gene,
        /** Abundance in Transcripts Per Million. */
        val tpm: Double)

class KallistoQuery(
        /** Which index to use. */
        val genome: Genome,
        /** Sample condition, e.g. [HumanCells.H1]. */
        val condition: CellId,
        /** Unique sample identifier within the condition, e.g. replicate number. */
        val suffix: String = "",
        /** An array of paired reads to process. */
        var fastqReads: Array<Path>)
: InputQuery<List<TranscriptAbundance>> {

    init {
        check(fastqReads.isNotEmpty()) { "no data" }
    }

    // XXX used in 'SleuthQuery'.
    val result: Kallisto.Result get() {
        val outputPath = Configuration.cachePath / "kallisto" / id
        return Kallisto().run(genome, fastqReads, outputPath,
                bootstrap = 100, threads = Runtime.getRuntime().availableProcessors())
    }

    override fun getUncached(): List<TranscriptAbundance> = result.abundances

    override val id: String get() {
        val args = arrayListOf("kallisto", genome.build, condition.name)
        if (suffix.isNotEmpty()) {
            args.add(suffix)
        }
        return args.joinToString("_")
    }

    companion object {
        val LOG = Logger.getLogger(KallistoQuery::class.java)
    }
}

val Path.fastqReads: Array<Path> get() {
    return list().filter { it.name.endsWith(".fastq") || it.name.endsWith(".fastq.gz") }
            .toList().toTypedArray()
}

fun kallistoTpmAbundance(genes: List<Gene>, replicates: List<KallistoQuery>): TObjectDoubleHashMap<Gene> {
    val tpmAbundance = TObjectDoubleHashMap<Gene>()
    for (replicate in replicates) {
        for (t in replicate.get()) {
            tpmAbundance.adjustOrPutValue(t.transcript, t.tpm, t.tpm)
        }
    }
    // Average over replicates
    for (g in genes) {
        if (g !in tpmAbundance) {
            KallistoQuery.LOG.warn("No information about gene ${g.names[GeneAliasType.ENSEMBL_ID]} in loaded abundance.")
        }
        tpmAbundance.put(g, tpmAbundance[g] / replicates.size)
    }
    return tpmAbundance
}

