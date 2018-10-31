package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpServer
import jline.TerminalFactory
import me.tongfei.progressbar.ProgressBar
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import java.awt.Desktop
import java.io.File
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


val manager = VFS.getManager()
val reports = TreeMap<String, MutableList<Item>>()
val items = mutableListOf<FileInfo>()

@Volatile
var allScheduled = false

var totalScheduled = AtomicInteger(0)
var totalProcessed = AtomicInteger()

lateinit var reportDir: File
lateinit var diffDir: File

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

val writeFiles = true
val lastId = AtomicInteger()
val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
val progressBar = ProgressBar("", 1, 300)

fun main(args: Array<String>) {
//    val expected = File("/Users/jetbrains/kotlin/dist")
//    val actual = File("/Users/jetbrains/tasks/dist")
//    diffDir = File("/Users/jetbrains/dist-compare/js/dist/diff")

    if (args.size !in 2..3) {
        println("Usage: distdiff <expected-dir> <actual-dir> [reports-dir]")
        return
    }

    val expected = File(args[0])
    val actual = File(args[1])
    reportDir =
            if (args.size == 3) File(args[2])
            else Files.createTempDirectory("dist-compare").toFile()

    println("Comparing `$expected` vs `$actual` to `$reportDir`")

    requireDir(expected)
    requireDir(actual)
    requireDir(reportDir)

    progressBar.extraMessage = "Removing previous report"
    progressBar.maxHint(-1)
    progressBar.start()
    reportDir.deleteRecursively()
    reportDir.mkdirs()

    listOf(
        "2da2b42ac8b23e24cd2b832c22626baf.gif",
        "app.js",
        "index.html"
    ).forEach {
        Files.copy(Item::class.java.getResourceAsStream("js/$it"), File(reportDir, it).toPath())
    }

    diffDir = reportDir.resolve("diff")
    diffDir.mkdirs()

    Item("", "root")
        .visit(manager.resolveFile(expected, ""), manager.resolveFile(actual, ""))

    allScheduled = true
    progressBar.maxHint(totalScheduled.get().toLong())
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
        diffDir.mkdirs()
        diffDir.resolve("data.json").writer().use {
            GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(items, it)
        }
    }

    progressBar.stop()
    println("http://localhost:8000")
    val server = HttpServer.create(InetSocketAddress(8000), 0)
    server.createContext("/", MyHandler())
    server.executor = null
    server.start()

    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI("http://localhost:8000"))
    }
}

private fun requireDir(file: File) {
    if (!file.isDirectory) {
        println("$file is not a directory")
        System.exit(1)
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

        setProgressMessage()
        progressBar.step()
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
                File("${diffDir.path}/$relativePath.patch")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use(outputWriter)
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
        setProgressMessage()
    }

    private fun setProgressMessage() {
        val width = TerminalFactory.get().width - 55
        progressBar.extraMessage = relativePath.takeLast(width).padEnd(width)
    }
}
