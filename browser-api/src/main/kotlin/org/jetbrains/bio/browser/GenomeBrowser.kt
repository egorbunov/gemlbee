package org.jetbrains.bio.browser

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.browser.desktop.Header
import org.jetbrains.bio.browser.desktop.MultipleLocationsHeader
import org.jetbrains.bio.browser.desktop.SingleLocationHeader
import org.jetbrains.bio.browser.model.*
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.time
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.GeneClass
import org.jetbrains.bio.genome.LocusType
import org.jetbrains.bio.genome.query.GenomeLocusQuery
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.genome.query.locus.LocusQuery

/**
 * Genome browser is a graphical interface for exploring genomic data.
 *
 * The logic of data visualization is delegated to [TrackView] subclasses.
 * Each subclass defines how to load and render specific data type. For
 * instance [BigBedTrackView] is responsible for displaying data from
 * [BigBedFile].
 *
 * @author Oleg Shpynov
 * @since 01/02/15
 */
interface GenomeBrowser {

    var model: BrowserModel

    val trackViews: List<TrackView>

    val locationsMap: Map<String, (GenomeQuery) -> List<LocationReference>>

    // Add completion for Loci and preconfigured locations
    val locationCompletion: List<String> get() =
    (LociCompletion[model.genomeQuery] + locationsMap.keys + LociCompletion.LOCI_COMPLETION)
            .map { it.toLowerCase() }
            .sorted()
            .distinct()
            .toList()

    /**
     * Executes a given command.
     *
     * A no-op if a [command] is `null`.
     */
    fun execute(command: Command?)

    /**
     * Sequentially preprocesses tracks before rendering.
     *
     * @see TrackView.preprocess for possible preprocessing use-cases.
     */
    fun preprocess() {
        // The lack of parallelism here is intentional. Preprocessing
        // is expected to be CPU/RAM-consuming.
        val log = Logger.getRootLogger()
        for (trackView in trackViews) {
            log.time(Level.INFO, "Preprocess track: '${trackView.title}'") {
                trackView.preprocess(model.genomeQuery)
            }
        }
    }

    /**
     * Tries to process given text as [MultipleLocationsBrowserModel]
     *
     * @return true if successful and text was recognized as multiple locations model
     */
    fun handleMultipleLocationsModel(text: String): Boolean {
        val current = model

        // Process particular location
        val matcher = LociCompletion.ABSTRACT_LOCATION_PATTERN.matcher(text)
        if (matcher.matches()) {
            // Key is before range
            val key = matcher.group(1)
            if (current !is MultipleLocationsBrowserModel || key != current.id) {
                if (key in locationsMap) {
                    model = MultipleLocationsBrowserModel.create(key, locationsMap[key]!!, getOriginalModel(current))
                } else {
                    try {
                        val query = parseLocus(key)
                        if (query != null) {
                            model = LocusQueryBrowserModel.create(key, query, getOriginalModel(current))
                        } else {
                            model = getOriginalModel(current)
                            return false
                        }
                    } catch (t: Throwable) {
                        model = getOriginalModel(current)
                        return false
                    }
                }
            }

            // Goto range if required or whole model range
            val startGroup = if (matcher.groupCount() >= 3) matcher.group(3) else null
            val endGroup = if (matcher.groupCount() >= 4) matcher.group(4) else null
            if (startGroup != null && endGroup != null) {
                val start = startGroup.replace("\\.|,".toRegex(), "").toInt()
                val end = endGroup.replace("\\.|,".toRegex(), "").toInt()
                execute(current.zoomAt(start, end))
            }
            return true
        }

        // Otherwise restore original model
        model = getOriginalModel(current)
        return false
    }

    fun parseLocus(text: String): GenomeLocusQuery<Chromosome, *>? {
        val locusType = LocusType.of(text)
        if (locusType is LocusType.ChromosomeType) {
            return GenomeLocusQuery.of(locusType.createQuery())
        }
        val queries = LocusQuery.parse(text)
        if (queries.isEmpty()) {
            return null
        }
        if (queries.size > 1) {
            LOG.error("Loci with parameters are not supported, using the first one: ${queries.first().id}")
        }
        return GenomeLocusQuery.of(queries.first(), GeneClass.ALL)
    }


    companion object {
        val LOG = Logger.getLogger(GenomeQuery::class.java)

        fun getOriginalModel(model: BrowserModel): BrowserModel {
            return if (model is MultipleLocationsBrowserModel)
                model.originalModel
            else
                model
        }

        fun createHeaderView(model: BrowserModel): Header {
            return if (model is MultipleLocationsBrowserModel)
                MultipleLocationsHeader(model)
            else
                SingleLocationHeader(model as SingleLocationBrowserModel)
        }
    }
}
