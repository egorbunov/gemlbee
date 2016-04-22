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
import kotlin.test.*

class DataConfigTest {
    @Test fun consistency() {
        withConfig { config ->
            assertTrue("H3K4me3" to CellId["test1"] in config.tracksMap)
            assertTrue("H3K9me3" to CellId["test1"] in config.tracksMap)
            assertTrue("H3K4me3" to CellId["test2"] in config.tracksMap)
        }
    }

    @Test fun labeledConsistency() {
        withConfig { config ->
            val labeled = config.tracksMap["H3K4me3" to CellId["test1"]]
            assertTrue(labeled is Section.Labeled)
            assertEquals(setOf("rep1", "rep2"),
                    (labeled as Section.Labeled).replicates.keys)
        }
    }

    @Test fun implicitConsistency() {
        withConfig { config ->
            val labeled = config.tracksMap["H3K4me3" to CellId["test2"]]
            assertTrue(labeled is Section.Implicit)
            assertEquals(1, (labeled as Section.Implicit).paths.size)
        }
    }

    @Test fun loadReloadDump() {
        withConfig { config ->
            val writer1 = StringWriter()
            config.genomeQuery
            config.tracksMap
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
            assertEquals(listOf("all", "H3K4me3@tss[-3000..2000]"), dataConfig.poi)
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
            val genome = Genome("to1")
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
            val config = ds.toDataConfig()
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
            assertNotEquals(dc1, dc2)
            assertNotEquals(dc1.hashCode(), dc2.hashCode())
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
   methylation:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(1, dc.tracksMap.size)
            assertEquals(1, dc.tracksMap.keys.size)
            assertEquals("(methylation, t)", dc.tracksMap.keys.first().toString())
            assert(dc.tracksMap.values.first() is Section.Implicit)
        }
    }


    @Test fun testMethLabeled() {
        withTempFile("test1", ".bam") { p ->
            val config = """genome: to1
tracks:
   methylation:
      t:
        a: $p
        b: $p
"""
            val dc = DataConfig.load(config.reader())
            assert(dc.tracksMap.values.first() is Section.Labeled)
            assertEquals("[a, b]", (dc.tracksMap.values.first() as Section.Labeled).replicates.keys.toString())
        }
    }


    @Test fun testExpression() {
        withTempFile("test1", ".fastq") { p ->
            val config = """genome: to1
tracks:
   transcription:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(1, dc.tracksMap.size)
            assertEquals(1, dc.tracksMap.keys.size)
            assertEquals("(transcription, t)", dc.tracksMap.keys.first().toString())
            assert(dc.tracksMap.values.first() is Section.Implicit)
        }
    }


    @Test fun testExpressionLabeled() {
        withTempFile("test1", ".fastq") { p ->
            val config = """genome: to1
tracks:
   transcription:
      t:
        a: $p
        b: $p
"""
            val dc = DataConfig.load(config.reader())
            assert(dc.tracksMap.values.first() is Section.Labeled)
            assertEquals("[a, b]", (dc.tracksMap.values.first() as Section.Labeled).replicates.keys.toString())
        }
    }



    @Test fun consistencyDataSet() {
        withTempFile("test1", ".bed") { tmp ->
            val genome = Genome("to1")
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
            })(ds.toDataConfig())
            assertEquals("[H3K4me3 A, H3K4me3 B, H3K27me3 A, H3K27me3 B, H3K36me3 A, H3K36me3 B]", order.toString())
        }
    }

    @Test fun testRulesSettings() {
        withTempFile("test1", ".bed") { p ->
            val config = """genome: to1
rule_min_support: 100
rule_min_conviction: 3.0
rule_max_complexity: 20
rule_top: 10
rule_output: 100
rule_regularizer: 1.5
tracks:
   H3K4me3:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals(100, dc.rule_min_support)
            assertEquals(3.0, dc.rule_min_conviction)
            assertEquals(20, dc.rule_max_complexity)
            assertEquals(10, dc.rule_top)
            assertEquals(100, dc.rule_output)
            assertEquals(1.5, dc.rule_regularizer)
        }
    }

    @Test fun testParams() {
        withTempFile("test1", ".bed") { p ->
            val config = """genome: to1
tracks:
   H3K4me3:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals("", dc.ruleParams())
        }
        withTempFile("test1", ".bed") { p ->
            val config = """genome: to1
rule_min_support: 100
rule_min_conviction: 3.0
rule_max_complexity: 20
rule_top: 10
rule_output: 100
rule_regularizer: 1.5
tracks:
   H3K4me3:
      t:
      - $p
"""
            val dc = DataConfig.load(config.reader())
            assertEquals("rule_max_complexity_20_rule_min_conviction_3.0_" +
                    "rule_min_support_100_rule_output_100_rule_regularizer_1.5", dc.ruleParams())
        }
    }



}
