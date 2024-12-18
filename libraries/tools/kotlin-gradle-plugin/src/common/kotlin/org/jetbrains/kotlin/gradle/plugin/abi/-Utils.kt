/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("DEPRECATION")

package org.jetbrains.kotlin.gradle.plugin.abi

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.abi.tools.api.v2.KlibTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Converts [KotlinTarget] to a [KlibTarget].
 */
internal fun KotlinTarget.toKlibTarget(): KlibTarget {
    if (this is KotlinNativeTarget) {
        return KlibTarget.fromKonanTargetName(konanTarget.name).configureName(targetName)
    }
    val name = when (platformType) {
        KotlinPlatformType.js -> "js"
        KotlinPlatformType.wasm -> when ((this as KotlinJsIrTarget).wasmTargetType) {
            KotlinWasmTargetType.WASI -> "wasmWasi"
            KotlinWasmTargetType.JS -> "wasmJs"
            else -> throw IllegalStateException("Unreachable")
        }
        else -> throw IllegalArgumentException("Unsupported platform type: $platformType")
    }
    return KlibTarget(name, targetName)
}

/**
 * Check specified target is supported by host compiler.
 */
internal fun targetIsSupported(target: KotlinTarget): Boolean {
    return when (target) {
        is KotlinNativeTarget -> HostManager().isEnabled(target.konanTarget)
        else -> true
    }
}

/**
 * Check specified target has klib file as output artifact.
 */
internal val KotlinTarget.emitsKlib: Boolean
    get() {
        val platformType = this.platformType
        return platformType == KotlinPlatformType.native ||
                platformType == KotlinPlatformType.wasm ||
                platformType == KotlinPlatformType.js
    }

@Suppress("DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")
internal val BaseVariant.isTestVariant: Boolean
    get() = this is TestVariant || this is UnitTestVariant


internal fun Project.prepareAbiClasspath(): Configuration {
    val version = getKotlinPluginVersion()
    val tools = dependencies.create("org.jetbrains.kotlin:abi-tools:$version")
    return configurations.detachedConfiguration(tools)
}

/**
 * Execute given [action] against compilation with name [SourceSet.MAIN_SOURCE_SET_NAME].
 */
internal inline fun <T : KotlinCompilation<*>> DomainObjectCollection<T>.withMainCompilation(crossinline action: T.() -> Unit) {
    all { compilation ->
        if (compilation.name == SourceSet.MAIN_SOURCE_SET_NAME) compilation.action()
    }
}