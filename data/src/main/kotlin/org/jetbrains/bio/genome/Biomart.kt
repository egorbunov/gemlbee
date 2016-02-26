package org.jetbrains.bio.genome

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.http.client.HttpResponseException
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.log4j.Logger
import java.io.IOException
import java.io.InputStream

/**
 * Incomplete but useful Biomart API bindings.
 *
 * See http://biomart.org if you don't know what it is.
 *
 * @author Sergei Lebedev
 * @since 17/09/15
 */

/**
 * Mart is dataset group.
 *
 * At least in the Biomart terms. For instance, there's a mart for all
 * Ensembl data, named `"ensembl"`. Here we couple mart with a dataset,
 * because we don't need cross-organism queries at the moment.
 */
data class Mart(val name: String, val dataset: String,
                val host: String = "www.ensembl.org",
                private val path: String = "/biomart/martservice") {

    data class Attribute(val name: String, val description: String)

    /** Lists available attributes, e.g. `"ensembl_gene_id"`. */
    val attributes: List<Attribute> by lazy(LazyThreadSafetyMode.NONE) {
        val params = mapOf("type" to "attributes",
                           "mart" to name,
                           "dataset" to dataset)
        wire(host, path, params) {
            CSVFormat.TDF.parse(it.bufferedReader()).map {
                Attribute(it[0], it[1])
            }.sortedBy { it.name }
        }
    }

    data class Filter(val name: String, val description: String)

    /** Lists available filters, e.g. `"chromosome_name"`. */
    val filters: List<Filter> by lazy(LazyThreadSafetyMode.NONE) {
        val params = mapOf("type" to "filters",
                           "mart" to name,
                           "dataset" to dataset)
        wire(host, path, params) {
            val format = CSVFormat.TDF.withHeader(
                    "name", "short_description", "allowed_values",
                    "long_description", "<unknown>", "type", "operator",
                    "config", "default")
            format.parse(it.bufferedReader()).map {
                Filter(it["name"], it["short_description"])
            }.sortedBy { it.name }
        }
    }

    /**
     * Normalize a list of attributes w.r.t. renamed between Ensembl releases.
     */
    private fun normalize(attributes: List<String>): List<String> = attributes.map {
        when {
            it == "external_gene_name" && "archive" in host -> "external_gene_id"
            it == "refseq_mrna" && host == forBuild("hg18").host -> "refseq_dna"
            else -> it
        }
    }

    fun <T> query(attributes: List<String>, format: Format = Format.TSV,
                  unique: Boolean = true, block: (CSVParser) -> T): T {

        // Yes, string formatting sucks, but it's not as bad as
        // AbstractDomBuilderFactoryBlahBlah.newBuilder.
        val query = """
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE Query>
<Query requestId="Kotlin" virtualSchemaName="default"
       formatter="$format" header="1" uniqueRows="${if (unique) 1 else 0}"
       datasetConfigVersion="0.6">
    <Dataset name="$dataset">
        ${normalize(attributes).map { "<Attribute name=\"$it\" />"}.joinToString("\n") }
    </Dataset>
</Query>
""".trim()
        return wire(host, path, mapOf("query" to query)) {
            val reader = it.bufferedReader()
            val line = reader.readLine()

            // Bioinformaticians don't get HTTP status code :(.
            if (line.startsWith("Query ERROR")) {
                throw IOException(line.substringAfterLast(": "))
            }

            block(CSVFormat.TDF.withHeader(*attributes.toTypedArray())
                          .withSkipHeaderRecord()
                          .parse(reader))
        }
    }

    /** Query result format. */
    enum class Format {
        TSV, CSV, JSON
    }

    companion object {
        val LOG = Logger.getLogger(Mart::class.java)

        private fun Genome.asDataset() = when (species) {
            "mm" -> "mmusculus_gene_ensembl"
            "hg" -> "hsapiens_gene_ensembl"
            else -> throw IllegalArgumentException()
        }

        /** Returns an appropriate mart for a given UCSC genome build. */
        fun forBuild(build: String): Mart {
            val dataset = Genome(build).asDataset()
            return when (build) {
                "mm9"  -> Mart("ENSEMBL_MART_ENSEMBL", dataset,
                               host = "may2012.archive.ensembl.org")
                // XXX we could've used the latest version, but. Genestack.
                "mm10" -> Mart("ENSEMBL_MART_ENSEMBL",  dataset,
                               host = "jan2013.archive.ensembl.org")
                "hg18" -> Mart("ENSEMBL_MART_ENSEMBL", dataset,
                               host = "may2009.archive.ensembl.org")
                "hg19" -> Mart("ENSEMBL_MART_ENSEMBL", dataset,
                               host = "feb2014.archive.ensembl.org")
                "hg38" -> Mart("ensembl", dataset)
                else   -> throw IllegalArgumentException(build)
            }
        }
    }
}

/**
 * Sends an HTTP GET request to the Biomart server.
 *
 * @param host which server to use.
 * @param path URI prefix to use.
 * @param params a mapping of GET params.
 * @param block called with HTTP response content.
 */
inline internal fun <T> wire(host: String, path: String,
                             params: Map<String, Any> = emptyMap(),
                             block: (InputStream) -> T): T {
    val mart = "http://$host$path"
    Mart.LOG.info("Access: $mart")

    val builder = URIBuilder(mart)
    builder.addParameter("requestId", "Kotlin")
    for ((param, value) in params) {
        builder.addParameter(param, value.toString())
    }
    val uri = builder.build()
    Mart.LOG.debug("URI: ${uri.toASCIIString()}")
    val response = DefaultHttpClient().execute(HttpGet(uri))
    val statusLine = response.statusLine
    if (statusLine.statusCode / 100 != 2) {
        throw HttpResponseException(statusLine.statusCode,
                                    statusLine.reasonPhrase)
    }

    return response.entity.content.use { block(it) }
}
