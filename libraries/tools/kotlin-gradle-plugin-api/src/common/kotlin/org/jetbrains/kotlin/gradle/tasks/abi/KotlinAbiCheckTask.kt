/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.abi

import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

/**
 * TODO Not implemented yet
 *
 * @since 2.1.20
 */
@ExperimentalAbiValidation
interface KotlinAbiCheckTask : Task {
    val referenceDump: Provider<RegularFile>
    val actualDump: Provider<RegularFile>
}
