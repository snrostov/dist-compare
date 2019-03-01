package org.jetbrains.distcmp

import org.jetbrains.distcmp.report.JsonReporter
import org.jetbrains.distcmp.report.Reporter
import java.io.File
import java.nio.file.Files

class DiffContext(args: Array<String>) {
    val settings = DiffSettings(args)
    val workManager = WorkManager(this)
    val reporter: Reporter = JsonReporter(this)
}

class DiffSettings(args0: Array<String>) {
    val expected: File
    val actual: File

    val reportDir: File
    val diffDir: File

    val runDiff = true
    val saveExpectedAndActual = !runDiff
    val saveMatchedContents = false
    val compareClassVerbose = true
    val compareClassVerboseIgnoreCompiledFrom = false

    val showProgress = true
    val printTeamCityMessageServices = false
    val runFrontend = true //!devMode

    val explicitReportDir: Boolean

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
        explicitReportDir = args.size == 3
        reportDir =
            if (explicitReportDir) File(args[2])
            else Files.createTempDirectory("dist-compare").toFile()

        println("Comparing `$expected` vs `$actual` to `${reportDir}`")

        requireDir(expected)
        requireDir(actual)
        requireDir(reportDir)

        diffDir = reportDir.resolve("diff")
        diffDir.mkdir()
    }

    private fun requireDir(file: File) {
        if (!file.isDirectory) {
            println("$file is not a directory")
            System.exit(1)
        }
    }
}