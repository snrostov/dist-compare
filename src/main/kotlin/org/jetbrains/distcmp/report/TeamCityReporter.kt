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
    val suiteName = "Compare dist for JPS"

    override fun beginReport() {
        send(TestSuiteStarted(suiteName))
    }

    override fun close() {
        // don't use io thread as it is shooted down at this moment
        out.println(TestSuiteFinished(suiteName).asString())
    }

    override fun show() {

    }

    private fun send(msg: ServiceMessage) {
        workManager.io {
            out.println(msg.asString())
        }
    }

    override fun dir(item: Item, body: (DiffContext) -> Unit) {
        lastName(item.relativePath)

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
    }

    override fun writeItem(item: FileInfo, expected: String?, actual: String?) {
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
            if (!mySettings.reportMatched) {
                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (item.status) {
                    MATCHED, COPY -> return
                }
            }

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