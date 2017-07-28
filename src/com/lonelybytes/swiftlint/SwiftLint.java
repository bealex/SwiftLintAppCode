package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

class SwiftLint {
    private static final String TERMINATOR = "|||end|||";
    private String _toolPath;
    private String _configFilePath;

    private Process process;
    private BufferedWriter stdOut;

    private void closeProcess() throws IOException {
        if (stdOut != null) {
            stdOut.close();
        }

        if (process != null) {
            process.destroy();
        }
    }

    private void restartSwiftLint() throws IOException {
        String frameworkSearchPath = _toolPath.substring(0, _toolPath.lastIndexOf("/"));

        closeProcess();

        String[] parameters;
        if (_configFilePath == null) {
            parameters = new String[]{
                    _toolPath,
                    "--reporter", "csv"
            };
        } else {
            parameters = new String[]{
                    _toolPath,
                    "--config", _configFilePath,
                    "--reporter", "csv"
            };
        }

        String[] environment = new String[]{
                "DYLD_FRAMEWORK_PATH", frameworkSearchPath
        };

        process = Runtime.getRuntime().exec(parameters, environment, new File(frameworkSearchPath));
        stdOut = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "utf-8"));
    }

    private void sendFileToSwiftLint(@NotNull PsiElement file) throws IOException {
        stdOut.write(file.getText());
        stdOut.write(TERMINATOR + "\n");
        stdOut.flush();
    }

    List<String> executeSwiftLint(@NotNull final String toolPath, @Nullable final String configFilePath, @NotNull final PsiElement aElement) throws IOException {
        if (!toolPath.equals(_toolPath) || !configFilePath.equals(_configFilePath) || process == null || stdOut == null) {
            _toolPath = toolPath;
            _configFilePath = configFilePath;

            restartSwiftLint();
        }

        try {
            sendFileToSwiftLint(aElement);
        } catch (IOException aE) {
            restartSwiftLint();
            sendFileToSwiftLint(aElement);

            aE.printStackTrace();
        }

        final List<String> outputLines = new ArrayList<>();
        final List<String> errorLines = new ArrayList<>();
        Thread outputThread = new Thread(() -> {
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            try {
                String line;
                while ((line = inputStream.readLine()) != null) {
                    if (line.contains(TERMINATOR)) {
                        break;
                    }

                    outputLines.add(line);
                }

                while ((line = errorStream.readLine()) != null) {
                    if (line.contains(TERMINATOR)) {
                        break;
                    }

                    String testLine = line.toLowerCase();
                    if (testLine.contains("error") || testLine.contains("warning") || testLine.contains("invalid")) {
                        errorLines.add(line);
                    }
                }
            } catch (IOException ex) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                ex.printStackTrace();

                try {
                    closeProcess();
                } catch (IOException aE) {
                    aE.printStackTrace();
                }
            }
        });
        outputThread.start();

        try {
            outputThread.join(1000);
        } catch (InterruptedException ex) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
            ex.printStackTrace();

            try {
                closeProcess();
            } catch (IOException aE) {
                aE.printStackTrace();
            }
        }

        for (String errorLine : errorLines) {
            if (!errorLine.trim().isEmpty()) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: " + errorLine, NotificationType.INFORMATION));
            }
        }

        return outputLines;
    }
}
