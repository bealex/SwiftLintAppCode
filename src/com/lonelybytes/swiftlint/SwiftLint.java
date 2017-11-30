package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwiftLint {
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

    public List<String> executeSwiftLint(@NotNull final String toolPath, @NotNull final String aAction, @NotNull SwiftLintConfig aConfig,
                                         @NotNull final String aFilePath) throws IOException, InterruptedException {
        if (aAction.equals("autocorrect")) {
            processAutocorrect(toolPath, aConfig.getConfigPath(), aFilePath);
        } else {
            if (!aConfig.isDisabled(aFilePath)) {
                return processAsApp(toolPath, aAction, aConfig.getConfigPath(), aFilePath);
            }
        }

        return Collections.emptyList();
    }

    private void processAutocorrect(String aToolPath, String aConfigPath, String aFilePath) throws IOException, InterruptedException {
        String[] parameters = new String[]{
                aToolPath, "autocorrect",
                "--no-cache",
                "--config", aConfigPath,
                "--path", aFilePath
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
