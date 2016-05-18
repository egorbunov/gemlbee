package org.jetbrains.bio.browser.desktop;

import com.jidesoft.swing.AutoCompletion;
import kotlin.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.ModelListener;
import org.jetbrains.bio.browser.query.desktop.TrackNameListener;
import org.jetbrains.bio.genome.query.GenomeQuery;
import org.jetbrains.bio.query.parse.LangParser;
import org.jetbrains.bio.util.Lexeme;
import org.jetbrains.bio.util.Match;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;


/**
 * @author Evgeny.Kurbatsky
 */
public class SearchPanel extends JPanel implements TrackNameListener {
    private final Logger LOG = Logger.getLogger(SearchPanel.class);
    private final DesktopGenomeBrowser myBrowser;

    private final JTextComponent myPositionComponent;
    private final ModelListener myModelListener;
    private final JComboBox<String> queryText;

    private Set<String> autoCompletionSet;

    public SearchPanel(final DesktopGenomeBrowser browser) {
        myBrowser = browser;

        // Genome name & change button
        final JLabel genomeLabel = new JLabel();
        final GenomeQuery genomeQuery = myBrowser.getModel().getGenomeQuery();
        genomeLabel.setText(genomeQuery.getGenome().getDescription() + ' ' + genomeQuery.getBuild());
        add(genomeLabel);

        final Pair<JComboBox<String>, JTextComponent> comboBoxAndTextComponent = createPositionText();
        queryText = comboBoxAndTextComponent.getFirst();
        myPositionComponent = comboBoxAndTextComponent.getSecond();
        add(queryText);

        autoCompletionSet = new HashSet<>();
        autoCompletionSet.addAll(myBrowser.getLocationCompletion());
        autoCompletionSet.addAll(myBrowser.getTracksCompletion());
        queryText.setModel(new DefaultComboBoxModel<>(
                autoCompletionSet.toArray(new String[0]))
        );

        // TODO: magic size numbers...
        queryText.setPreferredSize(new Dimension(600, 30));

        final JButton goButton = new JButton(new AbstractAction("Go") {
            @Override
            public void actionPerformed(@NotNull final ActionEvent e) {
                myBrowser.handleAnyQuery(myPositionComponent.getText());
            }
        });
        add(goButton);

        myModelListener = () -> setLocationText(myBrowser.getModel().toString());
        setBrowserModel(browser.getModel());

        // Init text
        setLocationText(myBrowser.getModel().toString());

        // Listening for new tracks names added
        browser.addTrackNameListener(this);
    }

    public void setBrowserModel(final BrowserModel browserModel) {
        browserModel.addListener(myModelListener);
    }

    protected Pair<JComboBox<String>, JTextComponent> createPositionText() {
        final JComboBox<String> queryText = new JComboBox<>();
        queryText.setEditable(true);
        queryText.setToolTipText("Gene name, chromosome name or position in 'chrX:20-5000' format or " +
                "track-generating query");

        // Install autocompletion
        final AutoCompletion autoCompletion = new AutoCompletion(queryText);
        autoCompletion.setStrict(false);

        final JTextComponent textEditorComponent = (JTextComponent) queryText.getEditor().getEditorComponent();
        // Handle position changed action
        queryText.addActionListener(e -> {
            // This action fires on autocompletion for first matcher result while typing,
            // so let's try to distinguish when user really wants to change position from
            // moment when he just typing/looking through completion list

            final Object selectedItem = queryText.getSelectedItem();
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
                    myBrowser.handleOnlyPositionChanged(text);
                }
            }
        });

        textEditorComponent.getDocument().addDocumentListener(new DocumentListener() {
            void highlight() {
                final HashSet<Lexeme> kws = new HashSet<Lexeme>() {{
                    add(LangParser.Keywords.INSTANCE.getASSIGN());
                    add(LangParser.Keywords.INSTANCE.getAND());
                    add(LangParser.Keywords.INSTANCE.getOR());
                    add(LangParser.Keywords.INSTANCE.getNOT());
                    add(LangParser.Keywords.INSTANCE.getIF());
                    add(LangParser.Keywords.INSTANCE.getELSE());
                    add(LangParser.Keywords.INSTANCE.getTHEN());
                    add(LangParser.Keywords.INSTANCE.getSHOW());
                    add(LangParser.Keywords.INSTANCE.getTRUE());
                    add(LangParser.Keywords.INSTANCE.getFALSE());
                }};

                java.util.List<Match> matches = LangParser.Companion.getMatches(
                        textEditorComponent.getText(),
                        kws
                );
                // highlight all characters that appear in charsToHighlight
                Highlighter h = textEditorComponent.getHighlighter();
                h.removeAllHighlights();

                for (Match match : matches) {
                    try {
                        h.addHighlight(match.getStart(), match.getEnd(),
                                new DefaultHighlighter.DefaultHighlightPainter(Color.lightGray));
                    } catch (BadLocationException e) {
                        LOG.trace(e);
                    }
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                highlight();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                highlight();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                highlight();
            }
        });

        // Hide arrow button
        final Component[] components = queryText.getComponents();
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

        return new Pair<>(queryText, textEditorComponent);
    }

    public void setLocationText(final String text) {
        myPositionComponent.setText(text);
    }

    @Override
    public void addTrackName(String name) {
        autoCompletionSet.add(name);
        queryText.setModel(new DefaultComboBoxModel<>(autoCompletionSet.toArray(new String[0])));
//        final AutoCompletion autoCompletion = new AutoCompletion(queryText);
//        autoCompletion.setStrict(false);
    }

    @Override
    public void deleteTrackName(String name) {
        autoCompletionSet.remove(name);
        queryText.setModel(new DefaultComboBoxModel<>(autoCompletionSet.toArray(new String[0])));
//        final AutoCompletion autoCompletion = new AutoCompletion(queryText);
//        autoCompletion.setStrict(false);
    }
}
