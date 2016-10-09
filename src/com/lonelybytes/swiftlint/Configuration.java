package com.lonelybytes.swiftlint;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.panels.VerticalLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class Configuration implements Configurable {
    static final String KEY_SWIFTLINT = "SwiftLint";

    private boolean modified = false;
    private JFilePicker filePicker;

    private ConfigurationModifiedListener listener = new ConfigurationModifiedListener(this);

    @Nls
    @Override
    public String getDisplayName() {
        return "SwiftLint";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel jPanel = new JPanel();

        VerticalLayout verticalLayout = new VerticalLayout(1, 2);
        jPanel.setLayout(verticalLayout);

        filePicker = new JFilePicker("SwiftLint binary path:", "...");
        filePicker.getTextField().getDocument().addDocumentListener(listener);

        jPanel.add(filePicker);

        reset();

        return jPanel;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        Properties.set(KEY_SWIFTLINT, filePicker.getTextField().getText());

        modified = false;
    }

    @Override
    public void reset() {
        filePicker.getTextField().setText(Properties.get(KEY_SWIFTLINT));

        modified = false;
    }

    @Override
    public void disposeUIResources() {
        filePicker.getTextField().getDocument().removeDocumentListener(listener);
    }

    private static class ConfigurationModifiedListener implements DocumentListener {
        private final Configuration option;

        ConfigurationModifiedListener(Configuration option) {
            this.option = option;
        }

        @Override
        public void insertUpdate(DocumentEvent documentEvent) {
            option.modified = true;
        }

        @Override
        public void removeUpdate(DocumentEvent documentEvent) {
            option.modified = true;
        }

        @Override
        public void changedUpdate(DocumentEvent documentEvent) {
            option.modified = true;
        }
    }
}