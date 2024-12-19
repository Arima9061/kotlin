/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.analysis.api.documentation.check

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.fir.builder.AbstractRawFirBuilderTestCase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEqualsToFile
import java.io.File

/**
 * This test was introduced to automatically check that every public API from the Analysis API module
 * is documented (i.e., has a KDoc attached).
 *
 * The test iterates through all the files in `analysis/analysis-api` module and builds a resulting file
 * containing all the undocumented public declarations along with
 * file paths, names of containing classes, annotations, keywords and signatures.
 *
 * Then the test compares the contents of the resulting file
 * and the master file `analysis/analysis-tools/analysis-api-documentation-check/undocumented/analysis-api.undocumented`.
 *
 * The test is intended to prevent Analysis API developers from writing undocumented APIs.
 * If the lack of documentation for some declaration is intentional,
 * the developer has to manually add this declaration to the master file.
 */
class AnalysisApiDocumentationTest : AbstractRawFirBuilderTestCase() {
    fun testAnalysisApiDocumentation() {
        val path = testDataPath + ANALYSIS_SOURCE_PATH
        val root = File(path)

        val actualText = buildList {
            for (file in root.walkTopDown()) {
                if (file.isDirectory) continue
                if (file.extension != "kt") continue

                val relativePath = file.relativeTo(root).path

                try {
                    val text = FileUtil.loadFile(file, CharsetToolkit.UTF8, true).trim()
                    val file = getPsiFile(text, file.path)
                    addAll(getUndocumentedDeclarationsByFile(file, relativePath))
                } catch (e: Exception) {
                    throw IllegalStateException(relativePath, e)
                }
            }
        }.sorted().joinToString("\n")

        val expectedFile = File(testDataPath + GENERATED_FILE_PATH)
        assertEqualsToFile(expectedFile, actualText, message = {
            "Some newer public Analysis API declarations are undocumented. Please, consider either documenting them or adding them to `analysis/analysis-tools/analysis-api-documentation-check/undocumented/analysis-api.undocumented`"
        })
    }

    private fun getUndocumentedDeclarationsByFile(file: KtFile, relativePathFromRoot: String): List<String> =
        file.collectPublicDeclarations()
            .filter { it.shouldBeRendered() }
            .map {
                "$relativePathFromRoot:${it.containingClassOrObject?.name?.plus(":") ?: ""}${it.getSignature()}"
            }


    private fun KtDeclaration.getSignature(): String {
        val modifierList =
            this.childrenOfType<KtModifierList>()
                .singleOrNull()
                ?.allChildren
                ?.filter { it !is PsiWhiteSpace && it.text !in NON_RENDERED_MODIFIERS }
                ?.joinToString(" ") { it.text }.orEmpty().let {
                    if (it.isNotEmpty()) "$it " else ""
                }

        val parameterList =
            this.childrenOfType<KtParameterList>().singleOrNull()
                ?.allChildren
                ?.filterIsInstance<KtParameter>()?.joinToString(", ") {
                    it.typeReference?.text ?: ""
                }?.let {
                    "($it)"
                } ?: ""

        return "$modifierList$name$parameterList"
    }

    private fun KtFile.collectPublicDeclarations(): List<KtDeclaration> = buildList {
        this@collectPublicDeclarations.declarations.forEach {
            this.collectPublicNestedDeclarations(it)
        }
    }

    private fun MutableList<KtDeclaration>.collectPublicNestedDeclarations(declaration: KtDeclaration) {
        if (!declaration.isPublic) return

        add(declaration)
        (declaration as? KtDeclarationContainer)?.declarations?.forEach { collectPublicNestedDeclarations(it) }
    }

    private fun KtDeclaration.shouldBeRendered(): Boolean =
        when {
            (this as? KtObjectDeclaration)?.isCompanion() == true -> false
            this.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> false
            this is KtProperty && this.name in IGNORED_PROPERTY_NAMES -> false
            this is KtNamedFunction && this.name in IGNORED_FUNCTION_NAMES -> false
            else -> this.docComment == null
        }

    fun getPsiFile(text: String, path: String): KtFile {
        return createPsiFile(FileUtil.getNameWithoutExtension(PathUtil.getFileName(path)), text) as KtFile
    }

    private companion object {
        private const val ANALYSIS_SOURCE_PATH = "/analysis/analysis-api/src/org/jetbrains/kotlin/analysis/api"

        private const val GENERATED_FILE_PATH =
            "/analysis/analysis-tools/analysis-api-documentation-check/undocumented/analysis-api.undocumented"

        private val IGNORED_PROPERTY_NAMES: List<String> = listOf()

        private val IGNORED_FUNCTION_NAMES: List<String> = listOf()

        private val NON_RENDERED_MODIFIERS: List<String> = listOf(
            "public"
        )
    }
}
