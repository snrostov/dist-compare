package org.jetbrains.distcmp

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

fun main(args0: Array<String>) {
    val context = DiffContext(args0)

    if (context.settings.explicitReportDir) {
        removePreviousReport(context)
    }

    context.workManager.startGathering()

    if (context.settings.runFrontend) copyHtmlApp(context)

    context.reporter.beginReport()

    Item("", "root")
        .visit(
            context,
            manager.resolveFile(context.settings.expected, ""),
            manager.resolveFile(context.settings.actual, "")
        )

    context.workManager.waitDone()
    context.reporter.close()
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

private fun removePreviousReport(context: DiffContext) {
    context.workManager.setProgressMessage("Removing previous report")
    context.settings.reportDir.deleteRecursively()
    context.settings.reportDir.mkdirs()
}