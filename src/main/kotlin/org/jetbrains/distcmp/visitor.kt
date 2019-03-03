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
import java.util.concurrent.atomic.AtomicInteger

val lastId = AtomicInteger()

data class DiffContext(
    val settings: DiffSettings,
    val workManager: WorkManager,
    val reporter: Reporter
)

class Item(val relativePath: String, ext: String) {
    val id = lastId.incrementAndGet()

    // heruistic to detect files without extenstion
    val badExt = isBadExt(ext)
    val ext = if (badExt) "" else ext
    var diffsCount = 0

    fun visit(
        context: DiffContext,
        expected: FileObject?,
        actual: FileObject?
    ) {
        require(expected != null || actual != null)
        val expectedOrActual = expected ?: actual!!

        when {
            expectedOrActual.isFolder -> visitDirectory(
                context,
                expected?.children?.toList(), actual?.children?.toList()
            )
            manager.canCreateFileSystem(expectedOrActual) -> visitDirectory(
                context,
                if (expected == null) null else manager.createFileSystem(expected).children.toList(), if (actual == null) null else manager.createFileSystem(actual).children.toList()
            )
            else -> visitFile(context, expected, actual)
        }
    }

    private fun visitDirectory(
        parentContext: DiffContext,
        expectedChildren: List<FileObject>?,
        actualChildren: List<FileObject>?
    ) {
        require(expectedChildren != null || actualChildren != null)

        parentContext.reporter.dir(this) { context ->
            val reporter = context.reporter

            if (expectedChildren == null) reporter.reportMismatch(this, FileStatus.UNEXPECTED, FileKind.DIR)
            else if (actualChildren == null) reporter.reportMismatch(this, FileStatus.MISSED, FileKind.DIR)

            val actualChildrenByName = actualChildren?.associateBy { it.name.baseName }
            val expectedChildrenNames = mutableSetOf<String>()

            // visit all expected items, and try to find corresponding actual
            expectedChildren?.forEach { expected ->
                val name = expected.name.baseName

                val actual = actualChildrenByName?.get(name)
                if (actual != null) expectedChildrenNames.add(name)

                val childItem = child(name, expected.name.extension)
                childItem.visit(context, expected, actual)
            }

            // mark unexpected items
            actualChildren?.forEach { actual ->
                val name = actual.name.baseName
                if (name !in expectedChildrenNames) {
                    val childItem = child(name, actual.name.extension)
                    childItem.visit(context, null, actual)
                }
            }
        }
    }

    private fun visitFile(
        context: DiffContext,
        expected: FileObject?,
        actual: FileObject?
    ) {
        when {
            expected == null -> visitUnpaired(context, actual!!, FileStatus.UNEXPECTED)
            actual == null -> visitUnpaired(context, expected, FileStatus.MISSED)
            else -> context.workManager.submit("READING $relativePath") {
                when (expected.kind) {
                    FileKind.CLASS -> matchClass(context, expected, actual)
                    FileKind.TEXT -> matchText(context, expected, actual)
                    FileKind.BIN -> matchBin(context, expected, actual)
                    FileKind.DIR -> error("should not be directory")
                }
            }
        }
    }

    private fun visitUnpaired(
        context: DiffContext,
        existed: FileObject,
        status: FileStatus
    ) {
        // check for copy (requires files content, so submit it in queue)
        context.workManager.submit("READING UNPAIRED $relativePath") {
            if (isAlreadyAnalyzed(context, existed.md5())) context.reporter.reportCopy(this@Item, existed.kind)
            else context.reporter.reportMismatch(this@Item, status, existed.kind)
        }
    }

    private fun matchClass(
        context: DiffContext,
        expected: FileObject,
        actual: FileObject
    ) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        val reporter = context.reporter
        when {
            isAlreadyAnalyzed(context, expectedTxt, actualTxt) -> reporter.reportCopy(this@Item, FileKind.CLASS)
            actualTxt == expectedTxt -> reporter.reportMatch(this@Item, FileKind.CLASS)
            else -> matchText(
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
        context: DiffContext,
        expected: FileObject,
        actual: FileObject
    ) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        if (isAlreadyAnalyzed(context, expectedTxt, actualTxt)) context.reporter.reportCopy(this@Item, FileKind.TEXT)
        else matchText(context, FileKind.TEXT, expectedTxt, actualTxt, expected, actual)
    }

    private fun matchBin(context: DiffContext, expected: FileObject, actual: FileObject) {
        if (expected.content.size != actual.content.size) {
            context.reporter.reportMismatch(this@Item, FileStatus.MISMATCHED, FileKind.BIN)
        } else {
            val expectedTxt = expected.content.inputStream.bufferedReader().readText()
            val actualTxt = actual.content.inputStream.bufferedReader().readText()

            when {
                isAlreadyAnalyzed(context, expectedTxt, actualTxt) -> context.reporter.reportCopy(this@Item, FileKind.BIN)
                expectedTxt == actualTxt -> context.reporter.reportMatch(this@Item, FileKind.BIN)
                else -> context.reporter.reportMismatch(this@Item, FileStatus.MISMATCHED, FileKind.BIN)
            }
        }
    }

    val maxComparsions = 1000000 // 1 sec

    class TooManyComparsions : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun matchText(
        context: DiffContext,
        fileKind: FileKind,
        expectedTxt: String,
        actualTxt: String,
        expected: FileObject,
        actual: FileObject
    ) {
        val reporter = context.reporter

        if (expectedTxt == actualTxt) {
            reporter.reportMatch(this, fileKind)
            if (context.settings.saveMatchedContents) {
                reporter.writeDiff(this, "contents") { print(expectedTxt) }
            }
        } else {
            reporter.reportMismatch(this, FileStatus.MISMATCHED, fileKind, expectedTxt, actualTxt)

            var expectedAndActualWasSaved = false

            fun ensureExpectedAndActualSaved() {
                if (expectedAndActualWasSaved) return
                expectedAndActualWasSaved = true

                reporter.writeDiff(this, "a.expected.txt") { print(expectedTxt) }
                reporter.writeDiff(this, "b.actual.txt") { print(actualTxt) }
            }

            if (context.settings.saveExpectedAndActual) {
                ensureExpectedAndActualSaved()
            }

            if (context.settings.runDiff) {
                val lines = expectedTxt.lines()
                if (lines.size > 10000) {
                    reporter.writeDiffAborted(this, "File too large (${lines.size} lines > 10000)")
                    ensureExpectedAndActualSaved()
                } else {
                    reporter.diffs.incrementAndGet()
                    context.workManager.submit("DIFF FOR $relativePath") {
                        var i = 0
                        try {
                            val patches = DiffUtils.diff<String>(lines, actualTxt.lines(), MyersDiff<String> { a, b ->
                                if (i++ > maxComparsions) throw TooManyComparsions()
                                a == b
                            })

                            val deltas = patches.deltas
                            diffsCount = deltas.size
                            val diff = UnifiedDiffUtils.generateUnifiedDiff(
                                expected.url.toString(),
                                actual.url.toString(),
                                lines,
                                patches,
                                5
                            )

                            val limit = 1000
                            reporter.writeDiff(this) {
                                diff.asSequence().take(limit).forEach {
                                    println(it)
                                }

                                if (diff.size > limit) {
                                    println("And more ${diff - limit}...")
                                }
                            }
                        } catch (e: TooManyComparsions) {
                            reporter.writeDiffAborted(
                                this,
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

    fun isAlreadyAnalyzed(context: DiffContext, expectedTxt: String, actualTxt: String): Boolean {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(expectedTxt.toByteArray())
        md5.update(actualTxt.toByteArray())
        return isAlreadyAnalyzed(context, md5)
    }

    private fun isAlreadyAnalyzed(context: DiffContext, md5: MessageDigest): Boolean {
        val digest = ByteBuffer.wrap(md5.digest())
        return context.reporter.itemsByDigest.getOrPut(digest) { id } != id
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

    private fun child(name: String, ext: String) = Item("$relativePath/$name", ext)
}