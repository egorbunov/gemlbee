package org.jetbrains.bio.browser.desktop;

import org.apache.log4j.Logger;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bio.browser.model.BrowserModel;
import org.jetbrains.bio.browser.model.ModelListener;
import org.jetbrains.bio.browser.query.desktop.LangTokenMaker;
import org.jetbrains.bio.browser.query.desktop.TrackNameListener;
import org.jetbrains.bio.genome.query.GenomeQuery;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Evgeny.Kurbatsky
 */
public class SearchPanel extends JPanel implements TrackNameListener {
    private final DesktopGenomeBrowser myBrowser;

    private final ModelListener myModelListener;
    private final RSyntaxTextArea queryText;

    private final DefaultCompletionProvider trackNamesCompletion = new DefaultCompletionProvider();
    private final DefaultCompletionProvider allCompletion = new DefaultCompletionProvider();

    public SearchPanel(final DesktopGenomeBrowser browser) {
        myBrowser = browser;

        // Genome name & change button
        final JLabel genomeLabel = new JLabel();
        final GenomeQuery genomeQuery = myBrowser.getModel().getGenomeQuery();
        genomeLabel.setText(genomeQuery.getGenome().getDescription() + ' ' + genomeQuery.getBuild());
        add(genomeLabel);

        queryText = createQueryText();
        add(queryText);

        // Setting up auto completions
        trackNamesCompletion.addCompletions(myBrowser.getTracksCompletion().stream()
                .map(it -> new BasicCompletion(trackNamesCompletion, it))
                .collect(Collectors.toList()));
        allCompletion.addCompletions(
                Stream.concat(myBrowser.getLocationCompletion().stream(), myBrowser.getTracksCompletion().stream())
                        .map(it -> new BasicCompletion(allCompletion, it))
                        .collect(Collectors.toList()));

        // Install auto completion
        AutoCompletion ac = new AutoCompletion(allCompletion);
        ac.install(queryText);

        final JButton goButton = new JButton(new AbstractAction("Go") {
            @Override
            public void actionPerformed(@NotNull final ActionEvent e) {
                myBrowser.handleAnyQuery(queryText.getText());
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

    protected RSyntaxTextArea createQueryText() {
        final RSyntaxTextArea queryText = new RSyntaxTextArea(1, 70); // TODO: magic numbers!
        queryText.setEditable(true);
        queryText.setToolTipText("Gene name, chromosome name or position in 'chrX:20-5000' format or " +
                "track-generating query");
        queryText.setBackground(Color.WHITE);
        queryText.setHighlightCurrentLine(false);

        // Setting up syntax highlighting
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping("text/GemlbeeQueryLanguage", LangTokenMaker.class.getName());
        queryText.setSyntaxEditingStyle("text/GemlbeeQueryLanguage");
        queryText.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground = Color.BLUE;
        queryText.getSyntaxScheme().getStyle(TokenTypes.LITERAL_BOOLEAN).foreground = Color.ORANGE;


        queryText.getDocument().addDocumentListener(new DocumentListener() {
            /**
             * Changing auto completion. Assumption: genome positions are all encoded with words
             * without any space character! (Caution: that may be wrong)
             * So auto completion for genome position would be shown if only
             * there is no space chars in current text
             */
            private void changeAutoCompletionStrategy() {
                if (queryText.getText().trim().split("\\s").length > 1) {
                    new AutoCompletion(trackNamesCompletion).install(queryText);
                } else {
                    new AutoCompletion(allCompletion).install(queryText);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changeAutoCompletionStrategy();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changeAutoCompletionStrategy();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changeAutoCompletionStrategy();
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

        // ESC handling
        queryText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "GBrowserReturnFocusToTracksList");
        queryText.getActionMap().put("GBrowserReturnFocusToTracksList", new AbstractAction() {
            @Override
            public void actionPerformed(@NotNull final ActionEvent e) {
                myBrowser.getController().getTrackListComponent().requestFocus();
            }
        });

        // ENTER handling
        queryText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ProcessQueryByEnterPress");
        queryText.getActionMap().put("ProcessQueryByEnterPress", new AbstractAction() {
            @Override
            public void actionPerformed(@NotNull final ActionEvent e) {
                myBrowser.handleAnyQuery(queryText.getText());
            }
        });

        return queryText;
    }

    public void setLocationText(final String text) {
        queryText.setText(text);
    }

    @Override
    public void addTrackName(@NotNull String name) {
        allCompletion.addCompletion(new BasicCompletion(allCompletion, name));
        trackNamesCompletion.addCompletion(new BasicCompletion(trackNamesCompletion, name));
    }

    @Override
    public void deleteTrackName(@NotNull String name) {
        allCompletion.removeCompletion(new BasicCompletion(allCompletion, name));
        trackNamesCompletion.removeCompletion(new BasicCompletion(trackNamesCompletion, name));
    }
}
