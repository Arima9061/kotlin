// IGNORE_BACKEND_K1: ANY
// LANGUAGE: +ContextParameters
// IGNORE_BACKEND_K2: JS_IR, JS_IR_ES6, WASM, NATIVE
// IGNORE_IR_DESERIALIZATION_TEST: JS_IR, NATIVE
// ISSUE: KT-74045

class A

context(a: A)
val b: String
    get() = "OK"

val b: String
    get() = "not OK"

fun box(): String {
    with(A()) {
        return b
    }
}