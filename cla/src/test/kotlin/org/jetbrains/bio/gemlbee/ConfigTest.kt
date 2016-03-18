package org.jetbrains.bio.gemlbee


import com.esotericsoftware.yamlbeans.YamlReader
import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.genome.query.GenomeQuery
import org.junit.Test
import kotlin.test.*

/**
 * @author Roman.Chernyatchik
 */
class ConfigTest {

    @Test fun formatDoc() {
        assertEquals("""YAML configuration for genome browser:
genome: <UCSC genome>
tracks:
- path/to/datafile
-----
Genome:
See https://genome.ucsc.edu/FAQ/FAQreleases.html
Examples:
- mm10
- hg19[chr1,chr2,chr3]
-----
Supported file formats:
- *.bed, *.bed.gz, *.bed.zip for ChIP-Seq
- *.bam for BS-Seq
- *.fastq, *.fastq.gz file/folder for transcriptome
- *.tdf for any data
""",
                Config.FORMAT)
    }

    @Test fun loadTracksLength() {
        withConfig { config -> assertEquals(2, config.tracks.size) }
    }

    @Test fun loadGenome() {
        withConfig { config -> assertEquals(GenomeQuery("to1"), config.genomeQuery) }
    }

    @Test fun loadGenomeFiltered() {
        withConfig("[chr1,chr12]") { config ->
            assertEquals(GenomeQuery("to1", "chr1", "chr12"), config.genomeQuery)
        }
    }

    @Test fun loadTracks() {
        withConfig { config ->
            assertTrue(config.tracks[0].fileName.toString().endsWith("test1.bed"))
            assertTrue(config.tracks[1].fileName.toString().endsWith("test2.tdf"))
        }
    }

    @Test fun loadNoTracksValue() {
        testException("Missing or empty tracks") {
            Config.load("genome: to1\ntracks:\n".reader())
        }
    }

    @Test fun loadNoTracksSection() {
        testException("Missing or empty tracks") {
            Config.load("genome: to1\n".reader())
        }
    }

    @Test fun loadNoGenomeValue() {
        testException("Missing or empty genome") {
            Config.load("genome:\ntracks:\n- /tmp/foo.bed".reader())
        }
    }

    @Test fun loadNoGenomeSection() {
        testException("Missing or empty genome") {
            val config = Config.load("tracks:\n- /tmp/foo.bed".reader())
            assertEquals("!", config.genomeQuery.build)
        }
    }

    @Test fun loadEmpty() {
        testException("Empty config") {
            Config.load("".reader())
        }
    }

    private fun testException(msg: String, block: (Unit) -> Unit) {
        try {
            block.invoke(Unit)
        } catch (t: Throwable) {
            assertEquals(msg, t.message)
            return
        }
        fail("No exception raised, expected: $msg")
    }

    @Test fun configList() {
        withTempFile("", "test1.bed") { p1 ->
            withTempFile("", "test2.tdf") { p2 ->
                val yamlReader = YamlReader("""---
genome: to1
tracks:
 - $p1
---
genome: to1
tracks:
 - $p1
    """)
                assertNotNull(Config.load(yamlReader))
                assertNotNull(Config.load(yamlReader))
                assertNull(Config.load(yamlReader))
            }
        }
    }

    private fun withConfig(chrsFilter: String = "",
                           block: (Config) -> Unit) {
        withTempFile("", "test1.bed") { p1 ->
            withTempFile("", "test2.tdf") { p2 ->
                val config = Config.load("""genome: to1${if (chrsFilter.isNotEmpty()) chrsFilter else ""}
tracks:
 - $p1
 - $p2
    """.reader())
                block(config)
            }
        }
    }
}
