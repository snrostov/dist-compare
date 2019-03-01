package org.jetbrains.distcmp.utils

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.jetbrains.distcmp.DiffContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

internal class HttpServerHandler(val context: DiffContext) : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val path0 = exchange.requestURI.path.trim()
        val path = if (path0 == "/") "/index.html" else path0
        val file = File(context.settings.reportDir.path + path).canonicalFile

        if (!file.isFile) {
            val response = "404 (Not Found)\n"
            exchange.sendResponseHeaders(404, response.length.toLong())
            exchange.responseBody.use {
                it.write(response.toByteArray())
            }
        } else {
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use {
                Files.copy(file.toPath(), exchange.responseBody)
            }
        }
    }
}