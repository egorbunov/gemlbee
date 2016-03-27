package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.genomeToScreen
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.genome.containers.RangeList
import org.jetbrains.bio.genome.containers.toRangeList
import org.jetbrains.bio.genome.query.GenomeQuery
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
class RepeatsTrackView() : TrackView("Repeats") {
    private lateinit var repeatColors: SortedMap<String, Color>

    override fun preprocess(genomeQuery: GenomeQuery) {
        val repeatClasses = genomeQuery.get().asSequence().flatMap {
            it.repeats.asSequence().map { it.repeatClass }.distinct()
        }.toSet()

        repeatColors = repeatClasses.zip(Colors.palette(repeatClasses.size))
                .associateTo(TreeMap(), { it })
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        val repeats = model.chromosome.repeats.asSequence()
                .groupBy { it.repeatClass }
                .mapValues { it.value.asSequence().map { it.location.toRange() }.toRangeList() }

        if (repeats.isEmpty()) {
            TrackUIUtil.drawErrorMessage(g, "Data not available.")
            return
        } else if (!validateAndFixTrackHeight(repeatColors.size)) {
            fireRelayoutRequired()
            return
        }

        g.font = TrackUIUtil.SMALL_FONT

        val trackWidth = conf[TrackView.WIDTH]
        var screenY = 5
        g.color = Color.LIGHT_GRAY
        g.drawLine(0, screenY, trackWidth, screenY)
        for (repeatClass in repeatColors.keys) {
            if (repeatClass in repeats) {
                paintRepeatsSubTrack(g, repeats[repeatClass]!!, screenY,
                                     repeatColors[repeatClass]!!,
                                     model, trackWidth)
            }

            screenY += SUB_TRACK_HEIGHT + SPACER_HEIGHT
            g.color = Color.LIGHT_GRAY
            g.drawLine(0, screenY, trackWidth, screenY)
        }
    }

    private fun validateAndFixTrackHeight(repeatsGroupsCount: Int): Boolean {
        val newPrefHeight = repeatsGroupsCount * (SUB_TRACK_HEIGHT + SPACER_HEIGHT) + 5

        // update track height
        if (newPrefHeight != preferredHeight) {
            preferredHeight = newPrefHeight
            return false
        }

        return true
    }

    private fun paintRepeatsSubTrack(g: Graphics, repeats: RangeList, screenY: Int, color: Color,
                                     model: SingleLocationBrowserModel, trackWidth: Int) {
        g.color = color

        // TODO: binary search optimization (?)
        for (repeat in repeats) {
            val (startOffset, endOffset) = repeat intersection model.range
            if (endOffset == startOffset) {
                continue  // Outside the visible range.
            }

            val startX = genomeToScreen(startOffset, trackWidth, model.range)
            val endX = genomeToScreen(endOffset, trackWidth, model.range)
            if (endX - startX <= 1) {
                g.drawLine(startX, screenY, startX, screenY + SUB_TRACK_HEIGHT)
            } else {
                g.fillRect(startX, screenY, endX - startX, SUB_TRACK_HEIGHT)
            }

            g.fillRect(startX, screenY, endX - startX, SUB_TRACK_HEIGHT)
        }
    }

    override fun drawLegend(g: Graphics, width:Int, height:Int, drawInBG: Boolean) {
        val origFont = g.font
        g.font = TrackUIUtil.SMALL_FONT

        var screenY = 5
        for (repeatClass in repeatColors.keys) {
            TrackUIUtil.drawString(g, repeatClass,
                                   5, screenY + SUB_TRACK_HEIGHT - 1,
                                   Color.BLACK)
            screenY += SUB_TRACK_HEIGHT + SPACER_HEIGHT
        }

        // Restore settings
        g.font = origFont
    }

    companion object {
        private val SPACER_HEIGHT = 1
        private val SUB_TRACK_HEIGHT = 10
    }
}