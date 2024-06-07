// JSPECIFY_STATE: warn
// FULL_JDK

// FILE: MyFunction.java
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MyFunction<F, T> extends java.util.function.Function<F, T> {}

// FILE: B.java
public interface B<V, T> extends MyFunction<V, T>, java.util.function.Function<V, T> {}

// FILE: A.java
public interface A<T> {
    <V> V foo(java.util.function.Function<? super T, V> f);
}

// FILE: main.kt
fun myRun(block: () -> Unit) = block()

fun foo(a: A<String>, b: B<String, Boolean>) = myRun {
    a.<!INFERENCE_UNSUCCESSFUL_FORK("B<kotlin/String, kotlin/Boolean> <: ft<java/util/function/Function<in kotlin/String!, ft<TypeVariable(V), TypeVariable(V)?>>, java/util/function/Function<in kotlin/String!, ft<TypeVariable(V), TypeVariable(V)?>>?>")!>foo<!>(b)
}
