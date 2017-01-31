package com.lonelybytes.swiftlint;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
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

        browser = new TextFieldWithBrowseButton(txtPath);
        browser.addBrowseFolderListener("SwiftLint Configuration", "Select path to SwiftLint executable", project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        browser.getTextField().setText(DEFAULT_SWIFTLINT_PATH);
        browser.getTextField().getDocument().addDocumentListener(listener);

        row.add(lblPath);
        row.add(browser);

        chQuickFix = new JBCheckBox("Enable \"Autocorrect\" quick-fix");
        chQuickFix.addChangeListener(listener);

        panel.add(row);
        panel.add(chQuickFix);

        initializePreferences();

        reset();

        return panel;
    }

    private void initializePreferences() {
        if (STATE.appPath == null) {
            File swiftLintFilePath = PathEnvironmentVariableUtil.findInPath("swiftlint");
            if (swiftLintFilePath != null) {
                STATE.appPath = swiftLintFilePath.getAbsolutePath();
            } else {
                STATE.appPath = DEFAULT_SWIFTLINT_PATH;
            }
        }
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        STATE.appPath = browser.getText();
        STATE.quickFixEnabled = chQuickFix.isSelected();

        modified = false;
    }

    @Override
    public void reset() {
        browser.getTextField().setText(STATE.appPath);
        chQuickFix.setSelected(STATE.quickFixEnabled);

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

    @com.intellij.openapi.components.State(name = "com.appcodeplugins.swiftlint")
    @Storage(StoragePathMacros.WORKSPACE_FILE)
    static class State implements PersistentStateComponent<State> {
        public String appPath = null;
        public boolean quickFixEnabled = false;

        @Nullable
        @Override
        public State getState() {
            return STATE;
        }

        @Override
        public void loadState(State aState) {
            STATE = aState;
        }
    }

    static State STATE = new State();
}