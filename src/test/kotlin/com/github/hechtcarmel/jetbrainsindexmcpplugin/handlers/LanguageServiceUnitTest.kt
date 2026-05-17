package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import junit.framework.TestCase

class LanguageServiceUnitTest : TestCase() {

    fun testNormalizeKindUppercasesCommonTypes() {
        assertEquals("METHOD", LanguageService.normalizeKind("method"))
        assertEquals("FUNCTION", LanguageService.normalizeKind("function"))
        assertEquals("CLASS", LanguageService.normalizeKind("class"))
        assertEquals("INTERFACE", LanguageService.normalizeKind("interface"))
        assertEquals("ENUM", LanguageService.normalizeKind("enum"))
        assertEquals("TRAIT", LanguageService.normalizeKind("trait"))
        assertEquals("STRUCT", LanguageService.normalizeKind("struct"))
        assertEquals("FIELD", LanguageService.normalizeKind("field"))
        assertEquals("VARIABLE", LanguageService.normalizeKind("variable"))
        assertEquals("PROPERTY", LanguageService.normalizeKind("property"))
        assertEquals("CONSTANT", LanguageService.normalizeKind("constant"))
        assertEquals("RECORD", LanguageService.normalizeKind("record"))
        assertEquals("ANNOTATION", LanguageService.normalizeKind("annotation"))
        assertEquals("PARAMETER", LanguageService.normalizeKind("parameter"))
        assertEquals("CONSTRUCTOR", LanguageService.normalizeKind("constructor"))
    }

    fun testNormalizeKindHandlesContains() {
        assertEquals("METHOD", LanguageService.normalizeKind("abstract method"))
        assertEquals("FUNCTION", LanguageService.normalizeKind("local function"))
        assertEquals("CLASS", LanguageService.normalizeKind("inner class"))
        assertEquals("INTERFACE", LanguageService.normalizeKind("functional interface"))
    }

    fun testNormalizeKindUnknownUppercasesAndUnderscores() {
        assertEquals("TYPE_ALIAS", LanguageService.normalizeKind("type alias"))
        assertEquals("SOME_NEW_THING", LanguageService.normalizeKind("some new thing"))
    }

    fun testFallbackKindFromClassName() {
        assertEquals("INTERFACE", LanguageService.fallbackKindFromClassName("PsiInterface"))
        assertEquals("METHOD", LanguageService.fallbackKindFromClassName("GoMethodDeclaration"))
        assertEquals("FUNCTION", LanguageService.fallbackKindFromClassName("RsFunction"))
        assertEquals("STRUCT", LanguageService.fallbackKindFromClassName("RsStructItem"))
        assertEquals("ENUM", LanguageService.fallbackKindFromClassName("KtEnumEntry"))
        assertEquals("CLASS", LanguageService.fallbackKindFromClassName("PsiClassImpl"))
        assertEquals("SYMBOL", LanguageService.fallbackKindFromClassName("UnknownElement"))
    }

    fun testFallbackKindPriorityOrder() {
        assertEquals("INTERFACE", LanguageService.fallbackKindFromClassName("InterfaceClassDecl"))
        assertEquals("TRAIT", LanguageService.fallbackKindFromClassName("TraitClassDecl"))
    }
}
