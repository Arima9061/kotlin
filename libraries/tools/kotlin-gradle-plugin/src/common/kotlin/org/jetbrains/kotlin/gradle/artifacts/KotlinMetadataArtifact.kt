/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.artifacts

import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.artifacts.uklibsPublication.UklibFragmentPlatformAttribute
import org.jetbrains.kotlin.gradle.artifacts.uklibsPublication.uklibFragmentPlatformAttribute
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.categoryByName
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.plugin.sources.internal
import org.jetbrains.kotlin.gradle.plugin.usageByName
import org.jetbrains.kotlin.gradle.targets.metadata.awaitMetadataCompilationsCreated
import org.jetbrains.kotlin.gradle.targets.metadata.isCompatibilityMetadataVariantEnabled
import org.jetbrains.kotlin.gradle.targets.metadata.isKotlinGranularMetadataEnabled
import org.jetbrains.kotlin.gradle.targets.metadata.locateOrRegisterGenerateProjectStructureMetadataTask
import org.jetbrains.kotlin.gradle.targets.native.internal.includeCommonizedCInteropMetadata
import org.jetbrains.kotlin.gradle.utils.setAttribute

internal val KotlinMetadataArtifact = KotlinTargetArtifact { target, apiElements, _ ->
    if (target !is KotlinMetadataTarget || !target.project.isKotlinGranularMetadataEnabled) return@KotlinTargetArtifact

    apiElements.attributes.setAttribute(Usage.USAGE_ATTRIBUTE, target.project.usageByName(KotlinUsages.KOTLIN_METADATA))
    apiElements.attributes.setAttribute(Category.CATEGORY_ATTRIBUTE, target.project.categoryByName(Category.LIBRARY))

    val metadataJarTask = target.createArtifactsTask { jar ->
        jar.description = "Assembles a jar archive containing the metadata for all Kotlin source sets."
        if (target.project.isCompatibilityMetadataVariantEnabled) {
            jar.archiveClassifier.set("all")
        }
    }

    /* Include 'KotlinProjectStructureMetadata' file */
    val generateMetadata = target.project.locateOrRegisterGenerateProjectStructureMetadataTask()
    metadataJarTask.configure { jar ->
        jar.from(generateMetadata.map { it.resultFile }) { spec ->
            spec.into("META-INF").rename { MULTIPLATFORM_PROJECT_METADATA_JSON_FILE_NAME }
        }
    }

    /* Include output of metadata compilations into metadata jar (including commonizer output if available */
    val hostSpecificSourceSets = getHostSpecificSourceSets(target.project)
    // FIXME: How are test compilations filtered out ????
    target.publishedMetadataCompilations().filter {
        /* Filter 'host specific' source sets (aka source sets that require a certain host to compile metadata) */
        it.defaultSourceSet !in hostSpecificSourceSets
    }.forEach { compilation ->
        metadataJarTask.configure { it.from(compilation.metadataPublishedArtifacts) { spec -> spec.into(compilation.metadataFragmentIdentifier) } }
        if (compilation is KotlinSharedNativeCompilation) {
            target.project.includeCommonizedCInteropMetadata(metadataJarTask, compilation)
        }
    }

    target.createPublishArtifact(metadataJarTask, JAR_TYPE, apiElements)
}

internal suspend fun KotlinMetadataTarget.publishedMetadataCompilations(): List<KotlinCompilation<*>> {
    return awaitMetadataCompilationsCreated().filter { compilation ->
        /* Filter legacy compilation */
        !(compilation is KotlinCommonCompilation && !compilation.isKlibCompilation)
    }
}

internal val KotlinCompilation<*>.metadataPublishedArtifacts: FileCollection get() = output.classesDirs
// FIXME: Use this everywhere we map between fragment name in PSM and the name of disk
internal val KotlinCompilation<*>.metadataFragmentIdentifier: String get() = defaultSourceSet.name
internal val KotlinCompilation<*>.metadataFragmentAttributes: Set<UklibFragmentPlatformAttribute> get() = defaultSourceSet.internal.compilations
    .filterNot {
        it is KotlinMetadataCompilation
    }.map { it.uklibFragmentPlatformAttribute }.toSet()