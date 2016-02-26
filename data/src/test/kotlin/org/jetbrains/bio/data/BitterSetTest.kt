package org.jetbrains.bio.data

import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitterSetTest {
    @Test fun empty() {
        assertEquals(0, BitterSet(0).size())
        assertEquals(0, BitterSet.Companion.of(0, BitSet()).size())
    }

    @Test fun equalsEmpty() {
        assertEquals(BitterSet(0), BitterSet(0))
    }

    @Test fun equals() {
        assertEquals(BitterSet.Companion.of(10) { it % 2 == 0 },
                BitterSet.Companion.of(10) { it % 2 == 0 })
    }

    @Test fun plusEmpty() {
        val bs = BitterSet.of(10, BitSet())
        assertEquals(bs, bs + BitterSet.Companion.of(0, BitSet()))
    }

    @Test fun plusNonEmpty() {
        val bs1 = with(BitterSet.of(10, BitSet())) {
            set(1)
            set(2)
            this
        }

        val bs2 = with(BitterSet.of(5, BitSet())) {
            set(1)
            this
        }

        val bs = bs1 + bs2
        assertEquals(2, bs1.cardinality())
        assertEquals(10, bs1.size())
        assertEquals(1, bs2.cardinality())
        assertEquals(5, bs2.size())

        assertEquals(bs1.cardinality() + bs2.cardinality(), bs.cardinality())
        assertEquals(bs1.size() + bs2.size(), bs.size())
        assertTrue(bs[1] && bs[2])
        assertTrue(bs[11])
    }
}
