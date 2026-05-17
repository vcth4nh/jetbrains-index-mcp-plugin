package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class JavaLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageId("com.intellij.lang.java.JavaLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Java"
    override fun isAvailable(): Boolean = PluginDetectors.java.isAvailable

    override fun resolveKind(element: PsiElement): String? {
        if (element !is PsiClass) return null
        return when {
            element.isAnnotationType -> "ANNOTATION"
            element.isInterface -> "INTERFACE"
            element.isEnum -> "ENUM"
            element.isRecord -> "RECORD"
            element.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }
}
