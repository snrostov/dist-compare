package org.jetbrains.distcmp

import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    CLASS, TEXT, BIN, DIR
}

class Reporter(val context: DiffContext) {
    val diffs = AtomicInteger()
    val abortedDiffs = AtomicInteger()
    val itemsByDigest = ConcurrentHashMap<ByteBuffer, Int>()

    private fun Item.toInfo(fileKind: FileKind, fileStatus: FileStatus, diffs: Int) =
        FileInfo(id, relativePath, badExt, ext, fileStatus, fileKind, false, diffs)

    private fun writeItem(item: FileInfo) {
        context.workManager.io {
            gson.toJson(item, item.javaClass, jsonWriter)
        }
    }

    fun reportMatch(item: Item, fileKind: FileKind) {
        writeItem(item.toInfo(fileKind, FileStatus.MATCHED, 0))
    }

    fun reportCopy(item: Item, fileKind: FileKind) {
        writeItem(item.toInfo(fileKind, FileStatus.COPY, 0))
    }

    fun reportMismatch(
        item: Item,
        status: FileStatus,
        fileKind: FileKind
    ) {
        writeItem(item.toInfo(fileKind, status, item.diffsCount))
    }

    fun writeDiff(
        item: Item,
        ext: String = "patch",
        outputWriter: (PrintWriter.() -> Unit)? = null
    ) {
        if (outputWriter == null) return

        context.workManager.io {
            val reportFile = File("${diffDir.path}/${item.relativePath}.$ext")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use {
                outputWriter(it)
            }
        }
    }

    fun writeDiffAborted(item: Item, reason: String) {
        abortedDiffs.incrementAndGet()
        writeDiff(item) {
            println("[DIFF-ABORTED] $reason")
        }
    }
}