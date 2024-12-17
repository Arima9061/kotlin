/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.abi

import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

/**
 * Task to dump public ABI into files in [dumpDir] directory.
 *
 * @since 2.1.20
 */
@ExperimentalAbiValidation
interface KotlinLegacyAbiDumpTask : Task {
    val dumpDir: Provider<Directory>
}
