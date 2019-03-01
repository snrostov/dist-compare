package org.jetbrains.distcmp

import org.jetbrains.distcmp.report.JsonReporter
import org.jetbrains.distcmp.report.Reporter
import java.io.File

class DiffSettings {
    lateinit var reportDir: File
    lateinit var diffDir: File

    val runDiff = true
    val saveExpectedAndActual = !runDiff
    val saveMatchedContents = false
    val compareClassVerbose = true
    val compareClassVerboseIgnoreCompiledFrom = false

    val showProgress = true
    val printTeamCityMessageServices = false
    val runFrontend = true //!devMode
}

class DiffContext {
    val settings = DiffSettings()
    val workManager = WorkManager(this)
    val reporter: Reporter = JsonReporter(this)
}