package artifacts

import java.io.File
import java.util.zip.ZipFile

fun buildAllJars(index: Index, dir: File) {
    dir.listFiles().forEach {
        when {
            it.isDirectory -> buildAllJars(index, it)
            it.name.endsWith(".jar") -> buildJar(index, it)
        }
    }
}

fun buildJar(index: Index, jar: File) {
    println("======= $jar")

    var found = 0
    val modules = mutableSetOf<Module>()

    ZipFile(jar).entries().toList().forEach {
        val fileName = it.name
        if (!fileName.endsWith("/") && fileName != "META-INF/MANIFEST.MF") {
            val candidates = index.byPath[fileName]
            if (candidates == null) {
                if (fileName.endsWith(".class")) {
                    notFound.add(fileName)
                }
            } else {
                modules.add(candidates.first().module)
//                if (candidates.size > 1) warn("ambigated: $fileName: ${candidates.joinToString { it.module.name }}")
                found++
            }
        }
    }

//    modules.map { it.name }.sorted().forEach {
//        println(it)
//    }

    println("found: $found, notFound: ${notFound.size}")
}


