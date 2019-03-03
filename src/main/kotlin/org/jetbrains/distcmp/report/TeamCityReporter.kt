package org.jetbrains.distcmp.report

import jetbrains.buildServer.messages.serviceMessages.*
import org.jetbrains.distcmp.DiffContext
import org.jetbrains.distcmp.DiffSettings
import org.jetbrains.distcmp.Item
import org.jetbrains.distcmp.WorkManager
import java.io.PrintStream
import java.io.PrintWriter

class TeamCityReporter(
    settings: DiffSettings,
    workManager: WorkManager,
    val flowId: String
) : AbstractReporter(settings, workManager) {
    private val out = System.out

    var dirUnpaired = false

    override fun beginReport() {
        send(TestSuiteStarted("root"))
    }

    override fun close() {
        send(TestSuiteFinished("root"))
    }

    private fun send(msg: ServiceMessage) {
        msg.setFlowId(flowId)
        out.println(msg.asString())
    }

    override fun dir(item: Item, body: (DiffContext) -> Unit) {
        val name = lastName(item.relativePath)
        val childReporter = TeamCityReporter(settings, workManager, item.relativePath)
        childReporter.send(TestSuiteStarted(name))
        body(rootContext.copy(reporter = childReporter))
        childReporter.send(TestSuiteFinished(name))
    }

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
        val name = lastName(item.relativePath)

        if (item.kind == FileKind.DIR) {
            dirUnpaired = true
            val fakeDirTestName = ".dir"
            send(TestStarted(fakeDirTestName, false, null))
            send(
                when (item.status) {
                    FileStatus.MISSED -> TestFailed(fakeDirTestName, "MISSED")
                    FileStatus.UNEXPECTED -> TestFailed(fakeDirTestName, "UNEXPECTED")
                    else -> error("DIR cannot be ${item.status}")
                }
            )
        } else {
            send(TestStarted(name, false, null))
            if (dirUnpaired) {
                send(TestIgnored(name, item.status.toString()))
            } else {
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
    }

    private fun lastName(path: String) = path.split("/").last()

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