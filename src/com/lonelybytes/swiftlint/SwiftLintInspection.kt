package com.lonelybytes.swiftlint;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
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

    public static class State {
        private static final String PREFIX = "com.appcodeplugins.swiftlint";
        private static final String VERSION_1_7 = "v1_7";

        private static final String QUICK_FIX_ENABLED = PREFIX + "." + VERSION_1_7 + ".quickFixEnabled";
        private static final String APP_NAME = PREFIX + "." + VERSION_1_7 + ".appName";
        private static final String DISABLE_WHEN_NO_CONFIG_PRESENT = PREFIX + "." + VERSION_1_7 + ".isDisableWhenNoConfigPresent";
        private static final String LOCAL_APP_NAME = PREFIX + "." + VERSION_1_7 + ".localAppName";

        public String getAppPath() {
            return PropertiesComponent.getInstance().getValue(APP_NAME);
        }

        public void setAppPath(String aAppPath) {
            PropertiesComponent.getInstance().setValue(APP_NAME, aAppPath);
        }

        public boolean isQuickFixEnabled() {
            return PropertiesComponent.getInstance().getBoolean(QUICK_FIX_ENABLED);
        }

        public void setQuickFixEnabled(boolean aQuickFixEnabled) {
            PropertiesComponent.getInstance().setValue(QUICK_FIX_ENABLED, aQuickFixEnabled);
        }

        public boolean isDisableWhenNoConfigPresent() {
            return PropertiesComponent.getInstance().getBoolean(DISABLE_WHEN_NO_CONFIG_PRESENT);
        }

        public void setDisableWhenNoConfigPresent(boolean aDisableWhenNoConfigPresent) {
            PropertiesComponent.getInstance().setValue(DISABLE_WHEN_NO_CONFIG_PRESENT, aDisableWhenNoConfigPresent);
        }

        public String getLocalAppPath() {
            return PropertiesComponent.getInstance(getProject()).getValue(LOCAL_APP_NAME);
        }

        public void setLocalAppPath(String aAppPath) {
            PropertiesComponent.getInstance(getProject()).setValue(LOCAL_APP_NAME, aAppPath);
        }

        public String getLocalOrGlobalAppPath() {
            String localAppPath = getLocalAppPath();
            if (localAppPath != null && !localAppPath.isEmpty()) {
                return localAppPath;
            }
            return getAppPath();
        }

        private Project getProject() {
            ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
            Project[] openProjects = projectManager.getOpenProjects();
            return openProjects.length == 0 ? projectManager.getDefaultProject() : openProjects[0];
        }
    }

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
                AnnotatorResult annotatorResult = null;
                if (initialInfo != null) {
                    annotatorResult = ANNOTATOR.doAnnotate(initialInfo);
                }
                ANNOTATOR.highlightInfos(file, annotatorResult).forEach(aHighlightInfo -> {
                    result.add(Pair.create(file, aHighlightInfo));
                });
            } finally {
                progress.stop();
            }
        }
    }
}
