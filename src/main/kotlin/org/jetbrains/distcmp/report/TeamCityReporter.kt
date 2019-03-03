package org.jetbrains.distcmp.report

import jetbrains.buildServer.messages.serviceMessages.*
import org.jetbrains.distcmp.*
import org.jetbrains.distcmp.report.FileStatus.*
import java.io.PrintStream
import java.io.PrintWriter
import java.util.concurrent.Future

class TeamCityReporter(
    settings: DiffSettings,
    workManager: WorkManager,
    val mySettings: Settings,
    val flowId: String
) : AbstractReporter(settings, workManager) {
    class Settings(val reportMatched: Boolean)

    private val out = System.out

    var dirUnpaired = false

    override fun beginReport() {
        send(TestSuiteStarted("root"))
    }

    override fun close() {
        // don't use io thread as it is shooted down at this momemnt
        out.println(TestSuiteFinished("root").asString())
    }

    override fun show() {

    }

    private fun send(msg: ServiceMessage) {
        workManager.io {
            out.println(msg.asString())
        }
    }

    override fun dir(item: Item, body: (DiffContext) -> Unit) {
        val name = lastName(item.relativePath)
        send(TestSuiteStarted(name))

        // wait until all work inside this dir to be done before going to next item
        // let its done by hooking workManager.submit
        val todo = mutableListOf<Future<*>>()
        body(
            rootContext.copy(
                workManager = object : IWorkManager {
                    override fun submit(workTitle: String, work: () -> Unit): Future<*> {
                        val f = workManager.submit(workTitle, work)
                        synchronized(todo) {
                            todo.add(f)
                        }
                        return f
                    }
                })
        )

        while (todo.isNotEmpty()) {
            synchronized(todo) {
                val copy = todo.toList()
                todo.clear()
                copy
            }.forEach {
                it.get()
            }
        }

        send(TestSuiteFinished(name))
    }

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
        val name = lastName(item.relativePath)

        if (item.kind == FileKind.DIR) {
            dirUnpaired = true
            val fakeDirTestName = ".dir"
            send(TestStarted(fakeDirTestName, false, null))
            send(
                when (item.status) {
                    MISSED -> TestFailed(fakeDirTestName, "MISSED")
                    UNEXPECTED -> TestFailed(fakeDirTestName, "UNEXPECTED")
                    else -> error("DIR cannot be ${item.status}")
                }
            )
        } else {
            if (!mySettings.reportMatched) {
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (item.status) {
                    MATCHED, COPY -> return
                }
            }

            send(TestStarted(name, false, null))

            if (dirUnpaired) {
                send(TestIgnored(name, item.status.toString()))
            } else {
                send(
                    when (item.status) {
                        MATCHED -> TestFinished(name, 0)
                        COPY -> TestIgnored(name, "COPY")
                        MISSED -> TestFailed(name, "MISSED")
                        UNEXPECTED -> TestFailed(name, "UNEXPECTED")
                        MISMATCHED -> TestFailed(name, fakeMismatchedException, actual, expected)
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