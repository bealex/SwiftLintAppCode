package com.lonelybytes.swiftlint.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.swift.psi.SwiftIdentifierPattern;
import com.jetbrains.swift.psi.SwiftParameter;
import com.jetbrains.swift.psi.SwiftVariableDeclaration;
import com.lonelybytes.swiftlint.Configuration;
import com.lonelybytes.swiftlint.SwiftLint;
import com.lonelybytes.swiftlint.SwiftLintConfig;
import com.lonelybytes.swiftlint.SwiftLintInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.lonelybytes.swiftlint.SwiftLintInspection.STATE;

public class SwiftLintHighlightingAnnotator extends ExternalAnnotator<SwiftLintAnnotatorInitialInfo, SwiftLintAnnotatorResult> {
    private static SwiftLintConfig swiftLintConfig = null;
    private static final SwiftLint SWIFT_LINT = new SwiftLint();

    private static final String SHORT_NAME = "SwiftLint";
    private static final String QUICK_FIX_NAME = "AutoСorrect";

    @Override
    public String getPairedBatchInspectionShortName() {
        return SHORT_NAME;
    }

    @Nullable
    @Override
    public SwiftLintAnnotatorInitialInfo collectInformation(@NotNull PsiFile aFile) {
        String filePath = aFile.getVirtualFile().getCanonicalPath();

        Document document = FileDocumentManager.getInstance().getDocument(aFile.getVirtualFile());
        if (document == null || document.getLineCount() == 0 || !shouldCheck(aFile)) {
            return new SwiftLintAnnotatorInitialInfo(filePath, false, 0);
        }

        if (STATE == null) {
            STATE = new SwiftLintInspection.State();
            STATE.setAppPath(Configuration.DEFAULT_SWIFTLINT_PATH);
            STATE.setDisableWhenNoConfigPresent(false);
            STATE.setQuickFixEnabled(true);
        } else if (STATE.getAppPath() == null || STATE.getAppPath().isEmpty()) {
            STATE.setAppPath(Configuration.DEFAULT_SWIFTLINT_PATH);
        }

        String swiftLintConfigPath = SwiftLintConfig.swiftLintConfigPath(aFile.getProject(), 5);
        if (STATE.isDisableWhenNoConfigPresent() && swiftLintConfigPath == null) {
            return null;
        }

        if (swiftLintConfig == null) {
            swiftLintConfig = new SwiftLintConfig(aFile.getProject(), swiftLintConfigPath);
        } else {
            swiftLintConfig.update(aFile.getProject(), swiftLintConfigPath);
        }

        return new SwiftLintAnnotatorInitialInfo(filePath, true, document.getLineCount());
    }

    @Nullable
    @Override
    public SwiftLintAnnotatorResult doAnnotate(SwiftLintAnnotatorInitialInfo collectedInfo) {
        if (!collectedInfo.shouldProcess) {
            return null;
        }

        String toolPath = STATE.getAppPath();

        List<SwiftLintAnnotatorResult.Line> lines = new ArrayList<>();
        try {
            List<String> lintedErrors = null;
            lintedErrors = SWIFT_LINT.executeSwiftLint(toolPath, "lint", swiftLintConfig, collectedInfo.filePath);

            if (lintedErrors != null && !lintedErrors.isEmpty()) {
                for (String line: lintedErrors) {
//                    System.out.println("--> " + line);

                    String[] lineParts = line.split("\\s*\\,\\s*");
                    if (lineParts.length == 0 || !line.contains(",") || line.trim().isEmpty()) {
                        continue;
                    }

                    if (lineParts[0].equals("file")) {
                        lineParts = Arrays.copyOfRange(lineParts, 7, lineParts.length);
                    }

                    while (lineParts.length >= 7) {
                        String[] parts = Arrays.copyOfRange(lineParts, 0, 7);
                        lineParts = Arrays.copyOfRange(lineParts, 7, lineParts.length);

                        lines.add(new SwiftLintAnnotatorResult.Line(parts, collectedInfo.documentLineCount));
                    }
                }
            }
        } catch (IOException ex) {
            if (ex.getMessage().contains("No such file or directory") || ex.getMessage().contains("error=2")) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "Can't find swiftlint utility here:\n" + toolPath + "\nPlease check the path in settings.", NotificationType.ERROR));
            } else {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.ERROR));
            }
        } catch (Exception ex) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "Exception: " + ex.getMessage(), NotificationType.INFORMATION));
            ex.printStackTrace();
        }

        return new SwiftLintAnnotatorResult(lines);
    }

    @Override
    public void apply(@NotNull PsiFile aFile, SwiftLintAnnotatorResult aResult, @NotNull AnnotationHolder aHolder) {
        Document document = FileDocumentManager.getInstance().getDocument(aFile.getVirtualFile());
        if (document == null) {
            return;
        }

        for (SwiftLintAnnotatorResult.Line line : aResult.lines) {
            processErrorLine(aFile, document, line, aHolder);
        }
    }

    // Process SwiftLint output
    
    private void processErrorLine(@NotNull PsiFile aFile, Document aDocument, SwiftLintAnnotatorResult.Line aLine, @NotNull AnnotationHolder aHolder) {
        int lineNumber = aLine.line;
        int columnNumber = aLine.column;

        int startOffset = aDocument.getLineStartOffset(lineNumber);
        int endOffset = lineNumber < aDocument.getLineCount() - 1
                ? aDocument.getLineStartOffset(lineNumber + 1)
                : aDocument.getLineEndOffset(lineNumber);

        TextRange range = TextRange.create(startOffset, endOffset);

        boolean weHaveAColumn = columnNumber > 0;

        if (weHaveAColumn) {
            startOffset = Math.min(aDocument.getTextLength() - 1, startOffset + columnNumber - 1);
        }

        CharSequence chars = aDocument.getImmutableCharSequence();
        if (chars.length() <= startOffset) {
            // This can happen when we browsing a file after it has been edited (some lines removed for example)
            return;
        }

        char startChar = chars.charAt(startOffset);
        PsiElement startPsiElement = aFile.findElementAt(startOffset);
        ASTNode startNode = startPsiElement == null ? null : startPsiElement.getNode();

        boolean isErrorInLineComment = startNode != null && startNode.getElementType().toString().equals("EOL_COMMENT");

        HighlightSeverity severity = severityFromSwiftLint(aLine.severity);

        if (isErrorInLineComment) {
            range = TextRange.create(aDocument.getLineStartOffset(lineNumber), aDocument.getLineEndOffset(lineNumber));
        } else {
            boolean isErrorNewLinesOnly = (startChar == '\n');
            boolean isErrorInSymbol = !Character.isLetterOrDigit(startChar) && !Character.isWhitespace(startChar);
            isErrorInSymbol |= aLine.rule.equals("opening_brace") || aLine.rule.equals("colon");

            if (!isErrorInSymbol) {
                if (!isErrorNewLinesOnly && weHaveAColumn) {
                    // SwiftLint returns column for the previous non-space token, not the erroneous one. Let's try to correct it.
                    switch (aLine.rule) {
                        case "return_arrow_whitespace": {
                            PsiElement psiElement = nextElement(aFile, startOffset, true);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "unused_closure_parameter": {
                            PsiElement psiElement = aFile.findElementAt(startOffset);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "syntactic_sugar": {
                            PsiElement psiElement = aFile.findElementAt(startOffset);
                            if (psiElement != null) {
                                psiElement = psiElement.getParent();
                            }
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "variable_name":
                            range = findVarInDefinition(aFile, startOffset);
                            break;
                        case "type_name": {
                            PsiElement psiElement = aFile.findElementAt(startOffset);
                            range = psiElement != null ? getNextTokenAtIndex(aFile, startOffset, aLine.rule) : range;
                            break;
                        }
                        case "identifier_name": {
                            PsiElement psiElement = aFile.findElementAt(startOffset);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        default:
                            range = getNextTokenAtIndex(aFile, startOffset, aLine.rule);
                            break;
                    }
                } else if (isErrorNewLinesOnly) {
                    switch (aLine.rule) {
                        case "superfluous_disable_command": {
                            PsiElement psiElement = aFile.findElementAt(startOffset - 1);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        default: {
                            // Let's select all empty lines here, we need to show that something is wrong with them
                            range = getEmptyLinesAroundIndex(aDocument, startOffset);
                        }
                    }
                }
            } else {
                PsiElement psiElement = aFile.findElementAt(startOffset);
                if (psiElement != null) {
                    range = psiElement.getTextRange();

                    if (aLine.rule.equals("colon")) {
                        range = getNextTokenAtIndex(aFile, startOffset, aLine.rule);
                    }
                }
            }

            if (aLine.rule.equals("opening_brace") && Character.isWhitespace(startChar)) {
                range = getNextTokenAtIndex(aFile, startOffset, aLine.rule);
            }

            if (aLine.rule.equals("valid_docs")) {
                range = prevElement(aFile, startOffset).getTextRange();
            }

            if (aLine.rule.equals("trailing_newline") && !weHaveAColumn && chars.charAt(chars.length() - 1) != '\n') {
                severity = HighlightSeverity.ERROR;
                range = TextRange.create(endOffset - 1, endOffset);
            }

            if (isErrorNewLinesOnly) {
                // Sometimes we need to highlight several returns. Usual error highlighting will not work in this case
                severity = HighlightSeverity.WARNING;
            }
        }

        Annotation annotation = aHolder.createAnnotation(severity, range, aLine.message.trim());
        annotation.registerFix(new IntentionAction() {
            @Nls
            @NotNull
            @Override
            public String getText() {
                return QUICK_FIX_NAME;
            }

            @Nls
            @NotNull
            @Override
            public String getFamilyName() {
                return "SwiftLint";
            }

            @Override
            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
                return true;
            }

            @Override
            public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
                executeSwiftLintQuickFix(file);
            }

            @Override
            public boolean startInWriteAction() {
                return false;
            }
        });
    }

    private TextRange getEmptyLinesAroundIndex(Document aDocument, int aInitialIndex) {
        CharSequence chars = aDocument.getImmutableCharSequence();

        int from = aInitialIndex;
        while (from >= 0) {
            if (!Character.isWhitespace(chars.charAt(from))) {
                from += 1;
                break;
            }
            from -= 1;
        }

        int to = aInitialIndex;
        while (to < chars.length()) {
            if (!Character.isWhitespace(chars.charAt(to))) {
                to -= 1;
                break;
            }
            to += 1;
        }

        from = Math.max(0, from);

        if (from > 0 && chars.charAt(from) == '\n') {
            from += 1;
        }

        while (to > 0 && chars.charAt(to - 1) != '\n') {
            to -= 1;
        }

        to = Math.max(from, to);

        return new TextRange(from, to);
    }

    private TextRange getNextTokenAtIndex(@NotNull PsiFile file, int aCharacterIndex, String aErrorType) {
        TextRange result = null;

        PsiElement psiElement;
        try {
            psiElement = file.findElementAt(aCharacterIndex);

            if (psiElement != null) {
                if (";".equals(psiElement.getText()) || (aErrorType.equals("variable_name") && psiElement.getNode().getElementType().toString().equals("IDENTIFIER"))) {
                    result = psiElement.getTextRange();
                } else {
                    result = psiElement.getNode().getTextRange();

                    psiElement = nextElement(file, aCharacterIndex, false);

                    if (psiElement != null) {
                        if (psiElement.getContext() != null && psiElement.getContext().getNode().getElementType().toString().equals("OPERATOR_SIGN")) {
                            result = psiElement.getContext().getNode().getTextRange();
                        } else {
                            result = psiElement.getNode().getTextRange();
                        }
                    }
                }
            }
        } catch (ProcessCanceledException aE) {
            // Do nothing
        } catch (Exception aE) {
            aE.printStackTrace();
        }

        return result;
    }

    private TextRange findVarInDefinition(@NotNull PsiFile file, int aCharacterIndex) {
        TextRange result = null;

        PsiElement psiElement;
        try {
            psiElement = file.findElementAt(aCharacterIndex);

            while (psiElement != null &&
                    !(psiElement instanceof SwiftVariableDeclaration) &&
                    !(psiElement instanceof SwiftParameter)) {
                psiElement = psiElement.getParent();
            }

            if (psiElement != null) {
                if (psiElement instanceof SwiftVariableDeclaration) {
                    SwiftVariableDeclaration variableDeclaration = (SwiftVariableDeclaration) psiElement;
                    SwiftIdentifierPattern identifierPattern = variableDeclaration.getVariables().get(0);
                    result = identifierPattern.getNode().getTextRange();
                } else /*if (psiElement instanceof SwiftParameter)*/ {
                    SwiftParameter variableDeclaration = (SwiftParameter) psiElement;
                    result = variableDeclaration.getNode().getTextRange();
                }
            }
        } catch (ProcessCanceledException aE) {
            // Do nothing
        } catch (Exception aE) {
            aE.printStackTrace();
        }

        return result;
    }

    private PsiElement nextElement(PsiFile aFile, int aElementIndex, boolean isWhitespace) {
        PsiElement nextElement = null;

        PsiElement initialElement = aFile.findElementAt(aElementIndex);

        if (initialElement != null) {
            int index = aElementIndex + initialElement.getTextLength();
            nextElement = aFile.findElementAt(index);

            while (nextElement != null && (
                    nextElement == initialElement ||
                    (!isWhitespace && nextElement instanceof PsiWhiteSpace) ||
                    (isWhitespace && !(nextElement instanceof PsiWhiteSpace))
                  )) {
                index += nextElement.getTextLength();
                nextElement = aFile.findElementAt(index);
            }
        }

        return nextElement;
    }

    private PsiElement prevElement(PsiFile aFile, int aElementIndex) {
        PsiElement nextElement = null;

        PsiElement initialElement = aFile.findElementAt(aElementIndex);

        if (initialElement != null) {
            int index = initialElement.getTextRange().getStartOffset() - 1;
            nextElement = aFile.findElementAt(index);

            while (nextElement != null && (nextElement == initialElement || nextElement instanceof PsiWhiteSpace)) {
                index = nextElement.getTextRange().getStartOffset() - 1;
                if (index >= 0) {
                    nextElement = aFile.findElementAt(index);
                } else {
                    break;
                }
            }
        }

        return nextElement;
    }

    private static HighlightSeverity severityFromSwiftLint(@NotNull final String severity) {
        switch (severity.trim().toLowerCase()) {
            case "error":
                return HighlightSeverity.ERROR;
            case "warning":
                return HighlightSeverity.WARNING;
            case "style":
            case "performance":
            case "portability":
                return HighlightSeverity.WEAK_WARNING;
            case "information":
                return HighlightSeverity.INFORMATION;
            default:
                return HighlightSeverity.INFORMATION;
        }
    }

    private boolean shouldCheck(@NotNull final PsiFile aFile) {
        boolean isSwift = "swift".equalsIgnoreCase(aFile.getVirtualFile().getExtension());
        boolean isInProject = ProjectFileIndex.SERVICE.getInstance(aFile.getProject()).isInSource(aFile.getVirtualFile());

        return isSwift && isInProject;
    }

    // QuickFix

    private void executeSwiftLintQuickFix(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();

        String toolPath = STATE.getAppPath();
        String filePath = virtualFile.getCanonicalPath();
        if (filePath == null) {
            return;
        }

        String name = SWIFT_LINT + " " + QUICK_FIX_NAME;

        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);

        Runnable action = () -> ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                SWIFT_LINT.executeSwiftLint(toolPath, "autocorrect", swiftLintConfig, filePath);
                ApplicationManager.getApplication().invokeLater(() ->
                    ApplicationManager.getApplication().runWriteAction(() -> file.getVirtualFile().refresh(false, false))
                );
            } catch (Exception e) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "Can't quick-fix.\nException: " + e.getMessage(), NotificationType.ERROR));
                e.printStackTrace();
            }
        });

        commandProcessor.executeCommand(file.getProject(), action, name, ActionGroup.EMPTY_GROUP);
    }

    private void saveAll() {
        final FileDocumentManager documentManager = FileDocumentManager.getInstance();
        if (documentManager.getUnsavedDocuments().length != 0) {
            ApplicationManager.getApplication().invokeLater(documentManager::saveAllDocuments);
        }
    }
}
