/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.getPackageFragment

@InternalKonanApi fun IrElement.getCompilerMessageLocation(containingFile: IrFile): CompilerMessageLocation? =
        createCompilerMessageLocation(containingFile, this.startOffset, this.endOffset)

@InternalKonanApi fun IrBuilderWithScope.getCompilerMessageLocation(): CompilerMessageLocation? {
    val declaration = this.scope.scopeOwnerSymbol.owner as? IrDeclaration ?: return null
    val file = declaration.getPackageFragment() as? IrFile ?: return null
    return createCompilerMessageLocation(file, startOffset, endOffset)
}

private fun createCompilerMessageLocation(containingFile: IrFile, startOffset: Int, endOffset: Int): CompilerMessageLocation? {
    val sourceRangeInfo = containingFile.fileEntry.getSourceRangeInfo(startOffset, endOffset)
    return CompilerMessageLocation.create(
            path = sourceRangeInfo.filePath,
            line = sourceRangeInfo.startLineNumber + 1,
            column = sourceRangeInfo.startColumnNumber + 1,
            lineContent = null // TODO: retrieve the line content.
    )
}
