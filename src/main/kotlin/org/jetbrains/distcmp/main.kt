package org.jetbrains.distcmp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.google.gson.GsonBuilder
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


val manager = VFS.getManager()
val reports = TreeMap<String, MutableList<Context>>()
val items = mutableListOf<FileInfo>()

@Volatile
var allScheduled = false

var totalScheduled: Int = 0
var totalProcessed = AtomicInteger()
val totalEstimated = 52870
val reportsDir = File("/Users/jetbrains/jps-report/report-1")

val textFileTypes = listOf(
    "txt",
    "bat",
//    "archive",
//    "class",
    "MF",
//    "so",
//    "dll",
//    "dylib",
    "kt",
//    "map",
//    "js",
//    "kjsm",
    "java",
    "html",
//    "jnilib",
    "a",
    "template",
    "dtd",
//    "gif",
    "properties",
    "caps",
//    "kotlin_module",
    "xml"
)

lateinit var result: OutputStreamWriter
val writeFiles = false
val writeHtml = true
val lastId = AtomicInteger()
val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
//val pool = Executors.newFixedThreadPool(100)
val progressSync = Any()

fun main(args: Array<String>) {
    println("Removing previous report...")
    reportsDir.deleteRecursively()

    val resultFile = reportsDir.resolve("report.html.zip")
    resultFile.parentFile.mkdirs()
    resultFile.createNewFile()
    val zipOutputStream = ZipOutputStream(resultFile.outputStream())
    zipOutputStream.putNextEntry(ZipEntry("report.html"))
    result = zipOutputStream.writer()
    result.write(
        """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Dist compare report</title>
    <script type="text/javascript" src="app.js"></script>
</head>
<body>
        """
    )

    val expected = File("/Users/jetbrains/kotlin/dist/artifacts/ideaPlugin/Kotlin")
    val actual = File("/Users/jetbrains/tasks/out/artifacts/ideaPlugin")

    print("\r...")
    Context("", "root")
        .visit(manager.resolveFile(expected, ""), manager.resolveFile(actual, ""))

    allScheduled = true
    pool.shutdown()
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

    println("\rtotal: $totalProcessed")

    reports.forEach { (type, list) ->
        println("$type: ${list.size}")
    }

    result.write("\n\n<script type=\"text/javascript\">\ninit(")

    GsonBuilder().create().toJson(items, result)

    result.write(")\n</script>")

    result.write(
        """
</body>
</html>
    """
    )

    result.flush()
    zipOutputStream.closeEntry()
    zipOutputStream.close()
}

class Context(val relativePath: String, val ext: String) {
    val id = lastId.incrementAndGet()

    // heruistic to detect files without extenstion
    val badExt = ext.isBlank() || (ext.any { it.isUpperCase() } && ext.any { it.isLowerCase() })

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
            extension == "class" -> {
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
            extension in textFileTypes || badExt -> {
                val expectedTxt = expected.content.inputStream.bufferedReader().readText()
                val actualTxt = actual.content.inputStream.bufferedReader().readText()

                matchText(FileKind.TEXT, expectedTxt, actualTxt, expected, actual)
            }
            else -> if (expected.content.size != actual.content.size) {
                reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)/* {
                    println(
                        "binary contents mismatched by size: " +
                                "${expected.content.size} != ${actual.content.size}"
                    )
                }*/
            } else {
                val expectedTxt = expected.content.inputStream.bufferedReader().readText()
                val actualTxt = actual.content.inputStream.bufferedReader().readText()

                if (expectedTxt == actualTxt) reportMatch(FileKind.BIN)
                else reportMismatch(FileStatus.MISMATCHED, FileKind.BIN)
            }
        }

        val total1 = totalProcessed.incrementAndGet()
        reportProgress(total1)
    }

    private fun matchText(
        fileKind: FileKind,
        expectedTxt: String,
        actualTxt: String,
        expected: FileObject,
        actual: FileObject
    ) {
        if (expectedTxt == actualTxt) reportMatch(fileKind)
        else reportMismatch(FileStatus.MISMATCHED, fileKind) {
            val lines = expectedTxt.lines()

            val diff = UnifiedDiffUtils.generateUnifiedDiff(
                expected.url.toString(),
                actual.url.toString(),
                lines,
                DiffUtils.diff(lines, actualTxt.lines()),
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
        outputWriter: (PrintWriter.() -> Unit)? = null
    ) {
        val kind = if (badExt) ".OTHER.$mismatchExt" else "$ext.$mismatchExt"

        reports.getOrPut(kind) { mutableListOf() }.add(this)
        reports.getOrPut("..TOTAL.$mismatchExt") { mutableListOf() }.add(this)

        items.add(FileInfo(id, relativePath, badExt, ext, mismatchExt, fileKind, false))

        if (writeFiles) {
            val reportFile =
                File("${reportsDir.path}/$relativePath.$mismatchExt")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use {
                if (outputWriter != null) {
                    outputWriter(it)
                } else {
                    println()
                }
            }
        }

        if (writeHtml && outputWriter != null) {
            val bytes = ByteArrayOutputStream()
            outputWriter(PrintWriter(DeflaterOutputStream(bytes)))
            bytes.close()

            if (bytes.size() != 0) {
                val base64 = String(Base64.getEncoder().encode(bytes.toByteArray()), Charset.forName("UTF-8"))
                synchronized(result) {
                    result.write("<script id='patch-$id' type='text/plain'>\n$base64\n</script>\n")
                }
            }
        }
    }

    fun reportMatch(fileKind: FileKind) {
        reports.getOrPut("MATCHED") { mutableListOf() }.add(this)
        items.add(FileInfo(id, relativePath, badExt, ext, FileStatus.MATCHED, fileKind, false))
    }

    private fun child(name: String, ext: String) = Context("$relativePath/$name", ext).also {
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

