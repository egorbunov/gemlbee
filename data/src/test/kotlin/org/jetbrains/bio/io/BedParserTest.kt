package org.jetbrains.bio.io

import org.jetbrains.bio.ext.bufferedWriter
import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.ext.write
import org.junit.Test
import java.awt.Color
import java.io.StringWriter
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * See format details at https://genome.ucsc.edu/FAQ/FAQformat.html#format1
 */
class BedParserTest {
    private val FORMAT = BedFormat.DEFAULT.splitter(' ')

    @Test fun testDefaultBedFormat_noRgb() {
        val contents = "chr1 1000 5000 cloneA 960 + 1000 5000 0 2 567,488, 0,3512\n" +
                "chr1 2000 6000 cloneB 900 - 2000 6000 0 2 433,399, 0,3601"

        withBedFile(contents) { path ->
            // Count
            assertEquals(2, FORMAT.parse(path).count())

            // Items
            val firstEntry = FORMAT.parse(path).first()
            assertEquals(BedEntry("chr1", 1000, 5000, "cloneA",
                                  960, '+', 1000, 5000, Color.BLACK, 2,
                                  intArrayOf(567, 488), intArrayOf(0, 3512)),
                         firstEntry)
        }
    }

    @Test fun testDefaultBedFormat_SecondEntry() {
        val contents = "chr1 1000 5000 cloneA 960 + 1000 5000\n" +
                "chr1 2000 6000 cloneB 900 - 2000 6000"

        withBedFile(contents) { path ->
            // Count
            assertEquals(2, FORMAT.parse(path).count())

            // Items
            val iterator = FORMAT.parse(path).iterator()
            iterator.next()
            val secondEntry = iterator.next()
            assertEquals(BedEntry("chr1", 2000, 6000, "cloneB",
                                  900, '-', 2000, 6000, null, 0,
                                  IntArray(0), IntArray(0)),
                         secondEntry)
        }
    }

    @Test fun testDefaultBedFormat_WithRgb() {
        val contents = "chr2    127471196  127472363  Pos1  0  +  127471196  127472363  255,0,0\n" +
                "chr2    127472363  127473530  Pos2  0  +  127472363  127473530  255,0,0\n" +
                "chr2    127473530  127474697  Pos3  0  +  127473530  127474697  255,0,0\n" +
                "chr2    127474697  127475864  Pos4  0  +  127474697  127475864  255,0,0\n" +
                "chr2    127475864  127477031  Neg1  0  -  127475864  127477031  0,0,255"

        withBedFile(contents) { path ->
            // Count
            assertEquals(5, FORMAT.parse(path).count())

            // Items
            val firstEntry = FORMAT.parse(path).first()
            assertEquals(BedEntry("chr2", 127471196, 127472363, "Pos1",
                                  0, '+', 127471196, 127472363, Color(255, 0, 0), 0,
                                  IntArray(0), IntArray(0)),
                         firstEntry)
        }
    }

    @Test fun testDefaultBedFormat_Separators() {
        val tabs = "chr2\t    127471196  \t  127472363\tPos1\t0\t+\t127471196\t127472363\t255,0,0"
        val mixed = "chr2\t127474697\t127475864\tPos4 \t0\t+\t127474697\t127475864\t255,0,0"
        val contents = tabs + "\n" + mixed

        withBedFile(contents) { path ->
            val bedFormat = FORMAT.splitter('\t')

            // Count
            assertEquals(2, bedFormat.parse(path).count())

            // Items
            for (entry in bedFormat.parse(path)) {
                assertEquals("chr2", entry.chromosome)
            }
        }
    }

    @Test fun testBedWithSkippedRecords_SkipOneRecord() {
        val skipped = "chr1 1000 5000 cloneA + 1000 5000 0 2 567,488, 0,3512\n" +
                "chr1 2000 6000 cloneB - 2000 6000 0 2 433,399, 0,3601"

        withBedFile(skipped) { path ->
            // Count
            val bedFormat = FORMAT.skip(BedEntry.SCORE)
            assertEquals(2, bedFormat.parse(path).count())

            // Items
            val firstEntry = bedFormat.parse(path).first()
            assertEquals(BedEntry("chr1", 1000, 5000, "cloneA", 0,
                                  '+', 1000, 5000, Color.BLACK, 2,
                                  intArrayOf(567, 488), intArrayOf(0, 3512)),
                         firstEntry)
        }
    }

    @Test fun testLastSymbolSplit() {
        withBedFile("chr1 1000 2000 +") { path ->
            val bedFormat = FORMAT.skip(BedEntry.NAME, BedEntry.SCORE)
            assertEquals(1, bedFormat.parse(path).count())
        }
    }

    @Test fun testBedWithSkippedRecords_SkipSeveralRecords() {
        val skipped = "chr1 1000 cloneA + 1000 5000 2 0,3512\n" +
                "chr1 2000 cloneB - 2000 6000 2 0,3601"

        withBedFile(skipped) { path ->
            // Count
            val bedFormat = FORMAT.skip(
                    BedEntry.SCORE, BedEntry.END_POS,
                    BedEntry.BLOCK_SIZES, BedEntry.ITEM_RGB)
            assertEquals(2, bedFormat.parse(path).count())

            // Items
            val firstEntry = bedFormat.parse(path).first()
            assertEquals(BedEntry("chr1", 1000, 0, "cloneA",
                                  0, '+', 1000, 5000, null, 2,
                                  IntArray(0), intArrayOf(0, 3512)),
                         firstEntry)
        }
    }

    @Test fun testBedWithSkippedRecords_SkipSeveralRecords_AtOnce() {
        val skipped = "chr1 1000 cloneA + 1000 5000 2 0,3512\n" +
                "chr1 2000 cloneB - 2000 6000 2 0,3601"

        withBedFile(skipped) { path ->
            // Count
            val bedFormat = FORMAT.skip(
                    BedEntry.SCORE, BedEntry.END_POS,
                    BedEntry.BLOCK_SIZES, BedEntry.ITEM_RGB)
            assertEquals(2, bedFormat.parse(path).count())

            // Items
            val firstEntry = bedFormat.parse(path).first()
            assertEquals(BedEntry("chr1", 1000, 0, "cloneA",
                                  0, '+', 1000, 5000, null, 2,
                                  IntArray(0), intArrayOf(0, 3512)),
                         firstEntry)
        }
    }

    @Test fun testComment() {
        val contents = "# comment\nchr1 1000 cloneA + 1000 5000 2 0,3512"

        withBedFile(contents) { path ->
            val bedFormat = FORMAT.skip(
                    BedEntry.SCORE, BedEntry.END_POS,
                    BedEntry.BLOCK_SIZES, BedEntry.ITEM_RGB)
            assertEquals(1, bedFormat.parse(path).count())
        }
    }

    @Test fun testTrackNameComment() {
        val contents = "track name=\"FooBar\"\n" +
                "chr1 1000 cloneA + 1000 5000 2 0,3512"

        withBedFile(contents) { path ->
            val bedFormat = FORMAT.skip(
                    BedEntry.SCORE, BedEntry.END_POS,
                    BedEntry.BLOCK_SIZES, BedEntry.ITEM_RGB)
            assertEquals(1, bedFormat.parse(path).count())
        }
    }

    @Test fun testWriteBedWithSkippedRecords() {
        val bedFormat = FORMAT.splitter('\t').skip(
                BedEntry.SCORE, BedEntry.END_POS,
                BedEntry.BLOCK_SIZES, BedEntry.ITEM_RGB)

        val writer = StringWriter()
        bedFormat.print(writer).use { bedPrinter ->
            bedPrinter.print(BedEntry("chr1", 1000, 5000, "cloneA",
                                      777, '-', 1000, 5000, Color.WHITE, 2,
                                      intArrayOf(567, 488), intArrayOf(0, 3512)))
        }

        assertEquals("chr1\t1000\tcloneA\t-\t1000\t5000\t2\t0,3512\n", writer.toString())
    }

    @Test fun testWriteBed() {
        val bedFormat = FORMAT.splitter('\t')
        val writer = StringWriter()
        bedFormat.print(writer).use { bedPrinter ->
            bedPrinter.print(BedEntry("chr1", 1000, 5000, "cloneA",
                                      777, '-', 1000, 5000, Color.BLACK, 2,
                                      intArrayOf(567, 488), intArrayOf(0, 3512)))
        }

        assertEquals("chr1\t1000\t5000\tcloneA\t777\t-\t1000\t5000\t0,0,0\t2\t567,488\t0,3512\n",
                     writer.toString())
    }

    @Test fun testAuto_Default() {
        val contents = "chr2\t1\t2\tDescription\t0\t+\t1000\t5000\t255,0,0\t2\t10,20\t1,2"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "Description",
                                    0, '+', 1000, 5000, Color.RED, 2, intArrayOf(10, 20), intArrayOf(1, 2))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_WhitespaceSep() {
        val contents = "chr2 1 2 Description 960 + 1000 5000 0 2 10,20, 1,2"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "Description",
                                    960, '+', 1000, 5000, Color.BLACK, 2, intArrayOf(10, 20), intArrayOf(1, 2))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_MinimalFormat() {
        // BED lines have three required fields and nine additional optional fields:

        val contents = "chr2\t1\t2"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "",
                                    0, '+', 0, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_SkippedStrandColumn() {
        val contents = "chr2\t1\t2\tDescription\t0\t1000\t5000\t255\t2\t10,20\t1,2"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "Description",
                                    0, '+', 1000, 5000, Color.RED, 2, intArrayOf(10, 20), intArrayOf(1, 2))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_SkippedScoreAndTruncated() {
        // no score, quite popular modification, e.g. roadmap epigenomics chipseqs:
        val contents = "chr2\t1\t2\tDescription\t-"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "Description",
                                    0, '-', 0, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_SkippedSeveralColumns() {
        // Name and Score skipped
        val contents = "chr2\t1\t2\t+\t1000"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr2", 1, 2, "",
                                    0, '+', 1000, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_Simple() {
        val contents = "chr1\t10051\t10250\tHWI-ST700693:250:D0TG9ACXX:3:2112:8798:84378\t1\t-\n"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr1", 10051, 10250,
                                    "HWI-ST700693:250:D0TG9ACXX:3:2112:8798:84378", 1, '-',
                                    0, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_SkippScore() {
        val contents = "chr1\t10051\t10250\tHWI-ST700693:250:D0TG9ACXX:3:2112:8798:84378\t-\n"

        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr1", 10051, 10250,
                                    "HWI-ST700693:250:D0TG9ACXX:3:2112:8798:84378", 0, '-',
                                    0, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    @Test fun testAuto_SimpleScheme() {
        withBedFile() { path ->
            BedFormat.SIMPLE.print(path.bufferedWriter()).use { bedPrinter ->
                val entry = BedEntry("chr2", 1, 2, "Description",
                                     0, '+', 1000, 5000, Color.RED, 0, IntArray(0), IntArray(0))
                bedPrinter.print(entry)
            }

            val format = BedFormat.auto(path)
            assertTrue(Arrays.equals(BedFormat.SIMPLE.schema, format.schema))
        }
    }


    @Test fun testAutoMacs2Peaks() {
        val contents = "chr1\t713739\t714020\tout_peak_1\t152\t.\t6.84925\t17.97400\t15.25668\t173\n"
        withBedFile(contents) { path ->
            val format = BedFormat.auto(path)
            val actual = format.parse(path).first()
            val expected = BedEntry("chr1", 713739, 714020, "out_peak_1", 152, '+',
                    0, 0, null, 0, IntArray(0), IntArray(0))
            assertEquals(expected, actual)
        }
    }

    private fun withBedFile(contents: String = "", block: (Path) -> Unit) {
        withTempFile("test", ".bed") { path ->
            if (contents.isNotEmpty()) {
                path.write(contents)
            }

            block(path)
        }
    }
}
