package com.lonelybytes.swiftlint.annotator;

public class InitialInfo {
    String filePath;
    boolean shouldProcess;

    InitialInfo(String aFilePath, boolean aShouldProcess) {
        filePath = aFilePath;
        shouldProcess = aShouldProcess;
    }
}
