package org.jetbrains.bio.browser.desktop

import org.jdesktop.swingx.util.OS.isMacOSX
import org.jetbrains.bio.browser.GenomeBrowser
import org.jetbrains.bio.browser.model.BrowserModel
import org.jetbrains.bio.browser.tracks.TrackView
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.KeyStroke.getKeyStroke

/**
 * Created on 13/06/13
 * Author: Sergey Dmitriev
 */
class MainPanel(browser: DesktopGenomeBrowser, trackViews: List<TrackView>) : JPanel() {
    val trackListComponent = TrackListComponent(this, browser, trackViews)

    private val searchPanel = SearchPanel(browser)
    private val scrollBarModel = ScrollBarModel(browser.model)

    init {
        layout = BorderLayout()

        add(searchPanel, BorderLayout.NORTH)
        add(trackListComponent.contentPane, BorderLayout.CENTER)

        val scrollBar = JScrollBar(JScrollBar.HORIZONTAL)
        scrollBar.model = scrollBarModel
        add(scrollBar, BorderLayout.SOUTH)
    }

    fun setModel(browserModel: BrowserModel) {
        searchPanel.setBrowserModel(browserModel)
        scrollBarModel.setBrowserModel(browserModel)
        trackListComponent.contentPane.setColumnHeaderView(GenomeBrowser.createHeaderView(browserModel))
    }

    fun fillMenu(mainMenu: JMenuBar) {
        val controller = trackListComponent.trackListController
        val navigationMenu = NavigationMenu().apply {
            back.addActionListener { controller.undo() }
            forward.addActionListener { controller.redo() }

            scrollLeft.addActionListener { controller.doScrollTrack(true, false) }
            scrollRight.addActionListener { controller.doScrollTrack(false, false) }
            scrollWindowLeft.addActionListener { controller.doScrollTrack(true, true) }
            scrollWindowRight.addActionListener { controller.doScrollTrack(false, true) }

            zoomIn.addActionListener { controller.doZoom2x(true) }
            zoomOut.addActionListener { controller.doZoom2x(false) }
        }

        val miscMenu = MiscMenu().apply {
            screenshot.addActionListener { trackListComponent.doTakeScreenshot(false) }
            screenshotSelected.addActionListener { trackListComponent.doTakeScreenshot(true) }

            showLegend.state = trackListComponent.uiModel[TrackView.SHOW_LEGEND]
            showLegend.addActionListener { controller.doToggleLegendVisibility() }
            showAxis.state = trackListComponent.uiModel[TrackView.SHOW_AXIS]
            showAxis.addActionListener { controller.doToggleAxisVisibility() }
        }

        mainMenu.add(navigationMenu)
        mainMenu.add(miscMenu)
    }
}

class NavigationMenu : JMenu("Navigation") {
    val back = JMenuItem("Back").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta OPEN_BRACKET" else "alt LEFT")
    }
    val forward = JMenuItem("Forward").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta CLOSE_BRACKET" else "alt RIGHT")
    }

    val scrollLeft = JMenuItem("Scroll Left").apply {
        accelerator = getKeyStroke("LEFT")
    }
    val scrollRight = JMenuItem("Scroll Right").apply {
        accelerator = getKeyStroke("RIGHT")
    }
    val scrollWindowLeft = JMenuItem("Scroll Window Left").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta LEFT" else "ctrl LEFT")
    }
    val scrollWindowRight = JMenuItem("Scroll Window Right").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta RIGHT" else "ctrl RIGHT")
    }

    val zoomIn = JMenuItem("Zoom In").apply {
        accelerator = getKeyStroke('=')
    }
    val zoomOut = JMenuItem("Zoom Out").apply {
        accelerator = getKeyStroke('-')
    }

    init {
        add(back)
        add(forward)
        addSeparator()
        add(scrollLeft)
        add(scrollRight)
        add(scrollWindowLeft)
        add(scrollWindowRight)
        addSeparator()
        add(zoomIn)
        add(zoomOut)
    }
}

class MiscMenu : JMenu("Misc") {
    val screenshot = JMenuItem("Screenshot").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta S" else "ctrl S")
    }
    val screenshotSelected = JMenuItem("Screenshot Selected Track").apply {
        accelerator = getKeyStroke(if (isMacOSX()) "meta shift S" else "ctrl shift S")
    }

    val showLegend = JCheckBoxMenuItem("Show Legend")
    val showAxis = JCheckBoxMenuItem("Show Axis")

    init {
        add(screenshot)
        add(screenshotSelected)
        addSeparator()
        add(showLegend)
        add(showAxis)
    }
}
