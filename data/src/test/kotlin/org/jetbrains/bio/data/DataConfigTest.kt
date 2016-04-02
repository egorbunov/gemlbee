package org.jetbrains.bio.data

import org.jetbrains.bio.ext.withTempFile
import org.jetbrains.bio.genome.CellId
import org.jetbrains.bio.genome.Genome
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.histones.BedTrackQuery
import org.jetbrains.bio.io.BedFormat
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataConfigTest {
    @Test fun consistency() {
        withConfig { config ->
            assertTrue("H3K4me3" to CellId["test1"] in config.tracks)
            assertTrue("H3K9me3" to CellId["test1"] in config.tracks)
            assertTrue("H3K4me3" to CellId["test2"] in config.tracks)
        }
    }

    @Test fun labeledConsistency() {
        withConfig { config ->
            val labeled = config.tracks["H3K4me3" to CellId["test1"]]
            assertTrue(labeled is Section.Labeled)
            assertEquals(setOf("rep1", "rep2"),
                    (labeled as Section.Labeled).replicates.keys)
        }
    }

    @Test fun implicitConsistency() {
        withConfig { config ->
            val labeled = config.tracks["H3K4me3" to CellId["test2"]]
            assertTrue(labeled is Section.Implicit)
            assertEquals(1, (labeled as Section.Implicit).paths.size)
        }
    }

    @Test fun loadReloadDump() {
        withConfig { config ->
            val writer1 = StringWriter()
            config.save(writer1)
            val save1 = writer1.toString()
            val reloaded = DataConfig.load(save1.reader())
            assertNotNull(reloaded)
            assertTrue('!' !in save1) // no YAML tags.
            val writer2 = StringWriter()
            config.save(writer2)
            val save2 = writer1.toString()
            assertEquals(save1, save2)
        }
    }

    @Test fun loadPOI() {
        withTempFile("test", ".bed") { p ->
            val config = """genome: to1
tracks:
   H3K4me3:
      test:
      - $p
poi:
- all
- H3K4me3@tss[-3000..2000]
"""
            val dataConfig = DataConfig.load(config.reader())
            assertEquals(listOf("all", "H3K4me3@tss[-3000..2000]"), dataConfig.poi.list)
        }
    }

    @Test fun savePOI() {
        withTempFile("test", ".bed") { p ->
            val config = """id: unknown
genome: to1
poi:
- H3K4me3@cds
- H3K4me3@exons
- H3K4me3@introns
- H3K4me3@tes[-200..200]
- H3K4me3@tes[-2000..2000]
- H3K4me3@tes[2500..5000]
- H3K4me3@tss[-200..200]
- H3K4me3@tss[-2000..2000]
- H3K4me3@tss[-3000..2000]
- H3K4me3@tss[-5000..-2500]
- H3K4me3@utr3
- H3K4me3@utr5
tracks:
   H3K4me3:
      test:
      - $p
"""
            val dataConfig = DataConfig.load(config.reader())
            val writer = StringWriter()
            dataConfig.save(writer)
            assertEquals(config,
                    writer.toString()
                            .replace("#[^\n]+\n".toRegex(), "") // Remove comment
                            .replace(" \n".toRegex(), "\n")) // Yaml trailing space
        }
    }

    @Test fun saveDefaultPOI() {
        withTempFile("test1", ".bed") { tmp ->
            val genome = Genome.get("to1")
            val ds = object : DataSet("test", genome), ChipSeqDataSet {
                override val dataPath: Path
                    get() = throw UnsupportedOperationException()

                override fun getCellIds(dataType: DataType): Collection<CellId> =
                        listOf(CellId["A"], CellId["B"])

                override val chipSeqTargets: List<ChipSeqTarget>
                    get() = listOf(ChipSeqTarget.H3K4me3, ChipSeqTarget.H3K27me3, ChipSeqTarget.H3K36me3)

                override fun getTracks(genomeQuery: GenomeQuery, cellId: CellId, target: ChipSeqTarget): List<BedTrackQuery> =
                        listOf(BedTrackQuery(genomeQuery, tmp, BedFormat.SIMPLE))
            }
            val config = DataConfig.forDataSet(genome.toQuery(), ds)
            val writer = StringWriter()
            config.save(writer)
            assertFalse("!" in writer.toString())
        }
    }

    @Test fun equalsHashCode() {
        withTempFile("test1", ".bed") { p ->
            val config1 = """genome: to1
tracks:
   H3K4me3:
      t:
      - $p
poi:
- all@tss[-3000..2000]
"""

            val config2 = """genome: to1
tracks:
   H3K4me3:
      t:
      - $p
poi:
- H3K4me3@tss[-3000..2000]
"""
            val dc1 = DataConfig.load(config1.reader())
            val dc2 = DataConfig.load(config2.reader())
            assertEquals(dc1, dc2)
            assertEquals(dc1.hashCode(), dc2.hashCode())
        }
    }

    private fun withConfig(block: (DataConfig) -> Unit) {
        withTempFile("test1", ".bed") { p11 ->
            withTempFile("test1", ".bed") { p12 ->
                withTempFile("test2", ".bed") { p2 ->
                    val config = DataConfig.Companion.load("""genome: to1
tracks:
   H3K4me3:
      test1:
        rep1: $p11
        rep2: $p12
      test2:
      - $p2
   H3K9me3:
      test1:
      - $p2
""".reader())
                    block(config)
                }
            }
        }
    }


    @Test fun testMeth() {
        withTempFile("test1", ".bam") { p ->
            val config = """genome: to1
tracks:
   meth:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(1, dc.tracks.size)
            assertEquals(1, dc.tracks.keys.size)
            assertEquals("(meth, t)", dc.tracks.keys.first().toString())
            assert(dc.tracks.values.first() is Section.Implicit)
        }
    }


    @Test fun testMethLabeled() {
        withTempFile("test1", ".bam") { p ->
            val config = """genome: to1
tracks:
   meth:
      t:
        a: $p
        b: $p
"""
            val dc = DataConfig.load(config.reader())
            assert(dc.tracks.values.first() is Section.Labeled)
            assertEquals("[a, b]", (dc.tracks.values.first() as Section.Labeled).replicates.keys.toString())
        }
    }


    @Test fun testExpression() {
        withTempFile("test1", ".fastq") { p ->
            val config = """genome: to1
tracks:
   rna-seq:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(1, dc.tracks.size)
            assertEquals(1, dc.tracks.keys.size)
            assertEquals("(rna-seq, t)", dc.tracks.keys.first().toString())
            assert(dc.tracks.values.first() is Section.Implicit)
        }
    }


    @Test fun testExpressionLabeled() {
        withTempFile("test1", ".fastq") { p ->
            val config = """genome: to1
tracks:
   rna-seq:
      t:
        a: $p
        b: $p
"""
            val dc = DataConfig.load(config.reader())
            assert(dc.tracks.values.first() is Section.Labeled)
            assertEquals("[a, b]", (dc.tracks.values.first() as Section.Labeled).replicates.keys.toString())
        }
    }



    @Test fun consistencyDataSet() {
        withTempFile("test1", ".bed") { tmp ->
            val genome = Genome.get("to1")
            val ds = object : DataSet("test", genome), ChipSeqDataSet {
                override val dataPath: Path
                    get() = throw UnsupportedOperationException()

                override fun getCellIds(dataType: DataType): Collection<CellId> =
                        listOf(CellId["A"], CellId["B"])

                override val chipSeqTargets: List<ChipSeqTarget>
                    get() = listOf(ChipSeqTarget.H3K4me3, ChipSeqTarget.H3K27me3, ChipSeqTarget.H3K36me3)

                override fun getTracks(genomeQuery: GenomeQuery, cellId: CellId, target: ChipSeqTarget): List<BedTrackQuery> =
                        listOf(BedTrackQuery(genomeQuery, tmp, BedFormat.SIMPLE))
            }
            val order = arrayListOf<String>()
            (object : DataConfigurator {
                override fun invoke(dataTypeId: String, condition: CellId, section: Section) {
                    order.add("$dataTypeId $condition")
                }
            })(DataConfig.forDataSet(genome.toQuery(), ds))
            assertEquals("[H3K4me3 A, H3K4me3 B, H3K27me3 A, H3K27me3 B, H3K36me3 A, H3K36me3 B]", order.toString())
        }
    }

    @Test fun testFormatHelp() {
        assertEquals("""YAML configuration for biological data:
    id: <experiment id>
    genome: <UCSC genome>
    tracks:
        <data type>:
            <condition>:
            - <track>
    poi:
    - <point of interest>

-----
Genome:
See https://genome.ucsc.edu/FAQ/FAQreleases.html
Examples:
- mm10
- hg19[chr1,chr2,chr3]

-----
Tracks:
Each condition is allowed to have multiple replicates. Replicates
can be either implicitly labeled by their position within the
condition or have explicit human-readable labels as in the example below.

With explicit labels:
    <condition>:
        <replicate>: path/to/replicate/data
        <replicate>: path/to/replicate/data

Without labels:
    <condition>:
    - path/to/replicate/data
    - path/to/replicate/data

Supported data types:
* ctcf, dnase, h2az, h3k18ac, h3k27ac, h3k27me3, h3k36me3, h3k4me1, h3k4me2, h3k4me3, h3k9ac, h3k9me3, h4k12ac, h4k20me1, input, input_mnase, input_sonicated, meth, polii, ppargab1, ppargab2, rna, rna-seq

Supported file formats:
- *.bed, *.bed.gz, *.bed.zip for ChIP-Seq
- *.bam for BS-Seq
- *.fastq, *.fastq.gz file/folder for transcriptome

---
POI:
POI = points of interest and predicates description.

All regulatory loci:
- cds, exons, introns, tes[-2000..2000], tes[2500..5000], transcript, tss[-2000..2000], tss[-5000..-2500], utr3, utr5

poi:
- all                       # All modifications x all regulatory loci + transcription
- H3K4me3@all               # Modification at all regulatory loci
- H3K4me3[80%][0.5]@all     # ChIP-Seq predicate, exist 80% range with >= 0.5 enrichment fraction
- H3K4me3[1000][0.8]@all    # ChIP-Seq predicate, exist range of length = 1000 with >= 0.8 enrichment fraction
- all@tss[-2000..2000]      # All modifications at given locus
- meth@exons                # Methylation at given locus
- meth[10][0.5]@tss         # Methylation predicate at least 0.5 enriched cytosines among at least 10 covered
- transcription             # Transcription, i.e. tpm abundance + 25, 50, 75, 100 percentile discrimination
---
rule_min_support:
If given limits min rule support of generated rules.
---
rule_min_conviction:
If given limits min conviction of generated rules.
---
rule_max_complexity:
If given limits max complexity of generated rules.
---
rule_top:
If given configures rule mining algorithm precision, i.e. guaranteed top for each target.
---
rule_output:
If given configures number of rules to output for each target.""",
                DataConfig.FORMAT)
    }


    @Test fun testRulesDefaults() {
        withConfig { config ->
            val writer = StringWriter()
            config.save(writer)
            val sdc = writer.toString().replace(Regex("#.*\n"), "")
            assertFalse("rule_min_support:" in sdc)
            assertEquals(0, config.ruleMinSupport)
            assertFalse("rule_max_complexity" in sdc)
            assertEquals(Double.POSITIVE_INFINITY, config.ruleMaxComplexity)
            assertFalse("rule_min_conviction" in sdc)
            assertEquals(0.0, config.ruleMinConviction)
            assertFalse("rule_top" in sdc)
            assertEquals(1, config.ruleTop)
            assertFalse("rule_output" in sdc)
            assertEquals(1, config.ruleOutput)
        }
    }

    @Test fun testRulesSettings() {
        withTempFile("test1", ".bed") { p ->
            val config = """genome: to1
rule_min_support: 100
rule_min_conviction: 3.0
rule_max_complexity: 10
rule_top: 10
rule_output: 100
tracks:
   H3K4me3:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(100, dc.ruleMinSupport)
            assertEquals(3.0, dc.ruleMinConviction)
            assertEquals(10.0, dc.ruleMaxComplexity)
            assertEquals(10, dc.ruleTop)
            assertEquals(100, dc.ruleOutput)
        }
    }


}
