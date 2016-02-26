package org.jetbrains.bio.data

import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.genome.Gene
import org.jetbrains.bio.genome.ImportantGenesAndLoci
import org.jetbrains.bio.genome.query.locus.GeneTranscriptQuery
import org.jetbrains.bio.genome.query.locus.LocusQuery
import org.jetbrains.bio.genome.query.locus.TssQuery
import org.jetbrains.bio.genome.query.locus.WholeGeneQuery
import org.junit.Test
import kotlin.test.assertEquals

class POITest {

    @Test fun testEmpty() {
        assertEquals(emptyList<LocusQuery<Gene>>(), POI(listOf<String>())["H3K4me3"])
    }

    @Test fun testAll() {
        assertEquals<List<LocusQuery<Gene>>>(ImportantGenesAndLoci.REGULATORY,
                                             POI(listOf("all"))["H3K4me3"])
    }

    @Test fun testModificationAll() {
        assertEquals<List<LocusQuery<Gene>>>(ImportantGenesAndLoci.REGULATORY,
                                             POI(listOf("H3K4me3@all"))["H3K4me3"])
        assertEquals<List<LocusQuery<Gene>>>(ImportantGenesAndLoci.REGULATORY,
                                             POI(listOf("H3K4me3@all", "H3K4me3@tss"))["H3K4me3"])
    }

    @Test fun testModification() {
        assertEquals(listOf(TssQuery()), POI(listOf("H3K4me3@all", "H3K4me2@tss"))["H3K4me2"])
        assertEquals(listOf(TssQuery(-100, 100), WholeGeneQuery()),
                POI(listOf("H3K4me3@all", "H3K4me2@tss[-100,100]", "H3K4me2@genes"))["H3K4me2"])
        assertEquals(listOf(TssQuery(-100, 100), GeneTranscriptQuery()),
                POI(listOf("H3K4me3@all", "H3K4me2@tss[-100,100]", "H3K4me2@transcript"))["H3K4me2"])
    }

    @Test fun testAllTss() {
        assertEquals(listOf(TssQuery()), POI(listOf("all@tss"))["H3K4me2"])
    }

    @Test fun testDump() {
        withTempFile("test", ".bed") { p ->
                val config = """genome: to1
tracks:
   H3K4me3:
      t:
      - $p
   H3K4me2:
      t:
      - $p
"""
            val dataConfig = DataConfig.load(config.reader())
            assertEquals("[H3K4me2@exons]",
                    POI(listOf("H3K4me2@exons")).full(dataConfig).toString())

            assertEquals("[H3K4me2@tss[-2000..2000], H3K4me3@tss[-2000..2000]]",
                    POI(listOf("all@tss")).full(dataConfig).toString())

            // all
            assertEquals(
                    "[H3K4me2@exons, H3K4me2@introns, H3K4me2@tes[-2000..2000], H3K4me2@tes[2500..5000], H3K4me2@tss[-2000..2000], H3K4me2@tss[-5000..-2500], " +
                    "H3K4me3@exons, H3K4me3@introns, H3K4me3@tes[-2000..2000], H3K4me3@tes[2500..5000], H3K4me3@tss[-2000..2000], H3K4me3@tss[-5000..-2500]]",
                    POI(listOf("all")).full(dataConfig).toString())
        }
    }

    @Test fun testMethylationDump() {
        withTempFile("test", ".bam") { p ->
            val config = """genome: to1
poi:
- meth
tracks:
   meth:
      t:
      - $p
"""
            val dataConfig = DataConfig.load(config.reader())
            assertEquals("[meth@exons, meth@introns, meth@tes[-2000..2000], meth@tes[2500..5000], meth@tss[-2000..2000], meth@tss[-5000..-2500]]",
                    POI(listOf("all")).full(dataConfig).toString())
            assertEquals("[meth@exons, meth@introns, meth@tes[-2000..2000], meth@tes[2500..5000], meth@tss[-2000..2000], meth@tss[-5000..-2500]]",
                    POI(listOf("meth@all")).full(dataConfig).toString())

        }
    }


    @Test fun testTranscriptionDump() {
        withTempFile("test", ".track") { p ->
            val config = """genome: to1
poi:
- transcription
tracks:
   rna-seq:
      t:
      - $p
"""
            val dataConfig = DataConfig.load(config.reader())
            assert(dataConfig.poi.transcription())
            assertEquals("[transcription]", POI(listOf("all")).full(dataConfig).toString())
            assertEquals("[transcription]", POI(listOf("transcription")).full(dataConfig).toString())
        }
    }
}
