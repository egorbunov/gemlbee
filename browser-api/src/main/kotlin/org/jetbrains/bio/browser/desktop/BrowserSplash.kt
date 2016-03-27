package org.jetbrains.bio.browser.desktop

import org.apache.log4j.Appender
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Logger
import org.apache.log4j.spi.LoggingEvent
import org.jdesktop.swingx.util.OS
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.util.Logs
import java.awt.*
import javax.imageio.ImageIO
import javax.swing.*

object BrowserSplash : JDialog(Frame()) {
    private val icon: Icon
    var message :String = "Loading browser..."
    var logAppender: Appender? = null

    init {
        icon = ImageIcon(ImageIO.read(SearchPanel::class.java.getResource("/splash.png")))
        contentPane.layout = BorderLayout()
        contentPane.add(object : JLabel(icon) {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                // Turn on antialiasing
                (g as Graphics2D).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.color = Color.WHITE
                g.fillRect(0, icon.iconHeight - 50, width, 50)
                TrackUIUtil.drawString(g, DesktopGenomeBrowser.GEMLBEE, 5, icon.iconHeight - 30, Color.BLACK)
                g.font = org.jetbrains.bio.browser.util.TrackUIUtil.SMALL_FONT
                TrackUIUtil.drawString(g, message, 5, icon.iconHeight - 10, Color.BLACK)
            }
        }, BorderLayout.CENTER)
        // Place in the center
        val dimension = if (!OS.isMacOSX())
            Toolkit.getDefaultToolkit().screenSize
        else
            Dimension(HeadlessGenomeBrowser.SCREENSHOT_WIDTH, HeadlessGenomeBrowser.SCREENSHOT_HEIGHT)
        location = Point((dimension.width - icon.iconWidth / 2) / 2, (dimension.height - icon.iconHeight) / 2)
        isUndecorated = true
        isResizable = false
        pack()
    }

    fun display() {
        isVisible = true
        toFront()
        logAppender = object : AppenderSkeleton() {
            override @Synchronized fun append(event: LoggingEvent) {
                message = Logs.LAYOUT_SHORT.format(event).trim()
                SwingUtilities.invokeLater { repaint() }
            }

            override fun close() {
                Logger.getRootLogger().removeAppender(this)
            }

            override fun requiresLayout(): Boolean = false;
        }
        Logger.getRootLogger().addAppender(logAppender)
    }

    @JvmStatic fun close() {
        logAppender?.close()
        if (isVisible) {
            isVisible = false
        }
        dispose()
    }
}