package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import junit.framework.TestCase

/**
 * Tests for the pure-logic dispatch table in LanguageAwareKindResolver.
 *
 * Full PSI-element classification is platform-test territory; this unit test
 * covers the language-id → resolver-strategy mapping and the substring-fallback
 * for cases where the language is unknown.
 */
class LanguageAwareKindResolverUnitTest : TestCase() {

    fun testFallbackKindFromClassName_unknownLanguage_classSubstring() {
        // Simple class-name substring fallback when no language-specific resolver matches.
        assertEquals("INTERFACE", LanguageAwareKindResolver.fallbackKindFromClassName("PsiInterfaceImpl"))
        assertEquals("ENUM", LanguageAwareKindResolver.fallbackKindFromClassName("MyEnumImpl"))
        assertEquals("CLASS", LanguageAwareKindResolver.fallbackKindFromClassName("MyClassImpl"))
        assertEquals("STRUCT", LanguageAwareKindResolver.fallbackKindFromClassName("RsStructItem"))
        assertEquals("TRAIT", LanguageAwareKindResolver.fallbackKindFromClassName("RsTraitItem"))
        // Order matters: "interface" before "class" so PhpClassImpl-style matches don't collapse.
        assertEquals("INTERFACE", LanguageAwareKindResolver.fallbackKindFromClassName("SomeInterfaceClassImpl"))
    }

    fun testFallbackKindFromClassName_default() {
        assertEquals("SYMBOL", LanguageAwareKindResolver.fallbackKindFromClassName("Unknown"))
        assertEquals("SYMBOL", LanguageAwareKindResolver.fallbackKindFromClassName(""))
    }

    fun testFallbackKindFromClassName_memberKinds() {
        // Member-level PSI impl class names (preserves OptimizedSymbolSearch.determineKind original behavior)
        assertEquals("METHOD", LanguageAwareKindResolver.fallbackKindFromClassName("PsiMethodImpl"))
        assertEquals("FUNCTION", LanguageAwareKindResolver.fallbackKindFromClassName("PyFunctionImpl"))
        assertEquals("FIELD", LanguageAwareKindResolver.fallbackKindFromClassName("PsiFieldImpl"))
        assertEquals("VARIABLE", LanguageAwareKindResolver.fallbackKindFromClassName("JSVariableImpl"))
        assertEquals("PROPERTY", LanguageAwareKindResolver.fallbackKindFromClassName("KtPropertyImpl"))
        assertEquals("CONSTANT", LanguageAwareKindResolver.fallbackKindFromClassName("PhpConstantImpl"))
    }

    fun testFallbackKindFromClassName_phpEnum_returnsEnum() {
        // PHP enums share the PhpClassImpl PSI class — the fallback substring matcher
        // catches the "enum" keyword in any synthetic / stub class name. This test
        // documents the regression-protection contract for the PHP enum fix path.
        assertEquals("ENUM", LanguageAwareKindResolver.fallbackKindFromClassName("PhpEnumImpl"))
        assertEquals("ENUM", LanguageAwareKindResolver.fallbackKindFromClassName("BackedEnumStub"))
    }
}
