/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.abi

import org.jetbrains.kotlin.abi.tools.api.AbiToolsFactory
import org.jetbrains.kotlin.buildtools.internal.KotlinBuildToolsInternalJdkUtils
import org.jetbrains.kotlin.buildtools.internal.getJdkClassesClassLoader
import org.jetbrains.kotlin.gradle.internal.ParentClassLoaderProvider

/**
 * Class loader to share some classes from [sharedClassLoader] with package name started with [sharedPackage].
 *
 * If class belongs to [sharedPackage] it will be taken from [sharedClassLoader], otherwise from JDK class loader.
 */
internal class SharedClassLoader(
    private val sharedClassLoader: ClassLoader,
    private val sharedPackage: String,
) : ClassLoader(@OptIn(KotlinBuildToolsInternalJdkUtils::class) (getJdkClassesClassLoader())) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return if (name.startsWith(sharedPackage)) {
            sharedClassLoader.loadClass(name)
        } else {
            super.loadClass(name, resolve)
        }
    }
}

/**
 * Provider for shared class loader, sharing classes from package `org.jetbrains.kotlin.abi.tools.api` and its subpackages.
 *
 * It will be used as the parent classloader, so its purpose is for all its heirs to use the same loaded classes for `org.jetbrains.kotlin.abi.tools.api` package.
 */
internal object SharedClassLoaderProvider : ParentClassLoaderProvider {
    override fun getClassLoader() = createSharedClassLoader()

    override fun hashCode() = SharedClassLoaderProvider::class.hashCode()

    override fun equals(other: Any?) = other is SharedClassLoaderProvider

    private fun createSharedClassLoader(): ClassLoader {
        return SharedClassLoader(
            AbiToolsFactory::class.java.classLoader,
            AbiToolsFactory::class.java.`package`.name,
        )
    }
}