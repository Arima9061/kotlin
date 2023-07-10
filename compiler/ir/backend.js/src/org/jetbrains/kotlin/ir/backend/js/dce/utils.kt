/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.dce

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.lower.PrimaryConstructorLowering
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fileEntry
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.SpecialNames
import java.io.File

private fun IrDeclaration.hasAnonymousParent(): Boolean =
    parent.let {
        require(parent != this) { println(this) }
        when {
            it is IrFile -> false
            it !is IrDeclaration -> false
            it is IrDeclarationWithName && (it.name == SpecialNames.NO_NAME_PROVIDED || it.name == SpecialNames.ANONYMOUS) -> true
            else -> it.hasAnonymousParent()
        }
    }

internal fun IrDeclaration.fqNameForDceDump(): String {
    // TODO: sanitize names
    val fqn = (this as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: "<unknown>"
    val signature = when (this is IrFunction) {
        true -> this.valueParameters.joinToString(prefix = "(", postfix = ")") { it.type.dumpKotlinLike() } +
                (this.extensionReceiverParameter?.type?.dumpKotlinLike()?.let { " (For: $it)" } ?: "")
        else -> ""
    }
    val synthetic = when (this.origin == PrimaryConstructorLowering.SYNTHETIC_PRIMARY_CONSTRUCTOR) {
        true -> "[synthetic]"
        else -> ""
    }

    val location = when (this.hasAnonymousParent()) {
        true -> " (From ${this.file.path}:${fileEntry.getLineNumber(this.startOffset)}:${fileEntry.getColumnNumber(this.startOffset)})"
        else -> ""
    }

    return (fqn + signature + synthetic + location)
}

private data class IrDeclarationDumpInfo(val fqName: String, val type: String, val size: Int)

fun dumpDeclarationIrSizesIfNeed(path: String?, allModules: List<IrModuleFragment>, dceDumpNameCache: DceDumpNameCache) {
    if (path == null) return

    val declarations = linkedSetOf<IrDeclarationDumpInfo>()

    allModules.forEach {
        it.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase) {
                val type = when (declaration) {
                    is IrFunction -> "function"
                    is IrProperty -> "property"
                    is IrField -> "field"
                    is IrAnonymousInitializer -> "anonymous initializer"
                    else -> null
                }
                type?.let {
                    declarations.add(
                        IrDeclarationDumpInfo(
                            fqName = dceDumpNameCache.getOrPut(declaration).removeQuotes(),
                            type = it,
                            size = declaration.dumpKotlinLike().length
                        )
                    )
                }

                super.visitDeclaration(declaration)
            }
        })
    }

    val out = File(path)
    val (prefix, postfix, separator, indent) = when (out.extension) {
        "json" -> listOf("{\n", "\n}", ",\n", "    ")
        "js" -> listOf("export const kotlinDeclarationsSize = {\n", "\n};\n", ",\n", "    ")
        else -> listOf("", "", "\n", "")
    }

    val value = declarations.joinToString(separator, prefix, postfix) { declaration ->
        """$indent"${declaration.fqName}": {
                |$indent$indent"size": ${declaration.size},
                |$indent$indent"type": "${declaration.type}"
                |$indent}
            """.trimMargin()
    }

    out.writeText(value)
}

internal fun String.removeQuotes() = replace('"'.toString(), "")
    .replace("'", "")
    .replace("\\", "\\\\")
