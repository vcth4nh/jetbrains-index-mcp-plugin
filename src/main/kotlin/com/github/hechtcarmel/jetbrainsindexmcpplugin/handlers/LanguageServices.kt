package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.lang.findUsages.LanguageFindUsages
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

object LanguageServices {

    fun getKind(element: PsiElement): String {
        // Tier 1a: per-language EP-registered kind resolver (new system)
        LanguageKindResolver.EP.forLanguage(element.language)?.resolveKind(element)?.let { return it }
        element.language.baseLanguage?.let { base ->
            LanguageKindResolver.EP.forLanguage(base)?.resolveKind(element)?.let { return it }
        }
        // Tier 1b: legacy LanguageServiceRegistry fallback (removed in cleanup phase)
        val legacyService = LanguageServiceRegistry.getService(element)
        if (legacyService != null) {
            legacyService.resolveKindOrNull(element)?.let { return it }
        }
        // Tier 2: platform FindUsagesProvider
        val ideType = LanguageFindUsages.getType(element)
        if (ideType.isNotEmpty()) return normalizeKind(ideType)
        // Tier 3: className fallback
        return fallbackKindFromClassName(element.javaClass.simpleName)
    }

    fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        // EP-registered provider first
        SuperMethodsProvider.EP.forLanguage(element.language)?.findSuperMethods(element, project)?.let { return it }
        element.language.baseLanguage?.let { base ->
            SuperMethodsProvider.EP.forLanguage(base)?.findSuperMethods(element, project)?.let { return it }
        }
        // Legacy registry fallback
        return LanguageServiceRegistry.findSuperMethods(element, project)
    }

    fun hasAnySuperMethodsProvider(): Boolean =
        SuperMethodsProvider.EP_NAME.extensionList.isNotEmpty() ||
            LanguageServiceRegistry.hasSuperMethodsSupport()

    // Kind normalization (moved here in cleanup phase; for now reuse LanguageService.Companion)
    private fun normalizeKind(ideType: String): String = LanguageService.normalizeKind(ideType)
    private fun fallbackKindFromClassName(simpleName: String): String =
        LanguageService.fallbackKindFromClassName(simpleName)
}
