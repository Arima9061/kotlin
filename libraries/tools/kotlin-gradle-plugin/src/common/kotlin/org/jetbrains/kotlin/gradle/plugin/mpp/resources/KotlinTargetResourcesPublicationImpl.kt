/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.resources

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginLifecycle
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.plugin.diagnostics.reportDiagnostic
import org.jetbrains.kotlin.gradle.plugin.launchInStage
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.mpp.internal
import org.jetbrains.kotlin.gradle.plugin.mpp.resources.publication.KotlinAndroidTargetResourcesPublication
import org.jetbrains.kotlin.gradle.plugin.mpp.resources.resolve.AggregateResourcesTask
import org.jetbrains.kotlin.gradle.plugin.mpp.resources.resolve.ResolveResourcesFromDependenciesTask
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.locateTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import java.io.File
import javax.inject.Inject

internal abstract class KotlinTargetResourcesPublicationImpl @Inject constructor(
    val project: Project
) : KotlinTargetResourcesPublication {

    private val targetsThatSupportPublication = listOf(
        KotlinJsIrTarget::class,
        KotlinNativeTarget::class,
        KotlinJvmTarget::class,
        // FIXME: Check how Android target published variants with flavors and with the grouping flag
        KotlinAndroidTarget::class,
    )

    private val targetsThatSupportResolution = listOf(
        KotlinJsIrTarget::class,
        KotlinNativeTarget::class,
    )

    private val targetToResourcesMap: MutableMap<KotlinTarget, KotlinTargetResourcesPublication.TargetResources> = mutableMapOf()
    private val androidTargetAssetsMap: MutableMap<KotlinAndroidTarget, KotlinTargetResourcesPublication.TargetResources> = mutableMapOf()

    private val targetResourcesSubscribers: MutableMap<KotlinTarget, MutableList<(KotlinTargetResourcesPublication.TargetResources) -> (Unit)>> = mutableMapOf()
    private val androidTargetAssetsSubscribers: MutableMap<KotlinAndroidTarget, MutableList<(KotlinTargetResourcesPublication.TargetResources) -> (Unit)>> = mutableMapOf()

    internal fun subscribeOnPublishResources(
        target: KotlinTarget,
        notify: (KotlinTargetResourcesPublication.TargetResources) -> (Unit),
    ) {
        targetToResourcesMap[target]?.let(notify)
        targetResourcesSubscribers.getOrPut(target, { mutableListOf() }).add(notify)
    }

    internal fun subscribeOnAndroidPublishAssets(
        target: KotlinAndroidTarget,
        notify: (KotlinTargetResourcesPublication.TargetResources) -> (Unit),
    ) {
        androidTargetAssetsMap[target]?.let(notify)
        androidTargetAssetsSubscribers.getOrPut(target, { mutableListOf() }).add(notify)
    }

    override fun canPublishResources(target: KotlinTarget): Boolean {
        if (targetsThatSupportPublication.none { it.isInstance(target) }) return false
        if (target is KotlinAndroidTarget) {
            return AndroidGradlePluginVersion.current >= KotlinAndroidTargetResourcesPublication.MIN_AGP_VERSION
        }
        return true
    }

    override fun publishResourcesAsKotlinComponent(
        target: KotlinTarget,
        resourcePathForSourceSet: (KotlinSourceSet) -> (KotlinTargetResourcesPublication.ResourceRoot),
        relativeResourcePlacement: Provider<File>,
    ) {
        if (!canPublishResources(target)) {
            target.project.reportDiagnostic(KotlinToolingDiagnostics.ResourceMayNotBePublishedForTarget(target.name))
            return
        }
        if (targetToResourcesMap[target] != null) {
            target.project.reportDiagnostic(KotlinToolingDiagnostics.ResourcePublishedMoreThanOncePerTarget(target.name))
            return
        }

        val resources = KotlinTargetResourcesPublication.TargetResources(
            resourcePathForSourceSet = resourcePathForSourceSet,
            relativeResourcePlacement = relativeResourcePlacement,
        )
        targetToResourcesMap[target] = resources
        targetResourcesSubscribers[target].orEmpty().forEach { notify ->
            notify(resources)
        }
    }

    override fun publishInAndroidAssets(
        target: KotlinAndroidTarget,
        resourcePathForSourceSet: (KotlinSourceSet) -> (KotlinTargetResourcesPublication.ResourceRoot),
        relativeResourcePlacement: Provider<File>,
    ) {
        if (androidTargetAssetsMap[target] != null) {
            target.project.reportDiagnostic(KotlinToolingDiagnostics.AssetsPublishedMoreThanOncePerTarget())
        }
        val resources = KotlinTargetResourcesPublication.TargetResources(
            resourcePathForSourceSet = resourcePathForSourceSet,
            relativeResourcePlacement = relativeResourcePlacement,
        )
        androidTargetAssetsMap[target] = resources
        androidTargetAssetsSubscribers[target].orEmpty().forEach { notify ->
            notify(resources)
        }
    }

    override fun canResolveResources(target: KotlinTarget): Boolean {
        return targetsThatSupportResolution.any { it.isInstance(target) }
    }

    override fun resolveResources(target: KotlinTarget): Provider<File> {
        if (!canResolveResources(target)) {
            error("Resources may not be resolved for target $target")
        }

        val aggregateResourcesTaskName = target.disambiguateName("AggregateResources")
        project.locateTask<AggregateResourcesTask>(aggregateResourcesTaskName)?.let {
            return it.flatMap { it.outputDirectory.asFile }
        }

        val resolveResourcesFromDependenciesTask = project.registerTask<ResolveResourcesFromDependenciesTask>(
            target.disambiguateName("ResolveResourcesFromDependencies")
        )
        val aggregateResourcesTask = project.registerTask<AggregateResourcesTask>(aggregateResourcesTaskName) { aggregate ->
            aggregate.resourcesFromDependenciesDirectory.set(resolveResourcesFromDependenciesTask.flatMap { it.outputDirectory })
            aggregate.outputDirectory.set(
                project.layout.buildDirectory.dir("$MULTIPLATFORM_RESOURCES_DIRECTORY/aggregated-resources/${target.targetName}")
            )
        }

        project.launchInStage(KotlinPluginLifecycle.Stage.AfterFinaliseCompilations) {
            val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            resolveResourcesFromDependencies(
                resourcesConfiguration = mainCompilation.internal.configurations.resourcesConfiguration,
                resolveResourcesFromDependenciesTask = resolveResourcesFromDependenciesTask,
                targetName = target.targetName,
            )
            resolveResourcesFromSelf(
                compilation = mainCompilation,
                target = target,
                aggregateResourcesTask = aggregateResourcesTask,
            )
        }

        return aggregateResourcesTask.flatMap { it.outputDirectory.asFile }
    }

    private fun resolveResourcesFromDependencies(
        resourcesConfiguration: Configuration,
        resolveResourcesFromDependenciesTask: TaskProvider<ResolveResourcesFromDependenciesTask>,
        targetName: String,
    ) {
        resolveResourcesFromDependenciesTask.configure {
            it.dependsOn(resourcesConfiguration)
            it.archivesFromDependencies.from(resourcesConfiguration.incoming.artifactView { view -> view.lenient(true) }.files)
            it.outputDirectory.set(
                project.layout.buildDirectory.dir("$MULTIPLATFORM_RESOURCES_DIRECTORY/resources-from-dependencies/${targetName}")
            )
        }
    }

    private fun resolveResourcesFromSelf(
        compilation: KotlinCompilation<*>,
        target: KotlinTarget,
        aggregateResourcesTask: TaskProvider<AggregateResourcesTask>,
    ) {
        subscribeOnPublishResources(target) { resources ->
            project.launch {
                val copyResourcesTask = compilation.registerAssembleHierarchicalResourcesTask(
                    target.disambiguateName("ResolveSelfResources"),
                    resources,
                )
                aggregateResourcesTask.configure { aggregate ->
                    aggregate.resourcesFromSelfDirectory.set(copyResourcesTask)
                }
            }
        }
    }

    internal companion object {
        const val MULTIPLATFORM_RESOURCES_DIRECTORY = "kotlin-multiplatform-resources"
    }

}