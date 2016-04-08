package org.jetbrains.bio.browser.tracks

import org.apache.log4j.Logger
import org.jdesktop.swingx.graphics.BlendComposite
import org.jdesktop.swingx.graphics.BlendComposite.BlendingMode
import org.jetbrains.bio.big.BigSummary
import org.jetbrains.bio.big.BigWigFile
import org.jetbrains.bio.big.WigFile
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.util.Key
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.ext.*
import org.jetbrains.bio.genome.ChromosomeRange
import org.jetbrains.bio.genome.Strand
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.util.Colors
import org.jetbrains.bio.util.Configuration
import java.awt.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.stream.IntStream
import javax.swing.*
import kotlin.properties.Delegates.observable
import kotlin.reflect.KProperty

/**
 * A track view for BigWIG files.
 *
 * @author Roman Chernyatchik
 * @since 27/08/15
 */
abstract class BigWigTrackView(lineType: LineType = LineType.HIST_LIKE,
                               title: String) :
        TrackView(title), TrackViewWithControls {

    protected val uiOpts: Storage = Storage()

    private fun <T> repaintOnChange(): (KProperty<*>, T, T) -> Unit = { _prop, old, new ->
        fireRepaintRequired()
    }

    protected var lineType: LineType by observable(lineType, repaintOnChange())
    protected var blendingMode: BlendingMode? by observable(null, repaintOnChange())
    protected var flipPlotMode: Boolean by observable(false, repaintOnChange())

    protected val TRACK_DATA: Key<TrackData> = Key("TRACK_DATA")

    private val NO_BLENDING = "<none>"
    private val VERTICAL_SPACER = 3

    protected abstract val layersNumber: Int;
    /**
     * True if data differs on strands, false if strand isn't important.
     */
    protected abstract val strandedData: Boolean;

    protected open fun renderer(layer: Int,
                                model: SingleLocationBrowserModel,
                                conf: Storage,
                                uiOptions: Storage): ValueRenderer? = ValueRenderer()

    protected abstract fun getDataPath(layer: Int, strand: Strand,
                                       model: SingleLocationBrowserModel,
                                       conf: Storage): Path;

    abstract val yAxisTitle: String

    ////////////////////////////////////////////////////////////
    override fun initConfig(model: SingleLocationBrowserModel, conf: Storage) {
        val width = conf[TrackView.WIDTH]
        val visibleRange = model.chromosomeRange

        val dataOn = { layer: Int, strand: Strand ->
            byPixelData(getDataPath(layer, strand, model, conf),
                        visibleRange, width)
        }
        val pixelData = (0 until layersNumber).map { layer ->
            val plusData = dataOn(layer, Strand.PLUS)
            val minusData = when {
                strandedData -> dataOn(layer, Strand.MINUS)
                else -> plusData
            }
            plusData.to(minusData)
        }

        conf[TRACK_DATA] = TrackData(pixelData, strandedData)
    }

    private fun byPixelData(path: Path,
                            range: ChromosomeRange,
                            width: Int): List<BigSummary> {

        val numBins = Math.min(range.length(), width)

        val binnedSummaryData: List<BigSummary>
        try {
            //TODO: try to reuse file instance
            binnedSummaryData = BigWigFile.read(path).use { bwFile ->
                bwFile.summarize(range.chromosome.name,
                                 range.startOffset,
                                 range.endOffset,
                                 numBins
                )
            }
        } catch (ex: Exception) {
            Logger.getRootLogger().error("Cannot read file: $path, size ${path.size}")
            throw ex
        }

        return if (numBins <= width) {
            // each bin is represented using several pixels
            val pxPerBin = width.toDouble() / numBins // always is >= 1

            val perPixelData = ArrayList<BigSummary>(width)
            (0 until numBins).forEach { binIndex ->
                val start = Math.round(binIndex * pxPerBin).toInt()
                val end = Math.round((binIndex + 1) * pxPerBin).toInt()
                assert(start < end) { "Start = $start, end = $end" }
                (start until end).forEach { perPixelData.add(binnedSummaryData[binIndex]) }
            }
            perPixelData
        } else {
            binnedSummaryData
        }
    }

    override fun paintTrack(g: Graphics, model: SingleLocationBrowserModel, conf: Storage) {
        // Custom blending mode
        if (blendingMode != null) {
            (g as Graphics2D).composite = BlendComposite.getInstance(blendingMode)
        }

        IntStream.range(0, layersNumber).forEach() { i ->
            renderer(i, model, conf, uiOpts)?.let { renderer ->
                drawLayer(i, renderer, g, conf)
            }
        }
    }

    private fun drawLayer(layer: Int,
                          renderer: ValueRenderer,
                          g: Graphics,
                          conf: Storage) {

        // track width, height:
        val width = conf[TrackView.WIDTH]
        val height = conf[TrackView.HEIGHT]

        if (strandedData) {
            // Paint plus strand
            val plusYH = strandPlotYAndHeight(Strand.PLUS, height)
            drawStrand(layer, renderer, plusYH.first, plusYH.second, Strand.PLUS,
                    g, conf)

            // Paint separator line
            val separatorY = plusYH.first + plusYH.second + 1
            // change blending to Stamp - to draw axis over plot and don't affect it by plot mode
            val originComposite = (g as Graphics2D).composite
            g.composite = AlphaComposite.SrcOver
            // do paint
            g.color = Color.LIGHT_GRAY
            g.drawLine(0, separatorY, width, separatorY)
            // restore previous blending
            g.composite = originComposite

            // Paint minus strand
            val minusYH = strandPlotYAndHeight(Strand.MINUS, height)
            drawStrand(layer,
                    renderer, minusYH.first, minusYH.second, Strand.MINUS,
                    g, conf)
        } else {
            // data only for one strand
            // Plot one strand: no need to separate track in 2 pars
            drawStrand(layer, renderer, 0, height, Strand.PLUS, g, conf)
        }
    }

    private fun strandPlotYAndHeight(strand: Strand, trackHeight: Int): Pair<Int, Int> {
        val plotHeight = Math.floor((trackHeight - VERTICAL_SPACER).toDouble() / 2).toInt()
        return strand.choose(0, plotHeight + VERTICAL_SPACER).to(plotHeight)
    }

    override fun computeScales(model: SingleLocationBrowserModel, conf: Storage): List<Scale> {
        val strands = if (strandedData) Strand.values() else arrayOf(Strand.PLUS)

        val minMax = ArrayList<Scale>()
        for (strand in strands) {
            for (layer in 0 until layersNumber) {
                renderer(layer, model, conf, uiOpts)?.let { renderer ->
                    val pointData = conf[TRACK_DATA][layer, strand]
                    check(pointData.isNotEmpty())

                    // summarized blocks min/max
                    val max = pointData.map { renderer.valueOf(it, Double.NEGATIVE_INFINITY) }.max()!!
                    val min = pointData.map { renderer.valueOf(it, Double.POSITIVE_INFINITY) }.min()!!

                    minMax.add(Scale(min, max))
                }
            }
        }
        check(minMax.isNotEmpty())

        val rendererMin = minMax.map { it.min }.min()!!
        val rendererMax = minMax.map { it.max }.max()!!

        return arrayListOf(Scale(rendererMin, rendererMax))

    }

    private fun drawStrand(layer: Int,
                           renderer: ValueRenderer,
                           plotY: Int, plotHeight: Int,
                           strand: Strand,
                           g: Graphics,
                           conf: Storage) {

        val flipVertical = strand.isPlus() && flipPlotMode

        var prevLineEndX = -1
        var prevLineEndY = -1

        val pointData = conf[TRACK_DATA][layer, strand]

        normalize(pointData, conf[TrackView.SCALES].first(), renderer).forEachIndexed { i, normValue ->
            val startX = i
            val endX = i + 1

            if (lineType == LineType.HIST_LIKE && (normValue == 0.0 || normValue.isNaN())) {
                //TODO: draw 0 or no?
                // Skip 0, NaNs in Hist mode
            } else {
                g.color = renderer.render(i, strand, normValue)

                val minValueY = if (!flipVertical) plotY + plotHeight - 1 else plotY
                val valueY = minValueY - (if (!flipVertical) 1 else -1) * Math.round((plotHeight - 1) * normValue).toInt()

                // current point/line
                if (lineType === LineType.LINES) {
                    val lineEndX = endX - 1 // inclusive point coordinate

                    g.drawLine(startX, valueY, lineEndX, valueY)

                    // connect current point with previous
                    if (prevLineEndX != -1) {
                        g.drawLine(prevLineEndX, prevLineEndY, startX, valueY)
                    }
                    prevLineEndX = lineEndX
                    prevLineEndY = valueY
                } else if (lineType === LineType.HIST_LIKE) {
                    val rectWidth = endX - startX

                    // fillRect() function requires coordinates of top left corner,
                    // it depends on vertical flip value
                    if (!flipVertical) {
                        g.fillRect(startX, valueY, rectWidth, minValueY - valueY)
                    } else {
                        g.fillRect(startX, minValueY, rectWidth, valueY - minValueY)
                    }
                }
            }
        }
    }

    fun normalize(pointData: List<BigSummary>,
                  scale: Scale,
                  renderer: ValueRenderer): Sequence<Double> {
        val min = scale.min
        val max = scale.max

        return pointData.asSequence().map { summary ->
            // Normalize data
            val blockLength = summary.count
            when {
                blockLength == 0L -> Double.NaN  // actually no signal at this bin
                max == min -> if (min == 0.0) 0.0 else 1.0
                else -> (renderer.valueOf(summary, Double.NaN) - min) / (max - min)
            }
        }
    }

    protected open fun axisPresentableValue(value: Double): Double = value

    override fun drawAxis(g: Graphics, conf: Storage,
                          width: Int, height: Int,
                          drawInBG: Boolean) {
        val scales = conf[SCALES]
        val rendererScale = scales[0].let { if (it.isInfinite()) Scale(0.0, 0.0) else it }

        val presentableScale = Scale(axisPresentableValue(rendererScale.min),
                                     axisPresentableValue(rendererScale.max));
        if (strandedData) {
            // should be first minus, than plus
            listOf(Strand.MINUS, Strand.PLUS).forEach { strand ->
                val yAndHeight = strandPlotYAndHeight(strand, height)
                TrackUIUtil.drawVerticalAxis(g,
                        // Don't draw title 2 times
                        strand.choose(yAxisTitle, ""),
                        presentableScale, drawInBG,
                        width, yAndHeight.second,
                        yAndHeight.first,
                        strand.isPlus() && flipPlotMode)
            }
        } else {
            TrackUIUtil.drawVerticalAxis(g, yAxisTitle, rendererScale, drawInBG, width, height,
                                         flipVertical = flipPlotMode)
        }
    }

    class TrackData(private val layersPlusMinusData: List<Pair<List<BigSummary>, List<BigSummary>>>,
                    private val stranded: Boolean) {
        operator fun get(layer: Int, strand: Strand = Strand.PLUS): List<BigSummary> {
            val data = layersPlusMinusData[layer]
            return when {
                stranded -> strand.choose(data.first, data.second)
                else -> data.first
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    override fun createTrackControlsPane(): Pair<JPanel, Consumer<Boolean>>? {
        val contentPane = JPanel()
        contentPane.layout = BoxLayout(contentPane, BoxLayout.X_AXIS)

        val controls = addTrackControls().map { JLabel(it.first).to(it.second) }
        for ((label, component) in controls) {
            if (label.text.isNotEmpty()) contentPane.add(label)
            contentPane.add(component)
        }

        contentPane.add(Box.createHorizontalGlue())
        return contentPane.to(Consumer { enabled: Boolean ->
            controls.forEach() {
                it.first.isEnabled = enabled
                it.second.isEnabled = enabled
            }
        })
    }

    protected open fun addTrackControls(): List<Pair<String, JComponent>> {
        return (listOf(lineTypeSelector(),
                blendingSelector(),
                flipPlotSelector()))
    }

    protected fun lineTypeSelector(): Pair<String, JComponent> {
        val comboBox = JComboBox(LineType.values()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            selectedItem = lineType
            isEditable = false // no sense
            preferredSize = Dimension(150, preferredSize.height)
            maximumSize = Dimension(200, maximumSize.height)
            addActionListener() {
                lineType = selectedItem as LineType
            }
        }

        return "Line type:" to comboBox
    }

    /**
     * Blending mode selector if you draw several plots one over another.
     * <p>
     * Not-null blending mode usage:
     * final Graphics2D graphics2D = (Graphics2D) g.create();
     * // 1-st plot
     * paintData(graphics2D, ...)
     * graphics2D.setComposite(BlendComposite.getInstance(propertiesSnapshot.get(BLENDING_MODE)));
     * // 2-nd plot
     * paintData(graphics2D, ...)
     * graphics2D.dispose();
     */
    protected fun blendingSelector(): Pair<String, JComponent> {
        val comboBox = JComboBox(arrayOf(NO_BLENDING, *BlendingMode.values())).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            selectedItem = blendingMode ?: NO_BLENDING
            isEditable = false  // no sense
            preferredSize = Dimension(150, preferredSize.height)
            maximumSize = Dimension(200, maximumSize.height)

            addActionListener() {
                val selectedItem = selectedItem
                blendingMode = if (selectedItem == NO_BLENDING) {
                    null
                } else {
                    selectedItem as BlendingMode
                }
            }
        }

        return "Blending mode:" to comboBox
    }

    protected fun flipPlotSelector(): Pair<String, JComponent> {
        val cb = JCheckBox("Flip plot vertically", flipPlotMode).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener() {
                flipPlotMode = isSelected
            }
        }

        return "" to cb
    }

    companion object {
        private val LOG = Logger.getLogger(BigWigTrackView::class.java)

        /**
         * Track view for bigwig (*.bw) or wig files (*.wig, *.wig.gz). By default
         * data shown at 1bp resolution (binSize = 1)
         */
        @JvmStatic fun create(title: String, yAxisTitle: String,
                              binSize: Int = 1,
                              vararg descAndPaths: Pair<String, Path>): BigWigTrackView {

            return object : BigWigTrackView(title = title) {
                override val layersNumber = descAndPaths.size
                override val strandedData = false
                override val yAxisTitle = yAxisTitle
                val descs = descAndPaths.map { it.first }
                val bwPaths = ArrayList<Path>()

                private val palette = when (layersNumber) {
                    1 -> listOf(Color.BLACK)
                    else -> Colors.palette(layersNumber, 200)
                }

                override fun preprocess(genomeQuery: GenomeQuery) {
                    val chromSizes = genomeQuery.get().map { it.name to it.length }

                    LOG.time(message = "Browser preprocess data for: $title") {
                        val executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
                        val tasks = ArrayList<Callable<*>>()
                        for ((desc, trackPath) in descAndPaths) {
                            val ext = trackPath.extension
                            if (ext == "bw") {
                                bwPaths.add(trackPath)
                            } else {
                                assert(ext == "wig" || ext == "wig.gz" || ext == "wig.zip") {
                                    "File type not supported: $trackPath"
                                }
                                val bwPath = Configuration.cachePath / "browser" / "${trackPath.name}.bw"

                                bwPaths.add(bwPath)

                                tasks.add(Callable {
                                    bwPath.checkOrRecalculate(BigWigTrackView.javaClass.simpleName) { output ->
                                        output.let { path ->
                                            BigWigFile.write(WigFile(trackPath), chromSizes, path)
                                        }
                                    }
                                })
                            }
                        }
                        executor.awaitAll(tasks)
                        check(executor.shutdownNow().isEmpty())
                    }

                }

                override fun renderer(layer: Int,
                                      model: SingleLocationBrowserModel,
                                      conf: Storage,
                                      uiOptions: Storage)
                        = BinnedRenderer(binSize, palette[layer])

                override fun getDataPath(layer: Int, strand: Strand,
                                         model: SingleLocationBrowserModel,
                                         conf: Storage)
                        = bwPaths[layer]

                override fun addTrackControls(): List<Pair<String, JComponent>> {
                    return if (layersNumber > 2) {
                        listOf(lineTypeSelector())
                    } else {
                        listOf()
                    }
                }

                override fun drawLegend(g: Graphics, width: Int, height: Int, drawInBG: Boolean) {
                    TrackUIUtil.drawBoxedLegend(g, width, height, drawInBG,
                                                *descs.mapIndexed { layer, desc -> palette[layer] to desc }
                                                        .toTypedArray())
                }
            }
        }
    }
}

// histogram-type vertical lines
enum class LineType(private val myPresentableName: String) {
    LINES("Lines"),
    HIST_LIKE("Histogram like");

    override fun toString() = myPresentableName;
}

open class ValueRenderer(protected val color: Color = Color.BLACK) {
    open fun render(pixelPos: Int, strand: Strand, normVal: Double): Color = color

    open fun valueOf(pixelSummary: BigSummary, nanDefaultValue: Double): Double
            = if (pixelSummary.count == 0L || pixelSummary.sum.isNaN())
        nanDefaultValue
    else
        pixelSummary.sum / pixelSummary.count

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
