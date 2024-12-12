// TARGET_BACKEND: JVM
// WITH_STDLIB

// FILE: JavaBase.java
import java.util.concurrent.atomic.AtomicInteger;

public class JavaBase {
    public String foo(AtomicInteger a) {
        return "1";
    }
}

// FILE: test.kt
import kotlin.concurrent.AtomicInt
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalStdlibApi::class)
class KotlinChildFromBase : JavaBase() {
    fun foo(a: AtomicInt): Int = 2
}

@OptIn(ExperimentalStdlibApi::class)
fun box(): String {
    return if (
        (KotlinChildFromBase().foo(AtomicInteger(1)) == "1") &&
        (KotlinChildFromBase().foo(AtomicInt(1)) == 2)
        ) "OK"
    else "not OK"
}