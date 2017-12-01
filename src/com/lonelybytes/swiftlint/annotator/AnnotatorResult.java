package com.lonelybytes.swiftlint.annotator;

import java.util.List;

public class AnnotatorResult {
    public static class Line {
        // file,line,character,severity,type,reason,rule_id,
        int line;
        int column;

        String rule;
        String severity;
        String message;

        Line(String[] aParts, int aDocumentLineCount) {
            final int lineIndex = 1;
            final int columnIndex = 2;
            final int severityIndex = 3;
            final int messageIndex = 5;
            final int ruleIndex = 6;

            rule = aParts[ruleIndex];

            int linePointerFix = rule.equals("mark") ? -1 : -1;

            line = Math.min(aDocumentLineCount + linePointerFix, Integer.parseInt(aParts[lineIndex]) + linePointerFix);
            line = Math.max(0, line);

            column = aParts[columnIndex].isEmpty() ? -1 : Math.max(0, Integer.parseInt(aParts[columnIndex]));

            if (rule.equals("empty_first_line")) {
                // SwiftLint shows some strange identifier on the previous line
                line += 1;
                column = -1;
            }

            severity = aParts[severityIndex];
            message = aParts[messageIndex];
        }
    }

    List<Line> lines;

    AnnotatorResult(List<Line> aLines) {
        lines = aLines;
    }
}
