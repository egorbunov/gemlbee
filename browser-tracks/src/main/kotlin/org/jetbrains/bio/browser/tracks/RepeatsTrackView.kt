package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.Location
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.containers.GenomeMap
import org.jetbrains.bio.genome.containers.LocationList
import org.jetbrains.bio.genome.containers.genomeMap
import org.jetbrains.bio.genome.containers.locationList
import org.jetbrains.bio.genome.query.locus.RepeatsQuery
import org.jetbrains.bio.util.Colors
import java.awt.Color
import java.awt.Graphics
import java.util.*

/**
 * @author Roman.Chernyatchik
 *
 * TODO: filter repeats not only by class, allow family & name
 * TODO: ability not to merge neighbours repeats
 */
public class RepeatsTrackView() : TrackView("Repeats") {

    private val REPEATS_CLASSES = Key<List<String>>("repeats_classes")
    private val REPEATS_TABLE = Key<GenomeMap<out Map<String, LocationList>>>("repeats_table")
    private val TRACK_HEIGHT = 10
    private val SPACER_HEIGHT = 1

    private val repeatsSubTrackHeight: Int

    private val repeats: Storage = Storage()

    init {
        repeatsSubTrackHeight = TRACK_HEIGHT
    }

    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        val gq = model.genomeQuery

        val classes = gq.get().flatMap { it.repeats.map { it.repeatClass } }
                .distinct().sorted().toList()

        repeats[REPEATS_CLASSES] = classes

        repeats[REPEATS_TABLE] = genomeMap(gq) { c ->
            val map: HashMap<String, LocationList> = hashMapOf()
            classes.forEach { rc ->
                map[rc] = locationList(gq, RepeatsQuery(rc).process(c))
            }
            map
        }
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val repeatsTable = repeats[REPEATS_TABLE][model.chromosome]
        if (repeatsTable.isEmpty()) {
            TrackUIUtil.drawErrorMessage(g, "Data not available.")
            return
        }
        val repeatGroupsCount = repeatsTable.keys.size
        if (!validateAndFixTrackHeight(repeatGroupsCount)) {
            fireRelayoutRequired()
        }
        val repeatClassNames = repeats[REPEATS_CLASSES]
        val repeatsGroupsCount = repeatClassNames.size
        if (!validateAndFixTrackHeight(repeatsGroupsCount)) {
            fireRelayoutRequired()
            return
        }

        val colors = Colors.palette(repeatsGroupsCount)

        g.font = TrackUIUtil.SMALL_FONT

        // Draw repeats subtracks
        val trackWidth = conf.get(TrackView.WIDTH)
        var screenY = 5
        g.color = Color.LIGHT_GRAY
        g.drawLine(0, screenY, trackWidth, screenY)
        for (i in 0..repeatsGroupsCount - 1) {
            val className = repeatClassNames.get(i)
            paintRepeatsSubTrack(g, repeatsTable.get(className) as LocationList, screenY, colors.get(i),
                                 model, trackWidth)
            screenY += repeatsSubTrackHeight + SPACER_HEIGHT
            g.color = Color.LIGHT_GRAY
            g.drawLine(0, screenY, trackWidth, screenY)
        }
    }

    private fun validateAndFixTrackHeight(repeatsGroupsCount: Int): Boolean {
        val newPrefHeight = repeatsGroupsCount * (repeatsSubTrackHeight + SPACER_HEIGHT) + 5

        // update track height
        if (newPrefHeight != preferredHeight) {
            preferredHeight = newPrefHeight
            return false
        }

        return true
    }

    private fun paintRepeatsSubTrack(g: Graphics, repeatsList: LocationList, screenY: Int, color: Color,
                                     modelSnapshot: SingleLocationBrowserModel, trackWidth: Int) {
        paintRepeatsOnStrand(g, screenY, repeatsSubTrackHeight, color,
                             repeatsList.get(modelSnapshot.chromosome, Strand.PLUS), null, modelSnapshot, trackWidth)
    }

    private fun paintRepeatsOnStrand(g: Graphics, screenY: Int, imgHeight: Int, color: Color,
                                     repeatsOnStrand: List<Location>, strand: Strand?,
                                     model: BrowserModel,
                                     trackWidth: Int) {
        val range = model.range
        val rangeStartOffset = range.startOffset
        val rangeEndOffset = range.endOffset

        g.color = color

        //TODO: binary search optimization (?)
        for (repeatLocation in repeatsOnStrand) {
            if (repeatLocation.toRange().intersects(range)) {
                val repeatStartOffset = Math.max(rangeStartOffset, repeatLocation.startOffset)
                val repeatEndOffset = Math.min(rangeEndOffset, repeatLocation.endOffset)
                val repeatStartScreenX = TrackUIUtil.genomeToScreen(repeatStartOffset, trackWidth, range)
                val repeatEndScreenX = TrackUIUtil.genomeToScreen(repeatEndOffset, trackWidth, range)

                val centerY = screenY + imgHeight / 2
                if (strand != null) {
                    g.drawLine(repeatStartScreenX, centerY - 1, repeatEndScreenX, centerY - 1)
                    g.drawLine(repeatStartScreenX, centerY, repeatEndScreenX, centerY)
                } else {
                    if (repeatEndScreenX - repeatStartScreenX <= 1) {
                        g.drawLine(repeatStartScreenX, screenY, repeatStartScreenX, screenY + imgHeight)
                    } else {
                        g.fillRect(repeatStartScreenX, screenY, repeatEndScreenX - repeatStartScreenX, imgHeight)
                    }
                }

                // arrow: if double strand
                if (strand != null) {
                    g.drawLine(if (strand.isPlus()) repeatEndScreenX - 5 else repeatStartScreenX + 5, screenY,
                               if (strand.isPlus()) repeatEndScreenX else repeatStartScreenX, centerY - 1)
                    g.drawLine(if (strand.isPlus()) repeatEndScreenX else repeatStartScreenX, centerY,
                               if (strand.isPlus()) repeatEndScreenX - 5 else repeatStartScreenX + 5, screenY + imgHeight)
                }
            }
        }
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        val repeatClassNames = repeats[REPEATS_CLASSES]

        val origFont = g.font
        g.font = TrackUIUtil.SMALL_FONT

        var screenY = 5
        for (i in repeatClassNames.indices) {
            TrackUIUtil.drawString(g, repeatClassNames.get(i),
                                   5, screenY + repeatsSubTrackHeight - 1,
                                   Color.BLACK)
            screenY += repeatsSubTrackHeight + SPACER_HEIGHT
        }

        // Restore settings
        g.font = origFont
    }

}
