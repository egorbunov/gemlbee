package org.jetbrains.bio.browser

import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.SimpleLocRef
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.Strand

/** A GoF command as is. */
interface Command {
    fun redo()
    fun undo()

    data class ChangeLocation(
            private val oldLocation: LocationReference,
            private val newLocation: LocationReference,
            private val model: SingleLocationBrowserModel) : Command {

        override fun redo() = doAction(newLocation);
        override fun undo() = doAction(oldLocation);

        private fun doAction(locRef: LocationReference) {
            model.setChromosomeRange(locRef.location.toChromosomeRange(), locRef)
        }

        override fun toString() = "Go To Location"
    }

    data class ChangeModel(
            private val oldModel: BrowserModel,
            private val newModel: BrowserModel,
            private val setup: (BrowserModel, BrowserModel) -> Unit) : Command {

        override fun redo() = setup(oldModel, newModel)
        override fun undo() = setup(newModel, oldModel)

        override fun toString() = "Change Model"
    }

    abstract class ChangeRange(protected val oldRange: Range,
                               protected val newRange: Range,
                               private val model: BrowserModel) : Command {

        override fun redo() = doAction(newRange)
        override fun undo() = doAction(oldRange)

        protected open fun doAction(range: Range) {
            model.range = range
        }
    }

    class Zoom(oldRange: Range, newRange: Range, model: BrowserModel) :
            ChangeRange(oldRange, newRange, model) {

        override fun toString(): String {
            return "Zoom " + if (oldRange.length() > newRange.length()) "In" else "Out"
        }
    }

    class Scroll(oldRange: Range, newRange: Range, browserModel: BrowserModel) :
            ChangeRange(oldRange, newRange, browserModel) {

        override fun toString(): String {
            return "Scroll " + if (oldRange.startOffset < newRange.endOffset) "Right" else "Left"
        }
    }

    class DragNDrop(oldRange: Range, newRange: Range, model: BrowserModel) :
            ChangeRange(oldRange, newRange, model) {

        override fun toString() = "DragNDrop"
    }
}

/** Changes the current model to another model. */
fun BrowserModel.changeTo(newModel: BrowserModel,
                          setup: (BrowserModel, BrowserModel) -> Unit): Command {
    return Command.ChangeModel(this, newModel, setup)
}

/** Zooms in or out using the current [range] as base. */
fun BrowserModel.zoom(scale: Double): Command? {
    val newLength = Math.round(range.length() / scale).toInt()
    if (newLength == 0) {
        return null
    }

    val shift = (newLength - range.length()) / 2
    return zoomAt(range.startOffset - shift, range.endOffset + shift)
}

/** Zooms to the given range. */
fun BrowserModel.zoomAt(newStartOffset: Int, newEndOffset: Int): Command? {
    return try {
        val newRange = Range(Math.max(0, newStartOffset), Math.min(newEndOffset, length))
        Command.Zoom(range, newRange, this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Shifts the current range by a given [amount].
 *
 * If the amount exceeds the available space no scrolling is done.
 * For example, here
 * 
 *                           length
 *     |------------------------|
 *             |---|
 *             range
 *
 * the allowed amounts are [-6, 12].
 */
fun BrowserModel.scrollBy(amount: Int): Command? {
    val restrictedAmount = if (amount < 0) {
        Math.max(amount, -range.startOffset)
    } else {
        Math.min(amount, length - range.endOffset)
    }

    return try {
        var newStartOffset = range.startOffset + restrictedAmount
        val newEndOffset = range.endOffset + restrictedAmount
        val newRange = Range(Math.max(0, newStartOffset),
                             Math.min(newEndOffset, length))
        Command.Scroll(range, newRange, this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Shift the current range.
 *
 * @param left should scroll to the left?
 * @param whole should scroll the whole [range] or just 20%?
 */
fun BrowserModel.scroll(left: Boolean, whole: Boolean): Command? {
    val regionLength = range.length()
    val scrollDistance = if (whole) {
        regionLength
    } else {
        Math.max(1.0, Math.ceil(regionLength * 0.2)).toInt()
    }

    return scrollBy(scrollDistance * (if (left) -1 else 1))
}

/** Navigates to a given [target]. */
fun SingleLocationBrowserModel.goTo(target: LocationReference): Command {
    val current = range.on(chromosome).on(Strand.PLUS)
    return Command.ChangeLocation(SimpleLocRef(current), target, this)
}

/** Basically an alias to [zoomAt], but executed on DND. */
fun BrowserModel.dragNDrop(newStartOffset: Int, newEndOffset: Int): Command {
    // We implicitly assume the inputs are valid, because they should
    // have been generated automatically.
    return Command.DragNDrop(range, Range(newStartOffset, newEndOffset), this)
}