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
import org.jetbrains.bio.query.DesktopInterpreter
import org.jetbrains.bio.query.NewTrackViewListener
import org.jetbrains.bio.util.Logs
import java.awt.Dimension
import java.awt.Point
import java.awt.Toolkit
import java.util.*
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
                           override val trackViews: ArrayList<TrackView>,
                           locationsMap: Map<String, (GenomeQuery) -> List<LocationReference>>,
                           val interpreter: DesktopInterpreter)
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

    private val newTrackViewListener = object : NewTrackViewListener {
        override fun addNewTrackView(trackView: TrackView) {
            trackView.preprocess(browserModel.genomeQuery)
            trackViews.add(trackView)
            mainPanel.addTrackView(trackView)
            modelListener.modelChanged()
        }
    }

    init {
        Logs.addConsoleAppender(Level.INFO)
        require(trackViews.isNotEmpty())
        browserModel.addListener(modelListener)
        // listening for new track views...
        interpreter.addNewTrackListener(newTrackViewListener)
    }

    override fun execute(command: Command?) {
        if (command != null) {
            controller.execute(command)
        }
    }

    val controller: TrackListController
        get() = mainPanel.trackListComponent.trackListController

    fun show() {
        if (OS.isMacOSX()) {
            // See http://stackoverflow.com/a/10366465/262432. This does not
            // affect the application name displayed to the left of the menu,
            // as explained here http://stackoverflow.com/a/26080591/262432.
            System.setProperty("apple.laf.useScreenMenuBar", "true")
        }

        BrowserSplash.display()

        preprocess()

        mainPanel = MainPanel(this, ImmutableList.copyOf(trackViews))
        val menu = JMenuBar()
        mainPanel.fillMenu(menu)
        JFrame(GEMLBEE).apply {
            contentPane = mainPanel
            jMenuBar = menu
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            location = Point(0, 0)
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

    /**
     * Returns error message or null if ok
     */
    fun handlePositionChanged(text: String): String? {
        if (handleMultipleLocationsModel(text)) {
            return null
        }

        val model = model as SingleLocationBrowserModel
        val locRef = LociCompletion.parse(text, model.genomeQuery)
        if (locRef != null) {
            try {
                execute(model.goTo(locRef))
            } catch (e: Exception) {
                return e.message
//                JOptionPane.showMessageDialog(mainPanel.parent,
//                                              e.message,
//                                              "Go To Location",
//                                              JOptionPane.ERROR_MESSAGE)
            }

        } else {
            return "Illegal location: " + text
//            JOptionPane.showMessageDialog(mainPanel.parent,
//                                          "Illegal location: " + text,
//                                          "Go To Location",
//                                          JOptionPane.ERROR_MESSAGE)
        }

        return null
    }

    companion object {
        val GEMLBEE = "GeMLBee - Genome Multiple Locations Browser"

        operator fun invoke(genomeQuery: GenomeQuery, trackViews: ArrayList<TrackView>,
                            interpreter: DesktopInterpreter): DesktopGenomeBrowser {
            return DesktopGenomeBrowser(SingleLocationBrowserModel(genomeQuery),
                                        trackViews,
                                        LociCompletion.DEFAULT_COMPLETION, interpreter)
        }
    }
}
