package org.jetbrains.bio.methylome

import org.jetbrains.bio.data.frame.*


/**
 * @author Roman.Chernyatchik
 */
fun byPatternOptional(ptn: CytosineContext?): RowPredicateFactory {
    val all = predicate { true }
    return when (ptn) {
        null -> all;
        else -> RowPredicateFactory { df ->
            when {
                "tag" in df.labels -> byPattern(ptn)(df)
                else -> all(df)
            }
        }
    }
}

fun byPattern(ptn: CytosineContext): RowPredicateFactory {
    val tag = ptn.tag
    return byByte("tag") { it == tag}
}

fun covered()
        = byShort("n") { it != 0.toShort() }

fun covered(replicateId: Int)
        = byShort("n$replicateId".intern()) { it != 0.toShort() }

fun controlFdr(alpha: Double)
        = byDouble("qvalue") { it <= alpha}

fun withMcReads()
        = byShort("k") { it != 0.toShort()}

inline fun byOffset(crossinline offsetPredicate: (Int) -> Boolean)
        = byInt("offset", offsetPredicate)
