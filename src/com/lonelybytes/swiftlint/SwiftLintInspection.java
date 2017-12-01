package com.lonelybytes.swiftlint;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.lonelybytes.swiftlint.annotator.AnnotatorResult;
import com.lonelybytes.swiftlint.annotator.InitialInfo;
import com.lonelybytes.swiftlint.annotator.SwiftLintHighlightingAnnotator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SwiftLintInspection extends GlobalSimpleInspectionTool {
    private static final String GROUP_NAME_SWIFT = "Swift";
    private static final String SHORT_NAME = "SwiftLint";

    private static final SwiftLintHighlightingAnnotator ANNOTATOR = new SwiftLintHighlightingAnnotator();

    @SuppressWarnings("WeakerAccess")
    public static class State {
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
    public static State STATE = new State();

    @NotNull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return GROUP_NAME_SWIFT;
    }

    @NotNull
    @Override
    public String getShortName() {
        return SHORT_NAME;
    }

    @Override
    public void checkFile(@NotNull PsiFile originalFile, @NotNull InspectionManager manager,
                          @NotNull ProblemsHolder problemsHolder, @NotNull GlobalInspectionContext globalContext,
                          @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
        for (Pair<PsiFile, HighlightInfo> pair : runGeneralHighlighting(originalFile)) {
            PsiFile file = pair.first;
            HighlightInfo info = pair.second;

            TextRange range = new TextRange(info.startOffset, info.endOffset);
            PsiElement element = file.findElementAt(info.startOffset);

            while (element != null && !element.getTextRange().contains(range)) {
                element = element.getParent();
            }

            if (element == null) {
                element = file;
            }

            if (SuppressionUtil.inspectionResultSuppressed(element, this)) continue;

            GlobalInspectionUtil.createProblem(
                    element,
                    info,
                    range.shiftRight(-element.getNode().getStartOffset()),
                    info.getProblemGroup(),
                    manager,
                    problemDescriptionsProcessor,
                    globalContext
            );
        }
    }

    private static List<Pair<PsiFile, HighlightInfo>> runGeneralHighlighting(PsiFile file) {
        SwiftLintInspection.MyPsiElementVisitor visitor = new SwiftLintInspection.MyPsiElementVisitor();
        file.accept(visitor);
        return new ArrayList<>(visitor.result);
    }

    private static class MyPsiElementVisitor extends PsiElementVisitor {
        private final List<Pair<PsiFile, HighlightInfo>> result = new ArrayList<>();

        MyPsiElementVisitor() {
        }

        @Override
        public void visitFile(final PsiFile file) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                return;
            }

            DaemonProgressIndicator progress = new DaemonProgressIndicator();
            progress.start();
            try {
                InitialInfo initialInfo = ANNOTATOR.collectInformation(file);
                AnnotatorResult annotatorResult = ANNOTATOR.doAnnotate(initialInfo);
                ANNOTATOR.highlightInfos(file, annotatorResult).forEach(aHighlightInfo -> {
                    result.add(Pair.create(file, aHighlightInfo));
                });
            } finally {
                progress.stop();
            }
        }
    }
}
