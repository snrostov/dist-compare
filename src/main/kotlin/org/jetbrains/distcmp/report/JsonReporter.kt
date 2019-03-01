package org.jetbrains.distcmp.report

import com.google.gson.GsonBuilder
import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
import java.io.File
import java.io.PrintWriter

class JsonReporter(settings: DiffSettings, workManager: WorkManager) : AbstractReporter(settings, workManager) {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val jsonWriter = gson.newJsonWriter(settings.diffDir.resolve("data.json").writer())

    override fun beginReport() {
        jsonWriter.beginArray()
    }

    override fun close() {
        jsonWriter.endArray()
        jsonWriter.close()
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
            val reportFile = File("${settings.diffDir.path}/${item.relativePath}.$ext")
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