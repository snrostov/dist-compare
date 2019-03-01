package org.jetbrains.distcmp

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.sun.net.httpserver.HttpServer
import org.apache.commons.vfs2.VFS
import org.jetbrains.distcmp.utils.HttpServerHandler
import java.awt.Desktop
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files

val devMode = System.getProperty("dev") != null

val manager = VFS.getManager()

val gson = GsonBuilder()
    .setPrettyPrinting()
    .create()

lateinit var jsonWriter: JsonWriter

fun main(args0: Array<String>) {
    val context = DiffContext()
    context.workManager.startGathering()


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
    context.settings.reportDir =
        if (explicitReportDir) File(args[2])
        else Files.createTempDirectory("dist-compare").toFile()

    println("Comparing `$expected` vs `$actual` to `${context.settings.reportDir}`")

    requireDir(expected)
    requireDir(actual)
    requireDir(context.settings.reportDir)

    if (explicitReportDir) removePreviousReport(context)

    context.settings.diffDir = context.settings.reportDir.resolve("diff")
    context.settings.diffDir.mkdir()

    if (context.settings.runFrontend) copyHtmlApp(context)

    jsonWriter = gson.newJsonWriter(context.settings.diffDir.resolve("data.json").writer())
    jsonWriter.beginArray()

    Item("", "root")
        .visit(
            context,
            manager.resolveFile(expected, ""),
            manager.resolveFile(actual, "")
        )

    context.workManager.waitDone()

    jsonWriter.endArray()
    jsonWriter.close()

    context.workManager.reportDone(context)

    if (context.settings.runFrontend) {
        println("Opening http://localhost:8000")

        val server = HttpServer.create(InetSocketAddress(8000), 0)
        server.createContext("/", HttpServerHandler(context))
        server.executor = null
        server.start()

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI("http://localhost:8000"))
        }
    }
}

private fun removePreviousReport(context: DiffContext) {
    context.workManager.setProgressMessage("Removing previous report")
    context.settings.reportDir.deleteRecursively()
    context.settings.reportDir.mkdirs()
}

private fun copyHtmlApp(context: DiffContext) {
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
                File(context.settings.reportDir, it).toPath()
            )
        } else {
            Files.copy(
                resourceAsStream,
                File(context.settings.reportDir, it).toPath()
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