data class P(val x: Int, val y: Int)

fun destruct(): Int {
    val (l, r) = P(1, 2)
    return l + r
}
