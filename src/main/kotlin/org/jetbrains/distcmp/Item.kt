package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.apache.commons.vfs2.FileObject
import org.jetbrains.distcmp.utils.classToTxt
import org.jetbrains.distcmp.utils.textFileTypes
import java.nio.ByteBuffer
import java.security.MessageDigest

class Item(val relativePath: String, ext: String) {
    val id = lastId.incrementAndGet()

    // heruistic to detect files without extenstion
    val badExt = ext.isBlank() || (ext.any { it.isUpperCase() } && ext.any { it.isLowerCase() })
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
                pool.submit {
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

        expectedChildren.forEach { aChild ->
            val name = aChild.name.baseName
            val bChild = actualChildrenByName[name]
            val child = child(name, aChild.name.extension)
            if (bChild == null) {
                child.reportMismatch(FileStatus.MISSED, FileKind.TEXT)
            } else {
                actualVisited.add(name)
                child.visit(aChild, bChild)
            }
        }

        actualChildren.forEach {
            val name = it.name.baseName
            if (name !in actualVisited) {
                child(name, it.name.extension).reportMismatch(
                    FileStatus.UNEXPECTED,
                    FileKind.TEXT
                )
            }
        }
    }

    private fun matchItems(expected: FileObject, actual: FileObject) {
        val extension = expected.name.extension.toLowerCase()

        when {
            extension == "class" -> matchClass(expected, actual)
            isTextFile(extension) -> matchText(expected, actual)
            else -> matchBin(expected, actual)
        }

        setProgressMessage(relativePath)
        progressBar.step()
    }

    private fun isTextFile(extension: String) = extension.toLowerCase() in textFileTypes || badExt

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

    fun isAlreadyAnalyzed(expectedTxt: String, actualTxt: String): Boolean {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(expectedTxt.toByteArray())
        md5.update(actualTxt.toByteArray())
        val digest = ByteBuffer.wrap(md5.digest())

        return itemsByDigest.getOrPut(digest) { this } !== this
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

    private fun child(name: String, ext: String) = Item("$relativePath/$name", ext).also {
        setProgressMessage(relativePath)
    }
}