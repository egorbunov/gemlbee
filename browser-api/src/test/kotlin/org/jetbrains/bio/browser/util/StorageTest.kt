package org.jetbrains.bio.browser.util

import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StorageTest {
    lateinit private var storage: Storage

    @Before fun setUp() {
        storage = Storage()
    }

    @Test fun testInit() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)
        assertEquals(1, storage[key])
    }

    @Test fun testSetGet() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)

        assertEquals(1, storage[key])
        storage[key] = 2
        assertEquals(2, storage[key])
    }

    @Test fun testSetNullValue() {
        val key = Key<Any>("KEY")
        storage.init(key, 1)
        storage[key] = null

        assertFalse(storage.contains(key))
    }

    @Test fun testListener() {
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