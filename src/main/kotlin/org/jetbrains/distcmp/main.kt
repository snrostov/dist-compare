package org.jetbrains.distcmp

import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import java.io.File

val manager = VFS.getManager()
val reports = mutableMapOf<String, MutableList<Context>>()
val extensions = mutableMapOf<String, Int>()
var total = 0

fun main(args: Array<String>) {
    val expected = File("/Users/jetbrains/kotlin/dist/artifacts/ideaPlugin/Kotlin")
    val actual = File("/Users/jetbrains/tasks/out/artifacts/ideaPlugin")

    print("...")
    Context("").visit(manager.resolveFile(expected, ""), manager.resolveFile(actual, ""))
    println("\rtotal: $total")

    extensions.forEach { (type, count) ->
        println("$type: $count")
    }

    println("----------------------")

    reports.forEach { (type, list) ->
        println("$type: ${list.size}")
    }
}

fun addExtension(extension: String) {
    val prev = extensions[extension]
    extensions[extension] = if (prev != null) prev + 1 else 1
}

class Context(val relativePath: String) {
    fun visit(expected: FileObject, actual: FileObject) {
        if (expected.isFolder) {
            addExtension("dir")
            visitDirectory(expected.children.toList(), actual.children.toList())
        } else if (manager.canCreateFileSystem(expected)) {
            addExtension("archive")
            visitDirectory(
                manager.createFileSystem(expected).children.toList(),
                manager.createFileSystem(actual).children.toList()
            )
        } else {
            val extension = expected.name.extension
            addExtension(extension)

            val expectedTxt = expected.content.inputStream.bufferedReader().readText()
            val actualTxt = actual.content.inputStream.bufferedReader().readText()

            if (expectedTxt == actualTxt) reportMatch()
            else reportMismatch("content mismatched")
        }
    }

    private fun visitDirectory(expectedChildren: List<FileObject>, actualChildren: List<FileObject>) {
        val actualChildrenByName = actualChildren.associateBy { it.name.baseName }
        val actualVisited = mutableSetOf<String>()

        expectedChildren.forEach { aChild ->
            val name = aChild.name.baseName
            val bChild = actualChildrenByName[name]
            val child = child(name)
            if (bChild == null) {
                child.reportMismatch("Missed file")
            } else {
                actualVisited.add(name)
                child.visit(aChild, bChild)
            }
        }

        actualChildren.forEach {
            val name = it.name.baseName
            if (name !in actualVisited) {
                child(name).reportMismatch("Unexpected file")
            }
        }
    }

    fun reportMismatch(description: String) {
        reports.getOrPut(description) { mutableListOf() }.add(this)
    }

    fun reportMatch() {
        reports.getOrPut("MATCHED") { mutableListOf() }.add(this)
    }

    private fun child(name: String) = Context("$relativePath/$name").also {
        total++
        print("\r" + it.relativePath)
    }
}

