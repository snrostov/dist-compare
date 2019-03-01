package org.jetbrains.distcmp

class DiffSettings {

}

class DiffContext(val settings: DiffSettings) {
    val reporter = Reporter()
}