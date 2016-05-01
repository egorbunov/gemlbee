package org.jetbrains.bio.query.parse

import org.junit.Test

/**
 * @author Egor Gorbunov
 * @since 02.05.16
 */

class LangParserTest {
    val dummyArithmeticTrack = NumericTrack(1.0)
    val dummyPredicateTrack = TruePredicateTrack()

    @Test fun simpleTest() {
        val str = "x := y"
        val parser = LangParser(str,
                mapOf(
                        Pair("y", dummyArithmeticTrack)
                ),
                mapOf(
                        Pair("p", dummyPredicateTrack)
                ))
        val st = parser.parse()
        val toStr = ToStringVisitor()
        st!!.accept(toStr)
        println(toStr.getString())
    }
}
