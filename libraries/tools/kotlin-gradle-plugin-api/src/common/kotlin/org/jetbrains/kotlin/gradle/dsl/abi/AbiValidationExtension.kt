/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl.abi

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinAbiCheckTask
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinAbiDumpTask

/**
 * A plugin DSL extension for configuring ABI Validation.
 *
 * ABI Validation - is a part of Kotlin toolset designed to control declarations available to other modules.
 * You can use this tool to control the binary compatibility of your library or shared module.
 *
 * This extension always located inside the `kotlin` block in your build script:
 * ```kotlin
 * kotlin {
 *     abiValidation {
 *         // Your ABI validation configuration
 *     }
 * }
 * ```
 *
 *  Note that this DSL is experimental, and it is most likely that it will be changed in the next versions.
 *
 * @since 2.1.20
 */
@AbiValidationDsl
@ExperimentalAbiValidation
public interface AbiValidationExtension : AbiValidationVariantSpec {
    /**
     * All ABI Validations report variants available in this project.
     *
     * See [AbiValidationVariantSpec] for detailed information about report variants.
     *
     * By default, there is always one variant in each project, called main variant. It has name [AbiValidationVariantSpec.MAIN_VARIANT_NAME] and is configured in current block
     * ```kotlin
     * kotlin {
     *     abiValidation {
     *         // main variant configuration
     *     }
     * }
     * ```
     *
     * This is a live mutable collection, new custom variant can be created via special functions like [NamedDomainObjectContainer.create] or [NamedDomainObjectContainer.register].
     * This variant can be configured at the time of its creation
     * ```kotlin
     * kotlin {
     *     abiValidation {
     *         variants.register("my") {
     *             // 'my' variant configuration, not main
     *         }
     *     }
     * }
     * ```
     * or later
     * ```kotlin
     * kotlin {
     *     abiValidation {
     *         variants.register("my")
     *     }
     * }
     * //
     * kotlin {
     *     abiValidation {
     *         variants.getByName("my").filters {
     *             // configure filters for 'my' variant
     *         }
     *     }
     * }
     * ```
     */
    val variants: NamedDomainObjectContainer<AbiValidationVariantSpec>
}

/**
 * Specification for the ABI Validation report variant.
 *
 * ABI Validation report variant - is a bunch of configurations (like filters, klib validation, etc.), for which a set of separate Gradle tasks is being created.
 * Different variants are used to generate ABI dumps for a different set of classes and targets without changing the build script.
 *
 * Each report variant has a unique name.
 *
 * For each variant, a different set of Gradle tasks is created, with differing names.
 *
 * You can access tasks using the properties:
 *
 * For main variant
 * ```kotlin
 * kotlin {
 *     abiValidation {
 *         dumpTaskProvider
 *         checkTaskProvider
 *         updateTaskProvider
 *
 *         legacyDump.legacyDumpTaskProvider
 *         legacyDump.legacyCheckTaskProvider
 *         legacyDump.legacyUpdateTaskProvider
 *     }
 * }
 * ```
 * and for a custom variant
 *```kotlin
 * kotlin {
 *     abiValidation {
 *         variants.getByName("my").dumpTaskProvider
 *         variants.getByName("my").checkTaskProvider
 *         variants.getByName("my").updateTaskProvider
 *
 *         variants.getByName("my").legacyDump.legacyDumpTaskProvider
 *         variants.getByName("my").legacyDump.legacyCheckTaskProvider
 *         variants.getByName("my").legacyDump.legacyUpdateTaskProvider
 *     }
 * }
 * ```
 *
 * @since 2.1.20
 */
@AbiValidationDsl
@ExperimentalAbiValidation
public interface AbiValidationVariantSpec : Named {
    val filters: AbiFiltersSpec

    /**
     * Configures the [filters] with the provided configuration.
     */
    fun filters(action: Action<AbiFiltersSpec>) {
        action.execute(filters)
    }

    /**
     * Reference dump file which the dump generated for the current code will be compared with by [checkTaskProvider] task.
     */
    val referenceDump: RegularFileProperty

    /**
     * Provides configuration for dumps stored in the old format used in separate [Binary Compatibility Validator plugin](https://github.com/Kotlin/binary-compatibility-validator).
     *
     * Used for smooth migration from it to a new dump format.
     */
    val legacyDump: AbiValidationLegacyDumpExtension

    /**
     * Configures the [legacyDump] with the provided configuration.
     */
    fun legacyDump(action: Action<AbiValidationLegacyDumpExtension>) {
        action.execute(legacyDump)
    }

    /**
     * Provider for the task of generating an actual dump for the current code.
     *
     * This dump is saved in the [KotlinAbiDumpTask.dumpFile] file and used when comparing with the [referenceDump] by the [checkTaskProvider] task.
     */
    val dumpTaskProvider: TaskProvider<KotlinAbiDumpTask>

    /**
     * Provider for the task of comparing an actual dump for the current generated by [dumpTaskProvider] code with [referenceDump].
     *
     * It will drop if any difference is found in the files.
     */
    val checkTaskProvider: TaskProvider<KotlinAbiCheckTask>

    /**
     * Overwrite [referenceDump] with the actual dump from [KotlinAbiDumpTask.dumpFile] for current code.
     */
    val updateTaskProvider: TaskProvider<Task>

    companion object {
        /**
         * Report variant name for variant configured in
         * ```kotlin
         * kotlin {
         *     abiValidation {
         *         // main variant
         *     }
         * }
         * ```
         * block.
         *
         * This variant also called "main variant".
         *
         * See [AbiValidationVariantSpec] for detailed information about report variants.
         */
        const val MAIN_VARIANT_NAME = "main"
    }
}
