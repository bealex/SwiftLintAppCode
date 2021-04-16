package com.lonelybytes.swiftlint.annotator;

import com.intellij.openapi.editor.Document;

import java.util.List;

public class AnnotatorResult {
    public static class Line {
        // file,line,character,severity,type,reason,rule_id,
        int line;
        int column;

        String rule;
        String severity;
        String message;

        private boolean positionWasFixed = false;

        Line(String[] aParts) {
            final int lineIndex = 1;
            final int columnIndex = 2;
            final int severityIndex = 3;
            final int messageIndex = 5;
            final int ruleIndex = 6;

            severity = aParts[severityIndex];
            message = aParts[messageIndex];

            if (message.startsWith("\"")) {
                message = message.substring(1);
            }
            if (message.endsWith("\"")) {
                message = message.substring(0, message.length() - 1);
            }

            rule = aParts[ruleIndex];

            line = Integer.parseInt(aParts[lineIndex]);
            column = aParts[columnIndex].isEmpty() ? -1 : Math.max(0, Integer.parseInt(aParts[columnIndex]));
        }

        void fixPositionInDocument(Document aDocument) {
            if (positionWasFixed) {
                return;
            }

            line = Math.max(0, Math.min(aDocument.getLineCount() - 1, line - 1));

            if (rule.equals("empty_first_line")) {
                // SwiftLint shows some strange identifier on the previous line
                line += 1;
                column = -1;
            }

            positionWasFixed = true;
        }
    }

    List<Line> lines;

    AnnotatorResult(List<Line> aLines) {
        lines = aLines;
    }
}
