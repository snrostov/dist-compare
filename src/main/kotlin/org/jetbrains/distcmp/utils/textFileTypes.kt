package org.jetbrains.distcmp.utils

val textFileTypes = listOf(
    "txt",
    "bat",
    "MF",
    "kt",
//    "js",
    "java",
    "html",
    "template",
    "dtd",
    "properties",
    "xml"
).map { it.toLowerCase() }.toSet()