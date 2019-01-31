package org.jetbrains.distcmp

import com.github.difflib.patch.Delta
import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    ioExecutor.submit {
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
    fileKind: FileKind,
    writeDiff: Boolean = false,
    outputWriter: (PrintWriter.() -> Unit)? = null
) {
    val item = toInfo(fileKind, status, diffsCount)
    writeItem(item)
}

fun Item.writeDiff(ext: String = "patch", outputWriter: (PrintWriter.() -> Unit)? = null) {
    if (outputWriter == null) return

    ioExecutor.submit {
        val reportFile =
            File("${diffDir.path}/$relativePath.$ext")
        reportFile.parentFile.mkdirs()
        reportFile.printWriter().use {
            outputWriter(it)
        }
    }
}

fun printTotals() {
    println("total: ${lastId}")
}
