package org.jetbrains.distcmp.report

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.sun.net.httpserver.HttpServer
import org.jetbrains.distcmp.*
import org.jetbrains.distcmp.utils.HttpServerHandler
import org.jetbrains.distcmp.utils.requireDir
import java.awt.Desktop
import java.io.File
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files

class JsonReporter(
    settings: DiffSettings,
    workManager: WorkManager,
    givenReportDir: File?
) : AbstractReporter(settings, workManager) {
    private val reportDir: File
    private val diffDir: File

    init {
        reportDir = givenReportDir ?: Files.createTempDirectory("dist-compare").toFile()

        requireDir(reportDir)
        removePreviousReport()

        diffDir = reportDir.resolve("diff")
        diffDir.mkdir()

        requireDir(diffDir)
    }

    override fun toString() = reportDir.toString()

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    val writer = diffDir.resolve("data.json").writer()
    private val jsonWriter= gson.newJsonWriter(writer)

    override fun beginReport() {
        if (settings.runFrontend) copyHtmlApp()
        jsonWriter.beginArray()
    }

    private fun removePreviousReport() {
        workManager.setProgressMessage("Removing previous report")
        reportDir.deleteRecursively()
        reportDir.mkdirs()
    }

    override fun close() {
        jsonWriter.endArray()
        jsonWriter.close()
        writer.close()

        if (settings.runFrontend) runServer()
    }

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
        workManager.io {
            gson.toJson(item, item.javaClass, jsonWriter)
        }
    }

    override fun writeDiff(
        item: Item,
        ext: String,
        outputWriter: (PrintWriter.() -> Unit)?
    ) {
        if (outputWriter == null) return

        workManager.io {
            val reportFile = File("${diffDir.path}/${item.relativePath}.$ext")
            reportFile.parentFile.mkdirs()
            reportFile.printWriter().use {
                outputWriter(it)
            }
        }
    }

    override fun writeDiffAborted(item: Item, reason: String) {
        abortedDiffs.incrementAndGet()
        writeDiff(item) {
            println("[DIFF-ABORTED] $reason")
        }
    }

    private fun copyHtmlApp() {
        listOf(
            "2da2b42ac8b23e24cd2b832c22626baf.gif",
            "app.js",
            "index.html"
        ).forEach {
            val resourceAsStream = Item::class.java.getResourceAsStream("js/$it")
            val isLocalRun = devMode || resourceAsStream == null

            if (isLocalRun) {
                Files.copy(
                    File("js/dist/$it").toPath(),
                    File(reportDir, it).toPath()
                )
            } else {
                Files.copy(
                    resourceAsStream,
                    File(reportDir, it).toPath()
                )
            }
        }
    }

    private fun runServer() {
        println("Opening http://localhost:8000")

        val server = HttpServer.create(InetSocketAddress(8000), 0)
        server.createContext("/", HttpServerHandler(reportDir))
        server.executor = null
        server.start()

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI("http://localhost:8000"))
        }
    }
}