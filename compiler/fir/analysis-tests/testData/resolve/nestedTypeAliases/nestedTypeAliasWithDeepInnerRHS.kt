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
        <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>NestedTAtoInnerInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
        NestedTAtoInnerNested() // Error (minor)
        NestedTAtoNested() // OK
        <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>NestedTAtoNestedInner<!>() // UNRESOLVED_REFERENCE_WRONG_RECEIVER
    }
}
