/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.symbols

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

abstract class ConeClassifierLookupTag : TypeConstructorMarker {
    abstract val name: Name

    override fun toString(): String {
        return name.asString()
    }

    abstract fun toSymbol(useSiteSession: FirSession): FirClassifierSymbol<*>?
}

abstract class ConeClassLikeLookupTag : ConeClassifierLookupTag() {
    abstract val classId: ClassId

    override val name: Name
        get() = classId.shortClassName

    abstract override fun toSymbol(useSiteSession: FirSession): FirClassLikeSymbol<*>?
}

