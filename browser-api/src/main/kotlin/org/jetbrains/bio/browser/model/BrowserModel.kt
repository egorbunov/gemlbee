package org.jetbrains.bio.browser.model

import com.google.common.collect.Lists
import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.genome.query.GenomeQuery
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * @author Roman.Chernyatchik
 */

abstract class BrowserModel(val genomeQuery: GenomeQuery, range: Range) {
    open var range: Range by CallbackProperty(range, { desc, oldCR, newCR ->
        if (!oldCR.equals(newCR)) {
            modelChanged()
        }
    })

    protected val modelListeners: MutableList<ModelListener> = Lists.newArrayList<ModelListener>()

    abstract val length: Int

    abstract fun presentableName(): String
    abstract fun copy(): BrowserModel

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

class CallbackProperty<T>(default: T,
                          private val callback: (name: KProperty<*>, oldValue: T, newValue: T) -> Unit)
: ReadWriteProperty<Any?, T> {
    private var value = default

    operator override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    operator override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val old = this.value
        this.value = value
        callback(property, old, value)
    }
}

interface ModelListener {
    fun modelChanged()
}

