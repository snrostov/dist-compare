package org.jetbrains.distcmp.report

import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractReporter(
    val settings: DiffSettings,
    val workManager: WorkManager
) :Reporter {
    val rootContext = DiffContext(settings, workManager, this)

    override val diffs = AtomicInteger()
    override val abortedDiffs = AtomicInteger()
    override val itemsByDigest = ConcurrentHashMap<ByteBuffer, Int>()

    override fun dir(item: Item, body: (DiffContext) -> Unit) {
        body(rootContext)
    }

    private fun Item.toInfo(fileKind: FileKind, fileStatus: FileStatus, diffs: Int) =
        FileInfo(id, relativePath, badExt, ext, fileStatus, fileKind, false, diffs)

    protected abstract fun writeItem(
        item: FileInfo,
        expected: String? = null,
        actual: String? = null
    )

    override fun reportMatch(item: Item, fileKind: FileKind) =
        writeItem(item.toInfo(fileKind, FileStatus.MATCHED, 0))

    override fun reportCopy(item: Item, fileKind: FileKind) =
        writeItem(item.toInfo(fileKind, FileStatus.COPY, 0))

    override fun reportMismatch(
        item: Item,
        status: FileStatus,
        fileKind: FileKind,
        expected: String?,
        actual: String?
    ) = writeItem(item.toInfo(fileKind, status, item.diffsCount), expected, actual)
}

data class FileInfo(
    val id: Int,
    val relativePath: String,
    val noExtension: Boolean,
    val extension: String,
    val status: FileStatus,
    val kind: FileKind,
    val suppressed: Boolean,
    val diffs: Int
)

enum class FileStatus {
    MATCHED, MISSED, UNEXPECTED, MISMATCHED, COPY
}

enum class FileKind {
    CLASS, TEXT, BIN, DIR
}