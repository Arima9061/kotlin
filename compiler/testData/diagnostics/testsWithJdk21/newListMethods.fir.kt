// ISSUE: KT-58371
// WITH_STDLIB

class A<T> : ArrayList<T>() {
    override fun addFirst(t: T) {
        super.addFirst(t)
    }

    override fun addLast(t: T) {
        super.addLast(t)
    }

    override fun getFirst(): T = super.getFirst()
    override fun getLast(): T = super.getLast()

    override fun removeFirst(): T = super.removeFirst()
    override fun removeLast(): T = super.removeLast()

    <!NOTHING_TO_OVERRIDE!>override<!> fun reversed(): List<T> = super.<!UNRESOLVED_REFERENCE!>reversed<!>()
}

fun foo(x: MutableList<String>, y: ArrayList<String>, z: A<String>) {
    x.addFirst("")
    x.addLast("")
    x.<!UNRESOLVED_REFERENCE!>getFirst<!>()
    x.<!FUNCTION_CALL_EXPECTED!>first<!> // synthetic property for getFirst()
    x.first() // stdlib extension on List
    x.<!UNRESOLVED_REFERENCE!>getLast<!>()
    x.<!FUNCTION_CALL_EXPECTED!>last<!>
    x.last()
    x.<!DEBUG_INFO_CALL("fqName: kotlin.collections.MutableList.removeFirst; typeCall: function")!>removeFirst()<!>
    x.<!DEBUG_INFO_CALL("fqName: kotlin.collections.MutableList.removeLast; typeCall: function")!>removeLast()<!>
    x.<!DEBUG_INFO_CALL("fqName: kotlin.collections.reversed; typeCall: extension function")!>reversed()<!>

    y.addFirst("")
    y.addLast("")
    y.getFirst()
    y.first
    y.first()
    y.getLast()
    y.last
    y.last()
    y.<!DEBUG_INFO_CALL("fqName: java.util.ArrayList.removeFirst; typeCall: function")!>removeFirst()<!>
    y.<!DEBUG_INFO_CALL("fqName: java.util.ArrayList.removeLast; typeCall: function")!>removeLast()<!>
    y.<!DEBUG_INFO_CALL("fqName: kotlin.collections.reversed; typeCall: extension function")!>reversed()<!>

    z.addFirst("")
    z.addLast("")
    z.getFirst()
    z.first
    z.first()
    z.getLast()
    z.last
    z.last()
    z.<!DEBUG_INFO_CALL("fqName: A.removeFirst; typeCall: function")!>removeFirst()<!>
    z.<!DEBUG_INFO_CALL("fqName: A.removeLast; typeCall: function")!>removeLast()<!>
    z.<!DEBUG_INFO_CALL("fqName: A.reversed; typeCall: function")!>reversed()<!>
}
