// WITH_STDLIB
// FIR_IDENTICAL
import kotlin.concurrent.*

@OptIn(ExperimentalStdlibApi::class)
fun foo(a: AtomicIntArray) {
    <!DEPRECATION!>a[1]<!>
    <!DEPRECATION!>a[1]<!> = 2
    a.loadAt(1)
    a.storeAt(1, 1)
}