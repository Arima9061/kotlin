/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.abi

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.abi.*
import org.jetbrains.kotlin.gradle.plugin.abi.getTask
import org.jetbrains.kotlin.gradle.tasks.abi.*
import org.jetbrains.kotlin.gradle.utils.newInstance
import javax.inject.Inject

internal abstract class AbiValidationExtensionImpl @Inject constructor(private val project: Project) :
    AbiValidationVariantSpecImpl(AbiValidationVariantSpec.MAIN_VARIANT_NAME, project), AbiValidationExtension {
    final override val variants: NamedDomainObjectContainer<AbiValidationVariantSpec> =
        project.objects.domainObjectContainer(AbiValidationVariantSpec::class.java) { name ->
            AbiValidationVariantSpecImpl(name, project)
        }
}

internal open class AbiValidationVariantSpecImpl(private val variantName: String, project: Project) : AbiValidationVariantSpec {
    override val filters: AbiFiltersSpec = project.objects.newInstance<AbiFiltersSpecImpl>(project.objects)

    override val legacyDump: AbiValidationLegacyDumpExtension =
        project.objects.newInstance<AbiValidationLegacyDumpExtensionImpl>(variantName, project)

    override val referenceDump: RegularFileProperty = project.objects.fileProperty()

    override val updateTaskProvider: TaskProvider<Task>
        get() = TODO("New dump format is not implemented yes")

    override val dumpTaskProvider: TaskProvider<KotlinAbiDumpTask>
        get() = TODO("New dump format is not implemented yes")

    override val checkTaskProvider: TaskProvider<KotlinAbiCheckTask>
        get() = TODO("New dump format is not implemented yes")

    override fun getName(): String = variantName
}

internal abstract class AbiValidationLegacyDumpExtensionImpl @Inject constructor(
    private val variantName: String,
    private val project: Project,
) : AbiValidationLegacyDumpExtension {
    override val legacyCheckTaskProvider: TaskProvider<KotlinLegacyAbiCheckTask> by lazy {
        // task is created a little later than the current object, so we add lazy access
        project.tasks.getTask(KotlinLegacyAbiCheckTaskImpl.nameForVariant(variantName))
    }

    override val legacyDumpTaskProvider: TaskProvider<KotlinLegacyAbiDumpTask> by lazy {
        // task is created a little later than the current object, so we add lazy access
        project.tasks.getTask(KotlinLegacyAbiDumpTaskImpl.nameForVariant(variantName))
    }

    override val legacyUpdateTaskProvider: TaskProvider<Task> by lazy {
        // task is created a little later than the current object, so we add lazy access
        project.tasks.getTask(KotlinLegacyAbiUpdateTask.nameForVariant(variantName))
    }
}


internal abstract class AbiValidationMultiplatformExtensionImpl @Inject constructor(project: Project) :
    AbiValidationVariantSpecImpl(AbiValidationVariantSpec.MAIN_VARIANT_NAME, project), AbiValidationMultiplatformExtension {

    override val variants: NamedDomainObjectContainer<AbiValidationMultiplatformVariantSpec> =
        project.objects.domainObjectContainer(AbiValidationMultiplatformVariantSpec::class.java) { name ->
            AbiValidationMultiplatformVariantSpecImpl(name, project)
        }

    override val klib: AbiValidationKlibKindExtension = project.objects.newInstance<AbiValidationKlibKindExtension>()
}

internal open class AbiValidationMultiplatformVariantSpecImpl(variantName: String, project: Project) :
    AbiValidationVariantSpecImpl(variantName, project), AbiValidationMultiplatformVariantSpec {
    override val klib: AbiValidationKlibKindExtension = project.objects.newInstance<AbiValidationKlibKindExtension>()
}
