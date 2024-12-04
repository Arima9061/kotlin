/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.artifacts.uklibsPublication

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.artifacts.metadataFragmentAttributes
import org.jetbrains.kotlin.gradle.artifacts.uklibsModel.Fragment
import org.jetbrains.kotlin.gradle.artifacts.metadataFragmentIdentifier
import org.jetbrains.kotlin.gradle.artifacts.metadataPublishedArtifacts
import org.jetbrains.kotlin.gradle.artifacts.publishedMetadataCompilations
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginLifecycle
import org.jetbrains.kotlin.gradle.plugin.await
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import java.io.File


internal data class KGPFragment(
    val fragment: Fragment,
    val providingTask: TaskProvider<*>,
    val outputFile: Provider<File>,
)

internal suspend fun kgpFragments(
    metadataTarget: KotlinMetadataTarget,
    allTargets: List<KotlinTarget>,
): List<KGPFragment> {
    // Guarantee that we can safely access any compilations
    KotlinPluginLifecycle.Stage.AfterFinaliseCompilations.await()

    val fragments = mutableListOf<KGPFragment>()

    metadataTarget.publishedMetadataCompilations().forEach { metadataCompilation ->
        val artifact = metadataCompilation.project.provider {
            metadataCompilation.metadataPublishedArtifacts.singleFile
        }
        fragments.add(
            KGPFragment(
                fragment = Fragment(
                    identifier = metadataCompilation.metadataFragmentIdentifier,
                    attributes = metadataCompilation.metadataFragmentAttributes.map { it.unwrap() }.toSet(),
                    file = {
                        artifact.get()
                    }
                ),
                providingTask = metadataCompilation.compileTaskProvider,
                outputFile = artifact,
            )
        )
    }

    allTargets.filterNot {
        it == metadataTarget
    }.forEach { target ->
        /**
         * FIXME: Tie this implementation to the publication implementations that are hardcoded in KotlinTarget to make the dependency
         * between the artifact that is published in Uklib and in the old publication model visible
         */
        when (target) {
            is KotlinJsIrTarget -> {
                val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                val file = mainCompilation.compileTaskProvider.flatMap { it.klibOutput }
                fragments.add(
                    KGPFragment(
                        fragment = Fragment(
                            identifier = mainCompilation.fragmentIdentifier,
                            attributes = setOf(mainCompilation.uklibFragmentPlatformAttribute.unwrap()),
                            file = {
                                file.get()
                            }
                        ),
                        providingTask = mainCompilation.compileTaskProvider,
                        outputFile = file,
                    )
                )
            }
            is KotlinNativeTarget -> {
                val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                val file = mainCompilation.compileTaskProvider.flatMap { it.klibOutput }
                fragments.add(
                    KGPFragment(
                        fragment = Fragment(
                            identifier = mainCompilation.fragmentIdentifier,
                            attributes = setOf(mainCompilation.uklibFragmentPlatformAttribute.unwrap()),
                            file = {
                                file.get()
                            }
                        ),
                        providingTask = mainCompilation.compileTaskProvider,
                        outputFile = file,
                    )
                )
            }
            is KotlinJvmTarget -> {
                val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                @Suppress("UNCHECKED_CAST")
                val jarTask = (target.project.tasks.named(target.artifactsTaskName) as TaskProvider<Jar>)
                val jarArtifact = jarTask.flatMap {
                    it.archiveFile.map { it.asFile }
                }
                fragments.add(
                    KGPFragment(
                        fragment = Fragment(
                            identifier = mainCompilation.fragmentIdentifier,
                            attributes = setOf(mainCompilation.uklibFragmentPlatformAttribute.unwrap()),
                            file = {
                                jarArtifact.get()
                            }
                        ),
                        providingTask = jarTask,
                        outputFile = jarArtifact,
                    )
                )
            }
            else -> {
                when (target.uklibFragmentPlatformAttribute) {
                    is UklibFragmentPlatformAttribute.OnlyConsumeInMetadataCompilationsAndIgnoreAtPublication -> { /* Do nothing for AGP */ }
                    is UklibFragmentPlatformAttribute.PublishAndConsumeInAllCompilations -> { /* FIXME: rewrite the logic above */ }
                    is UklibFragmentPlatformAttribute.FailOnPublicationAndIgnoreForConsumption -> target.uklibFragmentPlatformAttribute.unwrap()
                }
            }
        }
    }

    return fragments
}

internal enum class UklibJsTargetIdentifier {
    js_ir,
    wasm_js,
    wasm_wasi;

    fun deserialize(value: String): UklibJsTargetIdentifier {
        return enumValueOf<UklibJsTargetIdentifier>(value)
    }
}

internal sealed class UklibFragmentPlatformAttribute {
    // Jvm, native, js
    data class PublishAndConsumeInAllCompilations(val attribute: String) : UklibFragmentPlatformAttribute()
    // Android
    data class OnlyConsumeInMetadataCompilationsAndIgnoreAtPublication(val attribute: String) : UklibFragmentPlatformAttribute()
    // External target
    data class FailOnPublicationAndIgnoreForConsumption(val error: String) : UklibFragmentPlatformAttribute()

    // FIXME: Separate unwrap to consume for publication vs compilation
    fun unwrap(): String = when (this) {
        is PublishAndConsumeInAllCompilations -> attribute
        is OnlyConsumeInMetadataCompilationsAndIgnoreAtPublication -> attribute
        is FailOnPublicationAndIgnoreForConsumption -> error(error)
    }
}

internal val KotlinCompilation<*>.uklibFragmentPlatformAttribute: UklibFragmentPlatformAttribute get() = this.target.uklibFragmentPlatformAttribute
internal val KotlinTarget.uklibFragmentPlatformAttribute: UklibFragmentPlatformAttribute
    get() {
        // FIXME: Actually maybe request jvm transform in Android?
        if (this is KotlinAndroidTarget) {
            return UklibFragmentPlatformAttribute.OnlyConsumeInMetadataCompilationsAndIgnoreAtPublication(targetName)
        }

        when (this) {
            is KotlinNativeTarget -> konanTarget.name
            is KotlinJsIrTarget -> when (platformType) {
                KotlinPlatformType.js -> UklibJsTargetIdentifier.js_ir.name
                KotlinPlatformType.wasm -> when (wasmTargetType ?: error("${KotlinJsIrTarget::class} missing wasm type in wasm platform ")) {
                    KotlinWasmTargetType.JS -> UklibJsTargetIdentifier.wasm_js.name
                    KotlinWasmTargetType.WASI -> UklibJsTargetIdentifier.wasm_wasi.name
                }
                else -> error("${KotlinJsIrTarget::class} unexpected platform type ${platformType}")
            }
            // FIXME: Is this correct?
            is KotlinJvmTarget -> targetName
            else -> null
        }?.let {
            return UklibFragmentPlatformAttribute.PublishAndConsumeInAllCompilations(it)
        }

        val error = when (this) {
            is KotlinMetadataTarget -> "Metadata target does't have a platform attribute"
            // FIXME: Test this !!!
            is DecoratedExternalKotlinTarget -> "FIXME: This is explicitly unsupported"
            else -> "???"
        }
        return UklibFragmentPlatformAttribute.FailOnPublicationAndIgnoreForConsumption(error)
    }

internal val KotlinCompilation<*>.fragmentIdentifier: String get() = defaultSourceSet.name