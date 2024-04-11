/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.swiftexport.standalone

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.callCompilerWithoutOutputInterceptor
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.asSequence

enum class InputModuleKind {
    Source, Klib
}

abstract class AbstractSourceBasedSwiftRunnerTest : AbstractSwiftRunnerTestBase(inputModuleKind = InputModuleKind.Source, skipDocComments = false)

abstract class AbstractKlibBasedSwiftRunnerTest : AbstractSwiftRunnerTestBase(inputModuleKind = InputModuleKind.Klib, skipDocComments = true)

abstract class AbstractSwiftRunnerTestBase(
    val inputModuleKind: InputModuleKind,
    val skipDocComments: Boolean,
) {

    private val tmpdir = FileUtil.createTempDirectory("SwiftExportIntegrationTests", null, false)

    fun runTest(testPathString: String) {
        val path = Path(testPathString)
        val expectedFiles = path / "golden_result/"
        val moduleRoot = path / "input_root/"
        assert(expectedFiles.isDirectory() && moduleRoot.isDirectory()) { "setup for $path is incorrect" }

        val expectedSwift = if (skipDocComments && (expectedFiles / "result.no_comments.swift").exists()) {
            expectedFiles / "result.no_comments.swift"
        } else {
            expectedFiles / "result.swift"
        }
        val expectedCHeader = expectedFiles / "result.h"
        val expectedKotlinBridge = expectedFiles / "result.kt"

        val output = SwiftExportOutput(
            swiftApi = tmpdir.resolve("result.swift").toPath(),
            kotlinBridges = tmpdir.resolve("result.kt").toPath(),
            cHeaderBridges = tmpdir.resolve("result.c").toPath()
        )

        val inputModule = when (inputModuleKind) {
            InputModuleKind.Source -> {
                InputModule.SourceModule(
                    path = moduleRoot
                )
            }
            InputModuleKind.Klib -> {
                InputModule.BinaryModule(
                    path = compileToNativeKLib(moduleRoot)
                )
            }
        }
        val settings = buildMap {
            put(SwiftExportConfig.DEBUG_MODE_ENABLED, "true")
            put(SwiftExportConfig.BRIDGE_MODULE_NAME, SwiftExportConfig.DEFAULT_BRIDGE_MODULE_NAME)
            if (skipDocComments) {
                put(SwiftExportConfig.SKIP_DOC_RENDERING, "true")
            }
            put(SwiftExportConfig.SORT_DECLARATIONS, "true")
        }
        runSwiftExport(
            input = inputModule,
            output = output,
            config = SwiftExportConfig(
                settings = settings,
                logger = createDummyLogger(),
                distribution = Distribution(KonanHome.konanHomePath),
            )
        )

        KotlinTestUtils.assertEqualsToFile(expectedSwift, output.swiftApi.readText())
        KotlinTestUtils.assertEqualsToFile(expectedCHeader, output.cHeaderBridges.readText())
        KotlinTestUtils.assertEqualsToFile(expectedKotlinBridge, output.kotlinBridges.readText())
    }
}

internal fun compileToNativeKLib(kLibSourcesRoot: Path): Path {
    val ktFiles = Files.walk(kLibSourcesRoot).asSequence().filter { it.extension == "kt" }.toList()
    val testKlib = KtTestUtil.tmpDir("testLibrary").resolve("library.klib").toPath()

    val arguments = buildList {
        ktFiles.mapTo(this) { it.absolutePathString() }
        addAll(listOf("-produce", "library"))
        addAll(listOf("-output", testKlib.absolutePathString()))
    }

    val compileResult = callCompilerWithoutOutputInterceptor(arguments.toTypedArray())

    check(compileResult.exitCode == ExitCode.OK) {
        "Compilation error: $compileResult"
    }

    return testKlib
}

private object KonanHome {
    private const val KONAN_HOME_PROPERTY_KEY = "kotlin.internal.native.test.nativeHome"

    val konanHomePath: String
        get() = System.getProperty(KONAN_HOME_PROPERTY_KEY)
            ?: error("Missing System property: '$KONAN_HOME_PROPERTY_KEY'")
}
