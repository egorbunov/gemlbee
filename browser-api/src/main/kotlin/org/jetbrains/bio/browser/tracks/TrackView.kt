package org.jetbrains.bio.browser.tracks

import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.genome.query.GenomeQuery
import java.awt.Graphics
import java.util.*
import java.util.function.Consumer
import javax.swing.JPanel

/**
 * A single track in the genome browser.
 *
 * @author Roman Chernyatchik
 */
abstract class TrackView(title: String) {
    open var title: String = title
        protected set

    companion object {
        @JvmField val WIDTH: Key<Int> = Key("WIDTH")
        @JvmField val HEIGHT: Key<Int> = Key("HEIGHT")
        @JvmField val SHOW_LEGEND: Key<Boolean> = Key("SHOW_LEGEND")
        @JvmField val SHOW_AXIS: Key<Boolean> = Key("SHOW_AXIS")

        @JvmField val SCALES: Key<List<Scale>> = Key("SCALES")
    }

    var preferredHeight = 50  // Please don't change this default. Why?

    private val listeners = ArrayList<TrackViewListener>(1)

    /**
     * Preprocesses the track before rendering.
     *
     * The track should ensure all the necessary data is computed and available
     * for fast access.
     *
     * Examples:
     *
     * - fit model to the data,
     * - download gene annotations,
     * - compute summary statistics over the data.
     */
    open fun preprocess(genomeQuery: GenomeQuery) {}

    /**
     * Init config before rendering. Same conf will be used in computeScale(..) and paintTrack(..) methods
     */
    open fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
    }

    open fun addListener(listener: TrackViewListener) {
        listeners.add(listener)
    }

    open fun removeListener(listener: TrackViewListener) {
        listeners.remove(listener)
    }

    fun fireRepaintRequired() {
        listeners.forEach { it.repaintRequired() }
    }

    fun fireRelayoutRequired() {
        listeners.forEach { it.relayoutRequired() }
    }

    /**
     * Main method to draw content
     */
    abstract fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage)

    /**
     * Draws legend
     */
    open fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
    }

    /**
     * Draw Y axis
     */
    open fun drawAxis(g: Graphics, conf: Storage, width: Int, height: Int, drawInBG: Boolean) {
    }

    /**
     * @return Pair of min/max for each track
     */
    open fun computeScales(model: SingleLocationBrowserModel, conf: Storage): List<Scale>
            = listOf(Scale.undefined())

    data class Scale(val min: Double, val max: Double) {
        infix fun union(other: Scale): Scale {
            val newMin = when {
                min.isFinite() -> if (other.min.isFinite()) Math.min(min, other.min) else min
                else -> other.min
            }

            val newMax = when {
                max.isFinite() -> if (other.max.isFinite()) Math.max(max, other.max) else max
                else -> other.max
            }
            return Scale(newMin, newMax)
        }

        fun isInfinite(): Boolean = min.isInfinite() && max.isInfinite()

        override fun toString() = "[$min, $max]"

        companion object {
            fun undefined() = Scale(Double.NaN, Double.NaN)
        }
    }
}

/**
 * Marker interface of [TrackView] with controls UI
 *
 * @author Oleg Shpynov
 * @since 6/29/15
 */
interface TrackViewWithControls {
    /**
     * Custom components panel with enabled/disabled events handler. Please disable your
     * controls on process(false) request.

     * @return Pair of panel and panel component enabled(true - enabled, false - disabled) handler.
     */
    fun createTrackControlsPane(): Pair<JPanel, Consumer<Boolean>>?

    fun initTrackControlsPane() = createTrackControlsPane()?.first
}

interface TrackViewListener {
    fun repaintRequired()
    fun relayoutRequired()
}
