package com.lonelybytes.swiftlint

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class Configuration(val project: Project) : Configurable {
    private var modified = false

    private var swiftLintPathBrowser: TextFieldWithBrowseButton = TextFieldWithBrowseButton(JTextField(30))
    private var quickFixCheckbox: JBCheckBox = JBCheckBox("Show \"Run swiftlint autocorrect\" option on lint error popup")
    private var disableWhenNoConfigPresentCheckbox: JBCheckBox = JBCheckBox("Disable when no .swiftlint.yml present")

    private val listener = ConfigurationModifiedListener(this)
    private val state: SwiftLintInspection.State = SwiftLintInspection.State(project)

    @Nls
    override fun getDisplayName(): String = "SwiftLint"
    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        val panel = JPanel(VerticalLayout(2, SwingConstants.LEFT))

        // Global path
        val pathLabel = JLabel("SwiftLint binary path:", SwingConstants.RIGHT)
        swiftLintPathBrowser.addBrowseFolderListener(
            "SwiftLint", "Select path to SwiftLint executable", project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        swiftLintPathBrowser.textField.text = DEFAULT_SWIFTLINT_PATH
        swiftLintPathBrowser.textField.document.addDocumentListener(listener)

        val globalPathRow = JPanel(HorizontalLayout(20, SwingConstants.HORIZONTAL))
        globalPathRow.add(pathLabel)
        globalPathRow.add(swiftLintPathBrowser)
        panel.add(globalPathRow)

        // Quick fix
        quickFixCheckbox.addChangeListener(listener)
        panel.add(quickFixCheckbox)

        // Disable when no config
        disableWhenNoConfigPresentCheckbox.addChangeListener(listener)
        panel.add(disableWhenNoConfigPresentCheckbox)

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        return modified
    }

    override fun apply() {
        state.projectSwiftLintPath = swiftLintPathBrowser.text
        state.isQuickFixEnabled = quickFixCheckbox.isSelected
        state.isDisableWhenNoConfigPresent = disableWhenNoConfigPresentCheckbox.isSelected
        modified = false
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun reset() {
        swiftLintPathBrowser.textField.text = state.projectOrGlobalSwiftLintPath
        quickFixCheckbox.isSelected = state.isQuickFixEnabled
        disableWhenNoConfigPresentCheckbox.isSelected = state.isDisableWhenNoConfigPresent
        modified = false
    }

    override fun disposeUIResources() {
        swiftLintPathBrowser.textField.document.removeDocumentListener(listener)
        quickFixCheckbox.removeChangeListener(listener)
        disableWhenNoConfigPresentCheckbox.removeChangeListener(listener)
    }

    private class ConfigurationModifiedListener(private val option: Configuration) : DocumentListener, ChangeListener {
        override fun insertUpdate(documentEvent: DocumentEvent) {
            option.modified = true
        }

        override fun removeUpdate(documentEvent: DocumentEvent) {
            option.modified = true
        }

        override fun changedUpdate(documentEvent: DocumentEvent) {
            option.modified = true
        }

        override fun stateChanged(e: ChangeEvent) {
            option.modified = true
        }
    }

    companion object {
        const val KEY_SWIFTLINT = "SwiftLint"
        const val DEFAULT_SWIFTLINT_PATH = "/usr/local/bin/swiftlint"
    }
}