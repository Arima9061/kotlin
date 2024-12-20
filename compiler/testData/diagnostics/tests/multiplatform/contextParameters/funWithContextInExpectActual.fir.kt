// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +ContextParameters
// MODULE: m1-common
// FILE: common.kt

class A

context(a: A)
expect fun test1()

<!EXPECT_ACTUAL_MISMATCH{JVM}!>expect<!> fun test2()

context(a: A)
<!EXPECT_ACTUAL_MISMATCH{JVM}!>expect<!> fun test3()

context(a: A)
<!EXPECT_ACTUAL_MISMATCH{JVM}!>expect<!> fun test4()

context(a: A)
expect fun test5()

expect fun test6(a: A)

<!EXPECT_ACTUAL_MISMATCH{JVM}!>expect<!> fun A.test7()

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

context(a: A)
actual fun test1(){}

context(a: A)
actual fun test2(){}

actual fun test3(){}

actual fun A.<!ACTUAL_WITHOUT_EXPECT!>test4<!>(){}

actual fun <!ACTUAL_WITHOUT_EXPECT!>test5<!>(a: A){}

context(a: A)
actual fun <!ACTUAL_WITHOUT_EXPECT!>test6<!>(){}

context(a: A)
actual fun <!ACTUAL_WITHOUT_EXPECT!>test7<!>(){}