package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement

interface LanguageKindResolver {
    fun resolveKind(element: PsiElement): String?

    companion object {
        const val EP_NAME = "com.github.hechtcarmel.jetbrainsindexmcpplugin.languageKindResolver"
        val EP = LanguageExtension<LanguageKindResolver>(EP_NAME)
    }
}
