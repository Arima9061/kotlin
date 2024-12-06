// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-73752

typealias TAtoInner = Outer.Inner
typealias TAtoInnerInner = Outer.Inner.InnerInner
typealias TAtoInnerNested = Outer.Inner.InnerNested  // Error (minor)
typealias TAtoNested = Outer.Nested
typealias TAtoNestedInner = Outer.Nested.NestedInner

class Outer {
    inner class Inner {
        inner class InnerInner

        <!NESTED_CLASS_NOT_ALLOWED!>class InnerNested<!> // NESTED_CLASS_NOT_ALLOWED
    }

    class Nested {
        inner class NestedInner
    }

    typealias NestedTAtoInner = Inner
    typealias NestedTAtoInnerInner = Inner.InnerInner
    typealias NestedTAtoInnerNested = Inner.InnerNested // Error (minor)
    typealias NestedTAtoNested = Nested
    typealias NestedTAtoNestedInner = Nested.NestedInner

    fun test() {
        TAtoInner() // OK
        <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
        TAtoInnerNested() // Error (minor)
        TAtoNested() // OK
        <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER

        NestedTAtoInner() // OK
        NestedTAtoInnerInner() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
        NestedTAtoInnerNested() // Error (minor)
        NestedTAtoNested() // OK
        NestedTAtoNestedInner() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    }
}

fun testOuter() {
    val outer = Outer()

    outer.TAtoInner() // OK
    outer.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    outer.<!UNRESOLVED_REFERENCE!>TAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    outer.<!UNRESOLVED_REFERENCE!>TAtoNested<!>() // UNRESOLVED_REFERENCE
    outer.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER

    outer.NestedTAtoInner() // OK
    outer.NestedTAtoInnerInner() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    outer.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    outer.<!UNRESOLVED_REFERENCE!>NestedTAtoNested<!>() // UNRESOLVED_REFERENCE
    outer.NestedTAtoNestedInner() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
}

fun testInner() {
    val inner = Outer().Inner()

    inner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    inner.TAtoInnerInner() // OK
    inner.<!UNRESOLVED_REFERENCE!>TAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    inner.<!UNRESOLVED_REFERENCE!>TAtoNested<!>() // UNRESOLVED_REFERENCE
    inner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER

    inner.<!UNRESOLVED_REFERENCE!>NestedTAtoInner<!>() // URESOLVED_REFERENCE
    inner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerInner<!>() // URESOLVED_REFERENCE (?)
    inner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerNested<!>() // URESOLVED_REFERENCE
    inner.<!UNRESOLVED_REFERENCE!>NestedTAtoNested<!>() // URESOLVED_REFERENCE
    inner.<!UNRESOLVED_REFERENCE!>NestedTAtoNestedInner<!>() // URESOLVED_REFERENCE
}

fun testInnerInner() {
    val innerInner = Outer().Inner().InnerInner()

    innerInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    innerInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    innerInner.<!UNRESOLVED_REFERENCE!>TAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE!>TAtoNested<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER

    innerInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInner<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerInner<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE!>NestedTAtoNested<!>() // UNRESOLVED_REFERENCE
    innerInner.<!UNRESOLVED_REFERENCE!>NestedTAtoNestedInner<!>() // UNRESOLVED_REFERENCE
}

fun testNested() {
    val nested = Outer.Nested()

    nested.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    nested.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    nested.<!UNRESOLVED_REFERENCE!>TAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    nested.<!UNRESOLVED_REFERENCE!>TAtoNested<!>() // UNRESOLVED_REFERENCE
    nested.TAtoNestedInner() // OK

    nested.<!UNRESOLVED_REFERENCE!>NestedTAtoInner<!>() // UNRESOLVED_REFERENCE
    nested.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerInner<!>() // UNRESOLVED_REFERENCE
    nested.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    nested.<!UNRESOLVED_REFERENCE!>NestedTAtoNested<!>() // UNRESOLVED_REFERENCE
    nested.<!UNRESOLVED_REFERENCE!>NestedTAtoNestedInner<!>() // UNRESOLVED_REFERENCE (?)
}

fun testNestedInner() {
    val nestedInner = Outer.Nested().NestedInner()

    nestedInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    nestedInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    nestedInner.<!UNRESOLVED_REFERENCE!>TAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE!>TAtoNested<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER

    nestedInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInner<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerInner<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE!>NestedTAtoInnerNested<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE!>NestedTAtoNested<!>() // UNRESOLVED_REFERENCE
    nestedInner.<!UNRESOLVED_REFERENCE!>NestedTAtoNestedInner<!>() // UNRESOLVED_REFERENCE
}
