package org.jetbrains.distcmp.report

import jetbrains.buildServer.messages.serviceMessages.*
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
import org.jetbrains.distcmp.report.FileStatus.*
import java.io.PrintStream
import java.io.PrintWriter

class TeamCityReporter(
    settings: DiffSettings,
    workManager: WorkManager,
    val mySettings: Settings,
    val flowId: String
) : AbstractReporter(settings, workManager) {
    class Settings(val reportMatched: Boolean)

    private val out = System.out

    var dirUnpaired = false
    val suiteName = "Compare dist for JPS"

    override fun beginReport() {
        workManager.io {
            send(TestSuiteStarted(suiteName))
        }
    }

    override fun close() {
        // don't use io thread as it is shooted down at this moment
        out.println(TestSuiteFinished(suiteName).asString())
    }

    override fun show() {

    }

    private fun send(msg: ServiceMessage) {
        out.println(msg.asString())
    }

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
        workManager.io {
            val relativePath = item.relativePath
                .replace(Regex("[^A-Za-z0-9\\-/]"), "_")
                .replace('/', '.')
                .trim('.', '_')

            if (item.kind == FileKind.DIR) {
                dirUnpaired = true
                send(TestStarted(relativePath, false, null))
                send(
                    when (item.status) {
                        MISSED -> TestFailed(relativePath, "MISSED")
                        UNEXPECTED -> TestFailed(relativePath, "UNEXPECTED")
                        else -> error("DIR cannot be ${item.status}")
                    }
                )
            } else {
//                if (!mySettings.reportMatched) {
//                    @Suppress("NON_EXHAUSTIVE_WHEN")
//                    when (item.status) {
//                        MATCHED, COPY -> return
//                    }
//                }

                if (item.status == COPY) {
                    send(TestIgnored(relativePath, item.status.toString()))
                } else {
                    send(TestStarted(relativePath, false, null))

                    when (item.status) {
                        MATCHED -> Unit
                        COPY -> Unit
                        MISSED -> send(TestFailed(relativePath, "MISSED"))
                        UNEXPECTED -> send(TestFailed(relativePath, "UNEXPECTED"))
                        MISMATCHED -> send(TestFailed(relativePath, fakeMismatchedException, actual, expected))
                    }

                    send(TestFinished(relativePath, 0))
                }
            }
        }
    }

    override fun writeDiff(item: Item, ext: String, outputWriter: (PrintWriter.() -> Unit)?) {
        // do nothing
    }

    override fun writeDiffAborted(item: Item, reason: String) {
        // do nothing
    }

    companion object {
        private val fakeMismatchedException = object : Throwable("MISMATCHED") {
            override fun printStackTrace(s: PrintStream?) = Unit
            override fun toString() = message!!
        }
    }
}