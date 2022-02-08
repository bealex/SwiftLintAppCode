package com.lonelybytes.swiftlint

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class SwiftLint {
    companion object {
        private const val DEBUG_ON = false
        private const val THREAD_WAIT_TIMEOUT:Long = 10000

        fun log(message: String) {
            if (DEBUG_ON) println(message)
        }
    }

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
        return processSwiftLintOutput(process)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAutocorrect(aToolPath: String, aFilePath: String, aRunDirectory: File) {
        val params = mutableListOf(aToolPath, "autocorrect", "--no-cache", "--path", aFilePath)

        log(" --> # Run: '" + params.joinToString(separator = " ") + "' in '" + aRunDirectory.path + "'")

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        processSwiftLintOutput(process) // need this, otherwise swiftlint can wait indefinitely in case of lots of output
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun processAsApp(toolPath: String, aAction: String, aFilePath: String, aRunDirectory: File): List<String> {
        val params: MutableList<String> = mutableListOf(toolPath, aAction, "--no-cache", "--reporter", "csv", "--path", aFilePath)

        log(" --> # Run: '" + params.joinToString(separator = " ") + "' in '" + aRunDirectory.path + "'")

        val process = Runtime.getRuntime().exec(params.toTypedArray(), emptyArray(), aRunDirectory)
        return processSwiftLintOutput(process)
    }

    private fun processSwiftLintOutput(aProcess: Process): List<String> {
        log("Started to process swiftlint output...")

        val outputBufferedReader = aProcess.inputStream.bufferedReader(Charset.forName("UTF8"))
        var outputLines: List<String> = arrayListOf()
        val outputThread = thread(true) {
            try {
                outputLines = outputBufferedReader.readLines()
            } catch (e: IOException) {
                if (!e.message!!.contains("closed")) {
                    Notifications.Bus.notify(
                        Notification(Configuration.KEY_SWIFTLINT, "Error", "IOException: " + e.message, NotificationType.INFORMATION)
                    )
                }
                e.printStackTrace()
            }
        }

        val errorBufferedReader = aProcess.errorStream.bufferedReader(Charset.forName("UTF8"))
        var errorLines: List<String> = arrayListOf()
        val errorThread = thread(true) {
            try {
                errorLines = errorBufferedReader.readLines()
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
        }

        outputThread.join(THREAD_WAIT_TIMEOUT)
        errorThread.join(THREAD_WAIT_TIMEOUT)

        for (errorLine in errorLines) {
            if (errorLine.trim { it <= ' ' }.isNotEmpty()) {
                Notifications.Bus.notify(
                    Notification(Configuration.KEY_SWIFTLINT, "Error", "SwiftLint error: $errorLine", NotificationType.INFORMATION)
                )
            }
        }

        log(" --> # Output: \n\t" + outputLines.joinToString(separator = "\n\t"))
        log(" --> # Error: \n\t" + errorLines.joinToString(separator = "\n\t"))

        return outputLines
    }
}