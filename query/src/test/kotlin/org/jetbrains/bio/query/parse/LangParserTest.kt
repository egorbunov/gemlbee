package org.jetbrains.bio.query.parse

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

/**
 * @author Egor Gorbunov
 * @since 02.05.16
 */

@RunWith(Parameterized::class)
class LangParserTest(val query: String, val expectedStatement: Statement) {
    companion object {
        val dummyPredicateTrack = TruePredicateTrack()
        val dummyArithmeticTrack = NumericTrack(1.0)
        val arithmeticTracksMap = HashMap<String, ArithmeticTrack>()
        val predicateTracksMap = HashMap<String, PredicateTrack>()

        init {
            listOf("a", "b", "c", "X", "Y", "Z", "x", "y", "z").forEach {
                arithmeticTracksMap[it] = dummyArithmeticTrack
            }
            listOf("pa", "pb", "pc", "pX", "pY", "pZ", "px", "py", "pz").forEach {
                predicateTracksMap[it] = dummyPredicateTrack
            }
        }

        @Parameterized.Parameters
        @JvmStatic fun getTestData(): Collection<Any> {
            return Arrays.asList(*arrayOf(
                    arrayOf("1+2*3/10-5",
                            BinaryArithmeticTrack(
                                    ArithmeticOp.MINUS,
                                    BinaryArithmeticTrack(
                                            ArithmeticOp.PLUS,
                                            NumericTrack(1.0),
                                            BinaryArithmeticTrack(
                                                    ArithmeticOp.DIV,
                                                    BinaryArithmeticTrack(
                                                            ArithmeticOp.MUL,
                                                            NumericTrack(2.0),
                                                            NumericTrack(3.0)
                                                    ),
                                                    NumericTrack(10.0)
                                            )
                                    ),
                                    NumericTrack(5.0)
                            )),
                    arrayOf("x := y",
                            AssignStatement(
                                    "x",
                                    NamedArithmeticTrack("y", arithmeticTracksMap["y"]!!)
                            )),
                    arrayOf("x := 3 * (1 + 2)",
                            AssignStatement(
                                    "x",
                                    BinaryArithmeticTrack(
                                            ArithmeticOp.MUL,
                                            NumericTrack(3.0),
                                            BinaryArithmeticTrack(
                                                    ArithmeticOp.PLUS,
                                                    NumericTrack(1.0),
                                                    NumericTrack(2.0)
                                            ))
                            )),
                    arrayOf("show Y",
                            ShowTrackStatement(
                                    NamedArithmeticTrack(
                                            "Y",
                                            arithmeticTracksMap["Y"]!!
                                    )
                            )),
                    arrayOf("if (a < b) then 1 else 2",
                            IfStatementTrack(
                                    RelationPredicateTrack(
                                            RelationOp.LE,
                                            NamedArithmeticTrack("a", arithmeticTracksMap["a"]!!),
                                            NamedArithmeticTrack("b", arithmeticTracksMap["b"]!!)
                                    ),
                                    NumericTrack(1.0),
                                    NumericTrack(2.0)
                            )),
                    arrayOf("(if a < b then x else y) + 3",
                            BinaryArithmeticTrack(
                                    ArithmeticOp.PLUS,
                                    IfStatementTrack(
                                            RelationPredicateTrack(
                                                    RelationOp.LE,
                                                    NamedArithmeticTrack("a", arithmeticTracksMap["a"]!!),
                                                    NamedArithmeticTrack("b", arithmeticTracksMap["b"]!!)
                                            ),
                                            NamedArithmeticTrack("x", arithmeticTracksMap["x"]!!),
                                            NamedArithmeticTrack("y", arithmeticTracksMap["y"]!!)
                                    ),
                                    NumericTrack(3.0)
                            )),
                    arrayOf("if (if a < b then x else y) == Z then X else Z",
                            IfStatementTrack(
                                    RelationPredicateTrack(
                                            RelationOp.EQ,
                                            IfStatementTrack(
                                                    RelationPredicateTrack(
                                                            RelationOp.LE,
                                                            NamedArithmeticTrack("a", arithmeticTracksMap["a"]!!),
                                                            NamedArithmeticTrack("b", arithmeticTracksMap["b"]!!)
                                                    ),
                                                    NamedArithmeticTrack("x", arithmeticTracksMap["x"]!!),
                                                    NamedArithmeticTrack("y", arithmeticTracksMap["y"]!!)
                                            ),
                                            NamedArithmeticTrack("Z", arithmeticTracksMap["Z"]!!)
                                    ),
                                    NamedArithmeticTrack("X", arithmeticTracksMap["X"]!!),
                                    NamedArithmeticTrack("Z", arithmeticTracksMap["Z"]!!)
                            )),
                    arrayOf("pX AND pZ OR py AND (a < b)",
                            OrPredicateTrack(
                                    AndPredicateTrack(
                                            NamedPredicateTrack("pX", predicateTracksMap["pX"]!!),
                                            NamedPredicateTrack("pZ", predicateTracksMap["pZ"]!!)
                                            ),
                                    AndPredicateTrack(
                                            NamedPredicateTrack("py", predicateTracksMap["py"]!!),
                                            RelationPredicateTrack(
                                                    RelationOp.LE,
                                                    NamedArithmeticTrack("a", arithmeticTracksMap["a"]!!),
                                                    NamedArithmeticTrack("b", arithmeticTracksMap["b"]!!)
                                            )
                                    )

                            )),
                    arrayOf("(pX OR pY) AND (pX OR pZ) AND (NOT pX)",
                            AndPredicateTrack(
                                    AndPredicateTrack(
                                            OrPredicateTrack(
                                                    NamedPredicateTrack("pX", predicateTracksMap["pX"]!!),
                                                    NamedPredicateTrack("pY", predicateTracksMap["pY"]!!)
                                            ),
                                            OrPredicateTrack(
                                                    NamedPredicateTrack("pX", predicateTracksMap["pX"]!!),
                                                    NamedPredicateTrack("pZ", predicateTracksMap["pZ"]!!)
                                            )
                                    ),
                                    NotPredicateTrack(
                                            NamedPredicateTrack("pX", predicateTracksMap["pX"]!!)
                                    )
                            ))
            ))
        }
    }

    @Test fun testStrParsedCorrectly() {
        val parser = LangParser(query, arithmeticTracksMap, predicateTracksMap)
        val st = parser.parse()!!

//        val toStr = ToStringVisitor()
//        st.accept(toStr)
//        println(toStr.getString())

        Assert.assertEquals(st.compareTo(expectedStatement), 0)
    }
}
