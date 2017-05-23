package com.lonelybytes.swiftlint;

import com.intellij.codeInspection.InspectionToolProvider;
import org.jetbrains.annotations.NotNull;

public class InspectionsProvider implements InspectionToolProvider {
    @NotNull
    @Override
    public Class[] getInspectionClasses() {
//        List<String> rules = Arrays
//                .stream(SwiftLintConfig.rules)
//                .map(aStrings -> aStrings[0])
//                .collect(Collectors.toList());

        return new Class[] {
                SwiftLintInspection.class
        };
    }
}
