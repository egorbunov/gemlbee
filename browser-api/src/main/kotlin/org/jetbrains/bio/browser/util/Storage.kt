package org.jetbrains.bio.browser.util

import com.google.common.collect.Lists
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Key<T>(val id: String) {
    @Suppress("UNCHECKED_CAST")
    fun cast(v: Any?): T = v as T
}

interface Listener {
    fun valueChanged(key: Key<*>, value: Any?)
}

class Storage {

    private val map = ConcurrentHashMap<String, Any>()
    private val listeners = Lists.newArrayList<Listener>()

    operator fun contains(key: Key<*>): Boolean {
        return map.containsKey(key.id)
    }

    fun <T> init(key: Key<T>, value: T) {
        require(!contains(key)) { "Already contains key ${key.id}, use #set" }
        map.put(key.id, value!!)
    }

    operator fun <T> get(key: Key<T>): T {
        if (!contains(key)) {
            throw NoSuchElementException(key.toString())
        }
        return key.cast(map[key.id])
    }

    operator fun <T> set(key: Key<T>, value: T) {
        if (map[key.id] == value) {
            return
        }

        map.put(key.id, value as Any)

        synchronized (listeners) {
            listeners.forEach { l -> l.valueChanged(key, value) }
        }
    }

    fun addListener(listener: Listener) {
        synchronized (listeners) {
            listeners.add(listener)
        }
    }

    fun copy(): Storage {
        val copy = Storage()
        copy.map.putAll(map)
        return copy
    }
}
