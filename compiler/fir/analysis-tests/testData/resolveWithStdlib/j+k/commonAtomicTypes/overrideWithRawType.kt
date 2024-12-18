// RUN_PIPELINE_TILL: FRONTEND
// FULL_JDK
// WITH_STDLIB

// FILE: KotlinClass.kt
import kotlin.concurrent.atomics.AtomicReference

@OptIn(ExperimentalStdlibApi::class)
open class KotlinClass {
    open fun foo(a: AtomicReference<Int>) { }
}

// FILE: JavaClass.java
import java.util.concurrent.atomic.AtomicReference;

public class JavaClass extends KotlinClass {
    @Override
    public void foo(AtomicReference a) { }
}

// FILE: test.kt
import kotlin.concurrent.atomics.AtomicReference

@OptIn(ExperimentalStdlibApi::class)
fun usage6(a: JavaClass) {
    a.foo(java.util.concurrent.atomic.AtomicReference(""))
    a.foo(<!ARGUMENT_TYPE_MISMATCH!>AtomicReference(1)<!>)
}