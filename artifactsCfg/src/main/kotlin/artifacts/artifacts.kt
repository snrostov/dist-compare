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

    val fileNames = ZipFile(jar).entries().toList().map { it.name }.toSet()

    class GuessedModule(
        val module: Module,
        val unexpected: Int
    ) {
        var conflicts = 0
        var usages = 0

        override fun toString(): String {
            return "${module.name} ($usages matches, $unexpected unexpected, $conflicts overrides)"
        }
    }

    val guesses = mutableMapOf<Module, GuessedModule>()
    fun guess(module: Module) = guesses.getOrPut(module) {
        GuessedModule(module, unexpected = module.contents.count { it.endsWith(".class") && it !in fileNames })
    }

    val modules = mutableSetOf<GuessedModule>()

    fun add(candidates: List<GuessedModule>) {
        modules.addAll(candidates)
        if (candidates.size > 1) {
            candidates.sortedBy { it.unexpected }.forEach { it.conflicts++ }
        }

        candidates.forEach {
            it.usages++
        }
    }

    fun select(candidates: List<GuessedModule>) {
        val withoutAlienFiles = candidates.filter { it.unexpected == 0 }
        if (withoutAlienFiles.isNotEmpty()) add(withoutAlienFiles)
        else add(candidates)
    }

    fileNames.forEach { fileName ->
        // todo: remove fileName.endsWith(".class")
        if (fileName.endsWith(".class") && !fileName.endsWith("/") && fileName != "META-INF/MANIFEST.MF") {
            val candidates = index.byPath[fileName]
            if (candidates == null) {
                if (fileName.endsWith(".class")) {
                    notFound.add(fileName)
                }
            } else {
                found++

                val candidates2 = candidates.map { guess(it.module) }

                val moduleCandidates = candidates2.filter { !it.module.isLib }
                val libCandidates = candidates2.filter { it.module.isLib }
                    .groupBy { it.module.name }
                    .map { it.value.first() }

                if (moduleCandidates.isNotEmpty()) select(moduleCandidates)
                else select(libCandidates)
            }
        }
    }

    modules.map { it.toString() }.sorted().forEach {
        println(it)
    }

    println("found: $found, notFound: ${notFound.size}")
}


