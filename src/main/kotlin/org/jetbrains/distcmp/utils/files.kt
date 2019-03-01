package org.jetbrains.distcmp.utils

import java.io.File

fun requireDir(file: File) {
    if (!file.isDirectory) {
        println("$file is not a directory")
        System.exit(1)
    }
}
