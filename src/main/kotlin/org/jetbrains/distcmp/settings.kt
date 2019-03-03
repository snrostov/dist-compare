package org.jetbrains.distcmp

import org.jetbrains.distcmp.report.JsonReporter
import org.jetbrains.distcmp.report.Reporter
import org.jetbrains.distcmp.report.TeamCityReporter
import org.jetbrains.distcmp.utils.requireDir
import java.io.File

class DiffSettings(args: Array<String>) {
    val expected: File
    val actual: File

    var teamCity = false

    var runDiff = true
    var saveExpectedAndActual = false
    var saveMatchedContents = false

    var compareClassVerbose = true
    var compareClassVerboseIgnoreCompiledFrom = false

    var showProgress = true
    val reportArgs: List<String>

    init {
        val freeArgs = mutableListOf<String>()
        args.forEach {
            when {
                it == "--abiOnly" -> abiOnly()
                it == "--teamCity" -> teamCity()
                it == "--noDiff" -> noDiff()
                it == "--statusOnly" -> statusOnly()
                it == "--saveAllContents" -> saveAllContents()
                it.startsWith("--") -> printUsage("Unknown flag `$it`")
                else -> freeArgs.add(it)
            }
        }

        if (freeArgs.size !in 2..3) {
            printUsage()
        }

        expected = File(freeArgs[0])
        actual = File(freeArgs[1])
        reportArgs = freeArgs.drop(2)

        requireDir(expected)
        requireDir(actual)
    }

    private fun printUsage(reason: String? = null) {
        reason?.let(::println)
        println("Usage: distdiff <expected-dir> <actual-dir> [reports-dir] [--abiOnly] [--teamCity] [--noDiff] [--statusOnly] [--saveAllContents]")
        System.exit(1)
    }

    fun abiOnly() {
        compareClassVerbose = false
        compareClassVerboseIgnoreCompiledFrom = true
    }

    fun noDiff() {
        runDiff = true
        saveExpectedAndActual = true
    }

    fun statusOnly() {
        runDiff = true
        saveExpectedAndActual = false
        saveMatchedContents = false
    }

    fun saveAllContents() {
        saveExpectedAndActual = true
        saveMatchedContents = true
    }

    fun teamCity() {
        teamCity = true
        showProgress = false
    }

    fun createReporter(workManager: WorkManager): Reporter =
        when {
            teamCity -> TeamCityReporter(this, workManager, "root")
            else -> JsonReporter(
                this,
                workManager,
                if (reportArgs.isNotEmpty()) File(reportArgs[0]) else null,
                JsonReporter.Frontend()
            )
        }
}