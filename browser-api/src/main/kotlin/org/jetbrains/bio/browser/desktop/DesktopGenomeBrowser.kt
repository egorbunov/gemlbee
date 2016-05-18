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
import org.jetbrains.bio.browser.query.desktop.DesktopInterpreter
import org.jetbrains.bio.browser.query.desktop.NewTrackViewListener
import org.jetbrains.bio.browser.query.desktop.TrackNameListener
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.genome.query.GenomeQuery
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

    private var trackNameListeners: ArrayList<TrackNameListener> = ArrayList()

    fun addTrackNameListener(listener: TrackNameListener) {
        trackNameListeners.add(listener)
    }

    fun deleteTrackNameListener(listener: TrackNameListener) {
        trackNameListeners.remove(listener)
    }

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

    val tracksCompletion: List<String>
        get() = interpreter.getAvailableNamedTracks()

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

    /**
     * Just delegating...
     */
    private val newTrackNameListener = object : TrackNameListener {
        override fun addTrackName(name: String) {
            trackNameListeners.forEach { it.addTrackName(name) }
        }

        override fun deleteTrackName(name: String) {
            trackNameListeners.forEach { it.addTrackName(name) }
        }

    }

    init {
        Logs.addConsoleAppender(Level.INFO)
        require(trackViews.isNotEmpty())
        browserModel.addListener(modelListener)
        // listening for new track views...
        interpreter.addNewTrackViewListener(newTrackViewListener)
        interpreter.addNewTrackNameListener(newTrackNameListener)
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

    fun handleAnyQuery(text: String) {
        try {
            handlePosition(text);
        } catch (e: Exception) {
            try {
                val msg = handleStatementQuery(text)
                if (msg.isNotEmpty()) {
                    JOptionPane.showMessageDialog(mainPanel.parent,
                            msg,
                            "Interpreter",
                            JOptionPane.INFORMATION_MESSAGE)
                }
            } catch (ne: Exception) {
                JOptionPane.showMessageDialog(mainPanel.parent,
                        "Can't parse position or statement: \n 1) ${e.message} \n 2) ${ne.message}",
                        "Go To Location/Interpreter",
                        JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    fun handleOnlyPositionChanged(text: String) {
        if (interpreter.isParseable(text)) {
            return
        }
        try {
            handlePosition(text);
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(mainPanel.parent,
                    "Can't parse position: \n ${e.message} \n",
                    "Go To Location",
                    JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun handleStatementQuery(text: String): String {
        return interpreter.interpret(text)
    }

    /**
     * Returns error message or null if ok
     */
    private fun handlePosition(text: String) {
        if (handleMultipleLocationsModel(text)) {
            return
        }

        val model = model as SingleLocationBrowserModel
        val locRef = LociCompletion.parse(text, model.genomeQuery)
        if (locRef != null) {
            execute(model.goTo(locRef))
        } else {
            throw IllegalStateException("Illegal location: " + text)
        }
    }

    companion object {
        val GEMLBEE = "GeMLBee - Genome Multiple Locations Browser"

        operator fun invoke(genomeQuery: GenomeQuery, trackViews: ArrayList<TrackView>,
                            interpreter: DesktopInterpreter): DesktopGenomeBrowser {
            return DesktopGenomeBrowser(SingleLocationBrowserModel(genomeQuery),
                    trackViews,
                    LociCompletion.DEFAULT_COMPLETION,
                    interpreter)
        }
    }
}
