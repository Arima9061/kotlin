/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.providers

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.projectStructure.LLFirModuleData
import org.jetbrains.kotlin.analysis.low.level.api.fir.projectStructure.llFirModuleData
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.unwrapCopy
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirPrivateVisibleFromDifferentModuleExtension
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.psi.KtCodeFragment

/**
 * [FirPrivateVisibleFromDifferentModuleExtension] which is aware of [KaDanglingFileModule]s.
 * Dangling file can see private declarations of its respective context file.
 */
internal class LLFirPrivateVisibleFromDifferentModuleExtension(private val llFirSession: LLFirSession) :
    FirPrivateVisibleFromDifferentModuleExtension() {

    override fun canSeePrivateDeclarationsOfModule(otherModuleData: FirModuleData): Boolean {
        check(otherModuleData is LLFirModuleData)
        return llFirSession.ktModule.unwrapDangling() == otherModuleData.ktModule
    }

    private fun KaModule.unwrapDangling(): KaModule = if (this is KaDanglingFileModule) contextModule else this

    override fun canSeePrivateTopLevelDeclarationsFromFile(useSiteFile: FirFile, targetFile: FirFile): Boolean {
        return useSiteFile.isDanglingFileWithContextFileEqualTo(targetFile)
    }

    private fun FirFile.isDanglingFileWithContextFileEqualTo(targetFile: FirFile): Boolean {
        if (this.llFirModuleData.ktModule !is KaDanglingFileModule) return false
        if (targetFile.llFirModuleData.ktModule is KaDanglingFileModule) return false

        val unwrappedKtFile = findContextPsiFileForDanglingFirFile(useSiteFile = this)
        return unwrappedKtFile != null && unwrappedKtFile == targetFile.psi
    }

    private fun findContextPsiFileForDanglingFirFile(useSiteFile: FirFile): PsiFile? {
        val useSiteDanglingModule = useSiteFile.llFirModuleData.ktModule as? KaDanglingFileModule ?: return null
        val danglingFile = useSiteDanglingModule.file
        if (danglingFile is KtCodeFragment) {
            danglingFile.context?.containingFile?.let { return it }
        }
        return danglingFile.unwrapCopy(danglingFile)
    }
}
