package org.jetbrains.distcmp

data class FileInfo(
    val id: Int,
    val relativePath: String,
    val noExtension: Boolean,
    val extension: String,
    val status: FileStatus,
    val kind: FileKind,
    val suppressed: Boolean
)

enum class FileStatus {
    MATCHED, MISSED, UNEXPECTED, MISMATCHED
}

enum class FileKind {
    CLASS, TEXT, BIN
}