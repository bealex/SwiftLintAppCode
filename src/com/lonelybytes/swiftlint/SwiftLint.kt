package com.lonelybytes.swiftlint

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


class SwiftLint {
    @Throws(IOException::class, InterruptedException::class)
    fun executeSwiftLint(toolPath: String, aAction: String, aConfig: SwiftLintConfig?, aFilePath: String, aRunDirectory: File): List<String> {
        aConfig ?: return emptyList()

        if (aAction == "autocorrect") {
            processAutocorrect(toolPath, aFilePath, aRunDirectory)
        } else {
            if (aConfig.shouldBeLinted(aFilePath, true)) {
                return processAsApp(toolPath, aAction, aFilePath, aRunDirectory)
            }
        }

         return emptyList()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getSwiftLintRulesList(aToolPath: String): List<String> {
        val params = mutableListOf(
                aToolPath,
                "rules"
        )
        val process = Runtime.getRuntime().exec(params.toTypedArray())
        process.waitFor()
        return processSwiftLintOutput(process)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAutocorrect(aToolPath: String, aFilePath: String, aRunDirectory: File) {
        val params = mutableListOf(
                aToolPath,
                "autocorrect",
                "--no-cache",
                "--path", aFilePath
        )

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        process.waitFor()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAsApp(toolPath: String, aAction: String, aFilePath: String, aRunDirectory: File): List<String> {
        val params: MutableList<String> = mutableListOf(
                toolPath,
                aAction,
                "--no-cache",
                "--reporter", "csv",
                "--path", aFilePath
        )

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        process.waitFor()
        return processSwiftLintOutput(process)
    }

    private fun processSwiftLintOutput(aProcess: Process): List<String> {
        var outputLines: List<String> = emptyList()
        var errorLines: List<String> = emptyList()
        try {
            outputLines = BufferedReader(InputStreamReader(aProcess.inputStream))
                    .readLines()
            errorLines = BufferedReader(InputStreamReader(aProcess.errorStream))
                    .readLines()
                    .filter {
                        val testLine = it.toLowerCase()
                        testLine.contains("error:") || testLine.contains("warning:") || testLine.contains("invalid:") || testLine.contains("unrecognized arguments:")
                    }
        } catch (e: IOException) {
            if (!e.message!!.contains("closed")) {
                Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.message, NotificationType.INFORMATION))
            }
            e.printStackTrace()
        }

        for (errorLine in errorLines) {
            if (errorLine.trim { it <= ' ' }.isNotEmpty()) {
                Notifications.Bus.notify(Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: $errorLine", NotificationType.INFORMATION))
            }
        }
        return outputLines
    }
}