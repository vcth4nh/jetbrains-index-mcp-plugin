package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.javascript

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement

class JavaScriptLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        buildSet {
            resolveLanguageId("com.intellij.lang.javascript.JavascriptLanguage", "INSTANCE")?.let { add(it) }
            resolveLanguageId("com.intellij.lang.javascript.TypeScriptLanguage", "INSTANCE")?.let { add(it) }
            listOf("ECMAScript 6", "JSX Harmony", "TypeScript JSX").forEach { add(it) }
        }
    }
    override val displayName = "JavaScript/TypeScript"
    override fun isAvailable(): Boolean = PluginDetectors.javaScript.isAvailable
    // JS/TS kind resolution is already handled well by the platform's
    // JSNamedElementKind via LanguageFindUsages.getType(). No override needed.
}
