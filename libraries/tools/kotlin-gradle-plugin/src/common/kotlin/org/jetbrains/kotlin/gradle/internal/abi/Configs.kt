/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.abi

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationVariantSpec
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationVariantSpec.Companion.MAIN_VARIANT_NAME
import org.jetbrains.kotlin.gradle.plugin.abi.AbiValidationPaths
import org.jetbrains.kotlin.gradle.plugin.abi.AbiValidationPaths.DEFAULT_REFERENCE_DUMP_DIR
import org.jetbrains.kotlin.gradle.plugin.abi.AbiValidationPaths.DEFAULT_REFERENCE_DUMP_FILE
import org.jetbrains.kotlin.gradle.plugin.abi.AbiValidationPaths.LEGACY_ACTUAL_DUMP_DIR
import org.jetbrains.kotlin.gradle.plugin.abi.AbiValidationPaths.LEGACY_KLIB_DUMP_EXTENSION
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiCheckTaskImpl
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiDumpTaskImpl
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiUpdateTask
import org.jetbrains.kotlin.gradle.utils.newInstance

/**
 * Create and init extension for Kotlin JVM or Kotlin Android Gradle plugins.
 */
internal fun Project.abiValidationJvmOrAndroidDefault(): AbiValidationExtensionImpl {
    return objects.newInstance<AbiValidationExtensionImpl>(this)
        .configure(this)
}

/**
 * Create and init extension for Kotlin Multiplatform Gradle Plugin.
 */
internal fun Project.abiValidationMultiplatformDefault(): AbiValidationMultiplatformExtensionImpl {
    return objects.newInstance<AbiValidationMultiplatformExtensionImpl>(this)
        .configure(this)
}


/**
 * Configure properties with default values in ABI validation extension for Kotlin JVM or Kotlin Android Gradle plugins.
 */
private fun AbiValidationExtensionImpl.configure(project: Project): AbiValidationExtensionImpl {
    variants.configureCommon(project, this)
    return this
}

/**
 * Configure properties with default values in ABI validation extension for Kotlin Multiplatform Gradle plugins.
 */
private fun AbiValidationMultiplatformExtensionImpl.configure(project: Project): AbiValidationMultiplatformExtensionImpl {
    // add action before any possible addition of a variant
    variants.whenObjectAdded { variant ->
        variant.klib.enabled.convention(true)
        variant.klib.keepUnsupportedTargets.convention(true)
    }

    variants.configureCommon(project, this)
    return this
}

/**
 * Configure common part in ABI validation extension for all Kotlin Gradle Plugin types.
 */
private fun <T : AbiValidationVariantSpec> NamedDomainObjectContainer<T>.configureCommon(project: Project, mainVariant: T) {
    whenObjectAdded { variant ->
        project.configureVariant(variant)
        project.configureLegacyTasks(variant)
    }

    // add main root report variant
    add(mainVariant)
}

/**
 * Initialize specified report [variant] with default values, specific for all Kotlin Gradle Plugin types.
 */
private fun Project.configureVariant(variant: AbiValidationVariantSpec) {
    if (variant.name == MAIN_VARIANT_NAME) {
        // configure main report variant
        variant.referenceDump.convention(project.layout.projectDirectory.dir(DEFAULT_REFERENCE_DUMP_DIR).file(DEFAULT_REFERENCE_DUMP_FILE))
        variant.legacyDump.referenceDumpDir.convention(project.layout.projectDirectory.dir(AbiValidationPaths.LEGACY_DEFAULT_REFERENCE_DUMP_DIR))
    } else {
        // configure custom report variant
        variant.referenceDump.convention(
            project.layout.projectDirectory.dir(DEFAULT_REFERENCE_DUMP_DIR).dir(variant.name).file(DEFAULT_REFERENCE_DUMP_FILE)
        )
        variant.legacyDump.referenceDumpDir.convention(
            project.layout.projectDirectory.dir(AbiValidationPaths.LEGACY_DEFAULT_REFERENCE_DUMP_DIR + (if (variant.name == MAIN_VARIANT_NAME) "" else "-${variant.name}"))
        )
    }
}

/**
 * Create and preconfigure legacy tasks for given report [variant].
 */
private fun Project.configureLegacyTasks(variant: AbiValidationVariantSpec) {
    val projectName = project.name
    val variantName = variant.name
    val klibFileName = "$projectName$LEGACY_KLIB_DUMP_EXTENSION"

    val referenceDir = variant.legacyDump.referenceDumpDir
    val filters = variant.filters
    val dumpDir =
        project.layout.buildDirectory.dir(LEGACY_ACTUAL_DUMP_DIR + (if (variantName == MAIN_VARIANT_NAME) "" else "-$variantName"))

    val dumpTaskProvider =
        tasks.register(KotlinLegacyAbiDumpTaskImpl.nameForVariant(variantName), KotlinLegacyAbiDumpTaskImpl::class.java) {
            it.dumpDir.convention(dumpDir)
            it.referenceKlibDump.convention(referenceDir.map { dir -> dir.file(klibFileName) })
            it.keepUnsupportedTargets.convention(true)
            it.klibIsEnabled.convention(true)
            it.variantName.convention(variantName)

            it.includedClasses.convention(filters.included.classes)
            it.includedAnnotatedWith.convention(filters.included.annotatedWith)
            it.excludedClasses.convention(filters.excluded.classes)
            it.excludedAnnotatedWith.convention(filters.excluded.annotatedWith)

            // TODO what group should be used?
            // it.group = "abi"
        }

    tasks.register(KotlinLegacyAbiCheckTaskImpl.nameForVariant(variantName), KotlinLegacyAbiCheckTaskImpl::class.java) {
        it.actualDir.convention(dumpTaskProvider.map { t -> t.dumpDir.get() })
        it.referenceDir.convention(referenceDir)
        it.variantName.convention(variantName)

        // TODO what group should be used?
        // it.group = "abi"
    }

    tasks.register(KotlinLegacyAbiUpdateTask.nameForVariant(variantName), KotlinLegacyAbiUpdateTask::class.java) {
        it.actualDir.convention(dumpTaskProvider.map { t -> t.dumpDir.get() })
        it.referenceDir.convention(referenceDir)
        it.variantName.convention(variantName)

        // TODO what group should be used?
        // it.group = "abi"
    }
}
