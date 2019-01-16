package artifacts

import java.io.File

class Module(val root: File, val name: String = root.name) {
    override fun toString(): String = name
}

class CompileOutput(val module: Module)
