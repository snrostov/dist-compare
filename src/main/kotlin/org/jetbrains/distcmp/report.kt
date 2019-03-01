package org.jetbrains.distcmp

import com.github.difflib.patch.Delta
import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.security.MessageDigest
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

val diffs = AtomicInteger()
val abortedDiffs = AtomicInteger()
val lastId = AtomicInteger()
val itemsByDigest = ConcurrentHashMap<ByteBuffer, Int>()

val patchDigest = ConcurrentHashMap<ByteBuffer, Int>()
val patchId = AtomicInteger()

fun deltasDigests(deltas: MutableList<Delta<String>>): List<Int> {
    return deltas.mapTo(mutableSetOf()) {
        val md5 = MessageDigest.getInstance("MD5")
        it.original.lines.forEach {
            md5.update(it.toByteArray())
        }
        it.revised.lines.forEach {
            md5.update(it.toByteArray())
        }
        val bb = ByteBuffer.wrap(md5.digest())
        patchDigest.getOrPut(bb) { patchId.incrementAndGet() }
    }.toList()
}

fun Item.toInfo(fileKind: FileKind, fileStatus: FileStatus, diffs: Int) =
    FileInfo(id, relativePath, badExt, ext, fileStatus, fileKind, false, diffs)

private fun writeItem(item: FileInfo) {
    WorkManager.io {
        gson.toJson(item, item.javaClass, jsonWriter)
    }
}

fun Item.reportMatch(fileKind: FileKind) {
    val item = toInfo(fileKind, FileStatus.MATCHED, 0)
    writeItem(item)
}

fun Item.reportCopy(fileKind: FileKind) {
    val item = toInfo(fileKind, FileStatus.COPY, 0)
    writeItem(item)
}

fun Item.reportMismatch(
    status: FileStatus,
    fileKind: FileKind
) {
    val item = toInfo(fileKind, status, diffsCount)
    writeItem(item)
}

fun Item.writeDiff(ext: String = "patch", outputWriter: (PrintWriter.() -> Unit)? = null) {
    if (outputWriter == null) return

    WorkManager.io {
        val reportFile = File("${diffDir.path}/$relativePath.$ext")
        reportFile.parentFile.mkdirs()
        reportFile.printWriter().use {
            outputWriter(it)
        }
    }
}

fun Item.writeDiffAborted(reason: String) {
    abortedDiffs.incrementAndGet()
    writeDiff {
        println("[DIFF-ABORTED] $reason")
    }
}


fun printTotals() {
    println("total: ${lastId}")
}
