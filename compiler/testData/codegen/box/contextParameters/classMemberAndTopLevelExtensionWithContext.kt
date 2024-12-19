// IGNORE_BACKEND_K1: ANY
// IGNORE_BACKEND_K2: NATIVE
// ISSUE: KT-73779
// LANGUAGE: +ContextParameters
class A

class Base {
    context(a: A)
    fun funMember(): String { return "OK" }
}

context(a: A)
fun Base.funMember(): String { return "not OK" }

fun box(): String {
    with(A()) {
        return Base().funMember()
    }
}