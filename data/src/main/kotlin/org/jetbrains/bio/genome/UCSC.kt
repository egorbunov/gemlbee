package org.jetbrains.bio.genome

import org.apache.http.HttpEntity
import org.apache.http.client.HttpClient
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.HttpClientUtils
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.query.GenomeQuery
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

private val LOG = Logger.getLogger(UCSC::class.java)

private fun HttpClient.tryGet(url: String): HttpEntity? {
    val response = execute(HttpGet(url))
    val statusLine = response.statusLine
    if (statusLine.statusCode / 100 != 2) {
        throw HttpResponseException(statusLine.statusCode,
                                    statusLine.reasonPhrase)
    }

    return response.entity
}

/**
 * Downloads a URL to a given path.
 *
 * @param outputPath path to write to.
 * @param timeout HTTP connection timeout (in milliseconds).
 * @param maxRetries maximum number of download attempts.
 */
fun String.downloadTo(outputPath: Path,
                      timeout: Int = TimeUnit.SECONDS.toMillis(30).toInt(),
                      maxRetries: Int = 10) {
    outputPath.parent.createDirectories()

    val httpClient = DefaultHttpClient()
    val params = httpClient.params
    HttpConnectionParams.setConnectionTimeout(params, timeout)
    HttpConnectionParams.setSoTimeout(params, timeout)

    for (trial in 1..maxRetries) {
        try {
            val entity = httpClient.tryGet(this)
            if (entity != null) {
                entity.content.use {
                    it.copy(outputPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            return   // VICTORY!
        } catch (e: Exception) {
            if (trial == maxRetries ||
                e !is SocketTimeoutException || e !is ConnectTimeoutException) {
                throw e
            }

            LOG.warn("Connection timeout, retry ($trial/$maxRetries) ...")
            try {
                Thread.sleep(timeout.toLong())
            } catch (ignore: InterruptedException) {
            }
        } finally {
            HttpClientUtils.closeQuietly(httpClient)
        }
    }
}

/**
 * A proxy for UCSC data.
 *
 * @author Sergei Lebedev
 * @since 26/02/15
 */
object UCSC {
    /**
     * Downloads multiple gzipped files from UCSC into a single local file.
     *
     * @param outputPath path to gzipped file.
     * @param build genome version.
     * @param chunks an array of URI components with the last component being
     *               a string template accepting chromosome name, e.g.
     *               `"%s_rmsk.txt.gz"`.
     * @throws IOException if any of the I/O operations do so.
     */
    fun downloadBatchTo(outputPath: Path, build: String, vararg chunks: Any) {
        require(chunks.size > 0) { "expected at least a single chunk" }
        val template = chunks[chunks.size - 1].toString()

        val tmpDir = Files.createTempDirectory("batch")
        val targetPath = tmpDir / "target.gz"

        try {
            outputPath.outputStream().use { merged ->
                for (chromosome in GenomeQuery(build).get()) {
                    val copy = chunks.map { it.toString() }.toTypedArray()
                    copy[chunks.size - 1] = template.format(chromosome.name)
                    downloadTo(targetPath, build, *copy)

                    targetPath.inputStream().use { it.copyTo(merged) }
                }
            }
        } catch (e: IOException) {
            outputPath.deleteIfExists()  // no semi-merged files.
            throw e
        } finally {
            tmpDir.deleteDirectory()     // cleanup.
        }
    }

    fun downloadTo(outputFile: Path, build: String, vararg chunks: Any) {
        val uri = build + '/' + chunks.joinToString("/")
        val url = "http://hgdownload.cse.ucsc.edu/goldenPath/$uri";
        LOG.info("Downloading $url")
        url.downloadTo(outputFile)
    }
}
