package org.jetbrains.distcmp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.File
import java.io.IOException
import java.nio.file.Files


internal class MyHandler : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val root = reportDir.path
        val uri = exchange.requestURI
        val path0 = uri.path.trim()
        println(path0)
        val path = if (path0 == "/") "/index.html" else path0
        val file = File(root + path).canonicalFile
        println("looking for: $file")

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