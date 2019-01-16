package artifacts

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class Index {
    val byPath = mutableMapOf<String, MutableList<CompileOutput>>()
    val jars = mutableSetOf<File>()

    fun scanModuleOutputs(outputRoot: File) {
        outputRoot.listFiles().forEach {
            if (it.isDirectory) {
                progress("scanning module output: $it") {
                    scanModuleOutput(Module(it))
                }
            } else {
                warn("$it ignored")
            }
        }
    }

    fun scanModuleOutput(module: Module, dir: File = module.root) {
        dir.listFiles().forEach {
            if (it.isDirectory) {
                scanModuleOutput(module, it)
            } else {
                val relativePath = it.relativeTo(module.root).path
                add(relativePath, CompileOutput(module))
            }
        }
    }

    private fun add(path: String, item: CompileOutput) {
        byPath.getOrPut(path) { mutableListOf() }.add(item)

        if (path.startsWith("org/jetbrains/kotlin")) {
            add(path.replace("org/jetbrains/kotlin", "kotlin/reflect/jvm/internal/impl"), item)
        }
    }

    fun scanJar(jar: File) {
        jars.add(jar.canonicalFile)

        val module = Module(jar)
        progress("scanning jar: $jar") {
            ZipFile(jar).entries().asSequence().forEach {
                val fileName = it.name
                add(fileName, CompileOutput(module))
            }
        }
    }

    fun scanLibConfigs(configs: File) {
        configs.listFiles().forEach {
            when {
                it.isDirectory -> scanLibConfigs(it)
                it.name.endsWith(".xml") || it.name.endsWith(".iml") -> scanLibConfig(it)
            }
        }
    }

    fun scanLibConfig(xmlFile: File) {
//        println("XML $xmlFile")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        document.getElementsByTagName("library").forEach { lib ->
            val name = lib.attributes["name"]

            lib.childNodes.forEach { libEl ->
                if (libEl.nodeName.toLowerCase() == "classes") {
                    libEl.childNodes.forEach { classes ->
                        if (classes.nodeName.toLowerCase() == "root") {
                            val url = classes.attributes["url"]
                            if (url != null) {
                                if (url.startsWith("jar://")) {
                                    if (url.endsWith("!/")) {
                                        val filePath = url
                                            .removePrefix("jar://")
                                            .removeSuffix("!/")
                                            .replace("\$USER_HOME\$", home)
                                            .replace("\$PROJECT_DIR\$", "/Users/jetbrains/tasks/kwjps/wjps")
                                            .replace("\$MODULE_DIR\$", xmlFile.parent)
                                        val file = File(filePath).canonicalFile

                                        if (file.exists()) scanJar(file)
                                        else warn("cannot find: $file (referenced in $xmlFile)")
                                    } else warn("only jar root dirs supported")
                                } else warn("unsupported protocol: $url")
                            }
                        }
                    }
                }
            }
        }
    }
}