// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects
// WITH_STDLIB

// MODULE: common
// FILE: common.kt

import kotlin.collections.<!UNRESOLVED_REFERENCE!>listOf<!>

fun common() {
    <!UNRESOLVED_REFERENCE!>listOf<!>("foo", "bar").<!DEBUG_INFO_MISSING_UNRESOLVED!>map<!> { <!UNRESOLVED_REFERENCE!>it<!> }
}

// MODULE: jvm
// FILE: jvm.kt

import kotlin.collections.mapOf

fun jvm() {
    mapOf(1 to "1")
}
