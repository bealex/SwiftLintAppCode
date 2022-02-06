package com.lonelybytes.swiftlint

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit


class SwiftLint {
    @Throws(IOException::class, InterruptedException::class)
    fun executeSwiftLint(toolPath: String, aAction: String, aFilePath: String, aRunDirectory: File): List<String> {
        return if (aAction == "autocorrect") {
            processAutocorrect(toolPath, aFilePath, aRunDirectory)
            emptyList()
        } else {
            processAsApp(toolPath, aAction, aFilePath, aRunDirectory)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun getSwiftLintRulesList(aToolPath: String): List<String> {
        val params = mutableListOf(aToolPath, "rules")
        val process = Runtime.getRuntime().exec(params.toTypedArray())
        process.waitFor(1, TimeUnit.SECONDS)
        return processSwiftLintOutput(process)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAutocorrect(aToolPath: String, aFilePath: String, aRunDirectory: File) {
        val params = mutableListOf(aToolPath, "autocorrect", "--no-cache", "--path", aFilePath)

//        println(" --> # Run: '" + params.joinToString(separator = " ") + "' in '" + aRunDirectory.path + "'")

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        process.waitFor(1, TimeUnit.SECONDS)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAsApp(toolPath: String, aAction: String, aFilePath: String, aRunDirectory: File): List<String> {
        val params: MutableList<String> = mutableListOf(toolPath, aAction, "--no-cache", "--reporter", "csv", "--path", aFilePath)

//        println(" --> # Run: '" + params.joinToString(separator = " ") + "' in '" + aRunDirectory.path + "'")

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        process.waitFor(1, TimeUnit.SECONDS)
        return processSwiftLintOutput(process)
    }

    private fun processSwiftLintOutput(aProcess: Process): List<String> {
//        println("Started to process swiftlint output...")

        var outputLines: List<String> = arrayListOf()
        var errorLines: List<String> = arrayListOf()
        try {
            val output = aProcess.inputStream.bufferedReader(Charset.forName("UTF8")).use(BufferedReader::readText)
            outputLines = output.split("\n")

            val error = aProcess.errorStream.bufferedReader(Charset.forName("UTF8")).use(BufferedReader::readText)
            errorLines = error.split("\n")
                .filter {
                    val line = it.lowercase()
                    line.contains("error:") || line.contains("warning:") || line.contains("invalid:") || line.contains("unrecognized arguments:")
                }
        } catch (e: IOException) {
            if (!e.message!!.contains("closed")) {
                Notifications.Bus.notify(
                    Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.message, NotificationType.INFORMATION)
                )
            }
            e.printStackTrace()
        }

        for (errorLine in errorLines) {
            if (errorLine.trim { it <= ' ' }.isNotEmpty()) {
                Notifications.Bus.notify(
                    Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: $errorLine", NotificationType.INFORMATION)
                )
            }
        }

//        println(" --> # Output: \n\t" + outputLines.joinToString(separator = "\n\t"))
//        println(" --> # Error: \n\t" + errorLines.joinToString(separator = "\n\t"))

        return outputLines
    }
}