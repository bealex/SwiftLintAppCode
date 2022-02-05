package com.lonelybytes.swiftlint.annotator

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile

class InitialInfo internal constructor(
    val file: PsiFile,
    val path: String,
    val document: Document?,
    val shouldProcess: Boolean,
    val runDirectoryFromConfigPath: String?
)