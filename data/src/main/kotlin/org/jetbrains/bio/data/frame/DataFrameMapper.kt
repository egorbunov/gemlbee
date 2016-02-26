package org.jetbrains.bio.data.frame

import com.google.common.primitives.Primitives
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.QuoteMode
import org.apache.log4j.Logger
import org.jetbrains.bio.data.BitterSet
import org.jetbrains.bio.ext.bufferedReader
import org.jetbrains.bio.ext.bufferedWriter
import org.jetbrains.bio.ext.extension
import org.jetbrains.bio.npy.NpzFile
import java.io.IOException
import java.nio.file.Path

/**
 * A mapper implements data frame loading and saving logic.
 *
 * In most of the cases you should be good with [DataFrame.load] and
 * [DataFrame.save]. An appropriate mapper would be detected from file
 * extension.
 */
sealed class DataFrameMapper {
    /**
     * Tries to guess data frame spec for a given [path].
     */
    abstract fun guess(path: Path): DataFrameSpec

    /** Loads a data frame from a given [path]. */
    @Throws(IOException::class)
    fun load(path: Path): DataFrame {
        val spec = guess(path)
        return load(path, spec)
    }

    /**
     * Loads a data frame from a given [path] using the [spec] as a guide.
     */
    @Throws(IOException::class)
    abstract fun load(path: Path, spec: DataFrameSpec): DataFrame

    /**
     * Saves a data frame to a given [path].
     */
    @Throws(IOException::class)
    abstract fun save(path: Path, df: DataFrame)

    object CSV : DataFrameMapper() {
        private val LOG = Logger.getLogger(CSV::class.java)

        private val FORMAT = CSVFormat.TDF.withQuoteMode(QuoteMode.MINIMAL)
                .withCommentMarker('#')

        /**
         * Tries to guess data frame spec for a given [path].
         */
        override fun guess(path: Path): DataFrameSpec {
            val row = FORMAT.parse(path.bufferedReader()).use {
                checkNotNull(it.firstOrNull()) {
                    "${path.toAbsolutePath()} is empty, no header given."
                }
            }

            val names = row.toList()
            val types = checkNotNull(row.comment) {
                "${path.toAbsolutePath()} doesn't contain typed header"
            }.trim().split("\\s*;\\s*".toRegex())
            // ^^^ the format is # [Type; ]+

            return DataFrameSpec.fromNamesAndTypes(names, types, path)
        }

        override fun load(path: Path, spec: DataFrameSpec) = load(path, spec, true)

        fun load(path: Path, spec: DataFrameSpec, header: Boolean): DataFrame {
            val linesNumber = path.bufferedReader().use {
                it.lines().mapToInt { line -> if (line[0] != FORMAT.commentMarker) 1 else 0 }.sum()
            }

            var rowsNumber = if (linesNumber == 0) {
                0
            } else {
                linesNumber - (if (header) 1 else 0)
            }

            if (rowsNumber == 0) {
                LOG.warn("Empty data frame: " + path.toAbsolutePath())
            }

            val df = DataFrame(rowsNumber, spec.columns.map { it.resize(rowsNumber) })
            path.bufferedReader().use {
                val format = FORMAT.withHeader(*df.labels).withSkipHeaderRecord(header)
                for ((i, row) in format.parse(it).withIndex()) {
                    // XXX we allow row to contain more columns because it's often
                    // the case for UCSC annotations :(
                    check(row.size() >= df.columnsNumber) { "inconsistent record $row" }
                    for (col in 0..df.columnsNumber - 1) {
                        df.columns[col].load(i, row[col])
                    }
                }
            }

            return df
        }

        override fun save(path: Path, df: DataFrame) = save(path.bufferedWriter(), df, true, true)

        fun save(out: Appendable, df: DataFrame, header: Boolean = true, typed: Boolean = true) {
            FORMAT.print(out).use { csvPrinter ->
                if (typed) {
                    csvPrinter.printComment(
                            df.columns.map { it.typeName() }.joinToString("; "))
                }

                if (header) {
                    csvPrinter.printRecord(*df.labels)
                }

                for (r in 0..df.rowsNumber - 1) {
                    for (column in df.columns) {
                        csvPrinter.print(column.dump(r))
                    }

                    csvPrinter.println()
                }
            }
        }
    }

    object NPZ : DataFrameMapper() {
        override fun guess(path: Path) = NpzFile.read(path).use { reader ->
            val meta = reader.introspect()
            val names = meta.map { it.name }
            val types = meta.map { Primitives.wrap(it.type).simpleName }
            DataFrameSpec.fromNamesAndTypes(names, types, path)
        }

        @Suppress("unchecked_cast")
        override fun load(path: Path, spec: DataFrameSpec): DataFrame {
            return NpzFile.read(path).use { reader ->
                val columns = spec.columns.map { column ->
                    val values = reader[column.label]
                    when (column) {
                        is ByteColumn -> column.wrap(values as ByteArray)
                        is ShortColumn -> column.wrap(values as ShortArray)
                        is IntColumn -> column.wrap(values as IntArray)
                        is LongColumn -> column.wrap(values as LongArray)
                        is FloatColumn -> column.wrap(values as FloatArray)
                        is DoubleColumn -> column.wrap(values as DoubleArray)
                        is BooleanColumn -> {
                            val witness = values as BooleanArray
                            column.wrap(BitterSet.of(witness.size) { witness[it] })
                        }
                        is StringColumn -> column.wrap(values as Array<String>)
                        else -> error("unsupported column type: ${column.javaClass.canonicalName}")
                    }
                }

                val rowsNumber = columns.map { it.size() }.distinct().single()
                DataFrame(rowsNumber, columns)
            }
        }

        override fun save(path: Path, df: DataFrame) {
            NpzFile.write(path).use { writer ->
                for (column in df.columns) {
                    when (column) {
                        is ByteColumn -> writer.write(column.label, column.data)
                        is ShortColumn -> writer.write(column.label, column.data)
                        is IntColumn -> writer.write(column.label, column.data)
                        is LongColumn -> writer.write(column.label, column.data)
                        is FloatColumn -> writer.write(column.label, column.data)
                        is DoubleColumn -> writer.write(column.label, column.data)
                        is BooleanColumn -> writer.write(column.label, column.data.toBooleanArray())
                        is StringColumn -> writer.write(column.label, column.data)
                        else -> error("unsupported column type: ${column.javaClass.canonicalName}")
                    }
                }
            }
        }
    }

    companion object {
        /** Determines the appropriate mapper from file extension. */
        internal fun forPath(path: Path) = when (path.extension) {
            "npz" -> NPZ
            else  -> CSV
        }
    }
}

fun DataFrame.dumpHead(rowsCount:Int): String {
    require(rowsCount <= rowsNumber) {
        "Cannot dump $rowsCount from df size $rowsNumber"
    }

    val buff = StringBuilder()
    DataFrameMapper.CSV.save(buff, resize(rowsCount), true, true);
    return buff.toString();
}
