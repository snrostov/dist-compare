package org.jetbrains.distcmp

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.sun.net.httpserver.HttpServer
import jline.TerminalFactory
import me.tongfei.progressbar.ProgressBar
import org.apache.commons.vfs2.VFS
import org.jetbrains.distcmp.utils.HttpServerHandler
import java.awt.Desktop
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val devMode = System.getProperty("dev") != null
val runFrontend = true //!devMode
lateinit var reportDir: File
lateinit var diffDir: File


val manager = VFS.getManager()

val gson = GsonBuilder()
    .setPrettyPrinting()
    .create()

lateinit var jsonWriter: JsonWriter

fun main(args0: Array<String>) {
    val args = if (devMode) arrayOf(
        "/Users/jetbrains/tasks/kwjps/wgradle/dist",
        "/Users/jetbrains/tasks/kwjps/wjps/dist"
    ) else args0

    if (args.size !in 2..3) {
        println("Usage: distdiff <expected-dir> <actual-dir> [reports-dir]")
        return
    }

    val expected = File(args[0])
    val actual = File(args[1])
    val explicitReportDir = args.size == 3
    reportDir =
        if (explicitReportDir) File(args[2])
        else Files.createTempDirectory("dist-compare").toFile()

    println("Comparing `$expected` vs `$actual` to `$reportDir`")

    requireDir(expected)
    requireDir(actual)
    requireDir(reportDir)

    WorkManager.startGathering()

    if (explicitReportDir) removePreviousReport()

    diffDir = reportDir.resolve("diff")
    diffDir.mkdir()

    if (runFrontend) copyHtmlApp()

    jsonWriter = gson.newJsonWriter(diffDir.resolve("data.json").writer())
    jsonWriter.beginArray()

    Item("", "root")
        .visit(
            manager.resolveFile(expected, ""),
            manager.resolveFile(actual, "")
        )

    WorkManager.waitDone()

    jsonWriter.endArray()
    jsonWriter.close()

    WorkManager.reportDone()

    if (runFrontend) {
        println("Opening http://localhost:8000")

        val server = HttpServer.create(InetSocketAddress(8000), 0)
        server.createContext("/", HttpServerHandler())
        server.executor = null
        server.start()

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI("http://localhost:8000"))
        }
    }
}

private fun removePreviousReport() {
    WorkManager.setProgressMessage("Removing previous report")
    reportDir.deleteRecursively()
    reportDir.mkdirs()
}

private fun copyHtmlApp() {
    listOf(
        "2da2b42ac8b23e24cd2b832c22626baf.gif",
        "app.js",
        "index.html"
    ).forEach {
        if (devMode) {
            Files.copy(
                File("js/dist/$it").toPath(),
                File(reportDir, it).toPath()
            )
        } else {
            Files.copy(
                Item::class.java.getResourceAsStream("js/$it"),
                File(reportDir, it).toPath()
            )
        }
    }
}

private fun requireDir(file: File) {
    if (!file.isDirectory) {
        println("$file is not a directory")
        System.exit(1)
    }
}