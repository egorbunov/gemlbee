package org.jetbrains.bio.io

import com.google.common.base.Joiner
import com.google.common.base.MoreObjects
import com.google.common.base.Splitter
import com.google.common.collect.UnmodifiableIterator
import com.google.common.io.Closeables
import com.google.common.primitives.Ints
import org.apache.log4j.Logger
import org.jetbrains.bio.ext.bufferedReader
import org.jetbrains.bio.ext.bufferedWriter
import java.awt.Color
import java.io.*
import java.nio.file.Path
import java.util.*
import kotlin.properties.Delegates

private val NON_DATA_LINE_PATTERN = "^(?:#|track|browser).+$".toRegex()

/**
 * Even though BED has a well-defined spec it is widely known
 * as a very non-deterministic format. [BedFormat] is here to
 * help you fight the chaos, allowing for various subsets
 * of fields (aka columns) to be included or excluded.
 *
 * If you have a good BED file, just use [BedFormat.DEFAULT] or
 * [BedFormat.SIMPLE].
 *
 * See https://genome.ucsc.edu/FAQ/FAQformat.html#format1 for
 * the complete spec.
 *
 * @author Oleg Shpynov
 * @author Sergei Lebedev
 * @since 25/05/15
 */
class BedFormat(vararg fields: BedEntry.Field) {
    var schema = fields
    var separator = '\t'

    private fun copy(): BedFormat {
        val format = BedFormat(*schema)
        format.separator = separator
        return format
    }

    fun parse(path: Path): BedParser {
        return BedParser(path.bufferedReader(), path,
                         schema = schema, separator = separator)
    }

    fun parse(reader: Reader): BedParser {
        return BedParser(reader.buffered(), null,
                         schema = schema, separator = separator)
    }

    fun print(path: Path) = print(path.bufferedWriter())

    fun print(writer: Writer) = BedPrinter(writer.buffered(),
                                           schema = schema, separator = separator)

    /**
     * Returns a copy of format with given fields omitted.
     *
     * P.S: Optional trailing fields will be skipped automatically and
     * you don't need use this method for them.
     */
    fun skip(vararg skipped: BedEntry.Field): BedFormat {
        val format = copy()
        format.schema = schema.filter { it !in skipped }.toTypedArray()
        return format
    }

    /**
     * Returns a copy of the format with a given separator.
     */
    fun splitter(separator: Char): BedFormat {
        val format = copy()
        format.separator = separator
        return format
    }

    companion object {

        val LOG = Logger.getLogger(BedFormat::class.java)

        @JvmField val DEFAULT =
                BedFormat(BedEntry.CHROMOSOME, BedEntry.START_POS, BedEntry.END_POS,
                          BedEntry.NAME, BedEntry.SCORE, BedEntry.STRAND,
                          BedEntry.THICK_START, BedEntry.THICK_END, BedEntry.ITEM_RGB,
                          BedEntry.BLOCK_COUNT, BedEntry.BLOCK_SIZES, BedEntry.BLOCK_STARTS)

        @JvmField val SIMPLE = BedFormat(BedEntry.CHROMOSOME,
                                         BedEntry.START_POS, BedEntry.END_POS,
                                         BedEntry.STRAND)


        @JvmStatic fun auto(path: Path): BedFormat {
            val reader = path.bufferedReader()
            try {
                while (true) {
                    val line = reader.readLine()
                    if (line == null) {
                        return DEFAULT
                    } else if (NON_DATA_LINE_PATTERN.matches(line)) {
                        continue
                    }

                    val splitter = if ('\t' in line) '\t' else ' '
                    val chunks = Splitter.on(splitter).trimResults().omitEmptyStrings().split(line).toList()
                    val fields = matchFields(DEFAULT.schema, chunks)
                    if (fields.size != chunks.size) {
                        LOG.warn("Not all chunks were identified for line:\n$line\n" +
                                "Fields:\n${fields.map { it.toString() }.joinToString(", ")}")
                    }
                    return BedFormat(*fields.toTypedArray()).splitter(splitter)
                }
            } finally {
                reader.close()
            }
        }

        private fun matchFields(schema: Array<out BedEntry.Field>,
                                chunks: List<String>): List<BedEntry.Field> {

            // https://genome.ucsc.edu/FAQ/FAQformat.html#format1:
            //
            // BED format provides a flexible way to define the data lines that are displayed
            // in an annotation track. BED lines have three required fields and nine additional
            // optional fields. The number of fields per line must be consistent throughout
            // any single set of data in an annotation track. The order of the optional fields
            // is binding: lower-numbered fields must always be populated if higher-numbered fields are used.
            //
            // Order:
            //      chrom, chromStart, chromEnd, name, score, strand, thickStart, thickEnd, itemRgb,
            //      blockCount, blockSizes, blockStarts

            val fields = ArrayList<BedEntry.Field>()
            val testEntry = BedEntry()

            var ci = 0  // chunk index
            for (fi in 0..schema.size - 1) {
                val field = schema[fi]
                try {
                    field.read(testEntry, chunks[ci])
                    fields.add(field)
                    if (ci == chunks.size) {
                        break
                    }
                    ci++
                } catch (ignored: Throwable) {
                    // skip field, try next one
                }
            }

            return fields;
        }
    }
}

class BedParser(private val reader: BufferedReader,
                private val path: Path?,
                private val schema: Array<out BedEntry.Field>,
                private val separator: Char)
:
        Iterable<BedEntry>, AutoCloseable, Closeable {

    private val splitter = Splitter.on(separator).trimResults().omitEmptyStrings()

    override fun iterator(): UnmodifiableIterator<BedEntry> {
        return object : UnmodifiableIterator<BedEntry>() {
            private var nextEntry: BedEntry? = parse(readLine())

            override fun next(): BedEntry? {
                val entry = nextEntry
                nextEntry = parse(readLine())
                return entry
            }

            override fun hasNext(): Boolean = nextEntry != null

        }
    }

    private fun readLine(): String? {
        var line: String? = null
        try {
            do {
                line = reader.readLine()
            } while (line != null && NON_DATA_LINE_PATTERN.matches(line))
        } finally {
            if (line == null) {
                close()
            }
        }

        return line
    }

    protected fun parse(line: String?): BedEntry? {
        if (line == null) {
            return null
        }

        val chunks = splitter.splitToList(line)
        if (chunks.size < 3) {
            throw IllegalArgumentException("invalid BED: '$line'")
        }

        try {
            val result = BedEntry()
            var i = 0
            while (i < schema.size && i < chunks.size) {
                schema[i].read(result, chunks[i])
                i++
            }
            return result
        } catch (e: Exception) {
            LOG.error("${path ?: "unknown source"}: invalid BED: '$line'", e)
            return null
        }

    }

    override fun close() = Closeables.closeQuietly(reader)

    companion object {
        private val LOG = Logger.getLogger(BedParser::class.java)
    }
}

class BedPrinter(private val writer: BufferedWriter,
                 private val schema: Array<out BedEntry.Field>,
                        separator: Char) : AutoCloseable, Closeable {
    private val joiner = Joiner.on(separator).skipNulls()

    fun print(line: String) {
        writer.write(line)
        writer.newLine()
    }

    fun print(entry: BedEntry) {
        val chunks = schema.asSequence().map { it.write(entry) }
        print(joiner.join(chunks.iterator()))
    }

    override fun close() = Closeables.close(writer, true)
}

class BedEntry {
    /** The name of the chromosome e.g. `"chr3"` or `"chr2_random"`. */
    var chromosome: String by Delegates.notNull()
    /** 0-based starting position (inclusive). */
    var chromStart: Int = 0
    /** 0-based ending position (exclusive). */
    var chromEnd: Int = 0

    /** Defines the name of the BED line. */
    var name = ""

    /** A score between 0 and 1000. */
    var score: Int = 0

    /** Defines the strand - either '+' or '-'. */
    var strand = '+'

    /** The starting position at which the feature is drawn thickly. */
    var thickStart: Int = 0

    /** The ending position at which the feature is drawn thickly. */
    var thickEnd: Int = 0

    /** The colour of entry in the form R,G,B (e.g. 255,0,0). */
    var itemRgb: Color? = null

    /** The number of blocks (exons) in the BED line. */
    var blockCount: Int = 0

    /**
     * A comma-separated list of the block sizes.
     *
     * The number of items in this list should correspond to [blockCount].
     */
    var blockSizes = IntArray(0)

    /**
     * A comma-separated list of block starts.
     *
     * All positions should be calculated relative to [chromStart].
     * The number of items should correspond to [blockCount].
     */
    var blockStarts = IntArray(0)

    internal constructor() {}

    constructor(chromosome: String, chromStart: Int, chromEnd: Int, strand: Char) {
        this.chromosome = chromosome
        this.chromStart = chromStart
        this.chromEnd = chromEnd
        this.strand = strand
    }

    constructor(chromosome: String,
                chromStart: Int, chromEnd: Int, name: String,
                score: Int, strand: Char,
                thickStart: Int, thickEnd: Int, itemRgb: Color?,
                blockCount: Int, blockSizes: IntArray, blockStarts: IntArray) {
        this.chromosome = chromosome
        this.chromStart = chromStart
        this.chromEnd = chromEnd
        this.name = name
        this.score = score
        this.strand = strand
        this.thickStart = thickStart
        this.thickEnd = thickEnd
        this.itemRgb = itemRgb
        this.blockCount = blockCount
        this.blockSizes = blockSizes
        this.blockStarts = blockStarts
    }

    override fun toString() = MoreObjects.toStringHelper(this)
            .add("chrom", chromosome)
            .add("chromStart", chromStart).add("chromEnd", chromEnd)
            .add("name", name)
            .add("score", score)
            .add("strand", strand)
            .add("thickStart", thickStart).add("thickEnd", thickEnd)
            .add("itemRgb", itemRgb)
            .add("blocks", blockStarts.zip(blockSizes))
            .toString()

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is BedEntry -> false
        else -> chromStart == other.chromStart && chromEnd == other.chromEnd &&
                name == other.name && strand == other.strand &&
                score == other.score &&
                thickStart == other.thickStart && thickEnd == other.thickEnd &&
                itemRgb == other.itemRgb &&
                blockCount == other.blockCount && chromosome == other.chromosome &&
                Arrays.equals(blockSizes, other.blockSizes) &&
                Arrays.equals(blockStarts, other.blockStarts)
    }

    override fun hashCode(): Int {
        return Objects.hash(chromosome, chromStart, chromEnd, name, score, strand,
                            thickStart, thickEnd, itemRgb, blockCount,
                            Arrays.hashCode(blockSizes), Arrays.hashCode(blockStarts))
    }

    abstract class Field {
        abstract fun read(entry: BedEntry, value: String)

        abstract fun write(entry: BedEntry): String

        protected fun String.splitToInts(size: Int): IntArray {
            val chunks = IntArray(size)
            val s = Splitter.on(',').split(this).iterator()
            var ptr = 0
            while (s.hasNext() && ptr < size) {
                chunks[ptr++] = s.next().toInt()
            }

            return chunks
        }

        override fun toString(): String = this.javaClass.simpleName
    }

    companion object {
        @JvmField val CHROMOSOME: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // chrom - The name of the chromosome (e.g. chr3, chrY, chr2_random)
                // or scaffold (e.g. scaffold10671).
                entry.chromosome = value
            }

            override fun write(entry: BedEntry) = entry.chromosome
        }

        @JvmField val START_POS: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // chromStart - The starting position of the feature in the chromosome
                // or scaffold. The first base in a chromosome is numbered 0.
                entry.chromStart = value.toInt()
            }

            override fun write(entry: BedEntry) = entry.chromStart.toString()
        }

        @JvmField val END_POS: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // chromEnd - The ending position of the feature in the chromosome
                // or scaffold. The chromEnd base is not included in the display of
                // the feature. For example, the first 100 bases of a chromosome are
                // defined as chromStart=0, chromEnd=100, and span the bases numbered 0-99
                entry.chromEnd = value.toInt()
            }

            override fun write(entry: BedEntry) = entry.chromEnd.toString()
        }

        @JvmField val NAME: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // name - Defines the name of the BED line. This label is displayed to the left
                // of the BED line in the Genome Browser window when the track is open to full
                // display mode or directly to the left of the item in pack mode.
                if (value.length == 1) {
                    val ch = value.first()
                    check(ch != '+' && ch != '-') { "Name expected, but was: $value"}
                }
                entry.name = value
            }

            override fun write(entry: BedEntry) = entry.name
        }

        @JvmField val SCORE: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // score - A score between 0 and 1000. If the track line useScore attribute is set
                // to 1 for this annotation data set, the score value will determine the level of gray
                // in which this feature is displayed (higher numbers = darker gray). This table shows
                // the Genome Browser's translation of BED score values into shades of gray ..
                val score = value.toInt()
                entry.score = score
            }
            override fun write(entry: BedEntry) = entry.score.toString()
        }

        @JvmField val STRAND: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // strand - Defines the strand - either '+' or '-'.
                check(value.length == 1)
                val ch = value.first()
                check(ch == '+' || ch == '-') { "Strand char '+' or '-' expected, but was: $value" }
                entry.strand = ch
            }

            override fun write(entry: BedEntry) = entry.strand.toString()
        }

        @JvmField val THICK_START: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // thickStart - The starting position at which the feature is drawn thickly
                // (for example, the start codon in gene displays). When there is no thick part,
                // thickStart and thickEnd are usually set to the chromStart position.
                entry.thickStart = value.toInt()
            }

            override fun write(entry: BedEntry) = entry.thickStart.toString()
        }

        @JvmField val THICK_END: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // thickEnd - The ending position at which the feature is drawn thickly
                // (for example, the stop codon in gene displays).
                entry.thickEnd = value.toInt()
            }

            override fun write(entry: BedEntry) = entry.thickEnd.toString()
        }

        @JvmField val ITEM_RGB: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // itemRgb - An RGB value of the form R,G,B (e.g. 255,0,0). If the track line itemRgb
                // attribute is set to "On", this RBG value will determine the display color of the data
                // contained in this BED line. NOTE: It is recommended that a simple color scheme
                // (eight colors or less) be used with this attribute to avoid overwhelming the color
                // resources of the Genome Browser and your Internet browser.
                val chunks = value.splitToInts(3)
                entry.itemRgb = Color(chunks[0], chunks[1], chunks[2])
            }

            override fun write(entry: BedEntry): String {
                val itemRgb = checkNotNull(entry.itemRgb)
                return Ints.join(",", itemRgb.red, itemRgb.green, itemRgb.blue)
            }
        }

        @JvmField val BLOCK_COUNT: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // blockCount - The number of blocks (exons) in the BED line.
                entry.blockCount = value.toInt()
            }

            override fun write(entry: BedEntry) = entry.blockCount.toString()
        }

        @JvmField val BLOCK_SIZES: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // blockSizes - A comma-separated list of the block sizes. The number of items in this
                // list should correspond to blockCount.
                entry.blockSizes = value.splitToInts(entry.blockCount)
            }

            override fun write(entry: BedEntry) = Ints.join(",", *entry.blockSizes)
        }

        @JvmField val BLOCK_STARTS: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {
                // blockStarts - A comma-separated list of block starts. All of the blockStart positions
                // should be calculated relative to chromStart. The number of items in this list should
                // correspond to blockCount
                entry.blockStarts = value.splitToInts(entry.blockCount)
            }

            override fun write(entry: BedEntry) = Ints.join(",", *entry.blockStarts)
        }

        @JvmField val SKIP: Field = object : Field() {
            override fun read(entry: BedEntry, value: String) {}

            override fun write(entry: BedEntry) = throw UnsupportedOperationException()
        }
    }
}