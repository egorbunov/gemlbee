package org.jetbrains.bio.browser.model

import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery
import java.util.*
import kotlin.properties.Delegates

/**
 * @author Roman.Chernyatchik
 */

abstract class BrowserModel(val genomeQuery: GenomeQuery, range: Range) {
    open var range: Range by Delegates.observable(range) { _property, oldCR, newCR ->
        if (oldCR != newCR) {
            modelChanged()
        }
    }

    protected val modelListeners = ArrayList<ModelListener>()

    abstract val length: Int

    abstract fun presentableName(): String
    abstract fun copy(): BrowserModel

    // XXX why synchronized?

    fun modelChanged() {
        synchronized (modelListeners) {
            modelListeners.forEach(ModelListener::modelChanged)
        }
    }

    fun addModelListener(listener: ModelListener) {
        synchronized (modelListeners) {
            modelListeners.add(listener)
        }
    }

    fun removeModelListener(listener: ModelListener) {
        synchronized (modelListeners) {
            modelListeners.remove(listener)
        }
    }

    override fun toString() = presentableName()
}

interface ModelListener {
    fun modelChanged()
}