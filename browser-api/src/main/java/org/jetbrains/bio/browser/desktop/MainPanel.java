package org.jetbrains.bio.browser.desktop;

import org.jdesktop.swingx.util.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bio.browser.GenomeBrowser;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.tracks.TrackView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created on 13/06/13
 * Author: Sergey Dmitriev
 */
public class MainPanel extends JPanel implements ActionListener {

  private final HashMap<String, Consumer<ActionEvent>> myActionsMap = new HashMap<>();
  private final TrackListComponent myTrackListComponent;
  private final ControlsPanel myControlPane;
  private final ScrollBarModel myScrollBarModel;

  public MainPanel(final DesktopGenomeBrowser browser, final List<TrackView> trackViews) {
    setLayout(new BorderLayout());

    // controls
    myControlPane = new ControlsPanel(browser);
    add(myControlPane, BorderLayout.NORTH);

    myTrackListComponent = new TrackListComponent(this, browser, trackViews);
    add(myTrackListComponent.getContentPane(), BorderLayout.CENTER);

    // Genome region scroll bar
    final JScrollBar scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
    myScrollBarModel = new ScrollBarModel(browser.getBrowserModel());
    scrollBar.setModel(myScrollBarModel);
    add(scrollBar, BorderLayout.SOUTH);
  }

  public void setModel(final BrowserModel browserModel) {
    myControlPane.setBrowserModel(browserModel);
    myScrollBarModel.setBrowserModel(browserModel);
    myTrackListComponent.getContentPane().setColumnHeaderView(GenomeBrowser.Companion.createHeaderView(browserModel));
  }

  public TrackListComponent getTrackListComponent() {
    return myTrackListComponent;
  }

  @Override
  public void actionPerformed(@NotNull final ActionEvent e) {
    final String actionCommand = e.getActionCommand();
    final Consumer<ActionEvent> handler = myActionsMap.get(actionCommand);
    if (handler != null) {
      handler.accept(e);
    }
  }

  public void fillMenu(final JMenuBar mainMenu) {
    final TrackListController controller = myTrackListComponent.getTrackListController();
    // Navigation
    final JMenu navigationMenu = new JMenu("Navigation");
    navigationMenu.setMnemonic('n');
    mainMenu.add(navigationMenu);
    registerMenuItem(OS.isMacOSX() ? KeyEvent.VK_OPEN_BRACKET : KeyEvent.VK_LEFT,
                     OS.isMacOSX() ? ActionEvent.META_MASK : ActionEvent.ALT_MASK,
                     e -> controller.undo(), navigationMenu, "Back", 0, false, false);
    registerMenuItem(OS.isMacOSX() ? KeyEvent.VK_CLOSE_BRACKET : KeyEvent.VK_RIGHT,
                     OS.isMacOSX() ? ActionEvent.META_MASK : ActionEvent.ALT_MASK,
                     e -> controller.redo(), navigationMenu, "Forward", 0, false, false);

    navigationMenu.addSeparator();
    registerMenuItem(KeyEvent.VK_LEFT, 0, e -> controller.doScrollTrack(true, false), navigationMenu,
                     "Scroll Left", (int) 'l', false, false);
    registerMenuItem(KeyEvent.VK_RIGHT, 0, e -> controller.doScrollTrack(false, false), navigationMenu,
                     "Scroll Right", (int) 'r', false, false);
    registerMenuItem(KeyEvent.VK_LEFT, getMetaModifier(), e -> controller.doScrollTrack(true, true), navigationMenu,
                     "Scroll Window Left", 0, false, false);
    registerMenuItem(KeyEvent.VK_RIGHT, getMetaModifier(), e -> controller.doScrollTrack(false, true), navigationMenu,
                     "Scroll Window Right", 0, false, false);

    // Zoom actions
    navigationMenu.addSeparator();

    mainMenu.add(navigationMenu);
    registerMenuItem(KeyEvent.VK_EQUALS, 0, e -> controller.doZoom2x(true), navigationMenu,
                     "Zoom In", (int) 'i', false, false);
    registerMenuItem(KeyEvent.VK_MINUS, 0, e -> controller.doZoom2x(false), navigationMenu,
                     "Zoom Out", (int) 'o', false, false);

    // Misc
    final JMenu miscMenu = new JMenu("Misc");
    miscMenu.setMnemonic('m');
    mainMenu.add(miscMenu);
    registerMenuItem(KeyEvent.VK_A, getMetaModifier(), e -> controller.doSelectAllTracks(), miscMenu,
                     "Select All Tracks", (int) 'a', false, false);
    miscMenu.addSeparator();
    registerMenuItem(KeyEvent.VK_S, getMetaModifier(), e -> myTrackListComponent.doTakeScreenshot(false), miscMenu,
                     "Screenshot", (int) 's', false, false);
    registerMenuItem(KeyEvent.VK_S, getMetaModifier() | ActionEvent.SHIFT_MASK,
                     e -> myTrackListComponent.doTakeScreenshot(true), miscMenu,
                     "Screenshot Selected Track", 0, false, false);
    miscMenu.addSeparator();
    registerMenuItem(null, 0, e -> controller.doToggleLegendVisibility(), miscMenu, "Show legend", (int) 'l',
                     true, myTrackListComponent.getUIModel().get(TrackView.SHOW_LEGEND));
    registerMenuItem(null, 0, e -> controller.doToggleAxisVisibility(), miscMenu, "Show axis", (int) 'x',
                     true, myTrackListComponent.getUIModel().get(TrackView.SHOW_AXIS));
  }

  public static int getMetaModifier() {
    return OS.isMacOSX() ? ActionEvent.META_MASK : ActionEvent.CTRL_MASK;
  }

  private void registerMenuItem(@Nullable final Integer keyChar,
                                     final int modifiers,
                                     final Consumer<ActionEvent> handler,
                                     final JMenu menu,
                                     final String title,
                                     final int mnemonic,
                                     final boolean isCheckBox,
                                     final boolean checkBoxDefaultState) {
    final JMenuItem menuItem = isCheckBox
                               ? new JCheckBoxMenuItem(title, checkBoxDefaultState)
                               : new JMenuItem(title);
    menuItem.setMnemonic(mnemonic);
    menu.add(menuItem);
    if (keyChar != null) {
      menuItem.setAccelerator(KeyStroke.getKeyStroke(keyChar, modifiers));
    }
    menuItem.addActionListener(this);
    myActionsMap.put(title, handler);
  }
}
