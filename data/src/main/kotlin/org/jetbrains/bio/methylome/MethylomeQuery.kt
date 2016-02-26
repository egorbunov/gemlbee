package org.jetbrains.bio.methylome

import com.google.common.base.Joiner
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.query.CachingInputQuery
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.io.BisulfiteBamParser
import org.jetbrains.bio.util.Configuration
import java.io.IOException
import java.nio.file.Path

/**
 * @author Roman.Chernyatchik
 */
abstract class MethylomeQuery protected constructor(
        val genomeQuery: GenomeQuery,
        protected  val dataSetId: String,
        val cellId: String,
        private vararg val properties: String)
:
        CachingInputQuery<Methylome>() {

    private val LOG = Logger.getLogger(MethylomeQuery::class.java)

    /** Reads a methylome using an **unrestricted** i.e. full [GenomeQuery]. */
    @Throws(IOException::class)
    protected abstract fun read(genomeQuery: GenomeQuery): Methylome

    companion object {
        /**
         * Creates a query for a given file path.
         *
         * At the moment only HDF5 and BAM files are supported.
         */
        fun forFile(genomeQuery: GenomeQuery, cellId: String,
                    path: Path, dataSetId: String = "File",
                    verboseDescription: Boolean = false): MethylomeQuery {
            return FileMethylomeQuery(genomeQuery, cellId, path, dataSetId,
                                      verboseDescription)
        }
    }

    override fun getUncached(): Methylome {
        val binaryPath = Configuration.cachePath / "methylome" / "$id.npz"
        return binaryPath.readOrRecalculate(
                { Methylome.lazy(genomeQuery, binaryPath) },
                { output ->
                    val methylome = read(GenomeQuery(genomeQuery.build))
                    output.let { methylome.save(it) }
                    output to Methylome.lazy(genomeQuery, binaryPath)
                }, "Methylome")
    }

    override val id: String get() {
        return "${dataSetId}_${genomeQuery.id}_$cellId" +
               Joiner.on('_').join(properties).let { if (it.isNotBlank()) "_$it" else "" }
    }

    override val description: String get() {
        val genomeStr = genomeQuery.description
        val propDesc = if (properties.isEmpty()) "" else "(${Joiner.on('_').join(properties)})"
        return "Methylome, dataset $dataSetId$propDesc for $cellId cells line genome $genomeStr"
    }
}

private class FileMethylomeQuery(genomeQuery: GenomeQuery, cellId: String,
                                 private val path: Path, dataSetId: String,
                                 private val verboseDescription: Boolean)
:
        MethylomeQuery(genomeQuery, dataSetId, cellId, path.stem) {

    override fun read(genomeQuery: GenomeQuery) = when (path.extension) {
        "npz" -> Methylome.lazy(genomeQuery, path)
        "bam" -> BisulfiteBamParser.parse(path, genomeQuery)
        else -> error("unsupported extension: ${path.extension}")
    }

    // We use only the file name, because in practice it already
    // contains cell ID and genome build.
    override val id: String get() = path.stem

    override val description: String get() {
        return if (verboseDescription) {
            path.name
        } else {
            "Methylome for ${genomeQuery.description} :" +
            " $cellId, dataset $dataSetId(${path.name})"
        }
    }
}