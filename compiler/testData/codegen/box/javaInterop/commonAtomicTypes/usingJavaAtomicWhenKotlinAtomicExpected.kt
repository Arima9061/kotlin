// TARGET_BACKEND: JVM
// WITH_STDLIB

// FILE: JavaClass.java
import java.util.concurrent.atomic.*;

public class JavaClass {
    public AtomicInteger a = new AtomicInteger(0);
    public AtomicIntegerArray b = new AtomicIntegerArray(new int[]{1, 1, 1});
}

// FILE: test.kt
import JavaClass
import kotlin.concurrent.asKotlinAtomic
import kotlin.concurrent.asKotlinAtomicArray
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicIntArray

@OptIn(ExperimentalStdlibApi::class)
class KotlinClass {
    fun foo(a: AtomicInt): String {
        return "O"
    }
    fun bar(a: AtomicIntArray): String {
        return "K"
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun usage(a: KotlinClass): String {
    return a.foo(JavaClass().a.asKotlinAtomic())+ a.bar(JavaClass().b.asKotlinAtomicArray())
}

fun box(): String {
    return usage(KotlinClass())
}