/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin.services

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.extensions.FirScriptResolutionConfigurationExtension
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.scripting.compiler.plugin.irLowerings.scriptCompilationConfigurationAttr
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.host.ScriptingHostConfiguration

class FirScriptResolutionConfigurationExtensionImpl(
    session: FirSession,
    @Suppress("UNUSED_PARAMETER") hostConfiguration: ScriptingHostConfiguration
) : FirScriptResolutionConfigurationExtension(session) {

    override fun getScriptDefaultImports(script: FirScript): List<FirImport> =
        script.scriptCompilationConfigurationAttr?.firImportsFromDefaultImports(
            script.source?.fakeElement(KtFakeSourceElementKind.ImplicitImport)
        ) ?: emptyList()

    companion object {
        fun getFactory(hostConfiguration: ScriptingHostConfiguration): Factory {
            return Factory { session -> FirScriptResolutionConfigurationExtensionImpl(session, hostConfiguration) }
        }
    }
}

private fun ScriptCompilationConfiguration.firImportsFromDefaultImports(sourceElement: KtSourceElement?): List<FirImport> =
    this[ScriptCompilationConfiguration.defaultImports]?.map { defaultImport ->
        val trimmed = defaultImport.trim()
        val endsWithStar = trimmed.endsWith("*")
        val stripped = if (endsWithStar) trimmed.substring(0, trimmed.length - 2) else trimmed
        val fqName = FqName.fromSegments(stripped.split("."))
        buildImport {
            source = sourceElement
            importedFqName = fqName
            isAllUnder = endsWithStar
        }
    }.orEmpty()
