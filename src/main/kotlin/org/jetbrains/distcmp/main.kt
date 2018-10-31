package org.jetbrains.distcmp

import com.google.gson.GsonBuilder
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

val devMode = false
val writeFiles = true
lateinit var reportDir: File
lateinit var diffDir: File

val manager = VFS.getManager()
val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1)
val progressBar = ProgressBar("", 1, 300)
var totalScheduled = AtomicInteger(0)

fun main(args0: Array<String>) {
    val args = if (devMode) arrayOf(
        "/Users/jetbrains/kotlin/dist",
        "/Users/jetbrains/tasks/dist",
        "/Users/jetbrains/dist-compare/js/dist"
    ) else args0

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

    progressBar.maxHint(-1)
    progressBar.start()

    diffDir = reportDir.resolve("diff")
    diffDir.mkdir()

    removePreviousReport()

    if (!devMode) copyHtmlApp()

    Item("", "root")
        .visit(manager.resolveFile(expected, ""), manager.resolveFile(actual, ""))

    progressBar.maxHint(totalScheduled.get().toLong())
    pool.shutdown()
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

    printTotals()

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

    if (!devMode) {
        println("http://localhost:8000")
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
    setProgressMessage("Removing previous report")
    if (devMode) {
        diffDir.deleteRecursively()
        diffDir.mkdirs()
    } else {
        reportDir.deleteRecursively()
        reportDir.mkdirs()
    }
}

private fun copyHtmlApp() {
    listOf(
        "2da2b42ac8b23e24cd2b832c22626baf.gif",
        "app.js",
        "index.html"
    ).forEach {
        Files.copy(Item::class.java.getResourceAsStream("js/$it"), File(reportDir, it).toPath())
    }
}

private fun requireDir(file: File) {
    if (!file.isDirectory) {
        println("$file is not a directory")
        System.exit(1)
    }
}

fun setProgressMessage(msg: String) {
    val width = TerminalFactory.get().width - 55
    progressBar.extraMessage = msg.takeLast(width).padEnd(width)
}
