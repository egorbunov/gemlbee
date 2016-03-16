package org.jetbrains.bio.browser.command

import org.jetbrains.bio.browser.GenomeBrowser
import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.Strand

/**
 * @author Roman.Chernyatchik
 */

data class ChangeLocationCmd(private val oldLoc: LocationReference,
                             private val newLoc: LocationReference,
                             private val model: SingleLocationBrowserModel) : Command {
    override fun toString() = "Go To Location"

    override fun redo() = doAction(newLoc);
    override fun undo() = doAction(oldLoc);

    private fun doAction(locRef: LocationReference) {
        val (startOffset, endOffset, chromosome) = locRef.location
        model.setChromosomeRange(ChromosomeRange(startOffset, endOffset, chromosome), locRef)
    }
}

data class ChangeModelCmd(private val browser: GenomeBrowser,
                          private val newModel: BrowserModel,
                          private val setup: (BrowserModel, BrowserModel) -> Unit) : Command {
    private val oldModel = browser.browserModel

    override fun toString() = "Change model command"
    override fun redo() = setup(oldModel, newModel)
    override fun undo() = setup(newModel, oldModel)
}

abstract class ChangeRangeCmd(protected val oldR: Range,
                              protected val newR: Range,
                              protected val model: BrowserModel) : Command {

    override fun redo() = doAction(newR)
    override fun undo() = doAction(oldR)

    protected open fun doAction(range: Range) {
        model.range = range
    }
}

class ZoomCmd(oldRange: Range, newRange: Range, model: BrowserModel) :
        ChangeRangeCmd(oldRange, newRange, model) {

    override fun toString() = "Zoom ${if (oldR.length() > newR.length()) "In" else "Out"}"
}

class ScrollCmd(oldRange: Range, newRange: Range, browserModel: BrowserModel) :
        ChangeRangeCmd(oldRange, newRange, browserModel) {

    override fun toString() = "Scroll ${if (oldR.startOffset < newR.endOffset) "right" else "left"}"
}

class DragNDropCmd(oldRange: Range, newRange: Range, model: BrowserModel) :
        ChangeRangeCmd(oldRange, newRange, model) {

    override fun toString() = "DragNDrop"
    override fun doAction(range: Range) {
        model.range = range
    }
}


object Commands {

    @JvmStatic
    fun createZoomGenomeRegionCommand(model: BrowserModel, zoomScale: Double): ZoomCmd? {
        val currentGenomeRegion = model.range
        val currRegionLength = currentGenomeRegion.length()

        val newRegionLength = Math.round(currRegionLength / zoomScale).toInt()
        if (newRegionLength == 0) {
            return null
        }

        val shift = (newRegionLength - currRegionLength) / 2
        val newStartOffset = Math.max(0, currentGenomeRegion.startOffset - shift)
        val newEndOffset = Math.min(model.length,
                currentGenomeRegion.endOffset + shift)

        return createZoomToRegionCommand(model, newStartOffset, newEndOffset - newStartOffset)
    }

    @JvmStatic
    fun createZoomToRegionCommand(model: BrowserModel, newStartOffset: Int, length: Int): ZoomCmd? {
        try {
            // Can throw IllegalArgumentException
            val newRange = Range(Math.max(0, newStartOffset), Math.min(newStartOffset + length, model.length))
            return ZoomCmd(model.range, newRange, model)
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    @JvmStatic
    fun createScrollCommand(model: BrowserModel, offset: Int): ScrollCmd? {
        try {
            var newOffset = model.range.startOffset + offset
            // Can throw IllegalArgumentException
            val newRange = Range(Math.max(0, newOffset), Math.min(newOffset + model.range.length(), model.length))
            return ScrollCmd(model.range, newRange, model)
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    @JvmStatic
    fun createGoToLocationCommand(model: SingleLocationBrowserModel,
                                  locRef: LocationReference): ChangeLocationCmd {
        val currLocation = model.range.on(model.chromosome).on(Strand.PLUS)
        return ChangeLocationCmd(SimpleLocRef(currLocation), locRef, model)
    }

    @JvmStatic
    fun createScrollCommand(model: BrowserModel,
                            scrollLeft: Boolean,
                            shiftWholeRegion: Boolean): ScrollCmd? {
        val regionLength = model.range.length()
        val scrollDistance = if (shiftWholeRegion)
            regionLength
        else
            Math.max(1.0, Math.ceil(regionLength * 0.2)).toInt()
        return createScrollCommand(model, scrollDistance * (if (scrollLeft) -1 else 1))
    }

    @JvmStatic
    fun createDragNDropCommand(model: BrowserModel, start: Int, end: Int): DragNDropCmd {
        return DragNDropCmd(model.range, Range(start, end), model)
    }

    @JvmStatic
    fun createChangeModelCommand(browser: GenomeBrowser,
                                 newModel: BrowserModel,
                                 setup: (BrowserModel, BrowserModel) -> Unit): ChangeModelCmd? {
        return ChangeModelCmd(browser, newModel, setup)
    }
}