package com.lonelybytes.swiftlint.annotator;

public class InitialInfo {
    String filePath;
    boolean shouldProcess;
    int documentLineCount; // TODO: This value can be wrong :(

    InitialInfo(String aFilePath, boolean aShouldProcess, int aDocumentLineCount) {
        filePath = aFilePath;
        shouldProcess = aShouldProcess;
        documentLineCount = aDocumentLineCount;
    }
}
