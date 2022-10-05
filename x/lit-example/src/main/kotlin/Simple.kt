import kotlinx.browser.window
import lit.*

/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

fun main() {
    console.log("Hello, ${greet()}")
}

fun greet() = "world"

fun html(s: String): Any {
    val t = arrayOf(s)
    t.asDynamic().raw = t
    return html(t)
}

fun html(s: String, raw: String): Any {
    val t = arrayOf(s)
    t.asDynamic().raw = raw
    return html(t)
}
@CustomElement("simple-greeting")
class SimpleGreeting : LitElement() {
    @Property()
    var name = "Somebody"

    override fun render(): Any {
        println("1")
//        return html("It works!")
        window.setTimeout({
            name = "Lit"
        }, 3000)
        return html("<p>Hello, $name! $name</p>", "<p>Hello, \$name! \$name</p>")
//        html(["<p>Hello, ","!</p>"], name, name)
    }

    companion object {
        @JsStatic
        val styles = css("p { color: blue }")

        // SimpleGreeting.styles = css("p { color: blue }")
    }
}
