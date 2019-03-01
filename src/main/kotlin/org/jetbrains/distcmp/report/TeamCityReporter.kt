package org.jetbrains.distcmp.report

import jetbrains.buildServer.messages.serviceMessages.*
import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
import java.io.PrintWriter

class TeamCityReporter(settings: DiffSettings, workManager: WorkManager) : AbstractReporter(settings, workManager) {
    private val out = System.out

    override fun beginReport() {

    }

    override fun close() {

    }

    private fun send(msg: BaseTestMessage) {
        out.println(msg.asString())
    }

    inner class Channel {
        val flowId = 0

        fun open() {

        }

        fun close() {

        }
    }

//    override fun dir(item: Item, body: (DiffContext) -> Unit) {
//        body()
//    }

    private val fakeMismatchedException = Error("MISMATCHED")

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
        val name = item.relativePath.split("/").last()

        if (item.kind == FileKind.DIR) {
            // todo:
        } else {
            send(TestStarted(name, false, null))
            send(
                when (item.status) {
                    FileStatus.MATCHED -> TestFinished(name, 0)
                    FileStatus.COPY -> TestIgnored(name, "COPY")
                    FileStatus.MISSED -> TestFailed(name, "MISSED")
                    FileStatus.UNEXPECTED -> TestFailed(name, "UNEXPECTED")
                    FileStatus.MISMATCHED -> TestFailed(name, fakeMismatchedException, actual, expected)
                }
            )
        }
    }

    override fun writeDiff(item: Item, ext: String, outputWriter: (PrintWriter.() -> Unit)?) {
        error("Diffs not supporter in TeamCityReporter")
    }

    override fun writeDiffAborted(item: Item, reason: String) {
        error("Diffs not supporter in TeamCityReporter")
    }
}