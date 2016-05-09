package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.Range
import org.jetbrains.bio.query.containers.toSortedRangeList
import org.junit.Assert
import org.junit.Test

/**
 * @author Egor Gorbunov
 * @since 09.05.16
 */

class SortedRangeListTest() {

    @Test fun testConstruction() {
        // Pair for test is: Pair(list of ranges to construct from, expected resulting list of ranges)
        arrayOf(
                Pair(listOf(Range(10, 100), Range(40, 200), Range(200, 300)), listOf(Range(10, 300))),
                Pair(listOf(Range(50, 300), Range(10, 20), Range(30, 50), Range(0, 1)),
                        listOf(Range(0, 1), Range(10, 20), Range(30, 300))),
                Pair(emptyList(), emptyList()),
                Pair(listOf(Range(1, 100001)), listOf(Range(1, 100001))),
                Pair(listOf(Range(50, 60), Range(30, 40), Range(10, 20)),
                        listOf(Range(10, 20), Range(30, 40), Range(50, 60)))
        ).forEach { p ->
            val list = p.first.toSortedRangeList()
            Assert.assertEquals(p.second.size, list.size())
            list.zip(p.second).forEach {
                Assert.assertEquals(it.second, it.first)
            }
        }
    }


    @Test fun testOr() {
       arrayOf(
               arrayOf(
                       listOf(Range(0, 100), Range(200, 300), Range(400, 500)).toSortedRangeList(), // A
                       listOf(Range(100, 200), Range(300, 400)).toSortedRangeList(),  // B
                       listOf(Range(0, 500)).toSortedRangeList() // A or B (expected)
               ),
               arrayOf(
                       emptyList<Range>().toSortedRangeList(),
                       listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                       listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList()
               ),
               arrayOf(
                       listOf(Range(10, 20), Range(60, 100)).toSortedRangeList(),
                       listOf(Range(30, 50), Range(150, 200)).toSortedRangeList(),
                       listOf(Range(10, 20), Range(30, 50), Range(60, 100), Range(150, 200)).toSortedRangeList()
               ),
               arrayOf(
                       listOf(Range(10, 1000)).toSortedRangeList(),
                       listOf(Range(500, 900)).toSortedRangeList(),
                       listOf(Range(10, 1000)).toSortedRangeList()
               ),
               arrayOf(
                       listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                       listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                       listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList()
               ),
               arrayOf(
                       emptyList<Range>().toSortedRangeList(),
                       emptyList<Range>().toSortedRangeList(),
                       emptyList<Range>().toSortedRangeList()
               )
        ).forEach { testData ->
           val ans = testData[0] or testData[1]
           val expected = testData[2]
           Assert.assertEquals(expected.size(), ans.size())
           ans.zip(expected).forEach {
               Assert.assertEquals(it.second, it.first)
           }
       }
    }

    @Test fun testAnd() {
        arrayOf(
                arrayOf(
                        listOf(Range(0, 100), Range(200, 300), Range(400, 500)).toSortedRangeList(), // A
                        listOf(Range(100, 200), Range(300, 400)).toSortedRangeList(),  // B
                        emptyList<Range>().toSortedRangeList() // A and B (expected)
                ),
                arrayOf(
                        emptyList<Range>().toSortedRangeList(),
                        listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                        emptyList<Range>().toSortedRangeList()
                ),
                arrayOf(
                        listOf(Range(10, 20), Range(60, 100)).toSortedRangeList(),
                        listOf(Range(30, 50), Range(150, 200)).toSortedRangeList(),
                        emptyList<Range>().toSortedRangeList()
                ),
                arrayOf(
                        listOf(Range(10, 1000)).toSortedRangeList(),
                        listOf(Range(500, 900)).toSortedRangeList(),
                        listOf(Range(500, 900)).toSortedRangeList()
                ),
                arrayOf(
                        listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                        listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList(),
                        listOf(Range(10, 123), Range(444, 900), Range(1000, 1200)).toSortedRangeList()
                ),
                arrayOf(
                        emptyList<Range>().toSortedRangeList(),
                        emptyList<Range>().toSortedRangeList(),
                        emptyList<Range>().toSortedRangeList()
                ),
                arrayOf(
                        listOf(Range(0, 100), Range(200, 300), Range(400, 500)).toSortedRangeList(),
                        listOf(Range(50, 150), Range(160, 240), Range(250, 450), Range(460, 600)).toSortedRangeList(),
                        listOf(Range(50, 100), Range(200, 240), Range(250, 300), Range(400, 450), Range(460, 500))
                                .toSortedRangeList()
                )
        ).forEach { testData ->
            val ans = testData[0] and testData[1]
            val expected = testData[2]
            Assert.assertEquals(expected.size(), ans.size())
            ans.zip(expected).forEach {
                Assert.assertEquals(it.second, it.first)
            }
        }
    }
}

