package org.jetbrains.distcmp

import org.jetbrains.distcmp.report.JsonReporter
import org.jetbrains.distcmp.report.Reporter
import org.jetbrains.distcmp.report.TeamCityReporter
import org.jetbrains.distcmp.utils.requireDir
import java.io.File

class DiffSettings(args0: Array<String>) {
    val expected: File
    val actual: File

    val teamCity = false

    val runDiff = !teamCity
    val saveExpectedAndActual = !runDiff
    val saveMatchedContents = false

    val compareClassVerbose = true
    val compareClassVerboseIgnoreCompiledFrom = false

    val showProgress = !teamCity
    val runFrontend = true //!devMode

    val reportArgs: List<String>

    init {
        val args = if (devMode) arrayOf(
            "/Users/jetbrains/tasks/kwjps/wgradle/dist",
            "/Users/jetbrains/tasks/kwjps/wjps/dist"
        ) else args0

        if (args.size !in 2..3) {
            println("Usage: distdiff <expected-dir> <actual-dir> [reports-dir]")
            System.exit(1)
        }

        expected = File(args[0])
        actual = File(args[1])
        reportArgs = args.drop(2)

        requireDir(expected)
        requireDir(actual)
    }

    fun createReporter(workManager: WorkManager): Reporter =
        if (teamCity) {
            TeamCityReporter(this, workManager, "root")
        } else {
            JsonReporter(
                this,
                workManager,
                if (reportArgs.isNotEmpty()) File(reportArgs[0]) else null
            )
        }
}