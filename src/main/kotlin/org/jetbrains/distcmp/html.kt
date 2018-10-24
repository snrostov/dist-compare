package org.jetbrains.distcmp

import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.charset.Charset
import java.util.*
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun htmlStart() {
    val resultFile = reportsDir.resolve("report.html.zip")
    resultFile.parentFile.mkdirs()
    resultFile.createNewFile()
    zipOutputStream = ZipOutputStream(resultFile.outputStream())
    zipOutputStream.putNextEntry(ZipEntry("report.html"))
    html = zipOutputStream.writer()
    html.write(
        """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Dist compare report</title>
""" +
                "document.getElementById(\"diff\").innerHTML = " +
                """
</head>
<body>
        """
    )
}

fun Item.htmlWriteDiff(outputWriter: PrintWriter.() -> Unit) {
    val bytes = ByteArrayOutputStream()
    outputWriter(PrintWriter(DeflaterOutputStream(bytes)))
    bytes.close()

    if (bytes.size() != 0) {
        val base64 = String(Base64.getEncoder().encode(bytes.toByteArray()), Charset.forName("UTF-8"))
        synchronized(html) {
            html.write("<script id='patch-$id' type='text/plain'>\n$base64\n</script>\n")
        }
    }
}

fun htmlEnd() {
    html.write("\n\n<script type=\"text/javascript\">\ninit(")

    GsonBuilder().create().toJson(items, html)

    html.write(")\n</script>")

    html.write(
        """
</body>
</html>
    """
    )

    html.flush()
    zipOutputStream.closeEntry()
    zipOutputStream.close()
}

