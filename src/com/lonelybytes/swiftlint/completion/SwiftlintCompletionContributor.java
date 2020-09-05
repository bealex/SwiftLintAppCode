package com.lonelybytes.swiftlint.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.lonelybytes.swiftlint.Configuration;
import com.lonelybytes.swiftlint.SwiftLint;
import com.lonelybytes.swiftlint.SwiftLintInspection;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SwiftlintCompletionContributor extends CompletionContributor {

    private static final String LINE_COMMENT_PREFIX = "//";
    private static final String SWIFTLINT_KEYWORD_WITH_COLON = "swiftlint:";

    private static final List<String> actions = Arrays.asList(
        "enable",
        "disable"
    );
    private static final List<String> modifiers = Arrays.asList(
        "next",
        "this",
        "previous"
    );

    private static final List<String> actionsWithModifiers = actions.stream()
            .flatMap(action -> modifiers.stream().map(modifier -> action + ":" + modifier))
            .collect(Collectors.toList());

    private static final List<String> swiftlintActions = actions.stream()
            .map(s -> SWIFTLINT_KEYWORD_WITH_COLON + s)
            .collect(Collectors.toList());

    private static final List<String> swiftlintActionsWithModifiers = actionsWithModifiers.stream()
            .map(s -> SWIFTLINT_KEYWORD_WITH_COLON + s)
            .collect(Collectors.toList());

    private static List<String> swiftLintRulesIds = null;

    public SwiftlintCompletionContributor() {

        extend(CompletionType.BASIC, PlatformPatterns.psiComment(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet resultSet) {

                PsiElement position = parameters.getPosition();
                String text = position.getText();
                if (!text.startsWith(LINE_COMMENT_PREFIX)) {
                    return;
                }

                if (swiftLintRulesIds == null) {
                    swiftLintRulesIds = getRulesFromSwiftLint();
                    ProgressManager.checkCanceled();
                }
                List<String> rulesToSuggest = swiftLintRulesIds != null ? swiftLintRulesIds : Collections.emptyList();

                String prefix = resultSet.getPrefixMatcher().getPrefix();
                int prefixStart = parameters.getOffset() - prefix.length() - position.getTextRange().getStartOffset();
                String textBeforePrefix = StringUtils.stripStart(text.substring(0, prefixStart).substring(2), null);

                if (swiftlintActionsWithModifiers.stream().anyMatch(textBeforePrefix::startsWith)) {
                    rulesToSuggest.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                    return;
                }

                if (swiftlintActions.stream().anyMatch(textBeforePrefix::startsWith)) {
                    if (textBeforePrefix.endsWith(":")) {
                        modifiers.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                    } else if (textBeforePrefix.endsWith(" ")) {
                        rulesToSuggest.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                        resultSet.addElement(LookupElementBuilder.create("all"));
                    }
                    return;
                }

                if (textBeforePrefix.startsWith(SWIFTLINT_KEYWORD_WITH_COLON)) {
                    actions.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                    actionsWithModifiers.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                    return;
                }

                if (textBeforePrefix.isEmpty()) {
                    resultSet.addElement(LookupElementBuilder.create(SWIFTLINT_KEYWORD_WITH_COLON));
                    swiftlintActions.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                    swiftlintActionsWithModifiers.stream().map(LookupElementBuilder::create).forEach(resultSet::addElement);
                }
            }
        });
    }

    private List<String> getRulesFromSwiftLint() {
        SwiftLintInspection.State state = SwiftLintInspection.STATE;
        if (state == null) {
            return null;
        }

        SwiftLint swiftLint = new SwiftLint();
        try {
            List<String> outputLines = swiftLint.executeSwiftLintRules(state.getAppPath());
            Stream<String> rules = outputLines.stream().flatMap(s -> {
                String[] parts = s.split("\\|");
                if (parts.length != 8) {
                    return Stream.empty();
                }
                String ruleId = parts[1].trim();
                String isOptIn = parts[2].trim();

                // Skip header line
                if (ruleId.equals("identifier") && isOptIn.equals("opt-in")) {
                    return Stream.empty();
                }

                return Stream.of(ruleId);
            });
            return rules.collect(Collectors.toList());
        } catch (IOException e) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "Can't get SwiftLint rules\nException: " + e.getMessage(), NotificationType.ERROR));
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            return null;
        }
    }
}
