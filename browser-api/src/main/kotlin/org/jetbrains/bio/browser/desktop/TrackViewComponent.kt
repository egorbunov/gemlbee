package org.jetbrains.bio.browser.desktop

import com.jidesoft.swing.MultilineLabel
import net.miginfocom.swing.MigLayout
import org.jetbrains.bio.browser.tracks.TrackView
import org.jetbrains.bio.browser.tracks.TrackViewWithControls
import org.jetbrains.bio.browser.util.Storage
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.border.LineBorder

/**
 * @author Evgeny.Kurbatsky
 */
class TrackViewComponent(trackView: TrackView,
                         browser: DesktopGenomeBrowser,
                         uiModel: Storage) : JComponent() {

    private val titleLabel = object : MultilineLabel(trackView.title) {
        override fun getPreferredSize(): Dimension? {
            // I have no idea why the '-5' is required for proper title
            // wrapping. Without the '-' no wrapping is done.
            // See: 7a3c0e0be75edd1e70dfe0fa2c3f224c9672955f for
            // inspiration.
            val size = super.getPreferredSize()
            size.width = renderComponent.width - 5
            return size
        }
    }

    private val renderComponent = RenderComponent(trackView, browser, uiModel)

    var isSelected: Boolean = false
        set(selected) {
            field = selected

            border = if (selected) SELECTED_BORDER else NOT_SELECTED_BORDER
        }

    val trackView: TrackView get() = renderComponent.trackView

    val image: BufferedImage get() {
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { paint(graphics) }
    }

    init {
        isSelected = false

        val trackControlsPanel = if (trackView is TrackViewWithControls)
            trackView.initTrackControlsPane()
        else
            null

        val debug = false
        layout = MigLayout(
                // layout const
                (if (debug) "debug, " else "") + "ins 0, hidemode 2, wrap 1, gapy 0",
                // column const
                "[grow, fill]",
                // row const
                (if (trackControlsPanel == null) "" else "[]") + "[][][grow, fill]")

        add(titleLabel)

        if (trackControlsPanel != null) {
            add(trackControlsPanel)
        }

        add(renderComponent)
    }

    fun resizeOnDividerMoved(newHeight: Int) {
        val preferredHeight = preferredSize.height
        val drawingAreaPreferredHeight = renderComponent.preferredSize.height
        val otherComponentsPreferredHeight = preferredHeight - drawingAreaPreferredHeight
        val newDrawingAreaPreferredHeight = newHeight - otherComponentsPreferredHeight
        renderComponent.setPreferredHeight(newDrawingAreaPreferredHeight)
    }

    fun dispose() = renderComponent.dispose()

    companion object {
        private val SELECTED_BORDER = LineBorder(Color.BLUE, 2)
        private val NOT_SELECTED_BORDER = LineBorder(Color.LIGHT_GRAY, 2)
    }
}