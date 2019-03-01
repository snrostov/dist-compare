package org.jetbrains.distcmp

import com.google.gson.GsonBuilder
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

interface Reporter {
    val diffs: AtomicInteger
    val abortedDiffs: AtomicInteger
    val itemsByDigest: ConcurrentHashMap<ByteBuffer, Int>
    fun beginReport()
    fun close()
    fun reportMatch(item: Item, fileKind: FileKind)
    fun reportCopy(item: Item, fileKind: FileKind)
    fun reportMismatch(
        item: Item,
        status: FileStatus,
        fileKind: FileKind
    )

    fun writeDiff(
        item: Item,
        ext: String = "patch",
        outputWriter: (PrintWriter.() -> Unit)? = null
    )

    fun writeDiffAborted(item: Item, reason: String)
}

class JsonReporter(val context: DiffContext) : Reporter {
    override val diffs = AtomicInteger()
    override val abortedDiffs = AtomicInteger()
    override val itemsByDigest = ConcurrentHashMap<ByteBuffer, Int>()

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val jsonWriter = gson.newJsonWriter(context.settings.diffDir.resolve("data.json").writer())

    override fun beginReport() {
        jsonWriter.beginArray()
    }

    override fun close() {
        jsonWriter.endArray()
        jsonWriter.close()
    }

    private fun Item.toInfo(fileKind: FileKind, fileStatus: FileStatus, diffs: Int) =
        FileInfo(id, relativePath, badExt, ext, fileStatus, fileKind, false, diffs)

    private fun writeItem(item: FileInfo) {
        context.workManager.io {
            gson.toJson(item, item.javaClass, jsonWriter)
        }
    }

    override fun reportMatch(item: Item, fileKind: FileKind) {
        writeItem(item.toInfo(fileKind, FileStatus.MATCHED, 0))
    }

    override fun reportCopy(item: Item, fileKind: FileKind) {
        writeItem(item.toInfo(fileKind, FileStatus.COPY, 0))
    }

    override fun reportMismatch(
        item: Item,
        status: FileStatus,
        fileKind: FileKind
    ) {
        writeItem(item.toInfo(fileKind, status, item.diffsCount))
    }

    override fun writeDiff(
        item: Item,
        ext: String,
        outputWriter: (PrintWriter.() -> Unit)?
    ) {
        if (outputWriter == null) return

        context.workManager.io {
            val reportFile = File("${context.settings.diffDir.path}/${item.relativePath}.$ext")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use {
                outputWriter(it)
            }
        }
    }

    override fun writeDiffAborted(item: Item, reason: String) {
        abortedDiffs.incrementAndGet()
        writeDiff(item) {
            println("[DIFF-ABORTED] $reason")
        }
    }
}