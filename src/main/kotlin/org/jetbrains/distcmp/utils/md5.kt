package org.jetbrains.distcmp.utils

import org.apache.commons.vfs2.FileObject
import java.security.MessageDigest

fun FileObject.md5() = MessageDigest.getInstance("MD5").update(this)

fun MessageDigest.update(expected: FileObject): MessageDigest {
    val buffer = ByteArray(1024)
    val inputStream = expected.content.inputStream
    do {
        val hasMore = inputStream.read(buffer) == 1024
        update(buffer)
    } while (hasMore)
    return this
}