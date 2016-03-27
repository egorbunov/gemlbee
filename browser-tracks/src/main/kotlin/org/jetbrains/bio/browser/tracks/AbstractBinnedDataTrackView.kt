package org.jetbrains.bio.browser.tracks

import org.apache.log4j.Logger
import org.jetbrains.bio.big.*
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.Chromosome
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.util.Configuration
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.stream.Collectors
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JOptionPane
import kotlin.properties.Delegates.observable

/**
 * @author Roman.Chernyatchik
 */
abstract class AbstractBinnedDataTrackView @JvmOverloads constructor(
        lineType: LineType = LineType.HIST_LIKE,
        binSize: Int = 1000,
        title: String,
        protected val binSizes: IntArray = intArrayOf(binSize)) : BigWigTrackView(lineType, title) {

    protected var binSize: Int by observable(binSize) { _prop, old, new ->
        fireRepaintRequired()
    }

    protected abstract fun fileName(layer: Int, strand: Strand, binSize: Int, gq: GenomeQuery): String
    protected abstract fun preprocess(layer: Int, binSize: Int, chr: Chromosome, strand: Strand): FloatArray
    protected open fun ignoredValue(value: Float): Boolean = value.isNaN()
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getDataPath(layer: Int, strand: Strand, model: SingleLocationBrowserModel,
                             conf: Storage)
            = getDataPath(layer, strand, binSize, model.genomeQuery)

    private fun getDataPath(layer: Int, strand: Strand, binSize: Int, gq: GenomeQuery): Path {
        return Configuration.cachePath / "browser" / fileName(layer, strand, binSize, gq)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun preprocess(genomeQuery: GenomeQuery) {
        LOG.time(message = "Browser preprocess data for: $title") {
            val executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
            val tasks = ArrayList<Callable<*>>()
            binSizes.forEach { binSize ->
                (0 until layersNumber).forEach { layer ->
                    val strands = if (strandedData) Strand.values() else arrayOf(Strand.PLUS)
                    strands.forEach { strand ->
                        tasks.add(Callable {
                            val dataPath = getDataPath(layer, strand, binSize, genomeQuery)
                            dataPath.checkOrRecalculate(this.javaClass.simpleName) { output ->
                                val genomeWigData = genomeQuery.get().stream().parallel().flatMap { chr ->
                                    calcWigSections(layer, binSize, chr, strand).stream()
                                }.collectHack(Collectors.toList())

                                output.let { path ->
                                    BigWigFile.write(genomeWigData,
                                            genomeQuery.get().map { it.name to it.length },
                                            path)
                                }
                            }
                        })
                    }
                }
            }

            executor.awaitAll(tasks)
            check(executor.shutdownNow().isEmpty())
        }
    }

    private fun calcWigSections(layer: Int, binSize: Int, chr: Chromosome, strand: Strand): List<WigSection> {
        val binnedData = preprocess(layer, binSize, chr, strand)

        val chrName = chr.name

        // Sparse according to ignored values:
        val hasIgnoredValues = binnedData.firstOrNull { ignoredValue(it) }?.let { true } ?: false

        return if (!hasIgnoredValues) {
            // Fixed step
            val section = FixedStepSection(chrName, 0, step = binSize, span = binSize)
            binnedData.forEach { value -> section.add(value) }
            listOf(section)
        } else {
            // Variable step:
            val section = VariableStepSection(chrName, span = binSize);
            binnedData.forEachIndexed { i, value ->
                if (!ignoredValue(value)) {
                    section.set(i * binSize, value)
                }
            }
            listOf(section)
        }

    }

    override fun renderer(layer: Int, model: SingleLocationBrowserModel,
                          conf: Storage, uiOptions: Storage): BinnedRenderer?
            = BinnedRenderer(binSize)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun addTrackControls(): List<Pair<String, JComponent>> {
        val superControls = super.addTrackControls()
        return when {
            binSizes.size < 2 -> superControls
            else -> {
                val controls = ArrayList<Pair<String, JComponent>>()
                controls.add(binSizeSelector())
                controls.addAll(superControls)
                controls
            }
        }
    }

    protected fun binSizeSelector(): Pair<String, JComponent> {
        // Selector
        val cbItems = binSizes.map { it.toLong().asOffset() }.toTypedArray()
        val comboBox = JComboBox(cbItems)
        comboBox.alignmentX = Component.LEFT_ALIGNMENT
        comboBox.selectedItem = binSize.asOffset()

        comboBox.isEditable = false // no sense
        comboBox.preferredSize = Dimension(150, comboBox.preferredSize.height)
        comboBox.maximumSize = Dimension(200, comboBox.maximumSize.height)
        comboBox.addActionListener() {
            val selectedItem = comboBox.selectedItem.toString()
                    .replace(".", "").replace(",", "").replace("_", "").replace(" ", "")

            val selectedBinSize: Int
            try {
                selectedBinSize = selectedItem.toInt()
            } catch (ex: NumberFormatException) {
                JOptionPane.showMessageDialog(comboBox,
                        "Bin size isn't integer number: $selectedItem",
                        "Change Bin Size", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            binSize = selectedBinSize

        }
        return "Bin size:".to(comboBox)
    }

    companion object {
        private val LOG = Logger.getLogger(AbstractBinnedDataTrackView::class.java)
    }
}

/**
 * If range is less than bin size shows whole bin value, otherwise if range contain several bins
 * returns value of averaged bin in range
 */
open class BinnedRenderer(val binSize: Int,
                          color: Color = Color.BLACK) : ValueRenderer(color) {
    override fun valueOf(pixelSummary: BigSummary, nanDefaultValue: Double): Double {
        // When range intersects with bin, BigWig returns:
        //   pixelSummary.sum = sum{i}[value_i * (intersection_i %)] =
        //   = sum[value_i * (intersection_i/bin size)]
        //
        //   pixelSummary.count = sum{i}[intersection_i]
        //
        //   pixelSummary.sum / pixelSummary.count ~= avg value per 1 bp
        //   (pixelSummary.sum / pixelSummary.count) * bin_size ~= avg value per bin

        // Nucleotides in range with bins data intersection, may include more
        // than one bin for small zoom levels
        val intersectionCumSize = pixelSummary.count

        return when {
            intersectionCumSize == 0L || pixelSummary.sum.isNaN() -> nanDefaultValue
        // * if shorter than one bin => show corresponding bin value
        // * if covers several bins => avg bin value
            else -> pixelSummary.sum * binSize / intersectionCumSize
        }
    }
}
