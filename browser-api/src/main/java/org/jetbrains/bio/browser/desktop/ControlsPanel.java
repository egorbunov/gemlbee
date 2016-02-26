package org.jetbrains.bio.browser.desktop;

import com.jidesoft.swing.AutoCompletion;
import kotlin.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.ModelListener;
import org.jetbrains.bio.browser.util.TrackUIUtil;
import org.jetbrains.bio.genome.query.GenomeQuery;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * @author Evgeny.Kurbatsky
 */
public class ControlsPanel extends JPanel {
  private static final Logger LOG = Logger.getLogger(ControlsPanel.class);

  private final DesktopGenomeBrowser myBrowser;

  private final JTextComponent myPositionComponent;
  private final ModelListener myModelListener;


  public ControlsPanel(final DesktopGenomeBrowser browser) {
    myBrowser = browser;

    // Genome name & change button
    final JLabel genomeLabel = new JLabel();
    final GenomeQuery genomeQuery = myBrowser.getBrowserModel().getGenomeQuery();
    genomeLabel.setText(genomeQuery.getGenome().getDescription() + ' ' + genomeQuery.getBuild());
    add(genomeLabel);

    final Pair<JComboBox<String>, JTextComponent> comboBoxAndTextComponent = createPositionText();
    final JComboBox<String> positionText = comboBoxAndTextComponent.getFirst();
    myPositionComponent = comboBoxAndTextComponent.getSecond();
    add(positionText);
    positionText.setModel(new DefaultComboBoxModel<>(myBrowser.getLocationCompletion().toArray(new String[0])));

    final JButton goButton = new JButton(new AbstractAction("Go") {
      @Override
      public void actionPerformed(@NotNull final ActionEvent e) {
        myBrowser.handlePositionChanged(myPositionComponent.getText());
      }
    });
    add(goButton);

    // Back forward icons are downloaded from here: http://www.myiconfinder.com/
    try {
      final BufferedImage backImage = ImageIO.read(ControlsPanel.class.getResource("/back.png"));
      final BufferedImage forwardImage = ImageIO.read(ControlsPanel.class.getResource("/forward.png"));
      final int h = TrackUIUtil.DEFAULT_FONT_HEIGHT - 2;
      final JButton backButton = new JButton(new ImageIcon(backImage.getScaledInstance(h, h, 0)));
      backButton.setMaximumSize(new Dimension(h, h));
      backButton.addActionListener(e -> myBrowser.getController().undo());
      add(backButton);
      final JButton forwardButton = new JButton(new ImageIcon(forwardImage.getScaledInstance(h, h, 0)));
      forwardButton.setMaximumSize(new Dimension(h, h));
      forwardButton.addActionListener(e -> myBrowser.getController().redo());
      add(forwardButton);
    } catch (IOException e) {
      LOG.error(e);
    }

    myModelListener = () -> setLocationText(myBrowser.getBrowserModel().presentableName());
    setBrowserModel(browser.getBrowserModel());

    // Init text
    setLocationText(myBrowser.getBrowserModel().presentableName());
  }

  public void setBrowserModel(final BrowserModel browserModel) {
    browserModel.addModelListener(myModelListener);
  }

  protected Pair<JComboBox<String>, JTextComponent> createPositionText() {
    final JComboBox<String> positionText = new JComboBox<>();
    positionText.setEditable(true);
    positionText.setToolTipText("Gene name, chromosome name or position in 'chrX:20-5000' format");

    // Install autocompletion
    final AutoCompletion autoCompletion = new AutoCompletion(positionText);
    autoCompletion.setStrict(false);

    final JTextComponent textEditorComponent = (JTextComponent) positionText.getEditor().getEditorComponent();
    // Handle position changed action
    positionText.addActionListener(e -> {
      // This action fires on autocompletion for first matcher result while typing,
      // so let's try to distinguish when user really wants to change position from
      // moment when he just typing/looking through completion list

      final Object selectedItem = positionText.getSelectedItem();
      final String text = textEditorComponent.getText();

      // if completion list item equals to selected text
      // * a. User has chosen it in completion popup
      // * b. User typed something which matches to completion list item
      //     ** Single match => ok, e.g "sox2"
      //     ** Multi match and user probably want smth longer, e.g "sox10" instead of "sox1"
      // * c. It's on location changed call back result, it sets current range after "go to" action
      //
      // "comboBoxEdited" event name allow as to distinguish autocompletion/callback from
      // the case when user pressed ENTER
      if (text != null && text.equals(selectedItem)) {
        final boolean changePosition = "comboBoxEdited".equals(e.getActionCommand());
        if (changePosition) {
          if (text.equals(myBrowser.getBrowserModel().presentableName())) {
            // relax guys, it's a fake call back, no action is required
            return;
          }

          // clear selection
          textEditorComponent.setSelectionStart(0);
          textEditorComponent.setSelectionEnd(0);
          myBrowser.handlePositionChanged(text);
        }
      }
    });


    // Hide arrow button
    final Component[] components = positionText.getComponents();
    for (final Component component : components) {
      if (component instanceof JButton) {
        component.setVisible(false);
        break;
      }
    }

    // Select text on focus gained
    // Linux-specific fix, on MacOS all works without it
    textEditorComponent.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(final FocusEvent e) {
        textEditorComponent.setSelectionStart(0);
        textEditorComponent.setSelectionEnd(textEditorComponent.getText().length());
      }

      @Override
      public void focusLost(final FocusEvent e) {
        textEditorComponent.setSelectionStart(0);
        textEditorComponent.setSelectionEnd(0);
      }
    });

    // Additional actions
    final InputMap inputMap = textEditorComponent.getInputMap();
    // Auto-completion by Control+Space
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, ActionEvent.CTRL_MASK),
                 inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
    // ESC handling
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "GBrowserReturnFocusToTracksList");
    textEditorComponent.getActionMap().put("GBrowserReturnFocusToTracksList", new AbstractAction() {
      @Override
      public void actionPerformed(@NotNull final ActionEvent e) {
        myBrowser.getController().getTrackListComponent().requestFocus();
      }
    });

    return new Pair<>(positionText, textEditorComponent);
  }

  public void setLocationText(final String text) {
    myPositionComponent.setText(text);
  }

}
