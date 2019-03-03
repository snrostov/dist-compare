package org.jetbrains.distcmp.report

import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.Item
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface Reporter {
    fun beginReport()
    fun close()

    fun dir(item: Item, body: (DiffContext) -> Unit)
    fun reportMatch(item: Item, fileKind: FileKind)
    fun reportCopy(item: Item, fileKind: FileKind)
    fun reportMismatch(
        item: Item,
        status: FileStatus,
        fileKind: FileKind,
        expected: String? = null,
        actual: String? = null
    )

    fun writeDiff(
        item: Item,
        ext: String = "patch",
        outputWriter: (PrintWriter.() -> Unit)? = null
    )

    fun writeDiffAborted(item: Item, reason: String)
}