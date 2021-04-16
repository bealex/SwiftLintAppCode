package com.lonelybytes.swiftlint.annotator

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException
import com.jetbrains.swift.psi.SwiftFunctionDeclaration
import com.jetbrains.swift.psi.SwiftParameter
import com.jetbrains.swift.psi.SwiftVariableDeclaration
import com.lonelybytes.swiftlint.Configuration
import com.lonelybytes.swiftlint.SwiftLint
import com.lonelybytes.swiftlint.SwiftLintConfig
import com.lonelybytes.swiftlint.SwiftLintInspection
import java.io.IOException
import java.util.*
import java.util.function.Consumer

class SwiftLintHighlightingAnnotator : ExternalAnnotator<InitialInfo?, AnnotatorResult?>() {
    override fun collectInformation(aFile: PsiFile): InitialInfo? {
        if (!aFile.isWritable) return null
        val filePath: String = aFile.virtualFile.canonicalPath ?: return null
        val document: Document = FileDocumentManager.getInstance().getDocument(aFile.virtualFile) ?: return null
        if (document.lineCount == 0 || !shouldCheck(aFile)) return null

        val swiftLintConfigPath: String? = SwiftLintConfig.swiftLintConfigPath(aFile.project, 5)
        if (SwiftLintInspection.State(aFile.project).isDisableWhenNoConfigPresent && swiftLintConfigPath == null) {
            return null
        }

        if (swiftLintConfig == null) {
            swiftLintConfig = SwiftLintConfig(aFile.project, swiftLintConfigPath)
        }
        return InitialInfo(aFile, filePath, document, true)
    }

    override fun doAnnotate(collectedInfo: InitialInfo?): AnnotatorResult? {
        collectedInfo ?: return null
        if (!collectedInfo.shouldProcess) return null

        collectedInfo.document?.let {
            ApplicationManager.getApplication().invokeLater { FileDocumentManager.getInstance().saveDocument(it) }
        }

        val toolPath: String = SwiftLintInspection.State(collectedInfo.file.project).projectOrGlobalSwiftLintPath
        val lines: MutableList<AnnotatorResult.Line> = ArrayList()
        try {
            val lintedErrors: List<String> = SWIFT_LINT.executeSwiftLint(toolPath, "lint", swiftLintConfig, collectedInfo.path)
            if (lintedErrors.isNotEmpty()) {
                for (line in lintedErrors) {
                    var lineLocal = line
                    var newLine = lineLocal.replace("\"(.*),(.*)\"".toRegex(), "\"$1|||$2\"")
                    while (newLine != lineLocal) {
                        lineLocal = newLine
                        newLine = lineLocal.replace("\"(.*),(.*)\"".toRegex(), "\"$1|||$2\"")
                    }
                    var lineParts: List<String> = lineLocal
                            .split("\\s*,\\s*".toRegex())
                            .map { it.replace("|||", ",") }
                    if (lineParts.isEmpty() || !lineLocal.contains(",") || lineLocal.trim { it <= ' ' }.isEmpty()) {
                        continue
                    }
                    if (lineParts[0] == "file") {
                        lineParts = lineParts.subList(7, lineParts.size)
                    }
                    while (lineParts.size >= 7) {
                        val parts = lineParts.subList(0, 7)
                        lineParts = lineParts.subList(7, lineParts.size)
                        lines.add(AnnotatorResult.Line(parts))
                    }
                }
            }
        } catch (ex: IOException) {
            if (ex.message!!.contains("No such file or directory") || ex.message!!.contains("error=2")) {
                Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error", "Can't find swiftlint utility here:\n$toolPath\nPlease check the path in settings.", NotificationType.ERROR))
            } else {
                Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.message, NotificationType.ERROR))
            }
        } catch (ex: Exception) {
            Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error", "Exception: " + ex.message, NotificationType.INFORMATION))
            ex.printStackTrace()
        }
        return AnnotatorResult(lines)
    }

    fun highlightInfos(aFile: PsiFile, aResult: AnnotatorResult?): List<HighlightInfo?> {
        if (aResult == null) {
            return emptyList<HighlightInfo>()
        }
        val document: Document = FileDocumentManager.getInstance().getDocument(aFile.virtualFile)
                ?: return emptyList<HighlightInfo>()
        val result: MutableList<HighlightInfo?> = ArrayList()
        for (line in aResult.lines) {
            line.fixPositionInDocument(document)
            result.add(processErrorLine(aFile, document, line))
        }
        return result
    }

    override fun apply(aFile: PsiFile, annotationResult: AnnotatorResult?, aHolder: AnnotationHolder) {
        highlightInfos(aFile, annotationResult).forEach(Consumer { aHighlightInfo: HighlightInfo? ->
            if (aHighlightInfo != null) {
                val highlightRange: TextRange = TextRange.from(aHighlightInfo.startOffset, aHighlightInfo.endOffset - aHighlightInfo.startOffset)
                var annotationBuilder = aHolder
                        .newAnnotation(aHighlightInfo.severity, aHighlightInfo.description)
                        .range(highlightRange)
                if (SwiftLintInspection.State(aFile.project).isQuickFixEnabled) {
                    annotationBuilder
                            .withFix(object : IntentionAction {
                                override fun getText(): String {
                                    return QUICK_FIX_NAME
                                }

                                override fun getFamilyName(): String {
                                    return SHORT_NAME
                                }

                                override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
                                    return true
                                }

                                @Throws(IncorrectOperationException::class)
                                override operator fun invoke(project: Project, editor: Editor, file: PsiFile) {
                                    executeSwiftLintQuickFix(file)
                                }

                                override fun startInWriteAction(): Boolean = false
                            }).also { annotationBuilder = it }
                }
                annotationBuilder.create()
            }
        })
    }

    // Process SwiftLint output
    private fun processErrorLine(aFile: PsiFile, aDocument: Document, aLine: AnnotatorResult.Line): HighlightInfo? {
        val lineNumber = aLine.line
        val columnNumber = aLine.column
        var startOffset = aDocument.getLineStartOffset(lineNumber)
        val endOffset = if (lineNumber < aDocument.lineCount - 1) aDocument.getLineStartOffset(lineNumber + 1) else aDocument.getLineEndOffset(lineNumber)
        var range: TextRange = TextRange.create(startOffset, endOffset)
        val weHaveAColumn = columnNumber > 0
        if (weHaveAColumn) {
            startOffset = (aDocument.textLength - 1).coerceAtMost(startOffset + columnNumber - 1)
        }
        val chars = aDocument.immutableCharSequence
        if (chars.length <= startOffset) {
            // This can happen when we browsing a file after it has been edited (some lines removed for example)
            return null
        }
        val startChar = chars[startOffset]
        val startPsiElement: PsiElement = aFile.findElementAt(startOffset) ?: return null
        val startNode: ASTNode? = startPsiElement.node
        val isErrorInLineComment = startNode != null && startNode.elementType.toString() == "EOL_COMMENT"
        var severity: HighlightSeverity = severityFromSwiftLint(aLine.severity)

        // Rules: https://github.com/realm/SwiftLint/blob/master/Rules.md
        if (isErrorInLineComment) {
            range = TextRange.create(aDocument.getLineStartOffset(lineNumber), aDocument.getLineEndOffset(lineNumber))
        } else {
            val isErrorNewLinesOnly = startChar == '\n'
            var isErrorInSymbol = !Character.isLetterOrDigit(startChar) && !Character.isWhitespace(startChar)
            isErrorInSymbol = isErrorInSymbol or (aLine.rule == "opening_brace" || aLine.rule == "colon")
            if (!isErrorInSymbol) {
                if (!isErrorNewLinesOnly && weHaveAColumn) {
                    // SwiftLint returns column for the previous non-space token, not the erroneous one. Let's try to correct it.
                    when (aLine.rule) {
                        "return_arrow_whitespace" -> {
                            val psiElement: PsiElement? = nextElement(aFile, startOffset, true)
                            range = if (psiElement != null) psiElement.textRange else range
                        }
                        "unused_closure_parameter" -> {
                            aFile.findElementAt(startOffset)?.let { range = it.textRange }
                        }
                        "redundant_void_return" -> {
                            aFile.findElementAt(startOffset)?.nextSibling?.let { range = it.textRange }
                        }
                        "let_var_whitespace", "syntactic_sugar", "dynamic_inline" -> {
                            aFile.findElementAt(startOffset)?.parent?.let { range = it.textRange }
                        }
                        "variable_name", "identifier_name" -> {
                            range = findVarInDefinition(aFile, startOffset) ?: range
                        }
                        "type_name" -> {
                            aFile.findElementAt(startOffset)?.let {
                                range =
                                        if ((it as LeafPsiElement).elementType.toString() == "IDENTIFIER")
                                            it.textRange
                                        else
                                            getNextTokenAtIndex(aFile, startOffset, aLine.rule) ?: range
                            }
                        }
                        "force_cast", "operator_whitespace", "shorthand_operator", "single_test_class", "implicitly_unwrapped_optional" -> {
                            range = getNextTokenAtIndex(aFile, startOffset, aLine.rule) ?: range
                        }
                        "private_unit_test", "private_outlet", "override_in_extension" -> {
                            prevElement(aFile, startOffset)?.let { range = it.textRange }
                        }
                        "redundant_optional_initialization" -> {
                            getNextTokenAtIndex(aFile, startOffset, aLine.rule)?.let { range = getNextTokenAtIndex(aFile, it.endOffset, aLine.rule) ?: range }
                        }
                        "trailing_closure" -> {
                            getNextTokenAtIndex(aFile, startOffset, aLine.rule)?.let { range = getNextTokenAtIndex(aFile, it.endOffset, aLine.rule) ?: range }
                            aFile.findElementAt(range.startOffset)?.let { range = it.parent?.textRange ?: range }
                        }
                        else -> {
                            aFile.findElementAt(startOffset)?.let {
                                range = if (it.node is PsiWhiteSpace) {
                                    getNextTokenAtIndex(aFile, startOffset, aLine.rule) ?: range
                                } else {
                                    it.textRange
                                }
                            }
                        }
                    }
                } else if (isErrorNewLinesOnly) {
                    when (aLine.rule) {
                        "superfluous_disable_command" -> {
                            aFile.findElementAt(startOffset - 1)?.let { range = it.textRange }
                        }
                        "prohibited_super_call", "overridden_super_call", "empty_enum_arguments", "empty_parameters" -> {
                            aFile.findElementAt(startOffset)?.node?.treeParent?.psi?.let { range = it.textRange }
                        }
                        else -> {
                            // Let's select all empty lines here, we need to show that something is wrong with them
                            range = getEmptyLinesAroundIndex(aDocument, startOffset)
                        }
                    }
                }
            } else {
                when (aLine.rule) {
                    "empty_enum_arguments", "empty_parentheses_with_trailing_closure", "let_var_whitespace", "empty_parameters" -> {
                        aFile.findElementAt(startOffset)?.node?.treeParent?.psi?.let { range = it.textRange }
                    }
                    "return_arrow_whitespace" -> {
                        aFile.findElementAt(startOffset)?.let { range = it.parent.textRange }
                    }
                    "trailing_semicolon" -> {
                        aFile.findElementAt(startOffset)?.let { range = it.textRange }
                    }
                    else -> {
                        aFile.findElementAt(startOffset)?.let { element ->
                            when {
                                aLine.rule == "colon" -> {
                                    getNextTokenAtIndex(aFile, startOffset, aLine.rule)?.let { range = it }
                                }
                                element.text == "-" -> {
                                    element.parent.parent?.let {
                                        range = it.textRange
                                    }
                                }
                                else -> {
                                    range = element.textRange
                                }
                            }
                        }
                    }
                }
            }
            if (aLine.rule == "opening_brace" && Character.isWhitespace(startChar)) {
                range = getNextTokenAtIndex(aFile, startOffset, aLine.rule) ?: range
            }
            if (aLine.rule == "valid_docs") {
                range = prevElement(aFile, startOffset)?.textRange ?: range
            }
            if (aLine.rule == "trailing_newline" && !weHaveAColumn && chars[chars.length - 1] != '\n') {
                severity = HighlightSeverity.ERROR
                range = TextRange.create(endOffset - 1, endOffset)
            }
            if (isErrorNewLinesOnly) {
                // Sometimes we need to highlight several returns. Usual error highlighting will not work in this case
                severity = HighlightSeverity.WARNING
            }
        }

        return HighlightInfo
                .newHighlightInfo(HighlightInfo.convertSeverity(severity))
                .range(range)
                .descriptionAndTooltip("" + aLine.message.trim { it <= ' ' } + " (SwiftLint: " + aLine.rule + ")")
                .create()
    }

    private fun getEmptyLinesAroundIndex(aDocument: Document, aInitialIndex: Int): TextRange {
        val chars = aDocument.immutableCharSequence
        var from = aInitialIndex
        while (from >= 0) {
            if (!Character.isWhitespace(chars[from])) {
                from += 1
                break
            }
            from -= 1
        }

        var to = aInitialIndex
        while (to < chars.length) {
            if (!Character.isWhitespace(chars[to])) {
                to -= 1
                break
            }
            to += 1
        }

        from = 0.coerceAtLeast(from)
        if (from > 0 && chars[from] == '\n') {
            from += 1
        }
        while (to > 0 && chars[to - 1] != '\n') {
            to -= 1
        }
        to = from.coerceAtLeast(to)
        return TextRange(from, to)
    }

    private fun getNextTokenAtIndex(file: PsiFile, aCharacterIndex: Int, aErrorType: String): TextRange? {
        var result: TextRange? = null
        var psiElement: PsiElement?
        try {
            psiElement = file.findElementAt(aCharacterIndex)
            if (psiElement != null) {
                if (";" == psiElement.text || aErrorType == "variable_name" && psiElement.node.elementType.toString() == "IDENTIFIER") {
                    result = psiElement.textRange
                } else {
                    result = psiElement.textRange
                    psiElement = nextElement(file, aCharacterIndex, false)
                    if (psiElement != null) {
                        result = if (psiElement.context != null && psiElement.context?.node?.elementType.toString() == "OPERATOR_SIGN") {
                            psiElement.context?.node?.textRange
                        } else {
                            psiElement.textRange
                        }
                    }
                }
            }
        } catch (aE: ProcessCanceledException) {
            // Do nothing
        } catch (aE: Exception) {
            aE.printStackTrace()
        }

        return result
    }

    private fun findVarInDefinition(file: PsiFile, aCharacterIndex: Int): TextRange? {
        var result: TextRange? = null
        var psiElement: PsiElement?
        try {
            psiElement = file.findElementAt(aCharacterIndex)
            if (psiElement != null && (psiElement as LeafPsiElement).elementType.toString() == "IDENTIFIER") {
                result = psiElement.getTextRange()
            } else {
                while (psiElement != null &&
                        psiElement !is SwiftVariableDeclaration &&
                        psiElement !is SwiftFunctionDeclaration &&
                        psiElement !is SwiftParameter) {
                    psiElement = psiElement.parent
                }

                if (psiElement != null) {
                    result = when (psiElement) {
                        is SwiftVariableDeclaration -> psiElement.variables[0].node.textRange
                        is SwiftFunctionDeclaration -> psiElement.nameIdentifier?.textRange
                        is SwiftParameter -> psiElement.node.textRange
                        else -> psiElement.node.textRange
                    }
                }

                if (result == null) {
                    result = file.findElementAt(aCharacterIndex)?.textRange
                }
            }
        } catch (aE: ProcessCanceledException) {
            // Do nothing
        } catch (aE: Exception) {
            aE.printStackTrace()
        }

        return result
    }

    private fun nextElement(aFile: PsiFile, aElementIndex: Int, isWhitespace: Boolean): PsiElement? {
        var nextElement: PsiElement? = null
        val initialElement: PsiElement? = aFile.findElementAt(aElementIndex)
        if (initialElement != null) {
            var index: Int = aElementIndex + initialElement.textLength
            nextElement = aFile.findElementAt(index)
            while (nextElement != null && (nextElement === initialElement ||
                            !isWhitespace && nextElement is PsiWhiteSpace ||
                            isWhitespace && nextElement !is PsiWhiteSpace)) {
                index += nextElement.textLength
                nextElement = aFile.findElementAt(index)
            }
        }
        return nextElement
    }

    private fun prevElement(aFile: PsiFile, aElementIndex: Int): PsiElement? {
        var nextElement: PsiElement? = null
        val initialElement: PsiElement? = aFile.findElementAt(aElementIndex)
        if (initialElement != null) {
            var index: Int = initialElement.textRange.startOffset - 1
            nextElement = aFile.findElementAt(index)
            while (nextElement != null && (nextElement === initialElement || nextElement is PsiWhiteSpace)) {
                index = nextElement.textRange.startOffset - 1
                nextElement = if (index >= 0) {
                    aFile.findElementAt(index)
                } else {
                    break
                }
            }
        }
        return nextElement
    }

    private fun shouldCheck(aFile: PsiFile): Boolean {
        val isSwift = "swift".equals(aFile.virtualFile.extension, ignoreCase = true)
        val isInProject: Boolean = ProjectFileIndex.SERVICE.getInstance(aFile.project).isInSource(aFile.virtualFile)
        return isSwift && isInProject
    }

    // QuickFix
    private fun executeSwiftLintQuickFix(file: PsiFile) {
        val virtualFile: VirtualFile = file.virtualFile
        val toolPath: String = SwiftLintInspection.State(file.project).projectOrGlobalSwiftLintPath
        val filePath: String = virtualFile.canonicalPath ?: return
        val name = "$SWIFT_LINT $QUICK_FIX_NAME"
        val commandProcessor = CommandProcessor.getInstance()
        val action = Runnable {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    SWIFT_LINT.executeSwiftLint(toolPath, "autocorrect", swiftLintConfig, filePath)
                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            file.virtualFile.refresh(false, false)
                        }
                    }
                } catch (e: Exception) {
                    Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error",
                            """
                            Can't quick-fix.
                            Exception: ${e.message}
                            """.trimIndent(), NotificationType.ERROR))
                    e.printStackTrace()
                }
            }
        }
        commandProcessor.executeCommand(file.project, action, name, ActionGroup.EMPTY_GROUP)
    }

    companion object {
        private var swiftLintConfig: SwiftLintConfig? = null
        private val SWIFT_LINT: SwiftLint = SwiftLint()
        private const val SHORT_NAME = "SwiftLint"
        private const val QUICK_FIX_NAME = "Run swiftlint autocorrect"

        private fun severityFromSwiftLint(severity: String): HighlightSeverity {
            return when (severity.trim { it <= ' ' }.toLowerCase()) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                "style", "performance", "portability" -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.INFORMATION
            }
        }
    }
}