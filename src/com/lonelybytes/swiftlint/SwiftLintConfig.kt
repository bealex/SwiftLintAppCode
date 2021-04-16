package com.lonelybytes.swiftlint

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.Yaml
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.*

class SwiftLintConfig(aProject: Project, aConfigPath: String?) {
    class Config(aPath: String) {
        var file: File = File(aPath)
        var excludedDirectories: List<String> = emptyList()
        var includedDirectories: List<String> = emptyList()

        private var _lastUpdateTime: Long = 0

        fun updateIfNeeded() {
            if (file.lastModified() > _lastUpdateTime) {
                try {
                    loadDisabledDirectories()
                } catch (aE: Throwable) {
                    excludedDirectories = emptyList()
                    includedDirectories = emptyList()
                }
                _lastUpdateTime = file.lastModified()
            }
        }

        @Throws(FileNotFoundException::class)
        private fun loadDisabledDirectories() {
            val yaml = Yaml()
            val yamlData = yaml.load<Map<String, Any>>(BufferedInputStream(FileInputStream(file)))
            excludedDirectories = ((yamlData["excluded"] as List<*>?) ?: emptyList<String>()).map { it.toString() }
            includedDirectories = ((yamlData["included"] as List<*>?) ?: emptyList<String>()).map { it.toString() }
        }

        init {
            updateIfNeeded()
        }
    }

    private val _configs: MutableMap<String?, Config> = HashMap()

    private fun getConfig(aFilePath: String): Config? {
        var directory = File(aFilePath)
        if (!directory.isDirectory) {
            directory = directory.parentFile
        }
        var config = _configs[directory.absolutePath]
        while (config == null && directory.parentFile != null) {
            val possibleConfigPath = File(directory.absolutePath + "/" + FILE_NAME)
            if (possibleConfigPath.exists()) {
                config = Config(possibleConfigPath.absolutePath)
                _configs[directory.absolutePath] = config
                break
            }
            directory = directory.parentFile
            config = _configs[directory.absolutePath]
        }
        config?.updateIfNeeded()
        return config
    }

    fun shouldBeLinted(aFilePath: String, isLintedByDefault: Boolean): Boolean {
        val config = getConfig(aFilePath) ?: return isLintedByDefault
        var result = true
        if (config.includedDirectories.isNotEmpty()) {
            result = config.includedDirectories.stream().anyMatch { aS: String -> aFilePath.contains("/$aS/") }
        }
        if (config.excludedDirectories.isNotEmpty()) {
            result = result && config.excludedDirectories.stream().noneMatch { aS: String -> aFilePath.contains("/$aS/") }
        }
        return result
    }

    private class DepthedFile(var _depth: Int, var _file: VirtualFile)

    companion object {
        const val FILE_NAME = ".swiftlint.yml"

        fun swiftLintConfigPath(aProject: Project, aDepthToLookAt: Int): String? {
            val projectRootManager = ProjectRootManager.getInstance(aProject)
            val roots = projectRootManager.contentSourceRoots
            for (root in roots) {
                val configFile = root.findChild(FILE_NAME)
                if (configFile != null) {
                    return configFile.canonicalPath
                }
            }

            val filesToLookAt: MutableList<DepthedFile> = roots
                    .flatMap { it.children.asList() }
                    .filter { it.isDirectory }
                    .map { DepthedFile(0, it) }
                    .toMutableList()

            while (filesToLookAt.isNotEmpty()) {
                val file = filesToLookAt.removeAt(0)
                if (file._depth > aDepthToLookAt) { break }

                if (file._file.findChild(FILE_NAME) != null) {
                    return file._file.toString() + "/" + FILE_NAME
                } else {
                    filesToLookAt.addAll(
                            file._file.children
                                .filter { it.isDirectory }
                                .map { DepthedFile(file._depth + 1, it) }
                    )
                }
            }

            return null
        }
    }

    init {
        val projectPath = aProject.basePath
        var path = aConfigPath
        if (path == null || !File(path).exists()) {
            path = swiftLintConfigPath(aProject, 6)
        }
        if (path != null) {
            _configs[projectPath] = Config(path)
        }
    }
}