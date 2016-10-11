package com.lonelybytes.swiftlint;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

public class Configuration implements Configurable {
    static final String KEY_SWIFTLINT = "SwiftLint";
    private static final String DEFAULT_SWIFTLINT_PATH = "/usr/local/bin/swiftlint";

    private boolean modified = false;

    private TextFieldWithBrowseButton browser;

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
        JPanel panel = new JPanel(new VerticalLayout(2, SwingConstants.LEFT));
        JPanel row = new JPanel(new HorizontalLayout(20, SwingConstants.CENTER));
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        Project[] openProjects = projectManager.getOpenProjects();
        Project project = openProjects.length == 0 ? projectManager.getDefaultProject() : openProjects[0];

        JTextField txtPath = new JTextField(30);
        JLabel lblPath = new JLabel("SwiftLint path:");
        browser = new TextFieldWithBrowseButton(txtPath);

        browser.addBrowseFolderListener("SwiftLint configuration", "Select path to SwiftLint executable", project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        browser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
        browser.getTextField().getDocument().addDocumentListener(listener);

        row.add(lblPath);
        row.add(browser);
        panel.add(row);

        if (Properties.isEmpty(KEY_SWIFTLINT)) {
            File swiftLintFilePath = PathEnvironmentVariableUtil.findInPath("swiftlint");
            if (swiftLintFilePath != null) {
                Properties.set(KEY_SWIFTLINT, swiftLintFilePath.getAbsolutePath());
            }
        }
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        Properties.set(KEY_SWIFTLINT, browser.getText());
        modified = false;
    }

    @Override
    public void reset() {
        browser.getTextField().setText(Properties.get(KEY_SWIFTLINT));
        modified = false;
    }

    @Override
    public void disposeUIResources() {
        browser.getTextField().getDocument().removeDocumentListener(listener);
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