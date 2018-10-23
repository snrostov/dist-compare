package org.jetbrains.distcmp

import com.sun.tools.javap.JavapTask
import java.io.*
import java.net.URI
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.JavaFileObject

fun err(): Nothing = error("")

fun classToTxt(i: InputStream, name: String, modified: Long, uri: URI): String {
    val str = StringWriter()
    val p = JavapTask()
    p.handleOptions(arrayOf("-v", "nothing"))
    p.setLog(str)

    (getFiledValue(p, "context") as com.sun.tools.javap.Context).put(
        PrintWriter::class.java,
        PrintWriter(str)
    )

    p.write(p.read(object : JavaFileObject {
        override fun openOutputStream(): OutputStream = err()

        override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean = err()

        override fun getKind(): JavaFileObject.Kind = javax.tools.JavaFileObject.Kind.CLASS

        override fun getName(): String = name

        override fun getAccessLevel(): Modifier = err()

        override fun openWriter(): Writer = err()

        override fun openInputStream(): InputStream = i

        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = err()

        override fun getLastModified(): Long = modified

        override fun getNestingKind(): NestingKind = err()

        override fun toUri(): URI = uri

        override fun openReader(ignoreEncodingErrors: Boolean): Reader = i.reader()

        override fun delete(): Boolean = err()
    }))

    return str.toString()
}

private fun getFiledValue(p: Any, name: String) =
    p.javaClass.declaredFields.find { it.name == name }.also {
        it!!.isAccessible = true
    }!!.get(p)