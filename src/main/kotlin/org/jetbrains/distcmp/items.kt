package org.jetbrains.distcmp

import com.github.difflib.patch.Delta
import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

val lastId = AtomicInteger()
val items = mutableListOf<FileInfo>()
val itemsByDigest = ConcurrentHashMap<ByteBuffer, Item>()
val itemsByStatus = TreeMap<String, MutableList<Item>>()

val patchDigest = ConcurrentHashMap<ByteBuffer, Int>()
val patchId = AtomicInteger()

fun deltasDigests(
    deltas: MutableList<Delta<String>>
): List<Int> {
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

fun Item.reportMatch(fileKind: FileKind) {
    synchronized(itemsByStatus) {
        itemsByStatus.getOrPut("TOTAL.MATCHED") { mutableListOf() }.add(this)
    }

    synchronized(items) {
        items.add(
            FileInfo(
                id,
                relativePath,
                badExt,
                ext,
                FileStatus.MATCHED,
                fileKind,
                false,
                0
            )
        )
    }
}

fun Item.reportCopy(fileKind: FileKind) {
    synchronized(itemsByStatus) {
        itemsByStatus.getOrPut("COPY") { mutableListOf() }.add(this)
    }

    synchronized(items) {
        items.add(
            FileInfo(
                id,
                relativePath,
                badExt,
                ext,
                FileStatus.COPY,
                fileKind,
                false,
                0
            )
        )
    }
}

fun Item.reportMismatch(
    mismatchExt: FileStatus,
    fileKind: FileKind,
    writeDiff: Boolean = false,
    outputWriter: (PrintWriter.(FileInfo) -> Unit)? = null
) {
    val kind = if (badExt) "OTHER.$mismatchExt" else "$ext.$mismatchExt"
    val item =
        FileInfo(id, relativePath, badExt, ext, mismatchExt, fileKind, false, diffsCount)

    synchronized(itemsByStatus) {
        itemsByStatus.getOrPut(kind) { mutableListOf() }.add(this)
        itemsByStatus.getOrPut("TOTAL.$mismatchExt") { mutableListOf() }.add(this)
    }

    synchronized(items) {
        items.add(item)
    }

    if (outputWriter != null && writeDiff && writeFiles) {
        val reportFile =
            File("${diffDir.path}/$relativePath.patch")
        reportFile.parentFile.mkdirs()
        reportFile.printWriter().use {
            outputWriter(it, item)
        }
    }
}

fun printTotals() {
    println("total: ${items.size}")

    itemsByStatus
        .toList()
        .sortedByDescending { it.second.size }
        .take(10).forEach { (type, list) ->
            val diffs = list.sumBy { it.diffsCount }
            if (diffs > 0) {
                println("$type: ${list.size} items / $diffs diffs")
            } else {
                println("$type: ${list.size} items")
            }
        }

    val deltaUsages = mutableMapOf<Int, Int>()

    items.forEach {
        it.deltas.forEach {
            deltaUsages[it] = deltaUsages.getOrDefault(it, 0) + 1
        }
    }

    println("Top mismatches (10 of ${deltaUsages.size}):")
    deltaUsages.entries.sortedByDescending { it.value }.take(10).forEach { (id, count) ->
        println("- $id ($count)")
    }
}
