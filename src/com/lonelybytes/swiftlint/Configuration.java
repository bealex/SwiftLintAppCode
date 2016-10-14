package com.lonelybytes.swiftlint;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

public class Configuration implements Configurable {
    static final String KEY_SWIFTLINT = "SwiftLint";
    public static final String KEY_QUICK_FIX_ENABLED = "SwiftLintFixEnabled";
    private static final String DEFAULT_SWIFTLINT_PATH = "/usr/local/bin/swiftlint";

    private boolean modified = false;

    private TextFieldWithBrowseButton browser;

    private ConfigurationModifiedListener listener = new ConfigurationModifiedListener(this);

    private JBCheckBox chQuickFix;

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
        chQuickFix = new JBCheckBox("Enable \"Autocorrect\" quick-fix");
        browser = new TextFieldWithBrowseButton(txtPath);

        browser.addBrowseFolderListener("SwiftLint configuration", "Select path to SwiftLint executable", project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        browser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
        browser.getTextField().getDocument().addDocumentListener(listener);
        chQuickFix.addChangeListener(listener);
        row.add(lblPath);
        row.add(browser);
        panel.add(row);
        panel.add(chQuickFix);
        if (Properties.isEmpty(KEY_SWIFTLINT)) {
            File swiftLintFilePath = PathEnvironmentVariableUtil.findInPath("swiftlint");
            if (swiftLintFilePath != null) {
                Properties.set(KEY_SWIFTLINT, swiftLintFilePath.getAbsolutePath());
            }
        }
        if (Properties.isEmpty(KEY_QUICK_FIX_ENABLED)) {
            Properties.set(KEY_QUICK_FIX_ENABLED, true);
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
        Properties.set(KEY_QUICK_FIX_ENABLED, chQuickFix.isSelected());
        modified = false;
    }

    @Override
    public void reset() {
        browser.getTextField().setText(Properties.get(KEY_SWIFTLINT));
        chQuickFix.setSelected(Properties.getBoolean(KEY_QUICK_FIX_ENABLED));
        modified = false;
    }

    @Override
    public void disposeUIResources() {
        browser.getTextField().getDocument().removeDocumentListener(listener);
        chQuickFix.removeChangeListener(listener);
    }

    private static class ConfigurationModifiedListener implements DocumentListener, ChangeListener {
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

        @Override
        public void stateChanged(ChangeEvent e) {
            option.modified = true;
        }
    }
}