package com.lonelybytes.swiftlint.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.swift.psi.impl.elementType
import com.lonelybytes.swiftlint.Configuration
import com.lonelybytes.swiftlint.SwiftLint
import com.lonelybytes.swiftlint.SwiftLintInspection
import java.io.IOException

class SwiftLintCompletionContributor : CompletionContributor() {
    private var project: Project? = null
    private val rulesFromSwiftLint: List<String>
        get() {
            return try {
                val currentProject = project
                currentProject ?: return emptyList()

                return SwiftLint()
                    .getSwiftLintRulesList(SwiftLintInspection.State(currentProject).projectOrGlobalSwiftLintPath)
                    .flatMap { line ->
                        val parts = line.split("\\|".toRegex())
                        if (parts.size != 9) {
                            emptyList()
                        } else {
                            val ruleId = parts[1].trim { it <= ' ' }
                            val isOptIn = parts[2].trim { it <= ' ' }

                            // Skip header line
                            if (ruleId == "identifier" && isOptIn == "opt-in") {
                                emptyList()
                            } else {
                                listOf(ruleId)
                            }
                        }
                    }
            } catch (e: IOException) {
                Notifications.Bus.notify(
                    Notification(
                        Configuration.KEY_SWIFTLINT,
                        "Error",
                        """
                                Can't get SwiftLint rules
                                Exception: ${e.message}
                                """.trimIndent(),
                        NotificationType.ERROR
                    )
                )
                e.printStackTrace()
                emptyList()
            } catch (e: InterruptedException) {
                emptyList()
            }
        }

    companion object {
        private const val LINE_COMMENT_PREFIX = "//"
        private const val SWIFTLINT_KEYWORD_WITH_COLON = "swiftlint:"
        private val actions: List<String> = listOf("enable", "disable")
        private val modifiers: List<String> = listOf("next", "this", "previous")
        private val actionsWithModifiers: List<String> = actions.flatMap { action -> modifiers.map { "$action:$it" } }
        private val swiftlintActions: List<String> = actions.map { SWIFTLINT_KEYWORD_WITH_COLON + it }
        private val swiftlintActionsWithModifiers: List<String> = actionsWithModifiers.map { SWIFTLINT_KEYWORD_WITH_COLON + it }
        private var swiftLintRulesIds: List<String> = emptyList()
    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
//            PlatformPatterns.psiComment(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
                    val position = parameters.position
                    // swiftlint sees only comments that start with `//`, `///` will not do :(
                    // can't check statically, older CLion does not have this class.
                    if (!position.text.startsWith("$LINE_COMMENT_PREFIX ") && !position.parent.text.startsWith("$LINE_COMMENT_PREFIX ")) return
//                    if (position.parent.elementType.javaClass.kotlin.simpleName != "SwiftLazyEolCommentElementType" ||
//                        position.parent.firstChild.text != LINE_COMMENT_PREFIX ||
//                        position.text != LINE_COMMENT_PREFIX) return

                    if (swiftLintRulesIds.isEmpty()) {
                        project = parameters.originalFile.project
                        swiftLintRulesIds = rulesFromSwiftLint
                        ProgressManager.checkCanceled()
                    }

                    val prefix = resultSet.prefixMatcher.prefix

                    val textBeforePrefix =
                        if (position.text.startsWith("$LINE_COMMENT_PREFIX ")) {
                            position.text
                                .drop(3)
                                .replace("IntellijIdeaRulezzz", "")
                                .trimStart()
                                .dropLast(prefix.length)
                        } else {
                            position.parent.children
                                .drop(1)
                                .map { it.text }
                                .joinToString(separator = "")
                                .replace("IntellijIdeaRulezzz", "")
                                .trimStart()
                                .dropLast(prefix.length)
                        }

                    when {
                        swiftlintActionsWithModifiers.any { textBeforePrefix.startsWith(it) } -> {
                            swiftLintRulesIds.map { LookupElementBuilder.create(it) }
                                .forEach { resultSet.addElement(it) }
                        }
                        swiftlintActions.any { textBeforePrefix.startsWith(it) } -> {
                            if (textBeforePrefix.endsWith(":")) {
                                modifiers.map { LookupElementBuilder.create(it) }.forEach { resultSet.addElement(it) }
                            } else if (textBeforePrefix.endsWith(" ")) {
                                swiftLintRulesIds.map { LookupElementBuilder.create(it) }
                                    .forEach { resultSet.addElement(it) }
                                resultSet.addElement(LookupElementBuilder.create("all"))
                            }
                        }
                        textBeforePrefix.startsWith(SWIFTLINT_KEYWORD_WITH_COLON) -> {
                            actions.map { LookupElementBuilder.create(it) }.forEach { resultSet.addElement(it) }
                            actionsWithModifiers.map { LookupElementBuilder.create(it) }
                                .forEach { resultSet.addElement(it) }
                        }
                        textBeforePrefix.isEmpty() -> {
                            resultSet.addElement(LookupElementBuilder.create(SWIFTLINT_KEYWORD_WITH_COLON))
                            swiftlintActions.map { LookupElementBuilder.create(it) }
                                .forEach { resultSet.addElement(it) }
                            swiftlintActionsWithModifiers.map { LookupElementBuilder.create(it) }
                                .forEach { resultSet.addElement(it) }
                        }
                    }
                }
            })
    }
}