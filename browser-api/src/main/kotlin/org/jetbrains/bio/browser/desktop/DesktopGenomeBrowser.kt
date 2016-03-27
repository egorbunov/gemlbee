package org.jetbrains.bio.browser.desktop

import com.google.common.collect.ImmutableList
import org.apache.log4j.Level
import org.jdesktop.swingx.util.OS
import org.jetbrains.bio.browser.*
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.model.LocationReference
import org.jetbrains.bio.browser.model.ModelListener
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.genome.query.GenomeQuery
import org.jetbrains.bio.util.Logs
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.*

/**
 * The basic usage looks like this:
 *
 *     new DesktopGenomeBrowser(...).show()
 *
 * One can set bookmarks previous to the [.show] invocation.
 *
 * @author Roman Chernyatchik
 */
class DesktopGenomeBrowser(browserModel: BrowserModel,
                           override val trackViews: List<TrackView>,
                           locationsMap: Map<String, (GenomeQuery) -> List<LocationReference>>)
:
        GenomeBrowser {

    override val locationsMap = locationsMap.mapKeys { it.key.toLowerCase() }

    private lateinit var mainPanel: MainPanel

    override var model: BrowserModel = browserModel
        set(value: BrowserModel) {
            if (value == model) {
                return
            }

            val oldModel = field
            field = value
            execute(oldModel.changeTo(field) { oldModel, newModel ->
                oldModel.removeListener(modelListener)
                newModel.addListener(modelListener)
                model = newModel
                mainPanel.setModel(newModel)
                model.modelChanged()
            })
        }

    private val modelListener = object : ModelListener {
        override fun modelChanged() = trackViews.forEach(TrackView::fireRepaintRequired)
    }

    init {
        Logs.addConsoleAppender(Level.INFO)
        require(trackViews.isNotEmpty())
        browserModel.addListener(modelListener)
    }

    override fun execute(command: Command?) {
        if (command != null) {
            controller.execute(command)
        }
    }

    val controller: TrackListController
        get() = mainPanel.trackListComponent.trackListController

    fun show() {
        BrowserSplash.display()

        preprocess()

        mainPanel = MainPanel(this, ImmutableList.copyOf(trackViews))
        val menu = JMenuBar()
        mainPanel.fillMenu(menu)

        JFrame(GEMLBEE).apply {
            contentPane = mainPanel
            jMenuBar = menu
            invalidate()
            validate()
            repaint()
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            setLocation(0, 0)
            size = if (!OS.isMacOSX())
                Toolkit.getDefaultToolkit().screenSize
            else
                Dimension(HeadlessGenomeBrowser.SCREENSHOT_WIDTH,
                          HeadlessGenomeBrowser.SCREENSHOT_HEIGHT)
            minimumSize = Dimension(500, 300)
            isVisible = true
        }

        SwingUtilities.invokeLater { mainPanel.trackListComponent.requestFocus() }
        BrowserSplash.close()
    }

    fun handlePositionChanged(text: String) {
        if (handleMultipleLocationsModel(text)) {
            return
        }

        val model = model as SingleLocationBrowserModel
        val locRef = LociCompletion.parse(text, model.genomeQuery)
        if (locRef != null) {
            try {
                execute(model.goTo(locRef))
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(mainPanel.parent,
                                              e.message,
                                              "Go To Location",
                                              JOptionPane.ERROR_MESSAGE)
            }

        } else {
            JOptionPane.showMessageDialog(mainPanel.parent,
                                          "Illegal location: " + text,
                                          "Go To Location",
                                          JOptionPane.ERROR_MESSAGE)
        }
    }

    companion object {
        val GEMLBEE = "GeMLBee - Genome Multiple Locations Browser"

        operator fun invoke(genomeQuery: GenomeQuery, trackViews: List<TrackView>): DesktopGenomeBrowser {
            return DesktopGenomeBrowser(SingleLocationBrowserModel(genomeQuery),
                                        trackViews,
                                        LociCompletion.DEFAULT_COMPLETION)
        }
    }
}
