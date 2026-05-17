package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Hybrid kind resolver: per-language granular resolvers first, then
 * [LanguageFindUsages.getType()] as language-agnostic fallback, then
 * class-name substring matching as last resort.
 *
 * Per-language resolvers return null for elements they don't handle,
 * allowing the platform API to cover them. This means adding a new
 * language plugin automatically works via getType() without any code changes.
 */
object LanguageAwareKindResolver {
    private val LOG = logger<LanguageAwareKindResolver>()

    private val resolvers: Map<String, LanguageKindResolver> = buildMap {
        put("JAVA", JavaKindResolver)
        put("kotlin", KotlinKindResolver)
        put("go", GoKindResolver)
        put("PHP", PhpKindResolver)
        put("Rust", RustKindResolver)
    }

    fun resolveKind(element: PsiElement): String {
        // 1. Per-language granular resolver (if registered)
        val resolver = resolvers[element.language.id]
        if (resolver != null) {
            resolver.resolve(element)?.let { return it }
        }

        // 2. Language-agnostic platform API
        val ideType = com.intellij.lang.findUsages.LanguageFindUsages.getType(element)
        if (ideType.isNotEmpty()) return normalizeKind(ideType)

        // 3. Last resort
        return fallbackKindFromClassName(element.javaClass.simpleName)
    }

    fun normalizeKind(ideType: String): String {
        val lower = ideType.lowercase()
        return when {
            lower == "method" -> "METHOD"
            lower == "function" -> "FUNCTION"
            lower == "class" -> "CLASS"
            lower == "interface" -> "INTERFACE"
            lower == "enum" -> "ENUM"
            lower == "trait" -> "TRAIT"
            lower == "struct" -> "STRUCT"
            lower == "object" -> "OBJECT"
            lower == "field" -> "FIELD"
            lower == "variable" -> "VARIABLE"
            lower == "property" -> "PROPERTY"
            lower == "constant" -> "CONSTANT"
            lower == "type alias" || lower == "typealias" -> "TYPE_ALIAS"
            lower == "type" -> "TYPE"
            lower == "record" -> "RECORD"
            lower == "annotation" -> "ANNOTATION"
            lower == "parameter" -> "PARAMETER"
            lower == "constructor" -> "CONSTRUCTOR"
            lower == "module" -> "MODULE"
            lower == "package" -> "PACKAGE"
            lower == "label" -> "LABEL"
            lower.contains("method") -> "METHOD"
            lower.contains("function") -> "FUNCTION"
            lower.contains("class") -> "CLASS"
            lower.contains("interface") -> "INTERFACE"
            else -> ideType.uppercase().replace(" ", "_")
        }
    }

    /**
     * Fallback substring matcher for elements where neither a per-language
     * resolver nor the platform API provides a classification.
     */
    fun fallbackKindFromClassName(simpleName: String): String {
        val lower = simpleName.lowercase()
        return when {
            lower.contains("interface") -> "INTERFACE"
            lower.contains("trait") -> "TRAIT"
            lower.contains("annotation") -> "ANNOTATION"
            lower.contains("enum") -> "ENUM"
            lower.contains("record") -> "RECORD"
            lower.contains("structitem") -> "STRUCT"
            lower.contains("implitem") -> "IMPL"
            lower.contains("moditem") -> "MODULE"
            lower.contains("struct") -> "STRUCT"
            lower.contains("objectdeclaration") -> "OBJECT"
            lower.contains("method") -> "METHOD"
            lower.contains("function") -> "FUNCTION"
            lower.contains("field") -> "FIELD"
            lower.contains("variable") -> "VARIABLE"
            lower.contains("property") -> "PROPERTY"
            lower.contains("constant") -> "CONSTANT"
            lower.contains("class") -> "CLASS"
            else -> "SYMBOL"
        }
    }
}

private interface LanguageKindResolver {
    fun resolve(element: PsiElement): String?
}

/**
 * Java/Kotlin light classes: uses PsiClass API directly (compile-time dep).
 * Only handles PsiClass — methods/fields fall through to getType().
 */
private object JavaKindResolver : LanguageKindResolver {
    override fun resolve(element: PsiElement): String? {
        if (element !is PsiClass) return null
        return when {
            element.isInterface -> "INTERFACE"
            element.isEnum -> "ENUM"
            element.isAnnotationType -> "ANNOTATION"
            element.isRecord -> "RECORD"
            element.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }
}

/**
 * Kotlin: KtClass doesn't extend PsiClass, so we need reflection.
 * Only handles KtClass/KtObjectDeclaration — KtNamedFunction, KtProperty
 * etc. work fine via getType() ("function", "property").
 */
private object KotlinKindResolver : LanguageKindResolver {
    private val ktClassClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtClass") } catch (_: ClassNotFoundException) { null }
    }
    private val ktObjectDeclarationClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtObjectDeclaration") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolve(element: PsiElement): String? {
        val ktClass = ktClassClass
        if (ktClass != null && ktClass.isInstance(element)) {
            return try {
                val isInterface = ktClass.getMethod("isInterface").invoke(element) as? Boolean == true
                val isEnum = ktClass.getMethod("isEnum").invoke(element) as? Boolean == true
                val isSealed = ktClass.getMethod("isSealed").invoke(element) as? Boolean == true
                when {
                    isInterface -> "INTERFACE"
                    isEnum -> "ENUM"
                    isSealed -> "SEALED_CLASS"
                    else -> "CLASS"
                }
            } catch (_: Exception) { null }
        }
        val ktObject = ktObjectDeclarationClass
        if (ktObject != null && ktObject.isInstance(element)) {
            return "OBJECT"
        }
        return null
    }
}

/**
 * Go: GoTypeSpec wraps struct/interface but getType() only returns "interface"
 * or generic "type". We check the child type to distinguish struct.
 * Also handles GoMethodDeclaration/GoFunctionDeclaration for METHOD vs FUNCTION.
 */
private object GoKindResolver : LanguageKindResolver {
    private val goTypeSpecClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoTypeSpec") } catch (_: ClassNotFoundException) { null }
    }
    private val goStructTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoStructType") } catch (_: ClassNotFoundException) { null }
    }
    private val goInterfaceTypeClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoInterfaceType") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolve(element: PsiElement): String? {
        val typeSpec = goTypeSpecClass ?: return null
        if (!typeSpec.isInstance(element)) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val interfaceClass = goInterfaceTypeClass
            val structClass = goStructTypeClass
            when {
                interfaceClass != null && PsiTreeUtil.findChildOfType(
                    element, interfaceClass as Class<out PsiElement>
                ) != null -> "INTERFACE"
                structClass != null && PsiTreeUtil.findChildOfType(
                    element, structClass as Class<out PsiElement>
                ) != null -> "STRUCT"
                else -> null // let getType() handle type aliases etc.
            }
        } catch (_: Exception) { null }
    }
}

/**
 * PHP: PhpClass.getType() only checks isInterface(). We add trait/enum/abstract.
 */
private object PhpKindResolver : LanguageKindResolver {
    private val phpClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolve(element: PsiElement): String? {
        val phpClass = phpClassClass ?: return null
        if (!phpClass.isInstance(element)) return null
        return try {
            val isInterface = phpClass.getMethod("isInterface").invoke(element) as? Boolean == true
            val isTrait = phpClass.getMethod("isTrait").invoke(element) as? Boolean == true
            val isEnum = phpClass.getMethod("isEnum").invoke(element) as? Boolean == true
            val isAbstract = phpClass.getMethod("isAbstract").invoke(element) as? Boolean == true
            when {
                isInterface -> "INTERFACE"
                isTrait -> "TRAIT"
                isEnum -> "ENUM"
                isAbstract -> "ABSTRACT_CLASS"
                else -> "CLASS"
            }
        } catch (_: Exception) { null }
    }
}

/**
 * Rust: RsFindUsagesProvider.getType() returns empty string for all elements.
 * We check PSI class identity directly.
 */
private object RustKindResolver : LanguageKindResolver {
    private val rsStructItem: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsStructItem") } catch (_: ClassNotFoundException) { null }
    }
    private val rsTraitItem: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsTraitItem") } catch (_: ClassNotFoundException) { null }
    }
    private val rsEnumItem: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsEnumItem") } catch (_: ClassNotFoundException) { null }
    }
    private val rsFunction: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsFunction") } catch (_: ClassNotFoundException) { null }
    }
    private val rsImplItem: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsImplItem") } catch (_: ClassNotFoundException) { null }
    }
    private val rsModItem: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsModItem") } catch (_: ClassNotFoundException) { null }
    }
    private val rsConstant: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsConstant") } catch (_: ClassNotFoundException) { null }
    }
    private val rsTypeAlias: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsTypeAlias") } catch (_: ClassNotFoundException) { null }
    }
    private val rsEnumVariant: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsEnumVariant") } catch (_: ClassNotFoundException) { null }
    }
    private val rsNamedFieldDecl: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsNamedFieldDecl") } catch (_: ClassNotFoundException) { null }
    }
    private val rsMacro: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.RsMacro") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolve(element: PsiElement): String? {
        return when {
            rsStructItem?.isInstance(element) == true -> "STRUCT"
            rsTraitItem?.isInstance(element) == true -> "TRAIT"
            rsEnumItem?.isInstance(element) == true -> "ENUM"
            rsEnumVariant?.isInstance(element) == true -> "ENUM_VARIANT"
            rsFunction?.isInstance(element) == true -> resolveRustFunction(element)
            rsImplItem?.isInstance(element) == true -> "IMPL"
            rsModItem?.isInstance(element) == true -> "MODULE"
            rsConstant?.isInstance(element) == true -> "CONSTANT"
            rsTypeAlias?.isInstance(element) == true -> "TYPE_ALIAS"
            rsNamedFieldDecl?.isInstance(element) == true -> "FIELD"
            rsMacro?.isInstance(element) == true -> "MACRO"
            else -> null
        }
    }

    private fun resolveRustFunction(element: PsiElement): String {
        // Rust distinguishes methods (in impl/trait block) from free functions
        return try {
            val parent = element.parent
            if (rsImplItem?.isInstance(parent) == true || rsTraitItem?.isInstance(parent) == true) {
                "METHOD"
            } else {
                "FUNCTION"
            }
        } catch (_: Exception) { "FUNCTION" }
    }
}
