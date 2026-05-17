package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.go

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class GoLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageId("com.goide.GoLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Go"
    override fun isAvailable(): Boolean = PluginDetectors.go.isAvailable
    override val supportsSuperMethods: Boolean = false

    private val goTypeSpecClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoTypeSpec") } catch (_: ClassNotFoundException) { null }
    }
    private val goStructTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoStructType") } catch (_: ClassNotFoundException) { null }
    }
    private val goInterfaceTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoInterfaceType") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val typeSpec = goTypeSpecClass ?: return null
        if (!typeSpec.isInstance(element)) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            when {
                goInterfaceTypeClass != null && PsiTreeUtil.findChildOfType(
                    element, goInterfaceTypeClass as Class<out PsiElement>
                ) != null -> "INTERFACE"
                goStructTypeClass != null && PsiTreeUtil.findChildOfType(
                    element, goStructTypeClass as Class<out PsiElement>
                ) != null -> "STRUCT"
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
