package org.jetbrains.bio.browser.desktop;

import com.jidesoft.swing.AutoCompletion;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.ModelListener;
import org.jetbrains.bio.genome.query.GenomeQuery;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;

/**
 * @author Evgeny.Kurbatsky
 */
public class SearchPanel extends JPanel {
    private final DesktopGenomeBrowser myBrowser;

    private final JTextComponent myPositionComponent;
    private final ModelListener myModelListener;

    public SearchPanel(final DesktopGenomeBrowser browser) {
        myBrowser = browser;

        // Genome name & change button
        final JLabel genomeLabel = new JLabel();
        final GenomeQuery genomeQuery = myBrowser.getModel().getGenomeQuery();
        genomeLabel.setText(genomeQuery.getGenome().getDescription() + ' ' + genomeQuery.getBuild());
        add(genomeLabel);

        final Pair<JComboBox<String>, JTextComponent> comboBoxAndTextComponent = createPositionText();
        final JComboBox<String> positionText = comboBoxAndTextComponent.getFirst();
        myPositionComponent = comboBoxAndTextComponent.getSecond();
        add(positionText);
        positionText.setModel(new DefaultComboBoxModel<>(myBrowser.getLocationCompletion().toArray(new String[0])));

        // TODO: magic size numbers...
        positionText.setPreferredSize(new Dimension(600, 30));

        final JButton goButton = new JButton(new AbstractAction("Go") {
            @Override
            public void actionPerformed(@NotNull final ActionEvent e) {
                myBrowser.handlePositionChanged(myPositionComponent.getText());
            }
        });
        add(goButton);

        myModelListener = () -> setLocationText(myBrowser.getModel().toString());
        setBrowserModel(browser.getModel());

        // Init text
        setLocationText(myBrowser.getModel().toString());
    }

    public void setBrowserModel(final BrowserModel browserModel) {
        browserModel.addListener(myModelListener);
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
                    if (text.equals(myBrowser.getModel().toString())) {
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
