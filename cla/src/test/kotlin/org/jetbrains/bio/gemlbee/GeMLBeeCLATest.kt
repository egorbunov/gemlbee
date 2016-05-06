package org.jetbrains.bio.gemlbee

import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.tracks.KallistoTrackView
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * @author Roman.Chernyatchik
 */
class GeMLBeeCLATest {
    private val gq = GenomeQuery("to1")
    private var log: StringBuilder = StringBuilder()
    private var appender = object : AppenderSkeleton() {
        override @Synchronized fun append(event: LoggingEvent) {
            log.append(event.message).append('\n')
        }

        override fun close() {
        }

        override fun requiresLayout(): Boolean = false;
    }


    @Before fun setup() {
        log = StringBuilder()
        Logger.getRootLogger().addAppender(appender)
    }

    @After fun tearDown() {
        Logger.getRootLogger().removeAppender(appender)
    }

    @Test fun addTrack_Bed() {
        checkTrack("Created Bed track view for /tmp/foo.bed", "/tmp/foo.bed")
    }

    @Test fun addTrack_BedGz() {
        checkTrack("Created Bed track view for /tmp/foo.bed.gz", "/tmp/foo.bed.gz")
    }

    @Test fun addTrack_BedZip() {
        checkTrack("Created Bed track view for /tmp/foo.bed.zip", "/tmp/foo.bed.zip")
    }

    @Test fun addTrack_Bam() {
        checkTrack("Created Methylome track view for /tmp/foo.bam", "/tmp/foo.bam")
    }

    @Test fun addTrack_Fastq() {
        checkTrack("Created Kallisto track view for /tmp/foo.fastq", "/tmp/foo.fastq")
    }

    @Test fun addTrack_FastqGz() {
        checkTrack("Created Kallisto track view for /tmp/foo.fastq.gz", "/tmp/foo.fastq.gz")
    }

    @Test fun addTrack_Wig() {
        checkTrack("Created Wig track view for /tmp/foo.wig", "/tmp/foo.wig")
    }

    @Test fun addTrack_FastqDir() {
        withTempDirectory("tmp") { tmpDir ->
            (tmpDir / "foo.fastq").touch()
            (tmpDir / "foo.fastq.gz").touch()
            (tmpDir / "foo.txt").touch()

            val config = Config(gq, listOf(Pair("noname", tmpDir)))
            assert(GeMLBeeCLA.trackView(tmpDir, config.genomeQuery) is KallistoTrackView)
        }
    }

    @Test fun addTrack_Tdf() {
        checkTrack("Created TDF track view for /tmp/foo.tdf", "/tmp/foo.tdf")
    }

    @Test fun notSupported() {
        listOf("foo.bed.rar",
                "foo.bam.gz", "foo.bam.zip",
                "foo.fastq.zip", "foo.fastq.rar",
                "foo.tdf.gz", "foo.tdf.zip",
                "foo.gz", "foo.zip", "foo.boo").forEach { checkNotSupported(it) }
        listOf("/tmp/foo.bed.rar", "" +
                "/tmp/foo.bam.gz", "/tmp/foo.bam.zip",
                "/tmp/foo.fastq.zip", "/tmp/foo.fastq.rar",
                "/tmp/foo.tdf.gz", "/tmp/foo.tdf.zip",
                "/tmp/foo.gz", "/tmp/foo.zip", "/tmp/foo.boo").forEach { checkNotSupported(it) }
    }


    @Test fun parseCustomLoci_Genes() {
        val gqChr1 = GenomeQuery("to1")

        val namedLocs = listOf("chr1", "chr2").flatMap { chr ->
            randomGenes(chr, 2, 2).flatMap {
                it.map { gene ->
                    gene.names[GeneAliasType.GENE_SYMBOL]!! to gene.location
                }
            }
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLA().parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName, mapper) = groups.iterator().next()
            assertEquals(fName, filePath.name)

            val items = mapper.invoke(gqChr1)
            items.map { it.location }
            assertEquals(4, items.count { it.location.strand.isPlus() });
            assertEquals(4, items.count { it.location.strand.isMinus() });
            assertEquals(8, items.count { it is GeneLocRef });
        }
    }

    @Test fun parseCustomLoci_GenePart() {
        val gqChr1 = GenomeQuery("to1")

        val namedLocs = listOf("chr1", "chr2").flatMap { chr ->
            randomGenes(chr, 2, 2).flatMap {
                it.map { gene ->
                    val loc = gene.location
                    gene.names[GeneAliasType.GENE_SYMBOL]!! to loc.copy(endOffset = loc.startOffset + 20)
                }
            }
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLA().parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName, mapper) = groups.iterator().next()
            assertEquals(fName, filePath.name)

            val items = mapper.invoke(gqChr1)
            items.map { it.location }
            assertEquals(4, items.count { it.location.strand.isPlus() });
            assertEquals(4, items.count { it.location.strand.isMinus() });
            assertEquals(8, items.count { it is GeneLocRef });
            assertEquals(8, items.count { it.location.length() == 20 });

        }
    }

    @Test fun parseCustomLoci_NamedLoc() {
        val gqChr1 = GenomeQuery("to1")

        val names = ArrayList<String>();
        val namedLocs = listOf("chr1", "chr2").flatMap { chr ->
            val genes = randomGenes(chr, 2, 2)
            genes.flatMap {
                it.map { gene ->
                    val name = gene.names[GeneAliasType.GENE_SYMBOL]!!.reversed()
                    names.add(name)
                    name to gene.location
                }
            }
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLA().parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName, mapper) = groups.iterator().next()
            assertEquals(fName, filePath.name)

            val items = mapper.invoke(gqChr1)
            items.map { it.location }
            assertEquals(4, items.count { it.location.strand.isPlus() });
            assertEquals(4, items.count { it.location.strand.isMinus() });
            assertEquals(0, items.count { it is GeneLocRef });
            assertEquals(8, items.count { it is NamedLocRef });
            assertEquals(names.sorted(), items.map { it.name }.sorted());

        }
    }


    @Test fun namedLocationRef() {
        val loc = Location(0, 10, Chromosome.invoke("to1", "chr1"), Strand.PLUS)
        val locRef = NamedLocRef("foo", loc)
        assertEquals(loc, locRef.location)
        assertEquals("foo", locRef.name)

        val newLoc = loc.copy(startOffset = 5)
        val newLocRef = locRef.update(newLoc)

        assertEquals(loc, locRef.location)

        assertEquals(newLoc, newLocRef.location)
        assertEquals("foo", newLocRef.name)
    }

    private fun saveAsBed(filePath: Path, namedLocs: List<Pair<String, Location>>) {
        val content = namedLocs.map { name2Loc ->
            val name = name2Loc.first
            val loc = name2Loc.second
            "${loc.chromosome.name}\t${loc.startOffset}\t${loc.endOffset}\t${name}\t${loc.strand}"
        }.joinToString("\n") { it }
        filePath.write(content)
    }

    private fun randomGenes(chrName: String,
                            countPlus: Int,
                            countMinus: Int): List<List<Gene>> {
        val chr = Chromosome.invoke("to1", chrName)

        val rand = Random()

        return Strand.values().map { strand ->
            val genes = chr.genes.filter { it.strand == strand }
            val size = genes.size
            val count = if (strand.isPlus()) countPlus else countMinus;
            (0..count - 1).asSequence().map { genes[rand.nextInt(size)] }.toList()
        }
    }

    private fun checkTrack(expected: String, path: String) {
        GeMLBeeCLA.trackView(path.toPath(), gq)
        assertEquals(expected, log.toString().trim())
    }

    private fun checkNotSupported(path: String) {
        try {
            GeMLBeeCLA.trackView(path.toPath(), gq)
        } catch (t: Throwable) {
            assert(t.message!!.startsWith("Unknown file type: "))
            return
        }
        fail("No exception raised")
    }
}