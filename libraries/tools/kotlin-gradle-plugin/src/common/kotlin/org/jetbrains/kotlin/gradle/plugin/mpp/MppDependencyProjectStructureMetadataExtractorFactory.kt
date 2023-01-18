/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.toSingleKpmModuleIdentifier
import org.jetbrains.kotlin.gradle.utils.getOrPut
import org.jetbrains.kotlin.project.model.KpmModuleIdentifier

internal class MppDependencyProjectStructureMetadataExtractorFactory(
    private val projectStructureMetadataByProjectPath: Map<String, Provider<KotlinProjectStructureMetadata?>>
) {
    fun create(
        metadataArtifact: ResolvedArtifactResult
    ): MppDependencyProjectStructureMetadataExtractor {
        val moduleId = metadataArtifact.variant.owner

        if (moduleId is ProjectComponentIdentifier && moduleId.build.isCurrentBuild) {
            val psm = projectStructureMetadataByProjectPath[moduleId.projectPath]
                ?: error("Project structure metadata not found for project ${moduleId.projectPath}")
            return ProjectMppDependencyProjectStructureMetadataExtractor(
                moduleIdentifier = metadataArtifact.variant.toSingleKpmModuleIdentifier(),
                projectPath = moduleId.projectPath,
                projectStructureMetadataProvider = psm::get
            )
        }

        return JarMppDependencyProjectStructureMetadataExtractor(metadataArtifact.file)
    }

    companion object {
        private val extensionName = MppDependencyProjectStructureMetadataExtractorFactory::class.java.simpleName
        fun getOrCreate(project: Project): MppDependencyProjectStructureMetadataExtractorFactory =
            project.extraProperties.getOrPut(extensionName) {
                val projectStructureMetadataByProjectPath = collectProjectStructureMetadataFromAllProjects(project)
                MppDependencyProjectStructureMetadataExtractorFactory(projectStructureMetadataByProjectPath)
            }

        /**
         * Collect Kotlin Project StructureMetadata.
         */
        private fun collectProjectStructureMetadataFromAllProjects(project: Project): Map<String, Provider<KotlinProjectStructureMetadata?>> {
            return project.rootProject.allprojects.associateBy { it.path }.mapValues { (_, subProject) ->
                project.provider { subProject.multiplatformExtensionOrNull?.kotlinProjectStructureMetadata }
            }
        }
    }
}
