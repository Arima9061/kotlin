// RUN_PIPELINE_TILL: BACKEND
// MODULE: commonjs
// FILE: commonjs.kt

expect interface ExternalInterface

external class <!EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE!>ExternalClass<!>: ExternalInterface

// MODULE: js()()(commonjs)
// FILE: js.kt

actual external interface ExternalInterface
