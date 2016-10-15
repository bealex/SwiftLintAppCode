package com.lonelybytes.swiftlint;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

public class SwiftLintInspection extends LocalInspectionTool {
    public static final String QUICK_FIX_NAME = "Autocorrect";
    private Map<String, Integer> _fileHashes = new HashMap<>();

    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
        super.inspectionStarted(session, isOnTheFly);
        saveAll();
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null || document.getLineCount() == 0 || !shouldCheck(file, document)) {
            return null;
        }

        String toolPath = Properties.get(Configuration.KEY_SWIFTLINT);
        if (StringUtil.isEmpty(toolPath)) {
            return null;
        }

        String toolOptions = "lint --path";

        Pattern errorsPattern = Pattern.compile("^(\\S.*?):(?:(\\d+):)(?:(\\d+):)? (\\S+):([^\\(]*)\\((.*)\\)$");
        int lineMatchIndex = 2;
        int columnMatchIndex = 3;
        int severityMatchIndex = 4;
        int messageMatchIndex = 5;
        int errorTypeMatchIndex = 6;

        List<ProblemDescriptor> descriptors = new ArrayList<>();

        try {
            String fileText = Utils.executeCommandOnFile(toolPath, toolOptions, file);

            System.out.println("\n" + fileText + "\n");

            if (fileText.isEmpty()) {
                return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
            }

            Scanner scanner = new Scanner(fileText);

            String line;
            while (scanner.hasNext()) {
                line = scanner.nextLine();
                if (!line.contains(":")) {
                    continue;
                }

                Matcher matcher = errorsPattern.matcher(line);

                if (!matcher.matches()) {
                    continue;
                }

                final String errorType = matcher.group(errorTypeMatchIndex);

                int linePointerFix = errorType.equals("mark") ? -1 : -1;

                int lineNumber = Math.min(document.getLineCount() + linePointerFix, Integer.parseInt(matcher.group(lineMatchIndex)) + linePointerFix);
                lineNumber = Math.max(0, lineNumber);

                int columnNumber = matcher.group(columnMatchIndex) == null ? -1 : Math.max(0, Integer.parseInt(matcher.group(columnMatchIndex)));

                if (errorType.equals("empty_first_line")) {
                    // SwiftLint shows some strange identifier on the previous line
                    lineNumber += 1;
                    columnNumber = -1;
                }

                final String severity = matcher.group(severityMatchIndex);
                final String errorMessage = matcher.group(messageMatchIndex);

                int highlightStartOffset = document.getLineStartOffset(lineNumber);
                int highlightEndOffset = lineNumber < document.getLineCount() - 1
                        ? document.getLineStartOffset(lineNumber + 1)
                        : document.getLineEndOffset(lineNumber);

                TextRange range = TextRange.create(highlightStartOffset, highlightEndOffset);

                boolean weHaveAColumn = columnNumber > 0;

                if (weHaveAColumn) {
                    highlightStartOffset = Math.min(document.getTextLength() - 1, highlightStartOffset + columnNumber - 1);
                }

                CharSequence chars = document.getImmutableCharSequence();
                if (chars.length() <= highlightStartOffset) {
                    // This can happen when we browsing a file after it has been edited (some lines removed for example)
                    continue;
                }

                char startChar = chars.charAt(highlightStartOffset);
                PsiElement startPsiElement = file.findElementAt(highlightStartOffset);
                ASTNode startNode = startPsiElement == null ? null : startPsiElement.getNode();

                boolean isErrorInLineComment = startNode != null && startNode.getElementType().toString().equals("EOL_COMMENT");

                ProblemHighlightType highlightType = severityToHighlightType(severity);

                if (isErrorInLineComment) {
                    range = TextRange.create(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
                } else {
                    boolean isErrorNewLinesOnly = (startChar == '\n');
                    boolean isErrorInSymbol = !Character.isLetterOrDigit(startChar) && !Character.isWhitespace(startChar);
                    isErrorInSymbol |= errorType.equals("opening_brace");

                    if (!isErrorInSymbol) {
                        if (!isErrorNewLinesOnly && weHaveAColumn) {
                            // SwiftLint returns column for the previous non-space token, not the erroneous one. Let's try to correct it.
                            range = getNextTokenAtIndex(file, highlightStartOffset, errorType);
                        } else if (isErrorNewLinesOnly) {
                            // Let's select all empty lines here, we need to show that something is wrong with them
                            range = getEmptyLinesAroundIndex(document, highlightStartOffset);
                        }
                    } else {
                        PsiElement psiElement = file.findElementAt(highlightStartOffset);
                        if (psiElement != null) {
                            range = psiElement.getTextRange();
                        }
                    }

                    if (errorType.equals("opening_brace") && Character.isWhitespace(startChar)) {
                        range = getNextTokenAtIndex(file, highlightStartOffset, errorType);
                    }

                    if (errorType.equals("valid_docs")) {
                        range = prevElement(file, highlightStartOffset).getTextRange();
                    }

                    if (errorType.equals("trailing_newline") && !weHaveAColumn && chars.charAt(chars.length() - 1) != '\n') {
                        highlightType = GENERIC_ERROR;
                        range = TextRange.create(highlightEndOffset - 1, highlightEndOffset);
                    }

                    if (isErrorNewLinesOnly) {
                        // Sometimes we need to highlight several returns. Usual error highlighting will not work in this case
                        highlightType = GENERIC_ERROR_OR_WARNING;
                    }
                }

                descriptors.add(manager.createProblemDescriptor(file, range, errorMessage.trim(), highlightType, false, Properties.getBoolean(Configuration.KEY_QUICK_FIX_ENABLED) ? new LocalQuickFix() {
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
                        try {
                            saveAll();
                            Utils.executeCommandOnFile(toolPath, "autocorrect --path", file);
                            ArrayList<VirtualFile> virtualFiles = new ArrayList<>();
                            virtualFiles.add(file.getVirtualFile());
                            LocalFileSystem.getInstance().refreshFiles(virtualFiles);
                        } catch (IOException e) {
                            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.getMessage(), NotificationType.INFORMATION));
                        }

                    }
                } : null));
            }
        } catch (ProcessCanceledException ex) {
            // Do nothing here
        } catch (Exception ex) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
            ex.printStackTrace();
        }

        int newHash = document.getText().hashCode();
        _fileHashes.put(file.getVirtualFile().getCanonicalPath(), newHash);

        return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
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

        if (to > 0) {
            while (chars.charAt(to - 1) != '\n') {
                to -= 1;
            }
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

                    psiElement = nextElement(file, aCharacterIndex);

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

    private PsiElement nextElement(PsiFile aFile, int aElementIndex) {
        PsiElement nextElement = null;

        PsiElement initialElement = aFile.findElementAt(aElementIndex);

        if (initialElement != null) {
            int index = aElementIndex + initialElement.getTextLength();
            nextElement = aFile.findElementAt(index);

            while (nextElement != null && (nextElement == initialElement || nextElement instanceof PsiWhiteSpace)) {
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

    private boolean shouldCheck(@NotNull final PsiFile aFile, @NotNull final Document aDocument) {
        boolean isExtensionSwifty = "swift".equalsIgnoreCase(aFile.getVirtualFile().getExtension());

        Integer previousHash = _fileHashes.get(aFile.getVirtualFile().getCanonicalPath());
        int newHash = aDocument.getText().hashCode();
        boolean fileChanged = previousHash == null || previousHash != newHash;

        return isExtensionSwifty;
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
