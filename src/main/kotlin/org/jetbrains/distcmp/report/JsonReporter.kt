package org.jetbrains.distcmp.report

import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpServer
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
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
    givenReportDir: File?,
    private val frontend: Frontend? = null
) : AbstractReporter(settings, workManager) {
    private val reportDir: File = givenReportDir ?: Files.createTempDirectory("dist-compare").toFile()
    private val diffDir: File

    init {
        requireDir(reportDir)

        diffDir = reportDir.resolve("diff")
        removePreviousReport()
        diffDir.mkdir()

        requireDir(diffDir)
    }

    override fun toString() = reportDir.toString()

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    val writer = diffDir.resolve("data.json").writer()
    private val jsonWriter = gson.newJsonWriter(writer)

    override fun beginReport() {
        frontend?.prepare(reportDir)
        jsonWriter.beginArray()
    }

    private fun removePreviousReport() {
        workManager.setProgressMessage("Removing previous report")
        diffDir.deleteRecursively()
    }

    override fun close() {
        jsonWriter.endArray()
        jsonWriter.close()
        writer.close()
    }

    override fun show() {
        frontend?.run(reportDir)
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
        writeDiff(item) {
            println("[DIFF-ABORTED] $reason")
        }
    }

    data class Frontend(
        val address: InetSocketAddress = InetSocketAddress(8000),
        val openBrowser: Boolean = true
    ) {
        companion object {
            const val localJsDist = "js/dist"
        }

        private fun isSaveDevJs(reportDir: File) =
            reportDir.canonicalPath == File(localJsDist).canonicalPath

        fun prepare(reportDir: File) {
            if (isSaveDevJs(reportDir)) return

            listOf(
                "2da2b42ac8b23e24cd2b832c22626baf.gif",
                "app.js",
                "index.html"
            ).forEach {
                val resourceAsStream = Item::class.java.getResourceAsStream("js/$it")
                val isLocalRun = resourceAsStream == null

                if (isLocalRun) {
                    val file = File(localJsDist, it)

                    check(file.isFile) { "Cannot load $it from resources and from $file" }
                    Files.copy(
                        file.toPath(),
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

        fun run(reportDir: File) {
            if (isSaveDevJs(reportDir)) {
                println("`js/dist` dir mode: use `yarn watch` in `js/dist` dir, and open `js/dist/index.html from IDEA`")
                return
            }

            val server = HttpServer.create(InetSocketAddress(8000), 0)
            server.createContext("/", HttpServerHandler(reportDir))
            server.executor = null
            server.start()

            if (openBrowser) {
                val url = URI("http", null, address.hostString, address.port, null, null, null)
                println("Opening $url")
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(url)
                }
            }
        }
    }
}