package artifacts


fun warn(s: String) {
    println(s)
}

inline fun progress(name: String, x: () -> Unit) {
//    println(name)
    x()
}