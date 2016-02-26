package org.jetbrains.bio.data.frame

import java.util.*

/**
 * A row-by-row builder for [DataFrame].
 *
 * Note, that constructing the data frame via [DataFrame.with]
 * calls might be more efficient.
 *
 * @author Evgeny Kurbatsky
 * @since 17/06/14
 */
class DataFrameBuilder(private val spec: DataFrameSpec) {
    private val accumulator = ArrayList<Array<Any>>()

    fun add(vararg row: Any) {
        for ((i, value) in row.withIndex()) {
            val column = spec.columns[i]
            require(column.accepts(value)) {
                "Wrong type: $i-th arg (column: ${column.label}) value " +
                "$value is expected to be of type ${column.typeName()}"
            }
        }

        accumulator.add(arrayOf(*row))
    }

    fun build(): DataFrame {
        val rowsNumber = accumulator.size
        val columns = spec.columns.mapIndexed { col, column ->
            val newColumn = column.resize(rowsNumber)
            for (row in 0..rowsNumber - 1) {
                newColumn.load(row, accumulator[row][col].toString())
            }
            newColumn
        }.toList()

        return DataFrame(rowsNumber, columns)
    }
}
