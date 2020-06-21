package com.lonelybytes.swiftlint;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
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
import java.io.IOException;

public class Configuration implements Configurable {
    public static final String KEY_SWIFTLINT = "SwiftLint";
    public static final String DEFAULT_SWIFTLINT_PATH = "/usr/local/bin/swiftlint";

    private boolean modified = false;

    private TextFieldWithBrowseButton browser;
    private TextFieldWithBrowseButton localAppPathBrowser;
    private JBCheckBox quickFixCheckbox;
    private JBCheckBox disableWhenNoConfigPresentCheckbox;

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
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        Project[] openProjects = projectManager.getOpenProjects();
        Project project = openProjects.length == 0 ? projectManager.getDefaultProject() : openProjects[0];

        JPanel panel = new JPanel(new VerticalLayout(2, SwingConstants.LEFT));

        // Global path

        JPanel row = new JPanel(new HorizontalLayout(20, SwingConstants.CENTER));

        JTextField pathTextField = new JTextField(30);
        JLabel pathLabel = new JLabel("Global SwiftLint path:");
        pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        browser = new TextFieldWithBrowseButton(pathTextField);
        browser.addBrowseFolderListener("SwiftLint State", "Select path to SwiftLint executable", project,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        browser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
        browser.getTextField().getDocument().addDocumentListener(listener);

        row.add(pathLabel);
        row.add(browser);
        panel.add(row);

        // Local path

        JTextField localPathTextField = new JTextField(30);
        JLabel localPathLabel = new JLabel("Per-project SwiftLint path:");
        localPathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        localAppPathBrowser = new TextFieldWithBrowseButton(localPathTextField);
        localAppPathBrowser.addBrowseFolderListener("SwiftLint State", "Select path to SwiftLint executable", project,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        localAppPathBrowser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
        localAppPathBrowser.getTextField().getDocument().addDocumentListener(listener);

        JPanel localPathRow = new JPanel(new HorizontalLayout(20, SwingConstants.CENTER));
        localPathRow.add(localPathLabel);
        localPathRow.add(localAppPathBrowser);
        panel.add(localPathRow);

        // Quick fix

        quickFixCheckbox = new JBCheckBox("Enable \"Autocorrect\" quick-fix");
        quickFixCheckbox.addChangeListener(listener);
        panel.add(quickFixCheckbox);

        // Disable when no config

        disableWhenNoConfigPresentCheckbox = new JBCheckBox("Disable when no .swiftlint.yml present");
        disableWhenNoConfigPresentCheckbox.addChangeListener(listener);
        panel.add(disableWhenNoConfigPresentCheckbox);

        reset();

        return panel;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() {
        SwiftLintInspection.State state = SwiftLintInspection.STATE;
        if (state == null) {
            state = new SwiftLintInspection.State();
        }

        state.setAppPath(browser.getText());
        state.setLocalAppPath(localAppPathBrowser.getText());
        state.setQuickFixEnabled(quickFixCheckbox.isSelected());
        state.setDisableWhenNoConfigPresent(disableWhenNoConfigPresentCheckbox.isSelected());

        modified = false;
    }

    @Override
    public void reset() {
        SwiftLintInspection.State state = SwiftLintInspection.STATE;

        String appPath = state.getAppPath();
        
        if (appPath == null || appPath.isEmpty()) {
            File swiftLintFilePath = PathEnvironmentVariableUtil.findInPath("swiftlint");
            if (swiftLintFilePath != null) {
                try {
                    browser.getTextField().setText(swiftLintFilePath.getCanonicalPath());
                } catch (IOException aE) {
                    browser.getTextField().setText(swiftLintFilePath.getAbsolutePath());
                    aE.printStackTrace();
                }
            } else {
                browser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
            }

            localAppPathBrowser.getTextField().setText(null);
            quickFixCheckbox.setSelected(true);
            disableWhenNoConfigPresentCheckbox.setSelected(false);
        } else {
            browser.getTextField().setText(appPath);
            localAppPathBrowser.getTextField().setText(state.getLocalAppPath());
            quickFixCheckbox.setSelected(state.isQuickFixEnabled());
            disableWhenNoConfigPresentCheckbox.setSelected(state.isDisableWhenNoConfigPresent());
        }

        modified = false;
    }

    @Override
    public void disposeUIResources() {
        browser.getTextField().getDocument().removeDocumentListener(listener);
        localAppPathBrowser.getTextField().getDocument().removeDocumentListener(listener);
        quickFixCheckbox.removeChangeListener(listener);
        disableWhenNoConfigPresentCheckbox.removeChangeListener(listener);
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