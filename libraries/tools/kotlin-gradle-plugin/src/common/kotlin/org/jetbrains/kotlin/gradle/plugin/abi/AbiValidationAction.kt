/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.abi

import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.dsl.kotlinJvmExtensionOrNull
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginLifecycle
import org.jetbrains.kotlin.gradle.plugin.KotlinProjectSetupCoroutine
import org.jetbrains.kotlin.gradle.plugin.await

/**
 * Project action to finish configuring of ABI Validation part of Kotlin Gradle Plugin.
 */
internal val AbiValidationAction = KotlinProjectSetupCoroutine {
    // wait until all compilations are configured
    KotlinPluginLifecycle.Stage.AfterFinaliseCompilations.await()

    val abiClasspath = prepareAbiClasspath()
    when {
        kotlinJvmExtensionOrNull != null -> {
            val extension = kotlinJvmExtension
            val abiValidation = extension.abiValidation
            val target = extension.target

            abiValidation.variants.configureEach { variant ->
                variant.finalizeJvmOrAndroidVariant(this, abiClasspath, target)
            }
        }

        kotlinExtension is KotlinAndroidProjectExtension -> {
            val extension = kotlinExtension as KotlinAndroidProjectExtension
            val abiValidation = extension.abiValidation
            val target = extension.target

            abiValidation.variants.configureEach { variant ->
                variant.finalizeJvmOrAndroidVariant(this, abiClasspath, target)
            }
        }
        multiplatformExtensionOrNull != null -> {
            val extension = multiplatformExtension
            val abiValidation = extension.abiValidation
            val targets = extension.awaitTargets()

            abiValidation.variants.configureEach { variant ->
                variant.finalizeMultiplatformVariant(this, abiClasspath, targets)
            }
        }
    }
}
