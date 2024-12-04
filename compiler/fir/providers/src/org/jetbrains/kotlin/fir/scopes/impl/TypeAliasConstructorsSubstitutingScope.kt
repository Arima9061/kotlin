/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructedClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.scopes.DelicateScopeAPI
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*

private object TypeAliasConstructorKey : FirDeclarationDataKey()

var <T : FirFunction> T.originalConstructorIfTypeAlias: T? by FirDeclarationDataRegistry.data(TypeAliasConstructorKey)
val <T : FirFunction> FirFunctionSymbol<T>.originalConstructorIfTypeAlias: T?
    get() = fir.originalConstructorIfTypeAlias

val FirFunctionSymbol<*>.isTypeAliasedConstructor: Boolean
    get() = fir.originalConstructorIfTypeAlias != null

private object TypeAliasForConstructorKey : FirDeclarationDataKey()

var FirFunction.typeAliasForConstructor: FirTypeAliasSymbol? by FirDeclarationDataRegistry.data(TypeAliasForConstructorKey)
val FirFunctionSymbol<*>.typeAliasForConstructor: FirTypeAliasSymbol?
    get() = fir.typeAliasForConstructor

private object TypeAliasConstructorSubstitutorKey : FirDeclarationDataKey()

var FirConstructor.typeAliasConstructorSubstitutor: ConeSubstitutor? by FirDeclarationDataRegistry.data(TypeAliasConstructorSubstitutorKey)

private object TypeAliasOuterType : FirDeclarationDataKey()

var FirConstructor.outerTypeIfTypeAlias: ConeClassLikeType? by FirDeclarationDataRegistry.data(TypeAliasOuterType)

class TypeAliasConstructorsSubstitutingScope(
    private val typeAliasSymbol: FirTypeAliasSymbol,
    private val delegatingScope: FirScope,
    private val outerType: ConeClassLikeType?,
) : FirScope() {
    private val aliasedTypeExpansionGloballyEnabled: Boolean = typeAliasSymbol
        .moduleData
        .session
        .languageVersionSettings
        .getFlag(AnalysisFlags.expandTypeAliasesInTypeResolution)

    override fun processDeclaredConstructors(processor: (FirConstructorSymbol) -> Unit) {
        delegatingScope.processDeclaredConstructors wrapper@{ originalConstructorSymbol ->
            val originalConstructor = originalConstructorSymbol.fir
            val newConstructorSymbol = FirConstructorSymbol(originalConstructorSymbol.callableId)

            buildConstructor {
                symbol = newConstructorSymbol

                // Typealiased constructors point to the typealias source for the convenience of Analysis API
                source = typeAliasSymbol.source
                resolvePhase = originalConstructor.resolvePhase
                // We consider typealiased constructors to be coming from the module of the typealias
                moduleData = typeAliasSymbol.moduleData
                origin = FirDeclarationOrigin.Synthetic.TypeAliasConstructor
                attributes = originalConstructor.attributes.copy()

                typeAliasSymbol.fir.typeParameters.mapTo(typeParameters) {
                    buildConstructedClassTypeParameterRef { symbol = it.symbol }
                }

                status = originalConstructor.status

                returnTypeRef = originalConstructor.returnTypeRef.let {
                    if (aliasedTypeExpansionGloballyEnabled) {
                        it.withReplacedConeType(it.coneType.withAbbreviation(AbbreviatedTypeAttribute(typeAliasSymbol.defaultType())))
                    } else {
                        it
                    }
                }
                receiverParameter = originalConstructor.receiverParameter
                deprecationsProvider = originalConstructor.deprecationsProvider
                containerSource = originalConstructor.containerSource
                dispatchReceiverType = originalConstructor.dispatchReceiverType

                originalConstructor.contextParameters.mapTo(contextParameters) {
                    buildValueParameterCopy(it) {
                        symbol = FirValueParameterSymbol(it.name)
                        moduleData = typeAliasSymbol.moduleData
                        origin = FirDeclarationOrigin.Synthetic.TypeAliasConstructor
                        containingDeclarationSymbol = newConstructorSymbol
                    }
                }

                originalConstructor.valueParameters.mapTo(valueParameters) {
                    buildValueParameterCopy(it) {
                        symbol = FirValueParameterSymbol(it.name)
                        moduleData = typeAliasSymbol.moduleData
                        origin = FirDeclarationOrigin.Synthetic.TypeAliasConstructor
                        containingDeclarationSymbol = newConstructorSymbol
                    }
                }

                contractDescription = originalConstructor.contractDescription
                annotations.addAll(originalConstructor.annotations)
                delegatedConstructor = originalConstructor.delegatedConstructor
                body = originalConstructor.body
            }.apply {
                originalConstructorIfTypeAlias = originalConstructorSymbol.fir
                typeAliasForConstructor = typeAliasSymbol
                if (delegatingScope is FirClassSubstitutionScope) {
                    typeAliasConstructorSubstitutor = delegatingScope.substitutor
                }
                outerTypeIfTypeAlias = outerType
            }

            processor(newConstructorSymbol)
        }
    }

    @DelicateScopeAPI
    override fun withReplacedSessionOrNull(newSession: FirSession, newScopeSession: ScopeSession): TypeAliasConstructorsSubstitutingScope? {
        return delegatingScope.withReplacedSessionOrNull(newSession, newScopeSession)?.let {
            TypeAliasConstructorsSubstitutingScope(typeAliasSymbol, it, outerType)
        }
    }
}
