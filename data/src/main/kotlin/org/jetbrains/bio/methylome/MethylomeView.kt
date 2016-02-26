package org.jetbrains.bio.methylome

import com.google.common.base.MoreObjects
import org.jetbrains.bio.data.frame.DataFrame

/**
 * A view of a single chromosome/strand data.
 *
 * @author Sergei Lebedev
 * @since 23/05/14
 */
data class StrandMethylomeView internal constructor(
        private val frame: MethylomeFrame) : MethylomeView {

    override fun peel(): DataFrame = frame.peel()

    override fun size(): Int = frame.size()
}

/**
 * A view of strand-combined data for a single chromosome.
 *
 * @author Sergei Lebedev
 * @since 23/05/14
 */
data class ChromosomeMethylomeView internal constructor(
        private val framePlus: MethylomeFrame,
        private val frameMinus: MethylomeFrame) : MethylomeView {

    override fun peel(): DataFrame {
        val df = DataFrame.rowBind(
                framePlus.peel().with("strand", "+"),
                frameMinus.peel().with("strand", "-"))
        return df.reorder("offset")
    }

    override fun size() = framePlus.size() + frameMinus.size()

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("+", framePlus.size())
            .add("-", frameMinus.size())
            .toString()
}

/**
 * An immutable view of [Methylome] data.
 *
 * @author Sergei Lebedev
 * @since 23/05/14
 */
interface MethylomeView {
    /**
     * Extracts a data frame with view contents.
     *
     * @see Methylome.MethylomeFrame for concrete implementations.
     * @see MethylomeToDataFrame for a cytosine context-specific way
     *                           of extracting a data frame.
     */
    fun peel(): DataFrame

    fun size(): Int
}