/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("DuplicatedCode", "unused")

package org.jetbrains.kotlin.fir.declarations.builder

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirScriptCodeFragment
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.visitors.*
import org.jetbrains.kotlin.name.Name

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

@FirBuilderDsl
interface FirScriptCodeFragmentBuilder {
    abstract var source: KtSourceElement?
    abstract var resolvePhase: FirResolvePhase
    abstract val annotations: MutableList<FirAnnotation>
    abstract var moduleData: FirModuleData
    abstract var origin: FirDeclarationOrigin
    abstract var attributes: FirDeclarationAttributes
    abstract val statements: MutableList<FirStatement>
    abstract var resultPropertyName: Name?
    abstract val contextReceivers: MutableList<FirContextReceiver>

    fun build(): FirScriptCodeFragment
}
