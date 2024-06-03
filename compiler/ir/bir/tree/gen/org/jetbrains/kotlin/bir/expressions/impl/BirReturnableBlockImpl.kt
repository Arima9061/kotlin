/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/bir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "CanBePrimaryConstructorProperty")

package org.jetbrains.kotlin.bir.expressions.impl

import org.jetbrains.kotlin.bir.*
import org.jetbrains.kotlin.bir.declarations.BirAttributeContainer
import org.jetbrains.kotlin.bir.expressions.BirReturnableBlock
import org.jetbrains.kotlin.bir.symbols.BirReturnableBlockSymbol
import org.jetbrains.kotlin.bir.types.BirType
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.util.IdSignature

class BirReturnableBlockImpl(
    sourceSpan: SourceSpan,
    type: BirType,
    origin: IrStatementOrigin?,
    signature: IdSignature?,
) : BirReturnableBlock(), BirReturnableBlockSymbol {
    constructor(
        sourceSpan: SourceSpan,
        type: BirType,
        origin: IrStatementOrigin?,
    ) : this(
        sourceSpan = sourceSpan,
        type = type,
        origin = origin,
        signature = null,
    )

    override val owner: BirReturnableBlockImpl
        get() = this

    override val isBound: Boolean
        get() = true

    override var sourceSpan: SourceSpan = sourceSpan

    override var attributeOwnerId: BirAttributeContainer = this

    override var type: BirType = type

    override var origin: IrStatementOrigin? = origin

    override val signature: IdSignature? = signature

    override val symbol: BirReturnableBlockSymbol
        get() = this

    override val statements: BirImplChildElementList<BirStatement> = BirImplChildElementList(this, 1, false)


    init {
    }

    override fun acceptChildrenLite(visitor: BirElementVisitorLite) {
        statements.acceptChildrenLite(visitor)
    }

    override fun replaceChildProperty(old: BirElement, new: BirElement?) {
        return when {
            else -> throwChildForReplacementNotFound(old)
        }
    }

    override fun getChildrenListById(id: Int): BirChildElementList<*> {
        return when (id) {
            1 -> this.statements
            else -> throwChildrenListWithIdNotFound(id)
        }
    }
}
