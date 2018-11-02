package org.jetbrains.distcmp.utils

import org.apache.commons.vfs2.FileObject
import org.jetbrains.distcmp.FileKind

val FileObject.kind: FileKind
    get() =
        if (isFolder) FileKind.DIR
        else {
            val extension = name.extension.toLowerCase()
            when {
                extension == "class" -> FileKind.CLASS
                isTextFile(extension) -> FileKind.TEXT
                else -> FileKind.BIN
            }
        }

fun isBadExt(ext: String) = ext.isBlank() || (ext.any { it.isUpperCase() } && ext.any { it.isLowerCase() })

fun isTextFile(extension: String) = extension.toLowerCase() in textFileTypes || isBadExt(extension)