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
    private static final String TERMINATOR = "|||end|||";

    private String _toolPath;
    private String _configPath;

    private Process process;
    private BufferedWriter stdOut;

    private void closeProcess() throws IOException {
        if (stdOut != null) {
            stdOut.close();
        }

        if (process != null) {
            process.destroy();
        }

        stdOut = null;
        process = null;
    }

    private void restartSwiftLint() throws IOException {
        String frameworkSearchPath = _toolPath.substring(0, _toolPath.lastIndexOf("/"));

        closeProcess();

        String[] parameters = processParameters();

        String[] environment = new String[]{
                "DYLD_FRAMEWORK_PATH", frameworkSearchPath
        };

        process = Runtime.getRuntime().exec(parameters, environment, new File(frameworkSearchPath));
        stdOut = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "utf-8"));
    }

    @NotNull
    private String[] processParameters() {
        return processParameters("lint");
    }

    @NotNull
    private String[] processParameters(@NotNull final String aAction) {
        String[] parameters;
        if (_configPath == null) {
            parameters = new String[]{
                    _toolPath, aAction,
                    "--use-stdin",
                    "--no-cache",
                    "--reporter", "csv"
            };
        } else {
            parameters = new String[]{
                    _toolPath, aAction,
                    "--use-stdin",
                    "--no-cache",
                    "--config", _configPath,
                    "--reporter", "csv"
            };
        }
        return parameters;
    }

    List<String> executeSwiftLint(@NotNull final String toolPath, @NotNull final String aAction, @Nullable String configPath, @NotNull final PsiElement aElement, Document aDocument) throws IOException, InterruptedException {
        if (aAction.equals("autocorrect") && aElement instanceof PsiFile) {
            if (toolPath.endsWith("swiftLintService")) {
                throw new IOException("Autocorrection is unavailable when using swiftLintService");
            } else {
                processAutocorrect(toolPath, configPath, ((PsiFile) aElement));
            }
        } else {
            String fileText = "";
            if (aDocument != null) {
                fileText = aDocument.getText();
            } else {
                fileText = aElement.getText();
            }

            if (toolPath.endsWith("swiftlint")) {
                return processAsApp(toolPath, aAction, configPath, fileText);
            } else if (toolPath.endsWith("swiftLintService")) {
                return processAsService(toolPath, configPath, fileText);
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

        process = Runtime.getRuntime().exec(parameters);
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
    private List<String> processAsApp(@NotNull String toolPath, @NotNull final String aAction, @Nullable String configPath, @NotNull String aFileText) throws IOException, InterruptedException {
        _configPath = configPath;
        _toolPath = toolPath;

        String[] parameters = processParameters(aAction);

        process = Runtime.getRuntime().exec(parameters);
        stdOut = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "utf-8"));

        final List<String> outputLines = sendFileAndProcessSwiftLintOutput(aFileText, false);

        closeProcess();

        stdOut = null;
        process = null;

        return outputLines;
    }

    @NotNull
    private List<String> processAsService(@NotNull String toolPath, @Nullable String configPath, @NotNull String aElement) throws IOException, InterruptedException {
        boolean configPathDiffers = (configPath == null && _configPath != null) || (configPath != null && !configPath.equals(_configPath));

        if (!toolPath.equals(_toolPath) || configPathDiffers || process == null || stdOut == null) {
            _toolPath = toolPath;
            _configPath = configPath;

            restartSwiftLint();
        }

        return sendFileAndProcessSwiftLintOutput(aElement, true);
    }

    @NotNull
    private List<String> sendFileAndProcessSwiftLintOutput(String aFileText, boolean serviceMode) throws IOException, InterruptedException {
        final List<String> outputLines = new ArrayList<>();
        final List<String> errorLines = new ArrayList<>();

        stdOut.write(aFileText);

        if (serviceMode) {
            stdOut.write(TERMINATOR + "\n");
            stdOut.flush();
        }

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

                if (serviceMode) {
                    try {
                        closeProcess();
                    } catch (IOException aE) {
                        aE.printStackTrace();
                    }
                }
            }
        });
        outputThread.start();

        if (!serviceMode) {
            stdOut.flush();
            stdOut.close();
        }

        try {
            outputThread.join(1000);
            if (outputThread.isAlive()) {
                closeProcess();
            }
        } catch (InterruptedException e) {
            if (serviceMode) {
                closeProcess();
            }

            throw e;
        }

        for (String errorLine : errorLines) {
            if (!errorLine.trim().isEmpty()) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: " + errorLine, NotificationType.INFORMATION));
            }
        }
        
        return outputLines;
    }
}
