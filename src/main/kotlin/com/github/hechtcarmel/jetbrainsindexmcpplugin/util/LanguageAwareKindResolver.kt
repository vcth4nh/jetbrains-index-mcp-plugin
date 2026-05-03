package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * Single source of truth for classifying a PSI element into a language-aware
 * kind string (e.g. "INTERFACE", "ABSTRACT_CLASS", "STRUCT", "TRAIT").
 *
 * Replaces raw substring matching that was duplicated across FindClassTool
 * and OptimizedSymbolSearch and that mis-classified PHP interfaces, Java
 * abstract classes / records, and Go structs / interfaces.
 *
 * Each language branch is gated behind reflective Class.forName so missing
 * language plugins never trigger NoClassDefFoundError.
 */
object LanguageAwareKindResolver {
    private val LOG = logger<LanguageAwareKindResolver>()

    private val phpClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass") } catch (_: ClassNotFoundException) { null }
    }

    private val goTypeSpecClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoTypeSpec") } catch (_: ClassNotFoundException) { null }
    }

    private val goInterfaceTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoInterfaceType") } catch (_: ClassNotFoundException) { null }
    }

    private val goStructTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoStructType") } catch (_: ClassNotFoundException) { null }
    }

    /**
     * Classify [element] into a kind string. Dispatches by language id; falls
     * back to substring matching for unknown languages. Always returns a
     * non-null value; "SYMBOL" is the catch-all for unclassifiable elements.
     */
    fun resolveKind(element: PsiElement): String {
        return when (element.language.id) {
            // Kotlin: KtUltraLightClass IS a PsiClass and gets correct classification here.
            // Raw KtClass falls through to substring matching; Task 6 file-structure handler
            // adds Kotlin-specific data/sealed/object detection where needed.
            "JAVA", "kotlin" -> resolveJavaLikeKind(element)
            "PHP" -> resolvePhpKind(element)
            "go" -> resolveGoKind(element)
            else -> fallbackKindFromClassName(element.javaClass.simpleName)
        }
    }

    private fun resolveJavaLikeKind(element: PsiElement): String {
        if (element !is PsiClass) return fallbackKindFromClassName(element.javaClass.simpleName)
        return when {
            element.isInterface -> "INTERFACE"
            element.isEnum -> "ENUM"
            element.isAnnotationType -> "ANNOTATION"
            element.isRecord -> "RECORD"
            element.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

    private fun resolvePhpKind(element: PsiElement): String {
        val phpClass = phpClassClass ?: return fallbackKindFromClassName(element.javaClass.simpleName)
        if (!phpClass.isInstance(element)) return fallbackKindFromClassName(element.javaClass.simpleName)
        return try {
            val isInterface = phpClass.getMethod("isInterface").invoke(element) as? Boolean == true
            val isTrait = phpClass.getMethod("isTrait").invoke(element) as? Boolean == true
            val isAbstract = phpClass.getMethod("isAbstract").invoke(element) as? Boolean == true
            // Order matters: interface and trait take priority over abstract. An abstract
            // interface still reports INTERFACE; an abstract trait still reports TRAIT.
            when {
                isInterface -> "INTERFACE"
                isTrait -> "TRAIT"
                isAbstract -> "ABSTRACT_CLASS"
                else -> "CLASS"
            }
        } catch (e: Exception) {
            LOG.debug("PHP kind resolution failed: ${e.message}")
            fallbackKindFromClassName(element.javaClass.simpleName)
        }
    }

    private fun resolveGoKind(element: PsiElement): String {
        val typeSpec = goTypeSpecClass ?: return fallbackKindFromClassName(element.javaClass.simpleName)
        if (!typeSpec.isInstance(element)) return fallbackKindFromClassName(element.javaClass.simpleName)
        return try {
            val specType = typeSpec.getMethod("getSpecType").invoke(element) as? PsiElement
                ?: return fallbackKindFromClassName(element.javaClass.simpleName)
            when {
                goInterfaceTypeClass?.isInstance(specType) == true -> "INTERFACE"
                goStructTypeClass?.isInstance(specType) == true -> "STRUCT"
                else -> "CLASS"
            }
        } catch (e: Exception) {
            LOG.debug("Go kind resolution failed: ${e.message}")
            fallbackKindFromClassName(element.javaClass.simpleName)
        }
    }

    /**
     * Fallback substring matcher. Order matters — more specific names first
     * (interface before class) so e.g. PhpClassImpl-style names that contain
     * both substrings still report INTERFACE when interface is checked first.
     *
     * Also preserves the member-kind substring fallback that
     * OptimizedSymbolSearch.determineKind originally provided so that find_symbol
     * on Java/Kotlin/non-handler-language member PSI elements (e.g.
     * PsiMethodImpl, PyFunctionImpl, KtPropertyImpl) returns the correct member
     * kind instead of falling through to "CLASS".
     */
    fun fallbackKindFromClassName(simpleName: String): String {
        val lower = simpleName.lowercase()
        return when {
            // Most-specific type patterns first (interface beats class for PhpClassImpl-like names)
            lower.contains("interface") -> "INTERFACE"
            lower.contains("trait") -> "TRAIT"
            lower.contains("annotation") -> "ANNOTATION"
            lower.contains("enum") -> "ENUM"
            lower.contains("record") -> "RECORD"
            lower.contains("structitem") -> "STRUCT"
            lower.contains("implitem") -> "IMPL"
            lower.contains("moditem") -> "MODULE"
            lower.contains("struct") -> "STRUCT"
            // Member-level kinds (preserve OptimizedSymbolSearch's original substring fallback)
            lower.contains("method") -> "METHOD"
            lower.contains("function") -> "FUNCTION"
            lower.contains("field") -> "FIELD"
            lower.contains("variable") -> "VARIABLE"
            lower.contains("property") -> "PROPERTY"
            lower.contains("constant") -> "CONSTANT"
            // General type catch-all
            lower.contains("class") -> "CLASS"
            // "SYMBOL" preserves wire contract from OptimizedSymbolSearch.determineKind for find_symbol.
            // Other handlers (Rust/Go/JS/PHP) also use "SYMBOL" as their unknown-kind sentinel.
            else -> "SYMBOL"
        }
    }
}
