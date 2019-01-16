package artifacts

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.util.zip.ZipFile


val notFound = mutableSetOf<String>()
val home = System.getProperty("user.home")
//val jpsProject = File("/Users/jetbrains/kotlin")
val jpsProject = File("/Users/jetbrains/tasks/kwjps/wjps")
val gradleProject =  File("/Users/jetbrains/tasks/kwjps/wgradle")

fun main() {
    val index = Index() // classes that we are know
    index.scanLibConfigs(jpsProject.resolve(".idea"))
    index.scanModuleOutputs(jpsProject.resolve("out/production"))
    index.scanModuleOutputs(jpsProject.resolve("out/test"))

    makeKeg(index)
    printIndex(index)

    // let's traverse all dist jars and find where get it's contents
    buildAllJars(index, gradleProject.resolve("dist"))
    printNotFound()
}