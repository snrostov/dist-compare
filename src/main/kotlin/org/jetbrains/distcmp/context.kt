package org.jetbrains.distcmp

class DiffSettings {

}

class DiffContext(val settings: DiffSettings) {
    val workManager = WorkManager(this)
    val reporter = Reporter(this)
}