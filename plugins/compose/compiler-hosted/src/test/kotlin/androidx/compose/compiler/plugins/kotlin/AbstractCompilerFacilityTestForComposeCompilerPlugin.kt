/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package androidx.compose.compiler.plugins.kotlin

import androidx.compose.compiler.plugins.kotlin.services.ComposeExtensionRegistrarConfigurator
import androidx.compose.compiler.plugins.kotlin.services.ComposePluginAnnotationsProvider
import org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.compilerFacility.AbstractCompilerFacilityTest
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.runners.enableLazyResolvePhaseChecking

abstract class AbstractCompilerFacilityTestForComposeCompilerPlugin : AbstractCompilerFacilityTest() {
    override fun configureTest(builder: TestConfigurationBuilder) {
        super.configureTest(builder)
        builder.composeCompilerPluginConfiguration()
    }
}

fun TestConfigurationBuilder.composeCompilerPluginConfiguration() {
    defaultDirectives {
        +FirDiagnosticsDirectives.ENABLE_PLUGIN_PHASES
        +FirDiagnosticsDirectives.FIR_DUMP
    }

    useConfigurators(
        ::ComposeExtensionRegistrarConfigurator,
        ::ComposePluginAnnotationsProvider,
    )
}