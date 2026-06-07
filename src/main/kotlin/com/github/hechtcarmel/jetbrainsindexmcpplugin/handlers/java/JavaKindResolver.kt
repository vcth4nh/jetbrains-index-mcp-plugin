package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageKindResolver
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class JavaKindResolver : LanguageKindResolver {
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
