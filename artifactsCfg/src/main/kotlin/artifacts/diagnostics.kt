package artifacts

import java.io.File


fun printNotFound() {
    File("notFound.txt").writer().use { notFoundWriter ->
        notFound.sorted().forEach {
            notFoundWriter.append(it).appendln()
        }
    }
}

fun printIndex(index: Index) {
    File("index.txt").writer().use { w ->
        index.byPath.keys.forEach {
            w.append(it).appendln()
        }
    }
}

fun makeKeg(index: Index) {
    var totalSize = 0L
    index.jars.forEach {
//        println("JAR $it")
        totalSize += it.length()
        val dest = File("jars/${it.name}")
        if (!dest.exists()) {
            it.copyTo(dest)
        }
    }
    println(totalSize)
}

