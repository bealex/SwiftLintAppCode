package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Utils {
    static String executeCommandOnFile(final String command, final String[] options, @NotNull final PsiFile file) throws IOException {
        List<String> parameters = new ArrayList<>();
        parameters.add(command);
        parameters.addAll(Arrays.asList(options));

        final Process process = Runtime.getRuntime().exec(parameters.toArray(new String[0]));
        BufferedWriter stdOut = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        stdOut.write(file.getText());

        final StringBuilder outputStrings = new StringBuilder();
        final StringBuilder errorStrings = new StringBuilder();
        Thread errorThread = new Thread(() -> {
            BufferedReader outputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            try {
                String line;
                while ((line = outputStream.readLine()) != null) {
                    outputStrings.append(line).append("\n");
                }

                while ((line = errorStream.readLine()) != null) {
                    if (line.toLowerCase().contains("error") || line.toLowerCase().contains("warning") || line.toLowerCase().contains("invalid")) {
                        errorStrings.append(line).append("\n");
                    }
                }
            } catch (IOException ex) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                ex.printStackTrace();
            } finally {
                try {
                    outputStream.close();
                    errorStream.close();
                } catch (IOException ex) {
                    Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                    ex.printStackTrace();
                }
            }
        });
        errorThread.start();

        stdOut.flush();
        stdOut.close();

        try {
            errorThread.join();
        } catch (InterruptedException ex) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
            ex.printStackTrace();
        }

        String errorString = errorStrings.toString().trim();
        if (!errorString.isEmpty()) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: " + errorString, NotificationType.INFORMATION));
        }

        return outputStrings.toString();
    }
}
