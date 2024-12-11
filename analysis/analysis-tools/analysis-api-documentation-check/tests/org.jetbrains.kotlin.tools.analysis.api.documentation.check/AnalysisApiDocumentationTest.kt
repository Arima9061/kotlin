/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.analysis.api.documentation.check

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.childrenOfType
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.fir.builder.AbstractRawFirBuilderTestCase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEqualsToFile
import java.io.File
import kotlin.io.path.Path

class AnalysisApiDocumentationTest : AbstractRawFirBuilderTestCase() {
    @OptIn(KaNonPublicApi::class)
    private fun getUndocumentedDeclarationsByFile(file: KtFile): List<String> =
        buildList {
            val allowedDeclarations = file.getAllowedDeclarations()
            allowedDeclarations.filter { it.shouldBeRendered() }.forEach {
                add("${it.containingKtFile.virtualFilePath}:${it.containingClassOrObject?.name ?: ""}:${it.getSignature()}")
            }
        }

    private fun KtDeclaration.getSignature(): String {
        val modifierList =
            this.childrenOfType<KtModifierList>()
                .singleOrNull()
                ?.allChildren
                ?.filterIsInstance<LeafPsiElement>()
                ?.filter { it !is PsiWhiteSpace }
                ?.joinToString(" ") { it.text }?.let {
                    "$it "
                } ?: ""

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

    private fun KtFile.getAllowedDeclarations(): List<KtDeclaration> = this.declarations.flatMap { it.getAllowedNestedDeclarations() }

    private fun KtDeclaration.getAllowedNestedDeclarations(): List<KtDeclaration> {
        if (!this.isPublic) return emptyList()

        return buildList {
            add(this@getAllowedNestedDeclarations)
            if (this@getAllowedNestedDeclarations is KtDeclarationContainer) {
                addAll(this@getAllowedNestedDeclarations.declarations.flatMap { it.getAllowedNestedDeclarations() })
            }
        }
    }

    private fun KtDeclaration.shouldBeRendered(): Boolean {
        if ((this as? KtClass)?.isAnnotation() == true) return false
        if ((this as? KtObjectDeclaration)?.isCompanion() == true) return false

        if (!this.isPublic || this.hasModifier(KtTokens.OVERRIDE_KEYWORD))
            return false

        if (this is KtProperty && this.name in IGNORED_PROPERTY_NAMES) {
            return false
        }

        if (this is KtNamedFunction && this.name in IGNORED_FUNCTION_NAMES) {
            return false
        }

        return true
    }

    fun getPsiFile(text: String, path: String): KtFile {
        return createPsiFile(FileUtil.getNameWithoutExtension(PathUtil.getFileName(path)), text) as KtFile
    }

    fun testAnalysisApiIsDocumented() {
        val path = testDataPath + ANALYSIS_SOURCE_PATH
        val root = File(path)

        val actualText = buildList {
            for (file in root.walkTopDown()) {
                if (file.isDirectory) continue
                if (file.extension != "kt") continue

                try {
                    val text = FileUtil.loadFile(file, CharsetToolkit.UTF8, true).trim()
                    val file = getPsiFile(text, file.path)
                    addAll(getUndocumentedDeclarationsByFile(file))
                } catch (e: Exception) {
                    throw IllegalStateException(file.path, e)
                }
            }
        }.sorted().joinToString("\n")

        val expectedFilePath = Path(testDataPath + GENERATED_FILE_DIR)
        assertEqualsToFile(expectedFilePath, actualText)
    }

    private companion object {
        private const val ANALYSIS_SOURCE_PATH = "/analysis/analysis-api/src"

        private const val GENERATED_FILE_DIR =
            "/analysis/analysis-tools/analysis-api-documentation-check/undocumented/analysis-api.undocumented"

        private val IGNORED_PROPERTY_NAMES: List<String> = listOf(
            "symbol",
            "token"
        )

        private val IGNORED_FUNCTION_NAMES: List<String> = listOf(
            "getModule"
        )
    }
}
