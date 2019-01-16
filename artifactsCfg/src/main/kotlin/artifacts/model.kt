package artifacts

import java.io.File

class Module(val root: File, val name: String = root.name) {
    override fun toString(): String = name
}

class CompileOutput(val module: Module)

class PArtifact(val artifactName: String, val outputDir: File, private val contents: ArtifactElement.Root) {
    fun render(context: PathContext) = xml("component", "name" to "ArtifactManager") {
        xml("artifact", "name" to artifactName) {
            xml("output-path") {
                raw(context(outputDir))
            }

            add(contents.renderRecursively(context))
        }
    }
}

sealed class ArtifactElement {
    private val myChildren = mutableListOf<ArtifactElement>()
    val children get() = myChildren

    fun add(child: ArtifactElement) {
        myChildren += child
    }

    abstract fun render(context: PathContext): xml

    fun renderRecursively(context: PathContext): xml {
        return render(context).apply {
            children.forEach { add(it.renderRecursively(context)) }
        }
    }

    fun getDirectory(path: String): ArtifactElement {
        if (path.isEmpty()) {
            return this
        }

        var current: ArtifactElement = this
        for (segment in path.split("/")) {
            val existing = current.children.firstOrNull { it is Directory && it.name == segment }
            if (existing != null) {
                current = existing
                continue
            }

            current = Directory(segment).also { current.add(it) }
        }

        return current
    }

    class Root : ArtifactElement() {
        override fun render(context: PathContext) = xml("root", "id" to "root")
    }

    data class Directory(val name: String) : ArtifactElement() {
        override fun render(context: PathContext) = xml("element", "id" to "directory", "name" to name)
    }

    data class Archive(val name: String) : ArtifactElement() {
        override fun render(context: PathContext) = xml("element", "id" to "archive", "name" to name)
    }

    data class ModuleOutput(val moduleName: String) : ArtifactElement() {
        override fun render(context: PathContext) = xml("element", "id" to "module-output", "name" to moduleName)
    }

    data class FileCopy(val source: File, val outputFileName: String? = null) : ArtifactElement() {
        override fun render(context: PathContext): xml {
            val args = mutableListOf("id" to "file-copy", "path" to context(source))
            if (outputFileName != null) {
                args += "output-file-name" to outputFileName
            }

            return xml("element", *args.toTypedArray())
        }
    }

    data class DirectoryCopy(val source: File) : ArtifactElement() {
        override fun render(context: PathContext) = xml("element", "id" to "dir-copy", "path" to context(source))
    }

    data class ProjectLibrary(val name: String) : ArtifactElement() {
        override fun render(context: PathContext) =
            xml("element", "id" to "library", "level" to "project", "name" to name)
    }

    data class ExtractedDirectory(val archive: File, val pathInJar: String = "/") : ArtifactElement() {
        override fun render(context: PathContext) =
            xml("element", "id" to "extracted-dir", "path" to context(archive), "path-in-jar" to pathInJar)
    }
}

class xml(val name: String, private vararg val args: Pair<String, Any>, block: xml.() -> Unit = {}) {
    private companion object {
        fun makeXml(name: String, vararg args: Pair<String, Any>, block: xml.() -> Unit = {}): xml {
            return xml(name, *args, block = block)
        }
    }

    private val children = mutableListOf<xml>()
    private var value: Any? = null

    init {
        @Suppress("UNUSED_EXPRESSION")
        block()
    }

    fun xml(name: String, vararg args: Pair<String, Any>, block: xml.() -> Unit = {}) {
        children += makeXml(name, *args, block = block)
    }

    fun add(xml: xml) {
        children += xml
    }

    fun raw(text: String) {
        value = text
    }

//    private fun toElement(): Element {
//        val element = Element(name)
//
//        for (arg in args) {
//            element.setAttribute(arg.first, arg.second.toString())
//        }
//
//        require(value == null || children.isEmpty())
//
//        value?.let { value ->
//            element.addContent(value.toString())
//        }
//
//        for (child in children) {
//            element.addContent(child.toElement())
//        }
//
//        return element
//    }

//    override fun toString(): String {
//        val document = Document().also { it.rootElement = toElement() }
//        val output = XMLOutputter().also { it.format = Format.getPrettyFormat() }
//        return output.outputString(document)
//    }
}

interface PathContext {
    operator fun invoke(file: File): String

    fun url(file: File): Pair<String, String> {
        val path = when {
            file.isFile && file.extension.toLowerCase() == "jar" -> "jar://" + this(file) + "!/"
            else -> "file://" + this(file)
        }

        return Pair("url", path)
    }
}

class ProjectContext private constructor(private val projectDir: File) : PathContext {
//    constructor(project: PProject) : this(project.rootDirectory)
//    constructor(project: Project) : this(project.projectDir)

    override fun invoke(file: File): String {
        return simplifyUserHomeDirPath(
            replacePrefix(
                file.absolutePath,
                projectDir.absolutePath.withSlash(),
                "PROJECT_DIR"
            )
        )
    }
}

class ModuleContext(val projectRoot: File, val moduleFile: File) : PathContext {
    override fun invoke(file: File): String {
        if (!file.startsWith(projectRoot)) {
            return simplifyUserHomeDirPath(file.absolutePath)
        }

        return "\$MODULE_DIR\$/" + file.toRelativeString(moduleFile.parentFile)
    }
}

fun String.withSlash() = if (this.endsWith("/")) this else (this + "/")
fun String.withoutSlash() = this.trimEnd('/')

private val USER_HOME_DIR_PATH = System.getProperty("user.home").withSlash()

private fun replacePrefix(path: String, prefix: String, variableName: String): String {
    if (path.startsWith(prefix)) {
        return "$" + variableName + "$/" + path.drop(prefix.length)
    }

    return path
}

private fun simplifyUserHomeDirPath(path: String): String {
    return replacePrefix(path, USER_HOME_DIR_PATH, "USER_HOME")
}
