package org.jetbrains.distcmp.utils

import com.sun.tools.javap.JavapTask
import org.jetbrains.distcmp.compareClassVerbose
import java.io.*
import java.net.URI
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject

fun shouldNotBeCalled(): Nothing = error("Should not be called")

fun classToTxt(i: InputStream, name: String, modified: Long, uri: URI): String {
    val str = StringWriter()
    val p = JavapTask()
    val options = mutableListOf<String>()
    if (compareClassVerbose) {
        options.add("-v")
    }
    options.add("nothing")
    p.handleOptions(options.toTypedArray())
    p.setLog(str)

    (getFiledValue(p, "context") as com.sun.tools.javap.Context).put(
        PrintWriter::class.java,
        PrintWriter(str)
    )

    p.write(p.read(object : JavaFileObject {
        override fun openOutputStream(): OutputStream = shouldNotBeCalled()

        override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean =
            shouldNotBeCalled()

        override fun getKind(): JavaFileObject.Kind = javax.tools.JavaFileObject.Kind.CLASS

        override fun getName(): String = name

        override fun getAccessLevel(): Modifier = shouldNotBeCalled()

        override fun openWriter(): Writer = shouldNotBeCalled()

        override fun openInputStream(): InputStream = i

        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
            shouldNotBeCalled()

        override fun getLastModified(): Long = modified

        override fun getNestingKind(): NestingKind = shouldNotBeCalled()

        override fun toUri(): URI = uri

        override fun openReader(ignoreEncodingErrors: Boolean): Reader = i.reader()

        override fun delete(): Boolean = shouldNotBeCalled()
    }))

    return str.toString()
}

private fun getFiledValue(p: Any, name: String) =
    p.javaClass.declaredFields.find { it.name == name }.also {
        it!!.isAccessible = true
    }!!.get(p)