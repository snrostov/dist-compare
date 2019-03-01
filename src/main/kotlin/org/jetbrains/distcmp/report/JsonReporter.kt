package org.jetbrains.distcmp.report

import com.google.gson.GsonBuilder
import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.Item
import java.io.File
import java.io.PrintWriter

class JsonReporter(context: DiffContext) : AbstractReporter(context) {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val jsonWriter = gson.newJsonWriter(context.settings.diffDir.resolve("data.json").writer())

    override fun beginReport() {
        jsonWriter.beginArray()
    }

    override fun close() {
        jsonWriter.endArray()
        jsonWriter.close()
    }

    override fun writeItem(item: FileInfo) {
        context.workManager.io {
            gson.toJson(item, item.javaClass, jsonWriter)
        }
    }

    override fun writeDiff(
        item: Item,
        ext: String,
        outputWriter: (PrintWriter.() -> Unit)?
    ) {
        if (outputWriter == null) return

        context.workManager.io {
            val reportFile = File("${context.settings.diffDir.path}/${item.relativePath}.$ext")
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
}