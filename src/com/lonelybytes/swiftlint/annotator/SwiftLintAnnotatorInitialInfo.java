package com.lonelybytes.swiftlint.annotator;

class SwiftLintAnnotatorInitialInfo {
    String filePath;
    boolean shouldProcess;
    int documentLineCount;

    SwiftLintAnnotatorInitialInfo(String aFilePath, boolean aShouldProcess, int aDocumentLineCount) {
        filePath = aFilePath;
        shouldProcess = aShouldProcess;
        documentLineCount = aDocumentLineCount;
    }
}
