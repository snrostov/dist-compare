package artifacts

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory


val notFound = mutableSetOf<String>()
val home = System.getProperty("user.home")

fun main() {
    val index = Index()

    index.scanLibConfig(File("/Users/jetbrains/tasks/kwjps/wjps/.idea/modules/plugins/jvm-abi-gen/jvm-abi-gen_main.iml"))
    index.scanLibConfigs(File("/Users/jetbrains/tasks/kwjps/wjps/.idea"))

    index.scanModuleOutputs(File("/Users/jetbrains/sandbox/output/out/production"))
    index.scanModuleOutputs(File("/Users/jetbrains/sandbox/output/out/test"))
    index.scanModuleOutput(Module(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/builtins")))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/protobuf-2.6.1-lite.jar"))
    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/protobuf-java-relocated-2.6.1.jar"))
    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/common/kotlin-stdlib-common.jar"))
    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib.jar"))
    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jdk7.jar"))
    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jdk8.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jre7.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jre8.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/common/kotlin-stdlib-common-sources.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-sources.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jdk7-sources.jar"))
//    index.scanJar(File("/Users/jetbrains/tasks/kwjps/wjps/bootstrap/kotlin-stdlib-jdk8-sources.jar"))

    var totalSize = 0L
    index.jars.forEach {
        totalSize += it.length()
        val dest = File("jars/${it.name}")
        if (!dest.exists()) {
            it.copyTo(dest)
        }
    }
    println(totalSize)

    File("index.txt").writer().use { w ->
        index.byPath.keys.forEach {
            w.append(it).appendln()
        }
    }

    buildAllJars(index, File("/Users/jetbrains/sandbox/output/gradle-dist/dist"))

//    buildJar(
//        index,
//        File("/Users/jetbrains/sandbox/output/gradle-dist/dist/artifacts/ideaPlugin/Kotlin/lib/kotlin-plugin.jar")
//    )

    File("notFound.txt").writer().use { notFoundWriter ->
        notFound.sorted().forEach {
            notFoundWriter.append(it).appendln()
        }
    }
}

fun Node.get(name: String): Node? {
    childNodes.forEach {
        if (it.localName == name) return it
    }

    return null
}

inline fun NodeList.forEach(x: (Node) -> Unit) {
    var i = 0;
    while (i < length) {
        x(item(i))
        i++
    }
}

operator fun NamedNodeMap.get(name: String): String? = getNamedItem(name)?.nodeValue

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
                it.name.endsWith(".xml") -> scanLibConfig(it)
            }
        }
    }

    fun scanLibConfig(xmlFile: File) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        document.getElementsByTagName("library").forEach { lib ->
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
                                        else warn("cannot find: $file")
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


fun warn(s: String) {
    println(s)
}

inline fun progress(name: String, x: () -> Unit) {
//    println(name)
    x()
}

class Module(val root: File, val name: String = root.name) {
    override fun toString(): String = name
}

class CompileOutput(val module: Module)
