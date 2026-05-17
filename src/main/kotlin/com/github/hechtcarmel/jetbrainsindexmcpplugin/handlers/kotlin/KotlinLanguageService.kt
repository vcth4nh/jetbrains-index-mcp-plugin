package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.kotlin

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement

class KotlinLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageId("org.jetbrains.kotlin.idea.KotlinLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Kotlin"
    override fun isAvailable(): Boolean = PluginDetectors.kotlin.isAvailable

    private val ktClassClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtClass") } catch (_: ClassNotFoundException) { null }
    }
    private val ktObjectDeclarationClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtObjectDeclaration") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val ktClass = ktClassClass
        if (ktClass != null && ktClass.isInstance(element)) {
            return try {
                val isAnnotation = invokeBoolean(element, "isAnnotation")
                val isInterface = invokeBoolean(element, "isInterface")
                val isEnum = invokeBoolean(element, "isEnum")
                val isData = invokeBoolean(element, "isData")
                val isSealed = invokeBoolean(element, "isSealed")
                val isAbstract = kotlinHasModifier(element, "abstract")
                when {
                    isAnnotation -> "ANNOTATION"
                    isInterface -> "INTERFACE"
                    isEnum -> "ENUM"
                    isData -> "DATA_CLASS"
                    isSealed -> "SEALED_CLASS"
                    isAbstract -> "ABSTRACT_CLASS"
                    else -> "CLASS"
                }
            } catch (_: Exception) { null }
        }
        if (ktObjectDeclarationClass?.isInstance(element) == true) return "OBJECT"
        return null
    }

    private fun invokeBoolean(target: Any, method: String): Boolean = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Boolean ?: false
    }.getOrDefault(false)

    private fun kotlinHasModifier(element: PsiElement, modifier: String): Boolean = runCatching {
        val modList = element.javaClass.getMethod("getModifierList").invoke(element) as? PsiElement
        modList?.text?.let { text -> Regex("\\b${Regex.escape(modifier)}\\b").containsMatchIn(text) } == true
    }.getOrDefault(false)
}
