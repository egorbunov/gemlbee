package org.jetbrains.bio.browser.util

import junit.framework.TestCase
import java.util.concurrent.atomic.AtomicInteger

class StorageTest : TestCase() {

    lateinit private var storage: Storage

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        storage = Storage()
    }

    @Throws(Exception::class)
    fun testInit() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)
        assertEquals(1, storage[key])
    }

    @Throws(Exception::class)
    fun testSetGet() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)
        var changed: Boolean
        storage.addListener(object: Listener {
            override fun valueChanged(key: Key<*>, value: Any?) {
                changed = true;
            }
        })
        assertEquals(1, storage[key])
        changed = false
        storage[key] = 2
        assertTrue(changed)

        changed = false
        storage[key] = null
        assertTrue(changed)
        changed = false
        storage[key] = null
        assertFalse(changed)

        changed = false
        storage[key] = 1
        assertTrue(changed)
        changed = false
        storage[key] = 1
        assertFalse(changed)
    }

    @Throws(Exception::class)
    fun testSetNullValue() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)
        storage[key] = null

        assertFalse(storage.contains(key))
    }

    @Throws(Exception::class)
    fun testListener() {
        val key = Key<Any>("KEY")
        val counter = AtomicInteger()
        storage.addListener(object: Listener {
            override fun valueChanged(key: Key<*>, value: Any?) {
                counter.incrementAndGet();
            }
        })
        storage[key] = null
        assertEquals(0, counter.get())
        storage.init(key, 1)
        assertEquals(0, counter.get())
        storage[key] = 1
        assertEquals(0, counter.get())
        storage[key] = 2
        assertEquals(1, counter.get())
        storage[key] = 3
        storage[key] = 3
        assertEquals(2, counter.get())
    }
}