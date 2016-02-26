package org.jetbrains.bio.browser.desktop;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.apache.log4j.Level;
import org.jdesktop.swingx.util.OS;
import org.jetbrains.bio.browser.AbstractGenomeBrowser;
import org.jetbrains.bio.browser.command.Command;
import org.jetbrains.bio.browser.command.Commands;
import org.jetbrains.bio.browser.headless.HeadlessGenomeBrowser;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.LocationReference;
import org.jetbrains.bio.browser.model.ModelListener;
import org.jetbrains.bio.browser.model.SingleLocationBrowserModel;
import org.jetbrains.bio.browser.tracks.TrackView;
import org.jetbrains.bio.browser.util.LociCompletion;
import org.jetbrains.bio.genome.query.GenomeQuery;
import org.jetbrains.bio.util.Logs;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Roman.Chernyatchik
 *         <p>
 *         The basic usage looks like this:
 *         {@code new DesktopGenomeBrowser(...).show()}.
 *         One can set bookmarks previous to the {@link #show()} invocation.
 */
public class DesktopGenomeBrowser implements AbstractGenomeBrowser {
  public static final String GEMLBEE = "GeMLBee - Genome Multiple Locations Browser";
  private final List<TrackView> myTrackViews;
  private final Map<String, Function1<GenomeQuery, List<LocationReference>>> myLocationsMap;

  private MainPanel myPanel;
  private BrowserModel myBrowserModel;
  private ModelListener myModelListener;

  /**
   * This constructor specifies the available chromosomes and track views.
   *
   * @param query      Initial genome query
   * @param trackViews The list of the {@link TrackView} to display.
   */
  public DesktopGenomeBrowser(final GenomeQuery query, final List<TrackView> trackViews) {
    this(new SingleLocationBrowserModel(query), trackViews, LociCompletion.DEFAULT_COMPLETION);
  }

  public DesktopGenomeBrowser(final BrowserModel browserModel,
                              final List<TrackView> trackViews,
                              Map<String, Function1<GenomeQuery, List<LocationReference>>> locationsMap) {
    Logs.INSTANCE.addConsoleAppender(Level.INFO);
    Preconditions.checkArgument(!trackViews.isEmpty());
    myTrackViews = trackViews;
    myLocationsMap = locationsMap.keySet().stream().collect(Collectors.toMap(String::toLowerCase, locationsMap::get));
    myBrowserModel = browserModel;
    myModelListener = () -> getTrackViews().forEach(TrackView::fireRepaintRequired);
    myBrowserModel.addModelListener(myModelListener);
  }

  public void show() {
    preprocessTracks(myTrackViews, myBrowserModel.getGenomeQuery());

    myPanel = new MainPanel(this, ImmutableList.copyOf(myTrackViews));
    final JMenuBar menu = new JMenuBar();
    myPanel.fillMenu(menu);
    final JFrame frame = new JFrame(GEMLBEE);
    frame.setContentPane(myPanel);
    frame.setJMenuBar(menu);
    frame.invalidate();
    frame.validate();
    frame.repaint();
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setLocation(0, 0);
    frame.setSize(!OS.isMacOSX() ? Toolkit.getDefaultToolkit().getScreenSize()
                                 : new Dimension(HeadlessGenomeBrowser.SCREENSHOT_WIDTH,
                                                 HeadlessGenomeBrowser.SCREENSHOT_HEIGHT));
    frame.setMinimumSize(new Dimension(500, 300));
    frame.setVisible(true);
    SwingUtilities.invokeLater(() -> myPanel.getTrackListComponent().requestFocus());
    BrowserSplash.close();
  }

  @Override
  public BrowserModel getBrowserModel() {
    return myBrowserModel;
  }

  @Override
  public void setBrowserModel(final BrowserModel model) {
    if (myBrowserModel.equals(model)) {
      return;
    }
    execute(Commands.createChangeModelCommand(this, model, (oldModel, newModel) -> {
      oldModel.removeModelListener(myModelListener);
      newModel.addModelListener(myModelListener);
      myBrowserModel = newModel;
      myPanel.setModel(newModel);
      myBrowserModel.modelChanged();
      return Unit.INSTANCE;
    }));
  }

  public List<TrackView> getTrackViews() {
    return myTrackViews;
  }

  @Override
  public void execute(final Command cmd) {
    // If command wasn't rejected:
    if (cmd != null) {
      getController().execute(cmd);
    }
  }

  public TrackListController getController() {
    return myPanel.getTrackListComponent().getTrackListController();
  }

  @Override
  public Map<String, Function1<GenomeQuery, List<LocationReference>>> getLocationsMap() {
    return myLocationsMap;
  }

  public void handlePositionChanged(final String text) {
    if (handleMultipleLocationsModel(text)) {
      return;
    }

    final SingleLocationBrowserModel model = (SingleLocationBrowserModel) getBrowserModel();
    final LocationReference locRef = LociCompletion.parse(text, model.getGenomeQuery());
    if (locRef != null) {
      try {
        execute(Commands.createGoToLocationCommand(model, locRef));
      } catch (final Exception e) {
        JOptionPane.showMessageDialog(myPanel.getParent(),
                                      e.getMessage(),
                                      "Go To Location",
                                      JOptionPane.ERROR_MESSAGE);
      }
    } else {
      JOptionPane.showMessageDialog(myPanel.getParent(),
                                    "Illegal location: " + text,
                                    "Go To Location",
                                    JOptionPane.ERROR_MESSAGE);
    }
  }
}
