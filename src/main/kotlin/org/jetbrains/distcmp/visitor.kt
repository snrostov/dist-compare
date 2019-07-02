package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.algorithm.myers.MyersDiff
import org.apache.commons.vfs2.FileObject
import org.jetbrains.distcmp.report.FileKind
import org.jetbrains.distcmp.report.FileStatus
import org.jetbrains.distcmp.report.Reporter
import org.jetbrains.distcmp.utils.classToTxt
import org.jetbrains.distcmp.utils.isBadExt
import org.jetbrains.distcmp.utils.kind
import org.jetbrains.distcmp.utils.md5
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lazy tree of file items (including archive contents)
 * No references are stored between items
 */
class Item(val id: Long, val relativePath: String, ext: String) {
    // heruistic to detect files without extenstion
    val badExt = isBadExt(ext)
    val ext = if (badExt) "" else ext
    var diffsCount = 0

    fun child(id: Long, name: String, ext: String) = Item(id, "$relativePath/$name", ext)
}

data class DiffContext(
    val settings: DiffSettings,
    val workManager: IWorkManager,
    val reporter: Reporter
)

class ItemVisitor {
    private val lastId = AtomicLong()

    private val maxComparsions = 1000000 // 1 sec

    private val diffs = AtomicInteger()
    private val abortedDiffs = AtomicInteger()
    private val itemsByDigest = ConcurrentHashMap<ByteBuffer, Long>()

    val stats: String
        get() = "$lastId files, $diffs diffs ($abortedDiffs aborted)"

    fun visit(
        item: Item,
        context: DiffContext,
        expected: FileObject?,
        actual: FileObject?
    ) {
        require(expected != null || actual != null)
        val expectedOrActual = expected ?: actual!!

        when {
            expectedOrActual.isFolder -> visitDirectory(
                item,
                context,
                expected?.children?.toList(), actual?.children?.toList()
            )
            manager.canCreateFileSystem(expectedOrActual) -> visitDirectory(
                item,
                context,
                if (expected == null) null else manager.createFileSystem(expected).children.toList(),
                if (actual == null) null else manager.createFileSystem(actual).children.toList()
            )
            else -> visitFile(item, context, expected, actual)
        }
    }

    private fun visitDirectory(
        item: Item,
        parentContext: DiffContext,
        expectedChildren: List<FileObject>?,
        actualChildren: List<FileObject>?
    ) {
        require(expectedChildren != null || actualChildren != null)

        parentContext.reporter.dir(item) { context ->
            val reporter = context.reporter

            if (expectedChildren == null) reporter.reportMismatch(item, FileStatus.UNEXPECTED, FileKind.DIR)
            else if (actualChildren == null) reporter.reportMismatch(item, FileStatus.MISSED, FileKind.DIR)

            val actualChildrenByName = actualChildren?.associateBy { it.name.baseName }
            val expectedChildrenNames = mutableSetOf<String>()

            // visit all expected items, and try to find corresponding actual
            expectedChildren?.forEach { expected ->
                val name = expected.name.baseName

                val actual = actualChildrenByName?.get(name)
                if (actual != null) expectedChildrenNames.add(name)

                val childItem = child(item, expected)
                visit(childItem, context, expected, actual)
            }

            // mark unexpected items
            actualChildren?.forEach { actual ->
                val name = actual.name.baseName
                if (name !in expectedChildrenNames) {
                    val childItem = child(item, actual)
                    visit(childItem, context, null, actual)
                }
            }
        }
    }

    private fun child(parent: Item, obj: FileObject) =
        parent.child(lastId.incrementAndGet(), obj.name.baseName, obj.name.extension)

    private fun visitFile(
        item: Item,
        context: DiffContext,
        expected: FileObject?,
        actual: FileObject?
    ) {
        when {
            expected == null -> visitUnpaired(item, context, actual!!, FileStatus.UNEXPECTED)
            actual == null -> visitUnpaired(item, context, expected, FileStatus.MISSED)
            else -> context.workManager.submit("READING ${item.relativePath}") {
                when (expected.kind) {
                    FileKind.CLASS -> matchClass(item, context, expected, actual)
                    FileKind.TEXT -> matchText(item, context, expected, actual)
                    FileKind.BIN -> matchBin(item, context, expected, actual)
                    FileKind.DIR -> error("should not be directory")
                }
            }
        }
    }

    private fun visitUnpaired(
        item: Item,
        context: DiffContext,
        existed: FileObject,
        status: FileStatus
    ) {
        // check for copy (requires files content, so submit it in queue)
        context.workManager.submit("READING UNPAIRED ${item.relativePath}") {
            if (isAlreadyAnalyzed(item, existed.md5())) context.reporter.reportCopy(item, existed.kind)
            else context.reporter.reportMismatch(item, status, existed.kind)
        }
    }

    private fun matchClass(
        item: Item,
        context: DiffContext,
        expected: FileObject,
        actual: FileObject
    ) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        val reporter = context.reporter
        when {
            isAlreadyAnalyzed(item, context, expectedTxt, actualTxt) -> reporter.reportCopy(item, FileKind.CLASS)
            actualTxt == expectedTxt -> reporter.reportMatch(item, FileKind.CLASS)
            else -> matchText(
                item,
                context,
                FileKind.CLASS,
                classToText(context, expected),
                classToText(context, actual),
                expected,
                actual
            )
        }
    }

    private fun matchText(
        item: Item,
        context: DiffContext,
        expected: FileObject,
        actual: FileObject
    ) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        if (isAlreadyAnalyzed(item, context, expectedTxt, actualTxt)) context.reporter.reportCopy(item, FileKind.TEXT)
        else matchText(item, context, FileKind.TEXT, expectedTxt, actualTxt, expected, actual)
    }

    private fun matchBin(item: Item, context: DiffContext, expected: FileObject, actual: FileObject) {
        if (expected.content.size != actual.content.size) {
            context.reporter.reportMismatch(item, FileStatus.MISMATCHED, FileKind.BIN)
        } else {
            val expectedTxt = expected.content.inputStream.bufferedReader().readText()
            val actualTxt = actual.content.inputStream.bufferedReader().readText()

            when {
                isAlreadyAnalyzed(item, context, expectedTxt, actualTxt) -> context.reporter.reportCopy(
                    item,
                    FileKind.BIN
                )
                expectedTxt == actualTxt -> context.reporter.reportMatch(item, FileKind.BIN)
                else -> context.reporter.reportMismatch(item, FileStatus.MISMATCHED, FileKind.BIN)
            }
        }
    }

    class TooManyComparsions : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun matchText(
        item: Item,
        context: DiffContext,
        fileKind: FileKind,
        expectedTxt: String,
        actualTxt: String,
        expected: FileObject,
        actual: FileObject
    ) {
        val reporter = context.reporter

        if (expectedTxt == actualTxt) {
            reporter.reportMatch(item, fileKind)
            if (context.settings.saveMatchedContents) {
                reporter.writeDiff(item, "contents") { print(expectedTxt) }
            }
        } else {
            reporter.reportMismatch(item, FileStatus.MISMATCHED, fileKind, expectedTxt, actualTxt)

            var expectedAndActualWasSaved = false

            fun ensureExpectedAndActualSaved() {
                if (expectedAndActualWasSaved) return
                expectedAndActualWasSaved = true

                reporter.writeDiff(item, "a.expected.txt") { print(expectedTxt) }
                reporter.writeDiff(item, "b.actual.txt") { print(actualTxt) }
            }

            if (context.settings.saveExpectedAndActual) {
                ensureExpectedAndActualSaved()
            }

            if (context.settings.runDiff) {
                val lines = expectedTxt.lines()
                if (lines.size > 10000) {
                    reportDiffAborted(reporter, item, "File too large (${lines.size} lines > 10000)")
                    ensureExpectedAndActualSaved()
                } else {
                    diffs.incrementAndGet()
                    context.workManager.submit("DIFF FOR ${item.relativePath}") {
                        var i = 0
                        try {
                            val patches = DiffUtils.diff<String>(lines, actualTxt.lines(), MyersDiff<String> { a, b ->
                                if (i++ > maxComparsions) throw TooManyComparsions()
                                a == b
                            })

                            val deltas = patches.deltas
                            item.diffsCount = deltas.size
                            val diff = UnifiedDiffUtils.generateUnifiedDiff(
                                expected.url.toString(),
                                actual.url.toString(),
                                lines,
                                patches,
                                5
                            )

                            val limit = 1000
                            reporter.writeDiff(item) {
                                diff.asSequence().take(limit).forEach {
                                    println(it)
                                }

                                if (diff.size > limit) {
                                    println("And more ${diff - limit}...")
                                }
                            }
                        } catch (e: TooManyComparsions) {
                            reportDiffAborted(
                                reporter,
                                item,
                                "Building diff takes too long ($i comparisons). " +
                                        "Please see origin files " +
                                        "(saved below with extensions .a.expected.txt and .b.actual.txt)"
                            )

                            ensureExpectedAndActualSaved()
                        }
                    }
                }
            }
        }
    }

    private fun reportDiffAborted(
        reporter: Reporter,
        item: Item,
        reason: String
    ) {
        abortedDiffs.incrementAndGet()
        reporter.writeDiffAborted(item, reason)
    }

    private fun isAlreadyAnalyzed(
        item: Item,
        context: DiffContext,
        expectedTxt: String,
        actualTxt: String
    ): Boolean {
        return false
//        val md5 = MessageDigest.getInstance("MD5")
//        md5.update(expectedTxt.toByteArray())
//        md5.update(actualTxt.toByteArray())
//        return isAlreadyAnalyzed(item, md5)
    }

    private fun isAlreadyAnalyzed(
        item: Item,
        md5: MessageDigest
    ): Boolean {
        val digest = ByteBuffer.wrap(md5.digest())
        return itemsByDigest.getOrPut(digest) { item.id } != item.id
    }

    private fun classToText(context: DiffContext, expected: FileObject): String {
        val txt = classToTxt(
            context.settings,
            expected.content.inputStream,
            expected.name.baseName,
            expected.content.lastModifiedTime,
            expected.url.toURI()
        )

        return when {
            context.settings.compareClassVerbose -> {
                // remove timestamp and checksum
                txt.lines().drop(3).joinToString("\n")
            }
            context.settings.compareClassVerboseIgnoreCompiledFrom -> {
                txt.lines().drop(1).joinToString("\n")
            }
            else -> txt
        }
    }
}