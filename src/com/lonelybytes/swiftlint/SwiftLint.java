package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwiftLint {
    public List<String> executeSwiftLint(@NotNull final String toolPath, @NotNull final String aAction, @NotNull SwiftLintConfig aConfig, @NotNull final String aFilePath) throws IOException, InterruptedException {
        if (aAction.equals("autocorrect")) {
            processAutocorrect(toolPath, aConfig, aFilePath);
        } else {
            if (aConfig.shouldBeLinted(aFilePath, true)) {
                return processAsApp(toolPath, aAction, aConfig, aFilePath);
            }
        }

        return Collections.emptyList();
    }

    private void processAutocorrect(String aToolPath, SwiftLintConfig config, String aFilePath) throws IOException, InterruptedException {
        List<String> params = new ArrayList<>();
        params.add(aToolPath);
        params.add("autocorrect");
        params.add("--no-cache");
        SwiftLintConfig.Config configForFile = config.getConfig(aFilePath);
        if (configForFile != null) {
            params.add("--config");
            params.add(configForFile._file.getAbsolutePath());
        }
        params.add("--path");
        params.add(aFilePath);

        Process process = Runtime.getRuntime().exec(params.toArray(new String[0]));
        process.waitFor();
    }

    @NotNull
    private List<String> processAsApp(@NotNull String toolPath, @NotNull final String aAction, SwiftLintConfig config, @NotNull String aFilePath) throws IOException, InterruptedException {
        List<String> params = new ArrayList<>();
        params.add(toolPath);
        params.add(aAction);
        params.add("--no-cache");
        params.add("--reporter");
        params.add("csv");
        params.add("--path");
        params.add(aFilePath);
        SwiftLintConfig.Config configForFile = config.getConfig(aFilePath);
        if (configForFile != null) {
            params.add("--config");
            params.add(configForFile._file.getAbsolutePath());
        }
        Process process = Runtime.getRuntime().exec(params.toArray(new String[0]));
        process.waitFor();
        return processSwiftLintOutput(process);
    }

    @NotNull
    private List<String> processSwiftLintOutput(Process aProcess) {
        final List<String> outputLines = new ArrayList<>();
        final List<String> errorLines = new ArrayList<>();

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(aProcess.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(aProcess.getErrorStream()));

        try {
            String line;
            while ((line = inputStream.readLine()) != null) {
                outputLines.add(line);
            }

            while ((line = errorStream.readLine()) != null) {
                String testLine = line.toLowerCase();
                if (testLine.contains("error:") || testLine.contains("warning:") || testLine.contains("invalid:") || testLine.contains("unrecognized arguments:")) {
                    errorLines.add(line);
                }
            }
        } catch (IOException e) {
            if (!e.getMessage().contains("closed")) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.getMessage(), NotificationType.INFORMATION));
            }

            e.printStackTrace();
        }

        for (String errorLine : errorLines) {
            if (!errorLine.trim().isEmpty()) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: " + errorLine, NotificationType.INFORMATION));
            }
        }

        return outputLines;
    }
}
