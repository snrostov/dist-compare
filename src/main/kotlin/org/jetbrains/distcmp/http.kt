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

            // Object exists and is a file: accept with response code 200.
//            val mime = when {
//                path.endsWith(".js") -> "application/javascript"
//                path.endsWith(".gif") -> "image/gif"
//                path.endsWith(".patch") -> "text/plain"
//                else -> "text/html"
//            }
//
//            val h = exchange.responseHeaders
//            h.set("Content-Type", mime)
//            exchange.sendResponseHeaders(200, 0)
//
//            val os = exchange.responseBody
//            val fs = FileInputStream(file)
//            val buffer = ByteArray(0x10000)
//            var count = 0
//            while (true) {
//                count = fs.read(buffer)
//                if (count < 0) break
//                os.write(buffer, 0, count)
//            }
//            fs.close()
//            os.close()
        }
    }
}