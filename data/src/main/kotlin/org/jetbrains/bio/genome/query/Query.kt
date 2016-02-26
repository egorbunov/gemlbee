package org.jetbrains.bio.genome.query

import java.util.function.Supplier

/**
 * An annotated version of [java.util.function.Function].
 *
 * @author Oleg Shpynov
 * @since 21/06/12
 */
interface Query<I, O> {
    fun process(input: I): O

    /**
     * Returns unique identifier suitable for use in a file name.
     */
    val id: String

    /**
     * Returns a human-readable description for this query.
     */
    val description: String
        get() = id

    companion object {
        fun <I> ofSupplier(id: String, supplier: Supplier<I>): InputQuery<I> {
            return object : InputQuery<I> {
                override fun getUncached() = supplier.get()

                override val id: String get() = id
            }
        }
    }
}