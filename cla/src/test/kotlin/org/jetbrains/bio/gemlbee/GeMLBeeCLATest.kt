package org.jetbrains.bio.gemlbee

import org.jetbrains.bio.browser.model.GeneLocRef
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.*
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @author Roman.Chernyatchik
 */
class GeMLBeeCLATest {
    val gq = GenomeQuery("to1")

    @Before fun setup() {
        GeMLBeeCLAMock.okTracks.clear()
        GeMLBeeCLAMock.unsupportedTracks.clear()
    }

    @Test fun addTrack_Bed() {
        assertTrack("ChipSeq: foo.bed", "/tmp/foo.bed")
    }

    @Test fun addTrack_BedGz() {
        assertTrack("ChipSeq: foo.bed.gz", "/tmp/foo.bed.gz")
    }

    @Test fun addTrack_BedZip() {
        assertTrack("ChipSeq: foo.bed.zip", "/tmp/foo.bed.zip")
    }

    @Test fun addTrack_Bam() {
        assertTrack("BsSeq: foo.bam", "/tmp/foo.bam")
    }

    @Test fun addTrack_Fastq() {
        assertTrack("Fastq: foo.fastq", "/tmp/foo.fastq")
    }

    @Test fun addTrack_FastqGz() {
        assertTrack("Fastq: foo.fastq.gz", "/tmp/foo.fastq.gz")
    }

    @Test fun addTrack_FastqDir() {
        withTempDirectory("tmp") { tmpDir ->
            (tmpDir / "foo.fastq").touch()
            (tmpDir / "foo.fastq.gz").touch()
            (tmpDir / "foo.txt").touch()

            val config = Config(gq, listOf(tmpDir))
            GeMLBeeCLAMock.configureTracks(listOf(config), gq, arrayListOf())

            assertEquals(listOf("Fastq: foo.fastq", "Fastq: foo.fastq.gz"),
                         GeMLBeeCLAMock.okTracks.sorted())
            assertEquals(listOf<String>(), GeMLBeeCLAMock.unsupportedTracks)

        }
    }

    @Test fun addTrack_Tdf() {
        assertTrack("Tdf: foo.tdf", "/tmp/foo.tdf")
    }

    @Test fun notSupported() {
        assertNotSupported(listOf("foo.bed.rar",
                                  "foo.bam.gz", "foo.bam.zip",
                                  "foo.fastq.zip", "foo.fastq.rar",
                                  "foo.tdf.gz", "foo.tdf.zip",
                                  "foo.gz", "foo.zip", "foo.boo"),
                           listOf("/tmp/foo.bed.rar", "" +
                                   "/tmp/foo.bam.gz", "/tmp/foo.bam.zip",
                                  "/tmp/foo.fastq.zip", "/tmp/foo.fastq.rar",
                                  "/tmp/foo.tdf.gz", "/tmp/foo.tdf.zip",
                                  "/tmp/foo.gz", "/tmp/foo.zip", "/tmp/foo.boo"))
    }


    @Test fun parseCustomLoci_Genes() {
        val gqChr1 = GenomeQuery("to1")

        val namedLocs = listOf("chr1", "chr2").flatMap { chr ->
            randomGenes(chr, 2, 2).flatMap { it.map { gene ->
                gene.getName(GeneAliasType.GENE_SYMBOL) to gene.location
            } }
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLAMock.parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName , mapper) = groups.iterator().next()
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
            randomGenes(chr, 2, 2).flatMap { it.map { gene ->
                val loc = gene.location
                gene.getName(GeneAliasType.GENE_SYMBOL) to loc.copy(endOffset = loc.startOffset + 20)
            } }
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLAMock.parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName , mapper) = groups.iterator().next()
            assertEquals(fName, filePath.name)

            val items = mapper.invoke(gqChr1)
            items.map { it.location }
            assertEquals(4, items.count { it.location.strand.isPlus() });
            assertEquals(4, items.count { it.location.strand.isMinus() });
            assertEquals(8, items.count { it is GeneLocRef });
            assertEquals(8, items.count { it.location.length() == 20 } );

        }
    }

    @Test fun parseCustomLoci_NamedLoc() {
        val gqChr1 = GenomeQuery("to1")

        val names = ArrayList<String>();
        val namedLocs = listOf("chr1", "chr2").flatMap { chr ->
            val genes = randomGenes(chr, 2, 2)
            genes.flatMap { it.map { gene ->
                val name = gene.getName(GeneAliasType.GENE_SYMBOL).reversed()
                names.add(name)
                name to gene.location
            }}
        }

        val groups = HashMap<String, (GenomeQuery) -> List<LocationReference>>()

        withTempFile("foo", ".bed") { filePath ->
            // save file
            saveAsBed(filePath, namedLocs)

            GeMLBeeCLAMock.parseCustomLoci(gqChr1, listOf(filePath), groups)
            assertEquals(1, groups.size);

            val (fName , mapper) = groups.iterator().next()
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
        val loc = Location(0, 10, Chromosome["to1", "chr1"], Strand.PLUS)
        val locRef = NamedLocRef("foo", loc)
        assertEquals(loc, locRef.location)
        assertEquals("foo", locRef.name)
        assertNull(locRef.metaData)

        val newLoc = loc.copy(startOffset = 5)
        val newLocRef = locRef.update(newLoc)

        assertEquals(loc, locRef.location)

        assertEquals(newLoc, newLocRef.location)
        assertEquals("foo", newLocRef.name)
        assertNull(newLocRef.metaData)
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
        val chr = Chromosome["to1", chrName]

        val rand = Random()

        return Strand.values().map { strand ->
            val genes = chr.genes.filter { it.strand == strand }
            val size = genes.size
            val count = if (strand.isPlus()) countPlus else countMinus;
            (0..count - 1).asSequence().map { genes[rand.nextInt(size)] }.toList()
        }
    }

    private fun assertTrack(expected: String, fileName: String) {
        val config = Config(gq, listOf(fileName.toPath()))
        GeMLBeeCLAMock.configureTracks(listOf(config), gq, arrayListOf())

        assertEquals(listOf(expected), GeMLBeeCLAMock.okTracks.sorted())
        assertEquals(listOf<String>(), GeMLBeeCLAMock.unsupportedTracks)
    }

    private fun assertNotSupported(expected: List<String>, fileNames: List<String>) {
        val config = Config(gq, fileNames.map { it.toPath() })
        GeMLBeeCLAMock.configureTracks(listOf(config), gq, arrayListOf())

        assertEquals(expected.sorted(), GeMLBeeCLAMock.unsupportedTracks.sorted())
        assertEquals(listOf<String>(), GeMLBeeCLAMock.okTracks)
    }
}

object GeMLBeeCLAMock : GeMLBeeCLA() {

    val okTracks = ArrayList<String>()
    val unsupportedTracks = ArrayList<String>()

    override fun addTdf(file: Path, master: GenomeQuery, gq: GenomeQuery, tracks: MutableList<TrackView>) {
        okTracks.add("Tdf: ${file.name}")
    }

    override fun addRnaSeq(condition: String,
                           master: GenomeQuery,
                           gq: GenomeQuery,
                           tracks: MutableList<TrackView>, fastqReads: Array<Path>) {
        for (file in fastqReads) {
            okTracks.add("Fastq: ${file.name}")
        }
    }

    override fun addBsSeq(condition: String, master: GenomeQuery, gq: GenomeQuery, trackPath: Path, tracks: MutableList<TrackView>) {
        okTracks.add("BsSeq: ${trackPath.name}")
    }

    override fun addChipSeq(master: GenomeQuery, gq: GenomeQuery, trackPath: Path, tracks: MutableList<TrackView>) {
        okTracks.add("ChipSeq: ${trackPath.name}")
    }

    override fun unsupportedError(trackPath: Path) {
        unsupportedTracks.add(trackPath.name)
    }
}
