package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.ext.parallelStream
import org.jetbrains.bio.genome.CpGIsland
import org.jetbrains.bio.util.Colors
import java.awt.Color
import java.awt.Graphics

/**
 * Got islands?
 *
 * @author Sergei Lebedev
 * @since 10/06/15
 */
class CpGITrackView : LocationAwareTrackView<CpGIsland>(
        "CpG islands: groups = ${CpGITrackView.GROUP_COUNT}") {

    private var obsToExpRangeStep = 0.0
    private var obsToExpRangeMin = 0.0

    override fun getItems(model: SingleLocationBrowserModel): List<CpGIsland> {
        return model.chromosome.cpgIslands
    }

    override fun paintItems(g: Graphics, model: SingleLocationBrowserModel,
                            configuration: Storage,
                            items: List<CpGIsland>) {
        val summary = items.parallelStream()
                .mapToDouble { it.observedToExpectedRatio }
                .summaryStatistics()
        val obsRange = summary.max - summary.min
        obsToExpRangeStep = obsRange / GROUP_COUNT
        obsToExpRangeMin = summary.min

        super.paintItems(g, model, configuration, items)
    }

    override fun getItemColor(item: CpGIsland): Color {
        if (obsToExpRangeStep == 0.0) {
            return GROUP_COLORS.first()
        }

        val groupCount = GROUP_COLORS.size
        val ratio = item.observedToExpectedRatio
        var colorIndex = Math.round((ratio - obsToExpRangeMin) / obsToExpRangeStep).toInt()
        if (colorIndex >= groupCount) {
            colorIndex = groupCount - 1
        }

        return GROUP_COLORS[colorIndex]
    }

    companion object {
        private const val GROUP_COUNT: Int = 4

        private val GROUP_COLORS = Colors.palette(GROUP_COUNT)
    }
}
