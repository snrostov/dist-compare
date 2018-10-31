package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.google.gson.GsonBuilder
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipOutputStream


val manager = VFS.getManager()
val reports = TreeMap<String, MutableList<Item>>()
val items = mutableListOf<FileInfo>()

@Volatile
var allScheduled = false

var totalScheduled: Int = 0
var totalProcessed = AtomicInteger()
val totalEstimated = 52870
lateinit var reportsDir: File

val textFileTypes = listOf(
    "txt",
    "bat",
    "MF",
    "kt",
//    "js",
    "java",
    "html",
    "template",
    "dtd",
    "properties",
    "xml"
).map { it.toLowerCase() }.toSet()

lateinit var html: OutputStreamWriter
lateinit var htmlZip: ZipOutputStream

val writeFiles = true
val writeHtml = false
val lastId = AtomicInteger()
val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
val progressSync = Any()

fun main(args: Array<String>) {
    reportsDir = File("/Users/jetbrains/dist-compare/js/dist/diff")

    println("Removing previous report...")
    reportsDir.deleteRecursively()

    if (writeHtml) {
        htmlStart()
    }

    val expected = File("/Users/jetbrains/kotlin/dist/artifacts/ideaPlugin/Kotlin")
    val actual = File("/Users/jetbrains/tasks/out/artifacts/ideaPlugin")

    print("\r...")
    Item("", "root")
        .visit(manager.resolveFile(expected, ""), manager.resolveFile(actual, ""))

    allScheduled = true
    pool.shutdown()
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

    println("\rtotal: $totalProcessed")

    reports.forEach { (type, list) ->
        val diffs = list.sumBy { it.diffsCount }
        if (diffs > 0) {
            println("$type: ${list.size} items / $diffs diffs")
        } else {
            println("$type: ${list.size} items")
        }
    }

    if (writeFiles) {
        reportsDir.mkdirs()
        reportsDir.resolve("data.json").writer().use {
            GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(items, it)
        }
    }

    if (writeHtml) {
        htmlEnd()
    }
}

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

                totalScheduled++
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
                child(name, it.name.extension).reportMismatch(FileStatus.UNEXPECTED, FileKind.TEXT)
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

        reportProgress(totalProcessed.incrementAndGet())
    }

    private fun isTextFile(extension: String) = extension.toLowerCase() in textFileTypes || badExt

    private fun matchClass(expected: FileObject, actual: FileObject) {
        val expectedTxt = expected.content.inputStream.bufferedReader().readText()
        val actualTxt = actual.content.inputStream.bufferedReader().readText()

        if (actualTxt == expectedTxt) reportMatch(FileKind.CLASS)
        else {
            matchText(
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

        matchText(FileKind.TEXT, expectedTxt, actualTxt, expected, actual)
    }

    private fun matchBin(expected: FileObject, actual: FileObject) {
        if (expected.content.size != actual.content.size) {
            reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)
        } else {
            val expectedTxt = expected.content.inputStream.bufferedReader().readText()
            val actualTxt = actual.content.inputStream.bufferedReader().readText()

            if (expectedTxt == actualTxt) reportMatch(FileKind.BIN)
            else reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)
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

            val diff = UnifiedDiffUtils.generateUnifiedDiff(
                expected.url.toString(),
                actual.url.toString(),
                lines,
                DiffUtils.diff(lines, actualTxt.lines()),
                5
            )

            val limit = 1000
            diffsCount = diff.size
            diff.asSequence().take(limit).forEach {
                println(it)
            }

            if (diff.size > limit) {
                println("And more ${diff - limit}...")
            }
        }
    }

    private fun classToText(expected: FileObject): String {
        val txt = classToTxt(
            expected.content.inputStream,
            expected.name.baseName,
            expected.content.lastModifiedTime,
            expected.url.toURI()
        )
        return txt
    }

    fun reportMismatch(
        mismatchExt: FileStatus,
        fileKind: FileKind,
        writeDiff: Boolean = false,
        outputWriter: (PrintWriter.() -> Unit)? = null
    ) {
        val kind = if (badExt) ".OTHER.$mismatchExt" else "$ext.$mismatchExt"

        synchronized(reports) {
            reports.getOrPut(kind) { mutableListOf() }.add(this)
            reports.getOrPut("..TOTAL.$mismatchExt") { mutableListOf() }.add(this)
        }

        if (outputWriter != null && writeDiff && writeFiles) {
            val reportFile =
                File("${reportsDir.path}/$relativePath.patch")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use(outputWriter)
        }

        if (writeDiff && writeHtml && outputWriter != null) {
            htmlWriteDiff(outputWriter)
        }

        synchronized(items) {
            items.add(FileInfo(id, relativePath, badExt, ext, mismatchExt, fileKind, false, diffsCount))
        }
    }

    fun reportMatch(fileKind: FileKind) {
        synchronized(reports) {
            reports.getOrPut("MATCHED") { mutableListOf() }.add(this)
        }

        synchronized(items) {
            items.add(FileInfo(id, relativePath, badExt, ext, FileStatus.MATCHED, fileKind, false, 0))
        }
    }

    private fun child(name: String, ext: String) = Item("$relativePath/$name", ext).also {
        reportProgress(0)
    }

    fun reportProgress(total: Int) {
        if (total % 100 == 0) {
            synchronized(progressSync) {
                val progress =
                    if (allScheduled) (total * 100 / totalEstimated).toString().padStart(3)
                    else " ??"

                print("\r [$progress% ] $relativePath")
            }
        }
    }
}
