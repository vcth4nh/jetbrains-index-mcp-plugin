package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement

class PythonLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageIdViaGetInstance("com.jetbrains.python.PythonLanguage")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Python"
    override fun isAvailable(): Boolean = PluginDetectors.python.isAvailable

    private val pyTargetExpressionClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyTargetExpression") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val pyClass = pyTargetExpressionClass ?: return null
        if (!pyClass.isInstance(element)) return null
        return try {
            val containingClass = element.javaClass.getMethod("getContainingClass").invoke(element)
            if (containingClass != null) "FIELD" else "VARIABLE"
        } catch (_: Exception) { "FIELD" }
    }
}
