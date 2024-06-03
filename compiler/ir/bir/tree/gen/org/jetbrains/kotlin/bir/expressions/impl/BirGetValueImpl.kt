/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/bir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "CanBePrimaryConstructorProperty")

package org.jetbrains.kotlin.bir.expressions.impl

import org.jetbrains.kotlin.bir.SourceSpan
import org.jetbrains.kotlin.bir.declarations.BirAttributeContainer
import org.jetbrains.kotlin.bir.expressions.BirGetValue
import org.jetbrains.kotlin.bir.symbols.BirValueSymbol
import org.jetbrains.kotlin.bir.types.BirType
import org.jetbrains.kotlin.bir.util.ForwardReferenceRecorder
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

class BirGetValueImpl(
    sourceSpan: SourceSpan,
    type: BirType,
    symbol: BirValueSymbol,
    origin: IrStatementOrigin?,
) : BirGetValue() {
    override var sourceSpan: SourceSpan = sourceSpan

    override var attributeOwnerId: BirAttributeContainer = this

    override var type: BirType = type

    private var _symbol: BirValueSymbol = symbol
    override var symbol: BirValueSymbol
        get() {
            return _symbol
        }
        set(value) {
            if (_symbol !== value) {
                _symbol = value
                forwardReferencePropertyChanged()
            }
        }

    override var origin: IrStatementOrigin? = origin


    override fun getForwardReferences(recorder: ForwardReferenceRecorder) {
        recorder.recordReference(symbol)
    }
}
