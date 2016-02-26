package org.jetbrains.bio.data.frame

import java.util.function.IntPredicate

/**
 * @author Roman.Chernyatchik
 */

interface RowPredicateFactory {
    operator fun invoke(df: DataFrame): IntPredicate

    companion object {
        inline operator fun invoke(crossinline f: (DataFrame) -> IntPredicate) = object : RowPredicateFactory {
            override fun invoke(df: DataFrame) = f(df)
        }
    }
}

inline fun byInt(colName: String,
                 crossinline offsetPredicate: (Int) -> Boolean) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsInt(colName)
    IntPredicate { row -> offsetPredicate(col[row]) }
}

inline fun byShort(colName: String,
                   crossinline offsetPredicate: (Short) -> Boolean) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsShort(colName)
    IntPredicate { row -> offsetPredicate(col[row]) }
}

inline fun byByte(colName: String,
                  crossinline offsetPredicate: (Byte) -> Boolean) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsByte(colName)
    IntPredicate { row -> offsetPredicate(col[row]) }
}

fun byBool(colName: String) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsBool(colName)
    IntPredicate { row -> col[row] }
}

inline fun byDouble(colName: String,
                  crossinline offsetPredicate: (Double) -> Boolean) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsDouble(colName)
    IntPredicate { row -> offsetPredicate(col[row]) }
}

inline fun <T> byObj(colName: String,
                    crossinline offsetPredicate: (T) -> Boolean) = RowPredicateFactory.Companion { df ->
    val col = df.sliceAsObj<T>(colName)
    IntPredicate { row -> offsetPredicate(col[row]) }
}

inline fun byStr(colName: String, crossinline offsetPredicate: (String) -> Boolean)
        = byObj(colName, offsetPredicate)

inline fun predicate(crossinline rowPredicate: DataFrame.(Int) -> Boolean)
        = RowPredicateFactory.Companion { df -> IntPredicate { row -> df.rowPredicate(row) } }

fun all(vararg pfs: RowPredicateFactory): RowPredicateFactory {
    val size = pfs.size
    check(size > 0) { "Predicates number is expected to be"}

    if (size == 1) {
        return pfs[0]
    } else if (size < 6) {
        val p0 = pfs[0]
        val p1 = if (size > 1) pfs[1] else null
        val p2 = if (size > 2) pfs[2] else null
        val p3 = if (size > 3) pfs[3] else null
        val p4 = if (size > 4) pfs[4] else null
        return RowPredicateFactory.Companion { df ->
            val p0_ = p0(df)
            val p1_ = p1?.invoke(df)
            val p2_ = p2?.invoke(df)
            val p3_ = p3?.invoke(df)
            val p4_ = p4?.invoke(df)

            IntPredicate {
                p0_.test(it)
                        && p1_?.test(it) ?: true
                        && p2_?.test(it) ?: true
                        && p3_?.test(it) ?: true
                        && p4_?.test(it) ?: true
            }
        }
    } else {
        return RowPredicateFactory.Companion { df ->
            val ps: Array<IntPredicate> = Array(size) { i ->
                pfs[i](df)
            }

            IntPredicate { row ->
                for (p in ps) {
                    if (!p.test(row)) {
                        return@IntPredicate false
                    }
                }
                return@IntPredicate true
            }
        }
    }
}
