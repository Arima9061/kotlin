/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/bir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.bir.expressions

import org.jetbrains.kotlin.bir.BirElementBackReferencesKey
import org.jetbrains.kotlin.bir.BirElementClass
import org.jetbrains.kotlin.bir.BirElementVisitor
import org.jetbrains.kotlin.bir.accept
import org.jetbrains.kotlin.bir.symbols.BirConstructorSymbol
import org.jetbrains.kotlin.bir.util.BirImplementationDetail

abstract class BirDelegatingConstructorCall() : BirFunctionAccessExpression() {
    abstract override var symbol: BirConstructorSymbol

    override fun <D> acceptChildren(visitor: BirElementVisitor<D>, data: D) {
        dispatchReceiver?.accept(data, visitor)
        extensionReceiver?.accept(data, visitor)
        valueArguments.acceptChildren(visitor, data)
    }

    @BirImplementationDetail
    override fun getElementClassInternal(): BirElementClass<*> = BirDelegatingConstructorCall

    companion object : BirElementClass<BirDelegatingConstructorCall>(BirDelegatingConstructorCall::class.java, 31, true) {
        val symbol = BirElementBackReferencesKey<BirDelegatingConstructorCall, _>{ (it as? BirDelegatingConstructorCall)?.symbol?.owner }
    }
}
