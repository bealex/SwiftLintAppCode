package com.lonelybytes.swiftlint.fileWatcher

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class ConfigurationFileWatcher(val project: Project) : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
        super.after(events)

        if (events.count { it.path.endsWith(".swiftlint.yml") } != 0) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}