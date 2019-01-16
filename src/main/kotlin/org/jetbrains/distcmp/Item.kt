package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
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

    fun visit(expected: FileObject, actual: FileObject) {
        when {
            expected.isFolder -> visitDirectory(expected.children.toList(), actual.children.toList())
            manager.canCreateFileSystem(expected) -> visitDirectory(
                manager.createFileSystem(expected).children.toList(),
                manager.createFileSystem(actual).children.toList()
            )
            else -> {
                cpuExecutor.submit {
                    matchItems(expected, actual)
                }
                totalScheduled.incrementAndGet()
            }
        }
    }

    private fun visitDirectory(
        expectedChildren: List<FileObject>,
        actualChildren: List<FileObject>
    ) {
        val actualChildrenByName = actualChildren.associateBy { it.name.baseName }
        val actualVisited = mutableSetOf<String>()

        expectedChildren.forEach { expected ->
            val name = expected.name.baseName
            val actual = actualChildrenByName[name]
            val childItem = child(name, expected.name.extension)
            if (actual == null) childItem.visitUnpaired(expected, FileStatus.MISSED)
            else {
                actualVisited.add(name)
                childItem.visit(expected, actual)
            }
        }

        actualChildren.forEach {
            val name = it.name.baseName
            if (name !in actualVisited) {
                child(name, it.name.extension).visitUnpaired(it, FileStatus.UNEXPECTED)
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

    private fun matchItems(expected: FileObject, actual: FileObject) {
        when (expected.kind) {
            FileKind.CLASS -> matchClass(expected, actual)
            FileKind.TEXT -> matchText(expected, actual)
            FileKind.BIN -> matchBin(expected, actual)
            FileKind.DIR -> error("should not be directory")
        }

        setProgressMessage(relativePath)
        progressBar.step()
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

    private fun matchText(
        fileKind: FileKind,
        expectedTxt: String,
        actualTxt: String,
        expected: FileObject,
        actual: FileObject
    ) {
        if (expectedTxt == actualTxt) reportMatch(fileKind)
        else reportMismatch(FileStatus.MISMATCHED, fileKind, true) {
            val lines = expectedTxt.lines()

            if (lines.size > 10000) {
                println("File too large (${lines.size} lines > 10000)")
            } else {
                val patches = DiffUtils.diff(lines, actualTxt.lines())
                val deltas = patches.deltas
                diffsCount = deltas.size
                it.deltas = deltasDigests(deltas)
                val diff = UnifiedDiffUtils.generateUnifiedDiff(
                    expected.url.toString(),
                    actual.url.toString(),
                    lines,
                    patches,
                    5
                )

                val limit = 1000
                diff.asSequence().take(limit).forEach {
                    println(it)
                }

                if (diff.size > limit) {
                    println("And more ${diff - limit}...")
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