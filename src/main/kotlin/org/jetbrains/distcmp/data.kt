package org.jetbrains.distcmp

data class FileInfo(
    val id: Int,
    val relativePath: String,
    val noExtension: Boolean,
    val extension: String,
    val status: FileStatus,
    val kind: FileKind,
    val suppressed: Boolean,
    val diffs: Int
) {
    var deltas: List<Int> = listOf()
}

enum class FileStatus {
    MATCHED, MISSED, UNEXPECTED, MISMATCHED, COPY
}

enum class FileKind {
    CLASS, TEXT, BIN
}