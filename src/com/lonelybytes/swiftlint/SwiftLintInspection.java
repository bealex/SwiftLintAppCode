package com.lonelybytes.swiftlint;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.ASTNode;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.swift.psi.SwiftIdentifierPattern;
import com.jetbrains.swift.psi.SwiftParameter;
import com.jetbrains.swift.psi.SwiftVariableDeclaration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

public class SwiftLintInspection extends LocalInspectionTool {
    @SuppressWarnings("WeakerAccess")
    static class State {
        public String getAppPath() {
            return PropertiesComponent.getInstance().getValue("com.appcodeplugins.swiftlint.v1_7.appName");
        }

        public void setAppPath(String aAppPath) {
            PropertiesComponent.getInstance().setValue("com.appcodeplugins.swiftlint.v1_7.appName", aAppPath);
        }

        public boolean isQuickFixEnabled() {
            return PropertiesComponent.getInstance().getBoolean("com.appcodeplugins.swiftlint.v1_7.quickFixEnabled");
        }

        public void setQuickFixEnabled(boolean aQuickFixEnabled) {
            PropertiesComponent.getInstance().setValue("com.appcodeplugins.swiftlint.v1_7.quickFixEnabled", aQuickFixEnabled);
        }

        public boolean isDisableWhenNoConfigPresent() {
            return PropertiesComponent.getInstance().getBoolean("com.appcodeplugins.swiftlint.v1_7.isDisableWhenNoConfigPresent");
        }

        public void setDisableWhenNoConfigPresent(boolean aDisableWhenNoConfigPresent) {
            PropertiesComponent.getInstance().setValue("com.appcodeplugins.swiftlint.v1_7.isDisableWhenNoConfigPresent", aDisableWhenNoConfigPresent);
        }
    }
    
    @SuppressWarnings("WeakerAccess")
    static State STATE = new State();

    private static final String QUICK_FIX_NAME = "Autocorrect";
    
    private static SwiftLintConfig _swiftLintConfig = null;
    private static final SwiftLint SWIFT_LINT = new SwiftLint();

    private static final long THROTTLE_DELAY_MILLISECONDS = 1000;
    private static Map<String, Boolean> _alreadyExecuting = new HashMap<>();
    private static Map<String, Long> _lastRequestedTimes = new HashMap<>();

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "All SwiftLint Rules";
    }

    @NotNull
    @Override
    public String getID() {
        return "SwiftLintInspection";
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "SwiftLint";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
        super.inspectionStarted(session, isOnTheFly);
        saveAll();
    }

    @Override
    public boolean runForWholeFile() {
        return false;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null || document.getLineCount() == 0 || !shouldCheck(file)) {
            return null;
        }

        String filePath = file.getVirtualFile().getCanonicalPath();

        _lastRequestedTimes.put(filePath, System.currentTimeMillis());

        Boolean alreadyExecutingState = _alreadyExecuting.get(filePath);
        if (alreadyExecutingState == null) {
            alreadyExecutingState = false;
        }

        if (alreadyExecutingState) {
            System.out.println("Already started");
            return null;
        }

        System.out.println("Began");
        _alreadyExecuting.put(filePath, true);

        try {
            return getProblemDescriptors(file, manager, document, filePath);
        } catch (InterruptedException aE) {
            aE.printStackTrace();
        } finally {
            System.out.println("Ended");
            _alreadyExecuting.put(filePath, false);
        }

        return null;
    }

    @Nullable
    private ProblemDescriptor[] getProblemDescriptors(@NotNull PsiFile file, @NotNull InspectionManager manager, Document aDocument, String aFilePath) throws InterruptedException {
        long inspectionStartTime = _lastRequestedTimes.get(aFilePath) + THROTTLE_DELAY_MILLISECONDS;
        while (System.currentTimeMillis() < inspectionStartTime) {
            Thread.sleep(100);
            inspectionStartTime = _lastRequestedTimes.get(aFilePath) + THROTTLE_DELAY_MILLISECONDS;
            System.out.println("  Waiting");
        }

        System.out.println("  Started");

        if (STATE == null) {
            STATE = new State();
            STATE.setAppPath(Configuration.DEFAULT_SWIFTLINT_PATH);
            STATE.setDisableWhenNoConfigPresent(false);
            STATE.setQuickFixEnabled(true);
        } else if (STATE.getAppPath() == null || STATE.getAppPath().isEmpty()) {
            STATE.setAppPath(Configuration.DEFAULT_SWIFTLINT_PATH);
        }

        String swiftLintConfigPath = SwiftLintConfig.swiftLintConfigPath(file.getProject(), 5);
        if (STATE.isDisableWhenNoConfigPresent() && swiftLintConfigPath == null) {
            return null;
        }

        if (_swiftLintConfig == null) {
            _swiftLintConfig = new SwiftLintConfig(file.getProject(), swiftLintConfigPath);
        } else {
            _swiftLintConfig.update(file.getProject(), swiftLintConfigPath);
        }

        String toolPath = STATE.getAppPath();

        List<ProblemDescriptor> descriptors = new ArrayList<>();

        try {
            List<String> lintedErrors = SWIFT_LINT.executeSwiftLint(toolPath, "lint", _swiftLintConfig, file, aDocument);

            if (lintedErrors.isEmpty()) {
                return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
            }

            processLintErrors(
                file, manager, aDocument,
                toolPath, _swiftLintConfig,
                descriptors, lintedErrors
            );
        } catch (ProcessCanceledException ex) {
            ex.printStackTrace();
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

        return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }

    private void processLintErrors(@NotNull PsiFile file, @NotNull InspectionManager manager, Document aDocument,
                                   String aToolPath, SwiftLintConfig aConfig,
                                   List<ProblemDescriptor> aDescriptors,
                                   List<String> aLintedErrors) {
        // file,line,character,severity,type,reason,rule_id,
        // ___ ,10,  25,       Error,   Force Cast,Force casts should be avoided.,force_cast

        for (String line : aLintedErrors) {
//            System.out.println("--> " + line);

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

                processErrorLine(file, manager, aDocument, aToolPath, aConfig, aDescriptors, parts);
            }
        }
    }

    private void processErrorLine(@NotNull PsiFile file, @NotNull InspectionManager manager, Document aDocument,
                                  String aToolPath, SwiftLintConfig aConfig,
                                  List<ProblemDescriptor> aDescriptors,
                                  String[] aParts) {
//        final int fileIndex = 0;
        final int lineIndex = 1;
        final int columnIndex = 2;
        final int severityIndex = 3;
//        final int typeIndex = 4;
        final int messageIndex = 5;
        final int ruleIndex = 6;

        final String rule = aParts[ruleIndex];

        int linePointerFix = rule.equals("mark") ? -1 : -1;

        int lineNumber = Math.min(aDocument.getLineCount() + linePointerFix, Integer.parseInt(aParts[lineIndex]) + linePointerFix);
        lineNumber = Math.max(0, lineNumber);

        int columnNumber = aParts[columnIndex].isEmpty() ? -1 : Math.max(0, Integer.parseInt(aParts[columnIndex]));

        if (rule.equals("empty_first_line")) {
            // SwiftLint shows some strange identifier on the previous line
            lineNumber += 1;
            columnNumber = -1;
        }

        final String severity = aParts[severityIndex];
        final String errorMessage = aParts[messageIndex];

        int highlightStartOffset = aDocument.getLineStartOffset(lineNumber);
        int highlightEndOffset = lineNumber < aDocument.getLineCount() - 1
                ? aDocument.getLineStartOffset(lineNumber + 1)
                : aDocument.getLineEndOffset(lineNumber);

        TextRange range = TextRange.create(highlightStartOffset, highlightEndOffset);

        boolean weHaveAColumn = columnNumber > 0;

        if (weHaveAColumn) {
            highlightStartOffset = Math.min(aDocument.getTextLength() - 1, highlightStartOffset + columnNumber - 1);
        }

        CharSequence chars = aDocument.getImmutableCharSequence();
        if (chars.length() <= highlightStartOffset) {
            // This can happen when we browsing a file after it has been edited (some lines removed for example)
            return;
        }

        char startChar = chars.charAt(highlightStartOffset);
        PsiElement startPsiElement = file.findElementAt(highlightStartOffset);
        ASTNode startNode = startPsiElement == null ? null : startPsiElement.getNode();

        boolean isErrorInLineComment = startNode != null && startNode.getElementType().toString().equals("EOL_COMMENT");

        ProblemHighlightType highlightType = severityToHighlightType(severity);

        if (isErrorInLineComment) {
            range = TextRange.create(aDocument.getLineStartOffset(lineNumber), aDocument.getLineEndOffset(lineNumber));
        } else {
            boolean isErrorNewLinesOnly = (startChar == '\n');
            boolean isErrorInSymbol = !Character.isLetterOrDigit(startChar) && !Character.isWhitespace(startChar);
            isErrorInSymbol |= rule.equals("opening_brace") || rule.equals("colon");

            if (!isErrorInSymbol) {
                if (!isErrorNewLinesOnly && weHaveAColumn) {
                    // SwiftLint returns column for the previous non-space token, not the erroneous one. Let's try to correct it.
                    switch (rule) {
                        case "return_arrow_whitespace": {
                            PsiElement psiElement = nextElement(file, highlightStartOffset, true);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "unused_closure_parameter": {
                            PsiElement psiElement = file.findElementAt(highlightStartOffset);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "syntactic_sugar": {
                            PsiElement psiElement = file.findElementAt(highlightStartOffset);
                            if (psiElement != null) {
                                psiElement = psiElement.getParent();
                            }
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        case "variable_name":
                            range = findVarInDefinition(file, highlightStartOffset);
                            break;
                        case "type_name": {
                            PsiElement psiElement = file.findElementAt(highlightStartOffset);
                            range = psiElement != null ? getNextTokenAtIndex(file, highlightStartOffset, rule) : range;
                            break;
                        }
                        case "identifier_name": {
                            PsiElement psiElement = file.findElementAt(highlightStartOffset);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        default:
                            range = getNextTokenAtIndex(file, highlightStartOffset, rule);
                            break;
                    }
                } else if (isErrorNewLinesOnly) {
                    switch (rule) {
                        case "superfluous_disable_command": {
                            PsiElement psiElement = file.findElementAt(highlightStartOffset - 1);
                            range = psiElement != null ? psiElement.getTextRange() : range;
                            break;
                        }
                        default: {
                            // Let's select all empty lines here, we need to show that something is wrong with them
                            range = getEmptyLinesAroundIndex(aDocument, highlightStartOffset);
                        }
                    }
                }
            } else {
                PsiElement psiElement = file.findElementAt(highlightStartOffset);
                if (psiElement != null) {
                    range = psiElement.getTextRange();

                    if (rule.equals("colon")) {
                        range = getNextTokenAtIndex(file, highlightStartOffset, rule);
                    }
                }
            }

            if (rule.equals("opening_brace") && Character.isWhitespace(startChar)) {
                range = getNextTokenAtIndex(file, highlightStartOffset, rule);
            }

            if (rule.equals("valid_docs")) {
                range = prevElement(file, highlightStartOffset).getTextRange();
            }

            if (rule.equals("trailing_newline") && !weHaveAColumn && chars.charAt(chars.length() - 1) != '\n') {
                highlightType = GENERIC_ERROR;
                range = TextRange.create(highlightEndOffset - 1, highlightEndOffset);
            }

            if (isErrorNewLinesOnly) {
                // Sometimes we need to highlight several returns. Usual error highlighting will not work in this case
                highlightType = GENERIC_ERROR_OR_WARNING;
            }
        }

        if (STATE.isQuickFixEnabled()) {
            aDescriptors.add(manager.createProblemDescriptor(file, range, errorMessage.trim(), highlightType, false, new LocalQuickFix() {
                @Nls
                @NotNull
                @Override
                public String getName() {
                    return QUICK_FIX_NAME;
                }

                @Nls
                @NotNull
                @Override
                public String getFamilyName() {
                    return QUICK_FIX_NAME;
                }

                @Override
                public boolean startInWriteAction() {
                    return false;
                }

                @Override
                public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
                    WriteCommandAction writeCommandAction = new WriteCommandAction(project, file) {
                        @Override
                        protected void run(@NotNull Result aResult) {
                            executeSwiftLintQuickFix(aToolPath, aConfig, file);
                        }
                    };

                    writeCommandAction.execute();
                }
            }));
        } else {
            aDescriptors.add(manager.createProblemDescriptor(file, range, errorMessage.trim(), highlightType, false, LocalQuickFix.EMPTY_ARRAY));
        }
    }

    private void executeSwiftLintQuickFix(String aToolPath, SwiftLintConfig aConfig, @NotNull PsiFile file) {
        saveAll();
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                SWIFT_LINT.executeSwiftLint(aToolPath, "autocorrect", aConfig, file, null);
                LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(file.getVirtualFile()));
            } catch (Exception e) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "Can't quick-fix.\nException: " + e.getMessage(), NotificationType.ERROR));
                e.printStackTrace();
            }
        });
    }

    private void saveAll() {
        final FileDocumentManager documentManager = FileDocumentManager.getInstance();
        if (documentManager.getUnsavedDocuments().length != 0) {
            ApplicationManager.getApplication().invokeLater(documentManager::saveAllDocuments);
        }
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

    private boolean shouldCheck(@NotNull final PsiFile aFile) {
        boolean isSwift = "swift".equalsIgnoreCase(aFile.getVirtualFile().getExtension());
        boolean isInProject = ProjectFileIndex.SERVICE.getInstance(aFile.getProject()).isInSource(aFile.getVirtualFile());

        return isSwift && isInProject;
    }

    private static ProblemHighlightType severityToHighlightType(@NotNull final String severity) {
        switch (severity.trim().toLowerCase()) {
            case "error":
                return GENERIC_ERROR;
            case "warning":
                return GENERIC_ERROR_OR_WARNING;
            case "style":
            case "performance":
            case "portability":
                return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
            case "information":
                return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
            default:
                return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
        }
    }
}
