package artifacts

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList

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
