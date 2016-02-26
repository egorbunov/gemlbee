package org.jetbrains.bio.data.frame

import com.google.common.base.MoreObjects
import com.google.common.base.Preconditions.checkElementIndex
import com.google.common.base.Preconditions.checkPositionIndexes
import com.google.common.collect.ImmutableList
import com.google.common.collect.ObjectArrays
import org.jetbrains.bio.data.frame.DataFrameMapper
import gnu.trove.map.hash.TObjectIntHashMap
import org.jetbrains.bio.data.BitterSet
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream

class DataFrame @JvmOverloads constructor(
        val rowsNumber: Int,
        // XXX should be internal, but oh Krapp!
        val columns: List<Column<*>> = emptyList()) {

    constructor(): this(0)

    val labels = columns.map { it.label.intern() }.toTypedArray()

    val columnsNumber: Int get() = columns.size

    fun resize(rowsNumber: Int)
            = DataFrame(rowsNumber, columns.map { it.resize(rowsNumber) })

    fun reorder(vararg ons: String) = reorder(SortOrder.ASC, *ons)

    fun reorder(sortOrder: SortOrder, vararg ons: String): DataFrame {
        require(ons.size > 0) { "no columns to reorder on" }

        val columns = ArrayList(columns)
        var indices: IntArray
        for (on in ons.reversed()) {
            // sorting is stable in our case, let's use this property
            // for multiple columns sort
            indices = columns[getLabelIndex(on)].sorted(sortOrder)

            for ((c, column) in columns.withIndex()) {
                columns[c] = columns[c].reorder(indices)
            }
        }

        return DataFrame(rowsNumber, columns)
    }

    fun test(pf: RowPredicateFactory,
             startRow: Int = 0,
             endRow: Int = rowsNumber): BitterSet {

        checkPositionIndexes(startRow, endRow, rowsNumber)
        val rowPredicate = pf(this)

        val mask = BitterSet.of(endRow - startRow) { i ->
            rowPredicate.test(startRow + i)
        }

        return mask
    }

    @JvmOverloads fun count(pf: RowPredicateFactory,
                            startRow: Int = 0,
                            endRow: Int = rowsNumber): Int {

        checkPositionIndexes(startRow, endRow, rowsNumber)
        val rowPredicate = pf(this)

        var count = 0
        for (r in startRow..endRow - 1) {
            if (rowPredicate.test(r)) {
                count++
            }
        }
        return count
    }

    fun filter(pf: RowPredicateFactory) = filter(test(pf))

    fun filter(mask: BitSet)
            = DataFrame(mask.cardinality(), columns.map { it.filter(mask) })

    /**
     * Returns a data frame with a given subset of columns.
     */
    fun only(vararg labels: String) = DataFrame(rowsNumber, labels.map { get(it) })

    /**
     * Returns a data frame without a given subset of columns.
     */
    fun omit(label: String, vararg rest: String): DataFrame {
        val labels = ObjectArrays.concat(label, rest)
        return DataFrame(rowsNumber, columns.filter { it.label !in labels })
    }

    fun rename(from: String, to: String) = with(rowsNumber, get(from).rename(to.intern())).omit(from)

    fun with(rowsNumber: Int, column: Column<*>): DataFrame {
        require(columns.isEmpty() || rowsNumber == this.rowsNumber) {
            "#columns = ${columns.size}, #rows = ${this.rowsNumber}, #new column rows = $rowsNumber"
        }

        val columnBuilder = ImmutableList.builder<Column<*>>()
        val idx = getLabelIndexUnsafe(column.label)
        if (idx >= 0) {
            val columns = ArrayList(columns)
            columns[idx] = column
            columnBuilder.addAll(columns)
        } else {
            columnBuilder.addAll(columns)
            columnBuilder.add(column)
        }

        return DataFrame(rowsNumber, columnBuilder.build())
    }

    fun with(label: String, data: ByteArray) = with(data.size, ByteColumn(label, data))

    fun with(label: String, data: ShortArray) = with(data.size, ShortColumn(label, data))

    fun with(label: String, data: IntArray) = with(data.size, IntColumn(label, data))

    fun with(label: String, data: LongArray) = with(data.size, LongColumn(label, data))

    fun with(label: String, data: FloatArray) = with(data.size, FloatColumn(label, data))

    fun with(label: String, data: DoubleArray) = with(data.size, DoubleColumn(label, data))

    fun with(label: String, data: Array<String>) = with(data.size, StringColumn(label, data))

    fun with(label: String, data: BitterSet): DataFrame {
        return with(data.size(), BooleanColumn(label, data))
    }

    fun <T : Enum<T>> with(label: String, enumType: Class<T>, data: Array<T>): DataFrame {
        return with(data.size, EnumColumn(label, enumType, data))
    }

    fun with(label: String, defaultValue: Byte): DataFrame {
        val byteArray = ByteArray(rowsNumber)
        byteArray.fill(defaultValue)
        return with(label, byteArray)
    }

    fun with(label: String, defaultValue: Int): DataFrame {
        val intArray = IntArray(rowsNumber)
        intArray.fill(defaultValue)
        return with(label, intArray)
    }

    fun with(label: String, defaultValue: String) = with(label, Array(rowsNumber) { defaultValue })

    fun <T : Enum<T>> with(label: String, defaultValue: T): DataFrame {
        val enumType = defaultValue.javaClass
        val data = ObjectArrays.newArray<T>(enumType, rowsNumber)
        Arrays.fill(data, defaultValue)
        return with(label, enumType, data)
    }

    fun with(label: String, defaultValue: Boolean): DataFrame {
        val data = BitterSet(rowsNumber)
        data.set(0, rowsNumber, defaultValue)
        return with(label, data)
    }

    //TODO: redo private
    fun getAsByte(r: Int, c: Int) = sliceAsByte(c)[r]

    fun getAsByte(r: Int, label: String) = getAsByte(r, getLabelIndex(label))

    private fun getAsShort(r: Int, c: Int) = sliceAsShort(c)[r]

    fun getAsShort(r: Int, label: String) = getAsShort(r, getLabelIndex(label))

    fun getAsInt(r: Int, c: Int) = sliceAsInt(c)[r]

    fun getAsInt(r: Int, label: String) = getAsInt(r, getLabelIndex(label))

    private fun getAsFloat(r: Int, c: Int) = sliceAsFloat(c)[r]

    fun getAsFloat(r: Int, label: String) = getAsFloat(r, getLabelIndex(label))

    private fun getAsDouble(r: Int, c: Int) = sliceAsDouble(c)[r]

    fun getAsDouble(r: Int, label: String) = getAsDouble(r, getLabelIndex(label))

    @Suppress("unchecked_cast")
    private fun <T> getAsObj(r: Int, c: Int): T = sliceAsObj<Any>(c)[r] as T

    fun <T> getAsObj(r: Int, label: String): T = getAsObj(r, getLabelIndex(label))

    private fun getAsBool(r: Int, c: Int) = sliceAsBool(c)[r]

    fun getAsBool(r: Int, label: String) = getAsBool(r, getLabelIndex(label))

    fun sliceAsByte(label: String) = sliceAsByte(getLabelIndex(label))

    private fun sliceAsByte(c: Int) = columns[c].data as ByteArray

    fun sliceAsShort(c: Int) = columns[c].data as ShortArray

    fun sliceAsShort(label: String) = sliceAsShort(getLabelIndex(label))

    fun sliceAsInt(c: Int) = columns[c].data as IntArray

    fun sliceAsLong(label: String) = sliceAsLong(getLabelIndex(label))

    private fun sliceAsLong(c: Int) = columns[c].data as LongArray

    fun sliceAsInt(label: String) = sliceAsInt(getLabelIndex(label))

    private fun sliceAsFloat(c: Int) = columns[c].data as FloatArray

    fun sliceAsFloat(label: String) = sliceAsFloat(getLabelIndex(label))

    private fun sliceAsDouble(c: Int) = columns[c].data as DoubleArray

    fun sliceAsDouble(label: String) = sliceAsDouble(getLabelIndex(label))

    @Suppress("unchecked_cast")
    private fun <T> sliceAsObj(index: Int): Array<T> = columns[index].data as Array<T>

    fun <T> sliceAsObj(label: String): Array<T> = sliceAsObj(getLabelIndex(label))

    private fun sliceAsBool(index: Int) = columns[index].data as BitterSet

    fun sliceAsBool(label: String) = sliceAsBool(getLabelIndex(label))

    // XXX remove me if you can.
    fun rowAsDouble(r: Int): DoubleArray {
        checkElementIndex(r, rowsNumber)
        val result = DoubleArray(columnsNumber)
        for (c in 0..columnsNumber - 1) {
            result[c] = columns[c].getAsDouble(r)
        }
        return result
    }

    operator fun get(label: String) = columns[getLabelIndex(label)]

    private fun getLabelIndexUnsafe(label: String): Int {
        val strings = labels
        val size = strings.size
        for (i in 0..size - 1) {
            if (strings[i] === label) {
                return i
            }
        }
        return -1
    }

    private fun getLabelIndex(label: String): Int {
        val idx = getLabelIndexUnsafe(label)
        if (idx < 0) {
            require(idx >= 0) {
                listOf("Unknown label '$label'.",
                       "Make sure that you are using interned string as a label!",
                       "Reference equality is used for lookup.",
                       "Known labels ${Arrays.toString(labels)}").joinToString(" ")
            }
        }

        return idx
    }

    @Throws(IOException::class)
    fun save(path: Path) = DataFrameMapper.forPath(path).save(path, this)

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is DataFrame -> false
        else -> rowsNumber == other.rowsNumber && columns == other.columns
    }

    override fun hashCode() = Objects.hash(rowsNumber, columns)

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("rowsNumber", rowsNumber)
            .add("colsNumber", columnsNumber)
            .add("columns", '[' + labels.joinToString(", ") + ']')
            .toString()

    companion object {
        @Throws(IOException::class)
        @JvmStatic fun load(path: Path) = DataFrameMapper.forPath(path).load(path)

        /**
         * Performs an inner join of a list of data frames.
         *
         * @param on column to join on, should be present in all data
         *           frames; duplicate values aren't allowed.
         * @param dfs data frames.
         * @return new data frame with join result sorted wrt to the
         *         join column.
         */
        @Suppress("unchecked_cast")
        @JvmStatic fun mergeInner(on: String, vararg dfs: DataFrame): DataFrame {
            require(dfs.size >= 2) { "expected at least two data frames" }

            var predicate: ObjIntPredicate<*> = ObjIntPredicate<Any> { data, i -> true }
            for (i in 1 until dfs.size) {
                val l = dfs[i - 1][on]
                val r = dfs[i][on]
                // The '#and' call should be safe, because if 'l' is of different
                // type than 'r' we'll get an exception during the intersection.
                val copy = predicate as ObjIntPredicate<Any>
                predicate = (l.intersect(r) as ObjIntPredicate<Any>) and
                        ObjIntPredicate { data, i -> copy.test(data, i) }
            }

            val combinedPredicate = predicate
            val filtered = dfs.map { df ->
                val mask = df[on].test(combinedPredicate)
                df.filter(mask).reorder(on)
            }.toTypedArray()

            val first = filtered.first()
            return columnBind(setOf(on), *filtered)
                    .with(first.rowsNumber, first[on])
        }


        /**
         * Performs an outer join of a list of data frames.
         *
         * @param on column to join on, should be present in all data
         *           frames; duplicate values aren't allowed.
         * @param dfs data frames.
         * @return new data frame with join result sorted wrt to the
         *         join column.
         */
        @JvmStatic fun mergeOuter(on: String, vararg dfs: DataFrame): DataFrame {
            require(dfs.size >= 2) { "expected at least two data frames" }

            val combinedColumn = dfs.asSequence().map { it[on] }
                    .reduce { a, b -> a.merge(b) }
                    .let { it.reorder(it.sorted(SortOrder.ASC)) }

            val rowsNumber = combinedColumn.size()
            val resized = dfs.map { df ->
                df.omit(on).resize(rowsNumber)
                        .with(rowsNumber, df[on].merge(combinedColumn))
                        .reorder(on)
            }.toTypedArray()

            return columnBind(setOf(on), *resized)
                    .with(rowsNumber, combinedColumn)
        }

        /**
         * Combines a list of data frames.
         *
         * The columns with same labels are suffixed with a number, e.g.
         * the column `"n"` will be renamed to `"n1"`. Exceptions are
         * excluded columns.
         *
         * @param exclude a set of column labels to drop from the combined
         *                data frame.
         * @param dfs a list of data frames to combine, each having the
         *            same number of rows and (possibly) different number
         *            of columns.
         * @return a new data frame.
         */
        @JvmStatic fun columnBind(exclude: Set<String>,
                                  vararg dfs: DataFrame): DataFrame {
            require(dfs.isNotEmpty()) { "no data" }

            val summary = Stream.of(*dfs).mapToInt { it.rowsNumber }
                    .summaryStatistics()
            val rowsNumber = summary.max
            require(rowsNumber == summary.min) { "different number of rows" }

            val common = TObjectIntHashMap<String>()
            for (label in dfs.flatMap { it.labels.asList() }) {
                common.adjustOrPutValue(label, 1, 1)
            }

            val columns = ArrayList<Column<*>>()
            val counts = TObjectIntHashMap<String>(common.size())
            for (column in dfs.flatMap { it.columns }) {
                val label = column.label
                if (label in exclude) {
                    continue
                }

                if (common[label] > 1) {
                    val suffix = counts.adjustOrPutValue(label, 1, 1).toString()
                    columns.add(column.rename(label + suffix))
                } else {
                    columns.add(column)
                }
            }

            return DataFrame(rowsNumber, columns)
        }

        @JvmStatic fun columnBind(vararg dfs: DataFrame): DataFrame {
            return columnBind(emptySet(), *dfs)
        }

        /**
         * Combines data frames with the same set of columns by rows.
         */
        @JvmStatic fun rowBind(df1: DataFrame, df2: DataFrame): DataFrame {
            val labels = df1.labels
            if (!Arrays.equals(labels, df2.labels)) {
                val chunks = arrayOf("columns do not match: ",
                                     Arrays.toString(labels), " ",
                                     Arrays.toString(df2.labels))
                throw IllegalArgumentException(chunks.joinToString("\n"))
            }

            return when {
                df1.rowsNumber == 0 -> df2
                df2.rowsNumber == 0 -> df1
                else -> {
                    val columns = labels.map { df1[it] + df2[it] }
                    DataFrame(df1.rowsNumber + df2.rowsNumber, columns)
                }
            }
        }

        @JvmStatic fun rowBind(dfs: Array<DataFrame>): DataFrame {
            require(dfs.size >= 1) { "expected at least one data frame" }
            return dfs.reduce { a, b -> rowBind(a, b) }
        }
    }
}
