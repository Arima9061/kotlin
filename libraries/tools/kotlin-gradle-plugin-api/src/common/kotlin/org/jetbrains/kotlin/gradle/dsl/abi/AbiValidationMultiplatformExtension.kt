/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl.abi

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

/**
 * A plugin DSL extension for configuring ABI Validation.
 * common options for the entire project.
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
public interface AbiValidationMultiplatformExtension : AbiValidationMultiplatformVariantSpec {
    /**
     * All ABI Validations report variants available in this project.
     *
     * See [AbiValidationMultiplatformVariantSpec] for detailed information about report variants.
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
     * This is live mutable collection, new custom variant can be created via special functions like [NamedDomainObjectContainer.create] or [NamedDomainObjectContainer.register].
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
    val variants: NamedDomainObjectContainer<AbiValidationMultiplatformVariantSpec>
}

/**
 * Specification for ABI Validation report variant.
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
 * and for custom variant
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
public interface AbiValidationMultiplatformVariantSpec : AbiValidationVariantSpec {
    /**
     * Configure storing declarations from non-JVM and non-Android targets which are compiled in klibs.
     */
    val klib: AbiValidationKlibKindExtension

    /**
     * Configures the [klib] with the provided configuration.
     */
    fun klib(action: Action<AbiValidationKlibKindExtension>) {
        action.execute(klib)
    }
}

/**
 * Configuration of dumping declarations from non-JVM and non-Android targets which are compiled in klibs.
 *
 * @since 2.1.20
 */
@AbiValidationDsl
@ExperimentalAbiValidation
public interface AbiValidationKlibKindExtension {
    /**
     * Whether save declarations from klib into dump and check them.
     */
    val enabled: Property<Boolean>

    /**
     * Whether place in generated dump declarations for targets which are not supported by host compiler.
     * These declaration are taken from reference dump if it exists.
     *
     * If possible, unsupported targets will be supplemented with common declarations that are present in supported ones.
     *
     * However, this does not provide a complete guarantee, so you should use this opportunity with caution.
     *
     * If the flag is set to `false`, the dump generation will fail with an error if the compiler does not support some of the Kotlin targets used in current project.
     *
     * Default value: `true`
     */
    val keepUnsupportedTargets: Property<Boolean>
}
