package org.jetbrains.distcmp

import org.apache.commons.vfs2.VFS

val devMode = System.getProperty("dev") != null

val manager = VFS.getManager()

fun main(args0: Array<String>) {
    val settings = DiffSettings(args0)
    val workManager = WorkManager(settings)
    val reporter = settings.createReporter(workManager)

    val context = DiffContext(settings, workManager, reporter)

    println("Comparing `${settings.expected}ted` vs `${settings.actual}` to `$reporter`")

    workManager.startGathering()

    reporter.beginReport()

    Item("", "root")
        .visit(
            context,
            manager.resolveFile(settings.expected, ""),
            manager.resolveFile(settings.actual, "")
        )

    workManager.waitDone()
    reporter.close()
    workManager.reportDone(context)
}