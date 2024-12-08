// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-73752

class Outer<T> {
    inner class Inner

    inner class InnerWithParameter<K>

    typealias NestedTAtoExplicitInner = Outer<<!UNRESOLVED_REFERENCE!>T<!>>.Inner // UNRESOLVED_REFERENCE (not inner typealias)
    typealias NestedTAToInner = Inner // Error, not inner type alias can't capture type parameters (they are implicit)
    typealias NestedTAToIntInner = Outer<Int>.Inner // OK
    typealias NestedTAWithTypeParameterToInner<K> = Outer<K>.Inner // OK
    typealias NestedTAtoInnerWithTypeParameters = InnerWithParameter<String> // Error, not inner type alias can't capture type parameters (they are implicit)
    typealias NestedTAtoIntInnerWithTypeParameters = Outer<Int>.InnerWithParameter<String> // OK

    fun test() {
        <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>TAToIntInner<!>() // Error (different dispath receivers `Outer<T>` and `Outer<Int>`)

        NestedTAtoExplicitInner() // Error (not inner typealias)
        NestedTAToInner() // Error (not inner typealias)
        NestedTAToIntInner() // Error (different dispath receivers `Outer<T>` and `Outer<Int>`)
        NestedTAWithTypeParameterToInner<String>() // Error (different dispath receivers `Outer<T>` and `Outer<String>`)
        NestedTAtoInnerWithTypeParameters() // Error (not inner typealias)
        NestedTAtoIntInnerWithTypeParameters() // Error (different dispath receivers `Outer<T>` and `Outer<Int>`)
    }
}

typealias TAToIntInner = Outer<Int>.Inner
