package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SwiftLint {
    private String _toolPath;
    private String _configPath;

    @SuppressWarnings("SameParameterValue")
    @NotNull
    private String[] processParameters(@NotNull final String aAction, @NotNull final String aFilePath) {
        String[] parameters;
        if (_configPath == null) {
            parameters = new String[]{
                    _toolPath, aAction,
                    "--no-cache",
                    "--reporter", "csv",
                    "--path", aFilePath
            };
        } else {
            parameters = new String[]{
                    _toolPath, aAction,
                    "--no-cache",
                    "--config", _configPath,
                    "--reporter", "csv",
                    "--path", aFilePath
            };
        }
        return parameters;
    }

    List<String> executeSwiftLint(@NotNull final String toolPath, @NotNull final String aAction, @NotNull SwiftLintConfig aConfig, @NotNull final PsiElement aElement, Document aDocument) throws IOException, InterruptedException {
        if (aAction.equals("autocorrect") && aElement instanceof PsiFile) {
            processAutocorrect(toolPath, aConfig.getConfigPath(), ((PsiFile) aElement));
        } else {
            String filePath = aElement.getContainingFile().getVirtualFile().getCanonicalPath();
            if (!aConfig.isDisabled(filePath)) {
                if (filePath != null) {
                    return processAsApp(toolPath, aAction, aConfig.getConfigPath(), filePath);
                } else {
                    throw new IOException("Can't find file: " + aElement.getContainingFile().getVirtualFile());
                }
            }
        }

        return Collections.emptyList();
    }

    private void processAutocorrect(String aToolPath, String aConfigPath, PsiFile aFile) throws IOException, InterruptedException {
        String[] parameters = new String[]{
                aToolPath, "autocorrect",
                "--no-cache",
                "--config", aConfigPath,
                "--path", aFile.getVirtualFile().getCanonicalPath()
        };

        Process process = Runtime.getRuntime().exec(parameters);
        Thread outputThread = new Thread(() -> {
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));

            try {
                //noinspection StatementWithEmptyBody
                while (inputStream.readLine() != null) {
                    // do nothing
                }
            } catch (IOException ex) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                ex.printStackTrace();
            }
        });
        outputThread.start();

        outputThread.join(5000);
    }

    @NotNull
    private List<String> processAsApp(@NotNull String toolPath, @NotNull final String aAction, @Nullable String configPath, @NotNull String aFilePath) throws IOException, InterruptedException {
        _configPath = configPath;
        _toolPath = toolPath;

        String[] parameters = processParameters(aAction, aFilePath);
        return processSwiftLintOutput(Runtime.getRuntime().exec(parameters));
    }

    @NotNull
    private List<String> processSwiftLintOutput(Process aProcess) throws InterruptedException {
        final List<String> outputLines = new ArrayList<>();
        final List<String> errorLines = new ArrayList<>();

        Thread outputThread = new Thread(() -> {
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(aProcess.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(aProcess.getErrorStream()));

            try {
                String line;
                while ((line = inputStream.readLine()) != null) {
                    outputLines.add(line);
                }

                while ((line = errorStream.readLine()) != null) {
                    String testLine = line.toLowerCase();
                    if (testLine.contains("error") || testLine.contains("warning") || testLine.contains("invalid")) {
                        errorLines.add(line);
                    }
                }
            } catch (IOException e) {
                if (!e.getMessage().contains("closed")) {
                    Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.getMessage(), NotificationType.INFORMATION));
                }

                e.printStackTrace();
            }
        });
        outputThread.start();

        outputThread.join(1000);
        if (outputThread.isAlive()) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint takes too long to process file", NotificationType.INFORMATION));
        }

        for (String errorLine : errorLines) {
            if (!errorLine.trim().isEmpty()) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: " + errorLine, NotificationType.INFORMATION));
            }
        }

        return outputLines;
    }
}
