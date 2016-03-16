package org.jetbrains.bio.browser

import com.google.common.collect.Lists
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.bio.browser.command.Command
import org.jetbrains.bio.browser.command.Commands
import org.jetbrains.bio.browser.desktop.Header
import org.jetbrains.bio.browser.desktop.MultipleLocationsHeader
import org.jetbrains.bio.browser.desktop.SingleLocationHeader
import org.jetbrains.bio.browser.model.*
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.time
import org.jetbrains.bio.genome.LocusType
import org.jetbrains.bio.genome.query.GenomeLocusQuery
import org.jetbrains.bio.genome.query.GenomeQuery

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

    var browserModel: BrowserModel

    val trackViews: List<TrackView>

    val locationsMap: Map<String, (GenomeQuery) -> List<LocationReference>>

    fun execute(cmd: Command?)

    // Add completion for Loci and preconfigured locations
    val locationCompletion: List<String> get() {
        val result = Lists.newArrayList<String>()
        result.addAll(LociCompletion[browserModel.genomeQuery])
        result.addAll(locationsMap.keys)
        for (locusType in LocusType.values()) {
            result.add(if (locusType === LocusType.WHOLE_GENE) "genes" else locusType.toString())
        }

        for (i in 0 until 1000) {
            result.add("tss" + i)
            result.add("tss-$i,$i")
            result.add("tes" + i)
        }

        return result.asSequence().map { it.toLowerCase() }
                .sorted()
                .distinct()
                .toList()
    }

    /**
     * Tries to process given text as [MultipleLocationsBrowserModel]
     *
     * @return true if successful and text was recognized as multiple locations model
     */
    fun handleMultipleLocationsModel(text: String): Boolean {
        val model = browserModel
        val locationsMap = locationsMap

        // Process particular location
        val matcher = LociCompletion.ABSTRACT_LOCATION_PATTERN.matcher(text)
        if (matcher.matches()) {
            // Key is before range
            val key = matcher.group(1)
            if (model !is MultipleLocationsBrowserModel || key != model.id) {
                val lf = locationsMap[key]
                if (lf != null) {
                    browserModel = MultipleLocationsBrowserModel.create(key, lf, getOriginalModel(model))
                } else {
                    val query = GenomeLocusQuery.of(key)
                    if (query != null) {
                        browserModel = LocusQueryBrowserModel.create(key, query, getOriginalModel(model))
                    } else {
                        browserModel = getOriginalModel(model)
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
                execute(Commands.createZoomToRegionCommand(model, start, end - start))
            }
            return true
        }

        // Otherwise restore original model
        browserModel = getOriginalModel(model)
        return false
    }

    /**
     * Preprocess tracks before rendering, ensure that necessary data is loaded/cached for future
     * track fast rendering.
     */
    fun preprocessTracks(trackViews: List<TrackView>, genomeQuery: GenomeQuery) {
        // Do it sequentially, preprocessing is expected to be CPU and MEM consuming
        val log = Logger.getRootLogger()
        for (trackView in trackViews) {
            log.time(Level.INFO, "Preprocess track: '${trackView.title}'", true) {
                trackView.preprocess(genomeQuery)
            }
        }
    }

    companion object {
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
