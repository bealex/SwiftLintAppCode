package com.lonelybytes.swiftlint;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Utils {
    static String executeCommandOnFile(final String command, final String options, @NotNull final PsiFile file) throws IOException {
        List<String> parameters = new ArrayList<>();
        parameters.add(command);
        parameters.addAll(Arrays.asList(options.split(" ")));
        parameters.add(file.getVirtualFile().getCanonicalPath());

        final Process process = Runtime.getRuntime().exec(parameters.toArray(new String[0]));
        final StringBuilder errString = new StringBuilder();

        Thread errorThread = new Thread(() -> {
            BufferedReader errStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            try {
                while ((line = errStream.readLine()) != null) {
                    errString.append(line).append("\n");
                }
            } catch (IOException ex) {
                Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                ex.printStackTrace();
            } finally {
                try {
                    errStream.close();
                } catch (IOException ex) {
                    Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
                    ex.printStackTrace();
                }
            }
        });
        errorThread.start();

        try {
            errorThread.join();
        } catch (InterruptedException ex) {
            Notifications.Bus.notify(new Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + ex.getMessage(), NotificationType.INFORMATION));
            ex.printStackTrace();
        }

        return errString.toString();
    }
}
