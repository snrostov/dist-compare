package org.jetbrains.distcmp

import com.github.difflib.patch.Delta
import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    outputWriter: (PrintWriter.(FileInfo) -> Unit)? = null
) {
    val item = toInfo(fileKind, status, diffsCount)

    if (outputWriter != null && writeDiff) {
        ioExecutor.submit {
            val reportFile =
                File("${diffDir.path}/$relativePath.patch")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use {
                outputWriter(it, item)
            }
        }
    }

    writeItem(item)
}

fun printTotals() {
    println("total: ${lastId}")
}
