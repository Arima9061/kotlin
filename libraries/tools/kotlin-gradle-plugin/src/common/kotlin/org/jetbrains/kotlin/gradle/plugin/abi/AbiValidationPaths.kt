/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.abi

/**
 * Path commonly used in ABI Validation default configuration.
 */
internal object AbiValidationPaths {
    /**
     * Default directory for reference legacy dump files.
     */
    internal const val LEGACY_DEFAULT_REFERENCE_DUMP_DIR = "api"

    /**
     * Directory for actual legacy dump files.
     */
    internal const val LEGACY_ACTUAL_DUMP_DIR = "kotlin/abi-legacy"

    /**
     * Default file extension for legacy dumps for Kotlin JVM or Kotlin Android targets.
     */
    internal const val LEGACY_JVM_DUMP_EXTENSION = ".api"

    /**
     * Default file extension for legacy dumps for Kotlin Multiplatform targets.
     */
    internal const val LEGACY_KLIB_DUMP_EXTENSION = ".klib.api"

    /**
     * Default directory for reference dump file.
     */
    internal const val DEFAULT_REFERENCE_DUMP_DIR = "abi"

    /**
     * Default file name for reference dump.
     */
    internal const val DEFAULT_REFERENCE_DUMP_FILE = "abi.dump"
}