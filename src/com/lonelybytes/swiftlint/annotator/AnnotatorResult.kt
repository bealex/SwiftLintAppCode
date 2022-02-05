package com.lonelybytes.swiftlint.annotator

import com.intellij.openapi.editor.Document

class AnnotatorResult internal constructor(var lines: List<Line>) {
    class Line internal constructor(aParts: List<String>) {
        // file,line,character,severity,type,reason,rule_id,
        @JvmField
        var line: Int

        @JvmField
        var column: Int

        @JvmField
        var rule: String

        @JvmField
        var severity: String

        @JvmField
        var message: String

        private var positionWasFixed = false

        fun fixPositionInDocument(aDocument: Document) {
            if (positionWasFixed) {
                return
            }
            line = 0.coerceAtLeast((aDocument.lineCount - 1).coerceAtMost(line - 1))
            if (rule == "empty_first_line") {
                // SwiftLint shows some strange identifier on the previous line
                line += 1
                column = -1
            }
            positionWasFixed = true
        }

        init {
            val lineIndex = 1
            val columnIndex = 2
            val severityIndex = 3
            val messageIndex = 5
            val ruleIndex = 6

            severity = aParts[severityIndex]
            message = aParts[messageIndex]
            if (message.startsWith("\"")) {
                message = message.substring(1)
            }
            if (message.endsWith("\"")) {
                message = message.substring(0, message.length - 1)
            }
            rule = aParts[ruleIndex]
            line = aParts[lineIndex].toInt()
            column = if (aParts[columnIndex].isEmpty()) -1 else 0.coerceAtLeast(aParts[columnIndex].toInt())
        }
    }
}
