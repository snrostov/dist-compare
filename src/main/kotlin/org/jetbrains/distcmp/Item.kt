package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.algorithm.myers.MyersDiff
import org.apache.commons.vfs2.FileObject
import org.jetbrains.distcmp.utils.classToTxt
import org.jetbrains.distcmp.utils.isBadExt
import org.jetbrains.distcmp.utils.kind
import org.jetbrains.distcmp.utils.md5
import java.nio.ByteBuffer
import java.security.MessageDigest

class Item(val relativePath: String, ext: String) {
    val id = lastId.incrementAndGet()

    // heruistic to detect files without extenstion
    val badExt = isBadExt(ext)
    val ext = if (badExt) "" else ext
    var diffsCount = 0

    fun visit(expected: FileObject?, actual: FileObject?) {
        require(expected != null || actual != null)
        val expectedOrActual = expected ?: actual!!

        when {
            expectedOrActual.isFolder -> visitDirectory(expected?.children?.toList(), actual?.children?.toList())
            manager.canCreateFileSystem(expectedOrActual) -> visitDirectory(
                manager.createFileSystem(expected).children.toList(),
                manager.createFileSystem(actual).children.toList()
            )
            else -> {
                matchItems(expected, actual)

                totalScheduled.incrementAndGet()
            }
        }
    }

    private fun visitDirectory(
        expectedChildren: List<FileObject>?,
        actualChildren: List<FileObject>?
    ) {
        require(expectedChildren != null || actualChildren != null)

        val actualChildrenByName = actualChildren?.associateBy { it.name.baseName }
        val actualVisited = mutableSetOf<String>()

        expectedChildren?.forEach { expected ->
            val name = expected.name.baseName
            val actual = actualChildrenByName?.get(name)
            val childItem = child(name, expected.name.extension)
            if (actual == null) childItem.visitUnpaired(expected, FileStatus.MISSED)
            else actualVisited.add(name)

            childItem.visit(expected, actual)
        }

        actualChildren?.forEach { actual ->
            val name = actual.name.baseName
            if (name !in actualVisited) {
                val childItem = child(name, actual.name.extension)
                childItem.visitUnpaired(actual, FileStatus.UNEXPECTED)
                childItem.visit(null, actual)
            }
        }
    }

    private fun visitUnpaired(
        expected: FileObject,
        status: FileStatus
    ) {
        val fileKind = expected.kind
        when (fileKind) {
            FileKind.DIR -> reportMismatch(status, FileKind.DIR)
            else -> {
                cpuExecutor.submit {
                    if (isAlreadyAnalyzed(expected.md5())) reportCopy(fileKind)
                    else reportMismatch(status, fileKind)
                }
                totalScheduled.incrementAndGet()
            }
        }
    }

    private fun matchItems(expected: FileObject?, actual: FileObject?) {
        if (expected == null) {
            visitUnpaired(actual!!, FileStatus.MISSED)
            return
        } else if (actual == null) {
            visitUnpaired(expected, FileStatus.MISSED)
            return
        }

        cpuExecutor.submit {
            when (expected.kind) {
                FileKind.CLASS -> matchClass(expected, actual)
                FileKind.TEXT -> matchText(expected, actual)
                FileKind.BIN -> matchBin(expected, actual)
                FileKind.DIR -> error("should not be directory")
            }

            setProgressMessage(relativePath)
            progressBar.step()
        }
    }

    private fun matchClass(expected: FileObject, actual: FileObject) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        when {
            isAlreadyAnalyzed(expectedTxt, actualTxt) -> reportCopy(FileKind.CLASS)
            actualTxt == expectedTxt -> reportMatch(FileKind.CLASS)
            else -> matchText(
                FileKind.CLASS,
                classToText(expected),
                classToText(actual),
                expected,
                actual
            )
        }
    }

    private fun matchText(expected: FileObject, actual: FileObject) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        if (isAlreadyAnalyzed(expectedTxt, actualTxt)) reportCopy(FileKind.TEXT)
        else matchText(FileKind.TEXT, expectedTxt, actualTxt, expected, actual)
    }

    private fun matchBin(expected: FileObject, actual: FileObject) {
        if (expected.content.size != actual.content.size) {
            reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)
        } else {
            val expectedTxt = expected.content.inputStream.bufferedReader().readText()
            val actualTxt = actual.content.inputStream.bufferedReader().readText()

            when {
                isAlreadyAnalyzed(expectedTxt, actualTxt) -> reportCopy(FileKind.BIN)
                expectedTxt == actualTxt -> reportMatch(FileKind.BIN)
                else -> reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)
            }
        }
    }

    val maxComparsions = 1000000 // 1 sec

    class TooManyComparsions : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun matchText(
        fileKind: FileKind,
        expectedTxt: String,
        actualTxt: String,
        expected: FileObject,
        actual: FileObject
    ) {
        if (expectedTxt == actualTxt) reportMatch(fileKind)
        else {
            reportMismatch(FileStatus.MISMATCHED, fileKind)

            val lines = expectedTxt.lines()
            if (lines.size > maxComparsions) {
                writeDiff {
                    println("File too large (${lines.size} lines > 10000)")
                }
            } else {
                diffs.incrementAndGet()
                cpuExecutor.submit {
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
                        writeDiff {
                            diff.asSequence().take(limit).forEach {
                                println(it)
                            }

                            if (diff.size > limit) {
                                println("And more ${diff - limit}...")
                            }
                        }
                    } catch (e: TooManyComparsions) {
                        abortedDiffs.incrementAndGet()
                        writeDiff {
                            println(
                                "[DIFF-ABORTED] Building diff takes too long ($i comparsions). " +
                                        "Please see origin files " +
                                        "(saved below with extensions .a.expected.txt and .b.actual.txt)"
                            )
                        }
                        writeDiff("a.expected.txt") { print(expectedTxt) }
                        writeDiff("b.actual.txt") { print(actualTxt) }
                    }
                }
            }

        }
    }

    fun isAlreadyAnalyzed(expectedTxt: String, actualTxt: String): Boolean {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(expectedTxt.toByteArray())
        md5.update(actualTxt.toByteArray())
        return isAlreadyAnalyzed(md5)
    }

    private fun isAlreadyAnalyzed(md5: MessageDigest): Boolean {
        val digest = ByteBuffer.wrap(md5.digest())
        return itemsByDigest.getOrPut(digest) { id } != id
    }

    private fun classToText(expected: FileObject): String {
        val txt = classToTxt(
            expected.content.inputStream,
            expected.name.baseName,
            expected.content.lastModifiedTime,
            expected.url.toURI()
        )
        return txt.lines().drop(3).joinToString("\n")
    }

    private fun child(name: String, ext: String) = Item("$relativePath/$name", ext)
}