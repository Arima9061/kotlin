// ISSUE: KT-67764

interface Generic<out T>

typealias TA<K> = (String) -> Generic<K>
typealias RA<L> = TA<L>

fun rest(it: Any) = it <!UNCHECKED_CAST!>as RA<<!REDUNDANT_PROJECTION!>in<!> Any><!>
