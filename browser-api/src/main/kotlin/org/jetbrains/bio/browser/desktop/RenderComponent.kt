package org.jetbrains.bio.browser.desktop

import org.apache.log4j.Logger
import org.jetbrains.bio.browser.createAAGraphics
import org.jetbrains.bio.browser.tasks.CancellableState
import org.jetbrains.bio.browser.tasks.CancellableTask
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.tracks.TrackViewListener
import org.jetbrains.bio.browser.util.Storage
import org.jetbrains.bio.browser.util.TrackUIUtil
import org.jetbrains.bio.browser.util.TrackViewRenderer
import java.awt.*
import java.awt.image.BufferedImage
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Component use to draw [TrackView] or progress asynchronously
 *
 * @author Roman Chernyatchik
 * @author Oleg Shpynov
 */
class RenderComponent(val trackView: TrackView,
                      private val browser: DesktopGenomeBrowser,
                      private val uiModel: Storage)
:
        JComponent(), TrackViewListener {

    @Volatile private var alpha = 0f

    private val progressTimer = Timer(50) { repaint() }

    // These are volatile because some tracks (e.g. 'RepeatsTrackView')
    // call 'RenderComponent#relayoutRequired' from the 'paintTrack'
    // method, which is executed in the main thread.
    // 'RenderComponent#relayoutRequired' is allowed to cancel the task.
    //
    // A task can also be cancelled from the AWT thread, for instance,
    // after an appropriate keyboard event.
    @Volatile private var currentTask: CancellableTask<BufferedImage>? = null
    @Volatile private var currentImage: BufferedImage? = null

    init {
        trackView.addEventsListener(this)
    }

    private fun restart() {
        var task = currentTask
        if (task != null) {
            task.cancel()
            trace("Cancelled task ${task.id}")
        }

        currentTask = CancellableTask.of(Callable { paintToBuffer() })
        trace("Submitted task ${currentTask!!.id}")
    }

    override fun repaintRequired() {
        trace("Repaint required")
        restart()
        SwingUtilities.invokeLater { this.repaint() }
    }

    override fun relayoutRequired() {
        trace("Relayout required")
        restart()
        SwingUtilities.invokeLater {
            // Invalidate the component and all its parents.
            var c: Component? = this
            while (c != null) {
                c.invalidate()
                c.validate()
                c = c.parent
            }
        }
    }

    override fun getPreferredSize() = Dimension(10, trackView.preferredHeight)

    fun setPreferredHeight(height: Int) {
        trackView.preferredHeight = height
        relayoutRequired()
    }

    override fun paint(g: Graphics) {
        trace("Paint")
        val task = currentTask
        if (task == null) {
            restart()
            progress(g)
            return
        }
        if (!task.isDone) {
            progress(g)
            return
        }
        try {
            val rendered = task.get()
            if (task == currentTask) {
                trace("Done task ${task.id}")
                g.drawImage(rendered, 0, 0, null)
                timerStop()
                alpha = 0f
                currentImage = rendered
            }
        } catch (e: CancellationException) {
            restart()
            progress(g)
        }
    }

    private fun progress(g: Graphics) {
        trace("Progress")
        val background = currentImage
        if (background != null) {
            g.drawImage(background, 0, 0, null)
        }

        g.drawProgressLine(width, height, alpha)
        alpha = Math.min(MAX_ALPHA, alpha + 0.01f)
        timerStart()
    }

    private fun paintToBuffer(): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        try {
            TrackViewRenderer.paintToImage(image,
                                           browser.browserModel.copy(), width, height,
                                           trackView,
                                           CancellableState.current(),
                                           true, uiModel)
        } catch (e: Throwable) {
            LOG.error(e)
            val g2d = image.createAAGraphics()
            TrackUIUtil.drawErrorMessage(
                    g2d, "Exception occurred: ${e.javaClass.name} - ${e.message} (see details in log)")
            g2d.dispose()
        }

        return image
    }

    private fun timerStart() {
        // Start repaint timer to show a progress bar while buffered image is calculating
        if (!progressTimer.isRunning) {
            progressTimer.start()
        }
    }

    private fun timerStop() {
        if (progressTimer.isRunning) {
            progressTimer.stop()
        }
    }

    private fun trace(message: String) = LOG.trace("[${trackView.title}] $message")

    fun dispose() {
        timerStop()
        trackView.removeEventsListener(this)
    }

    companion object {
        private val LOG = Logger.getLogger(RenderComponent::class.java)

        private val MAX_ALPHA = 0.5f  // Same as the server version.
    }
}

private fun Graphics.drawProgressLine(width: Int, height: Int, alpha: Float) {
    (this as Graphics2D).composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
    color = Color.BLACK
    fillRect(0, 0, width, height)
    // draw progress line
    for (i in 0..width - 1) {
        if ((i + System.currentTimeMillis() / 10) % 20 < 10) {
            color = Color.BLUE
        } else {
            color = Color.LIGHT_GRAY
        }
        drawLine(i, 2, i - 5, 8)
    }
}
