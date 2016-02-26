package org.jetbrains.bio.transcriptome

import org.apache.commons.csv.CSVFormat
import org.apache.log4j.LogManager
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.GeneResolver
import org.jetbrains.bio.genome.Genome
import org.jetbrains.bio.genome.query.InputQuery
import org.jetbrains.bio.util.Configuration
import org.jetbrains.bio.util.run
import org.jetbrains.bio.util.withResource
import java.nio.file.Path
import java.util.*

/**
 * Sleuth is a tool for RNA-seq comparison
 *
 * See https://pachterlab.github.io/sleuth.
 *
 * @author Sergei Lebedev
 * @since 11/09/15
 */
class Sleuth {
    fun run(genome: Genome, outputPath: Path,
            kallistoResults1: List<Kallisto.Result>,
            kallistoResults2: List<Kallisto.Result>): Result {
        outputPath.createDirectories()

        val resultsPath = outputPath / "results.csv"
        if (resultsPath.notExists) {
            withResource(Sleuth::class.java, "run_sleuth.R") { script ->
                val designPath = outputPath / "design.csv"
                CSVFormat.TDF.print(designPath.bufferedWriter()).use {
                    it.printRecord("sample", "condition", "path")

                    // From
                    // https://groups.google.com/d/msg/kallisto-sleuth-users/LI5y3mG153c/g4BOb_0UBQAJ
                    //
                    // "The sample names should be 100% uniquely identifiable
                    //  to the particular sample."
                    var labeller = 0
                    for (result in kallistoResults1.maybeReplicate()) {
                        it.printRecord(labeller++, "1", result.outputPath)
                    }

                    for (result in kallistoResults2.maybeReplicate()) {
                        it.printRecord(labeller++, "2", result.outputPath)
                    }
                }

                "Rscript".toPath()
                        .run(script, outputPath, designPath.toString())
            }
        }

        return Result(genome, outputPath)
    }

    data class Result(val genome: Genome, val outputPath: Path) {
        /**
         * Fetches differentially expressed transcripts.
         *
         * @param alpha an upper bound on the FDR for the null hypothesis
         *              there's no difference in expression.
         * @return a list of pairs where the first element is a transcript
         *         and second natural log of fold-change.
         */
        operator fun get(alpha: Double): List<TranscriptDifference> {
            require(alpha > 0 && alpha < 1) { "alpha must be in (0, 1)" }
            val resultsPath = outputPath / "results.csv"
            val acc = ArrayList<TranscriptDifference>()
            for (row in FORMAT.parse(resultsPath.bufferedReader())) {
                val gene = GeneResolver.getAny(genome.build, row["target_id"])
                if (gene == null || row["qval"].toDouble() > alpha) {
                    continue
                }

                acc.add(TranscriptDifference(gene, row["b"].toDouble()))
            }

            return acc
        }

        companion object {
            // See https://groups.google.com/d/msg/kallisto-sleuth-users/kWodd7CQejE/g8Tdqcq3BgAJ
            // for table column description.
            private val FORMAT = CSVFormat.TDF
                    .withHeader("target_id", "pval", "qval", "b", "se_b",
                                "mean_obs", "var_obs", "teach_var", "sigma_sq",
                                "smooth_sigma_sq", "final_sigma_sq")
                    .withSkipHeaderRecord()
        }
    }

    private fun <T> List<T>.maybeReplicate(): List<T> {
        check(isNotEmpty()) { "no data" }

        // This is to work around variance estimation in 'sleuth_fit'.
        // See #635 on GitHub and discussion in
        // https://groups.google.com/d/msg/kallisto-sleuth-users/4GvquoOsHQc/qaJQxwnDBQAJ
        // for details.
        return if (size > 1) {
            this
        } else {
            val item = first()
            LOG.info("Augmenting $item with a fake replicate.")

            val copy = toMutableList()
            copy.add(item)
            copy
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(Sleuth::class.java)
    }
}

data class TranscriptDifference(
        /** Transcript. */
        val transcript: Gene,
        /**
         * Log of fold-change.
         *
         * Positive for upregulated genes and negative for downregulated.
         */
        val lfc: Double)

class SleuthQuery(val inputQueries1: List<KallistoQuery>,
                  val inputQueries2: List<KallistoQuery>,
                  val alpha: Double) :
        InputQuery<List<TranscriptDifference>> {

    override val id: String get() {
        val samples = (inputQueries1 + inputQueries2)
                .map { "${it.condition}_${it.suffix}" }
                .toTypedArray()
        return arrayOf("sleuth", genome.build, *samples).joinToString("_")
    }

    val genome: Genome get() {
        val available = (inputQueries1 + inputQueries2).map { it.genome }.toSet()
        check(available.size == 1) { "inconsistent queries" }
        return available.first()
    }

    override fun getUncached(): List<TranscriptDifference> {
        val outputPath = Configuration.cachePath / "sleuth" / id
        val result = Sleuth().run(genome, outputPath,
                                  inputQueries1.map { it.result },
                                  inputQueries2.map { it.result })
        return result[alpha]
    }
}
