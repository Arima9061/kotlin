/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.abi

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.abi.tools.api.v2.KlibTarget
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiCheckTaskImpl
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiDumpTaskImpl
import org.jetbrains.kotlin.gradle.tasks.abi.KotlinLegacyAbiUpdateTask

/**
 * A class for combining and conveniently configuring a bunch of tasks created for ABI Validation.
 *
 * All these tasks belong to the same report variant.
 */
internal class AbiValidationTaskSet(project: Project, variantName: String) {
    private val legacyDumpTaskProvider =
        project.tasks.getTask<KotlinLegacyAbiDumpTaskImpl>(KotlinLegacyAbiDumpTaskImpl.nameForVariant(variantName))
    private val legacyCheckDumpTaskProvider =
        project.tasks.getTask<KotlinLegacyAbiCheckTaskImpl>(KotlinLegacyAbiCheckTaskImpl.nameForVariant(variantName))
    private val legacyUpdateDumpTaskProvider =
        project.tasks.getTask<KotlinLegacyAbiUpdateTask>(KotlinLegacyAbiUpdateTask.nameForVariant(variantName))

    /**
     * Add declarations for JVM target, at the same time, there are no other JVM targets.
     *
     * @param [classfiles] result of compiling given target, collection of classfiles
     */
    fun addSingleJvmTarget(classfiles: FileCollection) {
        legacyDumpTaskProvider.configure {
            it.jvm.add(KotlinLegacyAbiDumpTaskImpl.JvmTargetInfo("", classfiles))
        }
    }

    /**
     * Add declarations for one of several JVM targets with name [targetName].
     *
     * @param [classfiles] result of compiling given target, collection of classfiles
     */
    fun addJvmTarget(targetName: String, classfiles: FileCollection) {
        legacyDumpTaskProvider.configure {
            it.jvm.add(KotlinLegacyAbiDumpTaskImpl.JvmTargetInfo(targetName, classfiles))
        }
    }

    /**
     * Add declarations for non-JVM target.
     *
     * @param [klibTarget] target to add
     * @param [klibFile] file in klib format with Kotlin compiled code
     */
    fun addKlibTarget(klibTarget: KlibTarget, klibFile: FileCollection) {
        legacyDumpTaskProvider.configure {
            it.klibInput.add(KotlinLegacyAbiDumpTaskImpl.KlibTargetInfo(klibTarget.configurableName, klibTarget.targetName, klibFile))
        }
    }

    /**
     * Allow to keep ABI declarations in dump file for unsupported targets which were added using [unsupportedTarget].
     */
    fun keepUnsupportedTargets(keep: Provider<Boolean>) {
        legacyDumpTaskProvider.configure {
            it.keepUnsupportedTargets.set(keep)
        }
    }

    /**
     * Allow to write declarations gained from klibs into dump files.
     */
    fun klibEnabled(isEnabled: Provider<Boolean>) {
        legacyDumpTaskProvider.configure {
            it.klibIsEnabled.set(isEnabled)
        }
    }

    /**
     * Mark specified target as unsupported by Kotlin compiler on current host.
     */
    fun unsupportedTarget(klibTarget: KlibTarget) {
        legacyDumpTaskProvider.configure {
            it.unsupportedTargets.add(klibTarget)
        }
    }

    /**
     * Set classpath of ABI Tools dependency for all ABI Validation tasks.
     */
    fun setClasspath(toolClasspath: Configuration) {
        legacyDumpTaskProvider.configure {
            it.toolsClasspath.from(toolClasspath)
        }
        legacyCheckDumpTaskProvider.configure {
            it.toolsClasspath.from(toolClasspath)
        }
    }
}