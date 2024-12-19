// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects

// MODULE: common

<!EXPECT_ACTUAL_INCOMPATIBILITY!>expect<!> class E1 {
    <!NO_ACTUAL_FOR_EXPECT, WRONG_MODIFIER_TARGET!>expect<!> class I
}

<!EXPECT_ACTUAL_INCOMPATIBILITY!>expect<!> class E2 {
    class <!NO_ACTUAL_FOR_EXPECT!>I<!>
}

expect class E3 {
    class I
}

// MODULE: platform()()(common)

// FILE: actual1.kt

actual class <!NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS("")!>E1<!> {
    actual typealias I = Int  // 'actual typealias' not allowed
}

// FILE: actual2.kt

class A {
    typealias I = Int
}

actual typealias <!NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS("")!>E2<!> = A  // actualizing nested 'expect class' with typealias not allowed

// FILE: actual3.kt

class B {
    class I
}

actual typealias E3 = B  // ok

