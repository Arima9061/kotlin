// WITH_STDLIB
// SKIP_TXT

fun <T> foo(p: VArray<<!TYPE_PARAMETER_AS_REIFIED!>T<!>>) {
    var y: VArray<<!TYPE_PARAMETER_AS_REIFIED!>T<!>>? = null
}

class A<T>(val fieldT: VArray<<!TYPE_PARAMETER_AS_REIFIED!>T<!>>) {
    var fieldTNullable: VArray<<!TYPE_PARAMETER_AS_REIFIED!>T<!>>? = null
}

inline fun <reified T> bar(p: VArray<T>) {
    var y: VArray<T>
    var z: VArray<Int>
}