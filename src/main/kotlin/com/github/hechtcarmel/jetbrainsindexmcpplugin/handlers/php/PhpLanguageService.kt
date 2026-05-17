package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement

class PhpLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageId("com.jetbrains.php.lang.PhpLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "PHP"
    override fun isAvailable(): Boolean = PluginDetectors.php.isAvailable

    private val phpClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val phpClass = phpClassClass ?: return null
        if (!phpClass.isInstance(element)) return null
        return try {
            val isInterface = invokeBoolean(element, "isInterface")
            val isTrait = invokeBoolean(element, "isTrait")
            val isEnum = invokeBoolean(element, "isEnum")
            val isAbstract = invokeBoolean(element, "isAbstract")
            when {
                isInterface -> "INTERFACE"
                isTrait -> "TRAIT"
                isEnum -> "ENUM"
                isAbstract -> "ABSTRACT_CLASS"
                else -> "CLASS"
            }
        } catch (_: Exception) { null }
    }

    private fun invokeBoolean(target: Any, method: String): Boolean = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Boolean ?: false
    }.getOrDefault(false)
}
