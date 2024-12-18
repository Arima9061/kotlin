// TARGET_BACKEND: JVM
// WITH_STDLIB

// FILE: KotlinClass.kt
import kotlin.concurrent.atomics.AtomicInt

@OptIn(ExperimentalStdlibApi::class)
open class KotlinClass {
    open fun foo(a: AtomicInt): String {
        return "KotlinClass"
    }
    open val a: AtomicInt = AtomicInt(0)
}

// FILE: JavaClassWithFakeOverride.java

public class JavaClassWithFakeOverride extends KotlinClass {}

// FILE: test.kt
import JavaClassWithFakeOverride
import kotlin.concurrent.atomics.*

class KotlinChildWithFakeOverride: JavaClassWithFakeOverride()

@OptIn(ExperimentalStdlibApi::class)
class KotlinChildWithExplicitOverride: JavaClassWithFakeOverride() {
    override fun foo(i: AtomicInt): String {
        return "KotlinChildWithExplicitOverride"
    }
    override val a: AtomicInt
        get() = AtomicInt(1)
}

@OptIn(ExperimentalStdlibApi::class)
fun box(): String {
    val child1 = KotlinChildWithFakeOverride()
    val child2 = KotlinChildWithExplicitOverride()
    return if (
        (child1.foo(AtomicInt(0)) == "KotlinClass") &&
        (child2.foo(AtomicInt(0)) == "KotlinChildWithExplicitOverride") &&
        (child1.a.load() == 0) &&
        (child2.a.load() == 1)
    ) "OK"
    else "not OK"
}