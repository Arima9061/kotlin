/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl.abi

/**
 * Marks parts of Kotlin Gradle Plugin related to the Kotlin ABI Validation tool.
 *
 * Marked DSL can be changed or removed as development continues.
 * Therefore, it does not provide any compatibility guarantees.
 */
@RequiresOptIn(
    "The DSL is related to the ABI Validation experimental DSL and may be changed or removed in any future release.",
    RequiresOptIn.Level.ERROR
)
@MustBeDocumented
public annotation class ExperimentalAbiValidation