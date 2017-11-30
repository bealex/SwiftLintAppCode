package com.lonelybytes.swiftlint;

import com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitorBasedInspection;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SwiftLintInspection extends DefaultHighlightVisitorBasedInspection.AnnotatorBasedInspection {
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

    private static final String SHORT_NAME = "SwiftLint";

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return SHORT_NAME;
    }

    @NotNull
    @Override
    public String getShortName() {
        return SHORT_NAME;
    }
}
