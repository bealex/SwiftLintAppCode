package com.lonelybytes.swiftlint

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.*
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.lonelybytes.swiftlint.annotator.AnnotatorResult
import com.lonelybytes.swiftlint.annotator.SwiftLintHighlightingAnnotator
import org.jetbrains.annotations.Nls
import java.io.IOException

class SwiftLintInspection : GlobalSimpleInspectionTool() {
    class State(private val project: Project) {
        var isQuickFixEnabled: Boolean
            get() = PropertiesComponent.getInstance(project).getBoolean(QUICK_FIX_ENABLED)
            set(value) = PropertiesComponent.getInstance(project).setValue(QUICK_FIX_ENABLED, value)

        var isDisableWhenNoConfigPresent: Boolean
            get() = PropertiesComponent.getInstance(project).getBoolean(DISABLE_WHEN_NO_CONFIG_PRESENT)
            set(value) = PropertiesComponent.getInstance(project).setValue(DISABLE_WHEN_NO_CONFIG_PRESENT, value)

        var projectSwiftLintPath: String?
            get() = PropertiesComponent.getInstance(project).getValue(LOCAL_APP_NAME)
            set(aAppPath) = PropertiesComponent.getInstance(project).setValue(LOCAL_APP_NAME, aAppPath)

        val projectOrGlobalSwiftLintPath: String
            get() = projectSwiftLintPath ?: foundSwiftLintPath

        companion object {
            private const val PREFIX = "com.appcodeplugins.swiftlint"
            private const val VERSION = "v1_7"

            private const val LOCAL_APP_NAME = "$PREFIX.$VERSION.localAppName"
            private const val QUICK_FIX_ENABLED = "$PREFIX.$VERSION.quickFixEnabled"
            private const val DISABLE_WHEN_NO_CONFIG_PRESENT = "$PREFIX.$VERSION.isDisableWhenNoConfigPresent"

            private val foundSwiftLintPath: String
                get() {
                    val swiftLintFilePath = PathEnvironmentVariableUtil.findInPath("swiftlint")
                    return if (swiftLintFilePath != null) {
                        try {
                            swiftLintFilePath.canonicalPath
                        } catch (aE: IOException) {
                            aE.printStackTrace()
                            swiftLintFilePath.absolutePath
                        }
                    } else {
                        Configuration.DEFAULT_SWIFTLINT_PATH
                    }
                }

        }
    }

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    @Nls
    override fun getGroupDisplayName(): String = GROUP_NAME_SWIFT
    override fun getShortName(): String = SHORT_NAME
    override fun isEnabledByDefault(): Boolean = true

    override fun checkFile(
        originalFile: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        for (pair in runGeneralHighlighting(originalFile)) {
            val file = pair.first!!
            val info = pair.second!!
            val range = TextRange(info.startOffset, info.endOffset)
            var element = file.findElementAt(info.startOffset)
            while (element != null && !element.textRange.contains(range)) {
                element = element.parent
            }
            if (element == null) {
                element = file
            }
            if (SuppressionUtil.inspectionResultSuppressed(element, this)) continue
            GlobalInspectionUtil.createProblem(
                element,
                info,
                range.shiftRight(-element.node.startOffset),
                info.problemGroup,
                manager,
                problemDescriptionsProcessor,
                globalContext
            )
        }
    }

    private class MyPsiElementVisitor : PsiElementVisitor() {
        val result: MutableList<Pair<PsiFile?, HighlightInfo?>> = ArrayList()

        override fun visitFile(file: PsiFile) {
            file.virtualFile ?: return

            val progress = DaemonProgressIndicator()
            progress.start()
            try {
                val initialInfo = ANNOTATOR.collectInformation(file, true)
                var annotatorResult: AnnotatorResult? = null
                if (initialInfo != null) {
                    annotatorResult = ANNOTATOR.doAnnotate(initialInfo)
                }
                ANNOTATOR
                    .highlightInfos(file, annotatorResult)
                    .forEach { result.add(Pair.create(file, it)) }
            } finally {
                progress.stop()
            }
        }
    }

    companion object {
        private const val GROUP_NAME_SWIFT = "Swift"
        private const val SHORT_NAME = "SwiftLint"
        private val ANNOTATOR = SwiftLintHighlightingAnnotator()

        private fun runGeneralHighlighting(file: PsiFile): List<Pair<PsiFile?, HighlightInfo?>> {
            val visitor = MyPsiElementVisitor()
            file.accept(visitor)
            return ArrayList(visitor.result)
        }
    }
}