/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir.analysisapi

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.pointers.symbolPointer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.sir.SirOrigin
import org.jetbrains.kotlin.sir.SirDeclaration
import org.jetbrains.kotlin.sir.SirForeignFunction
import org.jetbrains.kotlin.sir.SirVisibility
import org.jetbrains.kotlin.sir.analysisapi.origin.AASirFunctionOrigin
import org.jetbrains.kotlin.sir.builder.SirForeignFunctionBuilder
import org.jetbrains.kotlin.sir.builder.buildForeignFunction

/**
 * A root interface for classes that produce Swift IR elements.
 */
interface SirFactory {
    fun build(fromFile: KtFile): List<SirDeclaration>
}

class SirGenerator : SirFactory {
    override fun build(fromFile: KtFile): List<SirDeclaration> = analyze(fromFile) {
        val res = mutableListOf<SirForeignFunction>()
        fromFile.accept(Visitor(res))
        return res.toList()
    }

    context(KtAnalysisSession)
    private class Visitor(val res: MutableList<SirForeignFunction>) : KtTreeVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (!function.isPublic) return
            val foreignFunction = buildForeignFunction {
                origin = AASirFunctionOrigin(
                    element = function
                )
                visibility = SirVisibility.PUBLIC
            }
            res += foreignFunction
        }
    }
}