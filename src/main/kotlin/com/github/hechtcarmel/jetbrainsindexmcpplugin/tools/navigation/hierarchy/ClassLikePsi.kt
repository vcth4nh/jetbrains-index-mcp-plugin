package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.intellij.psi.PsiElement

/**
 * Reflection-based introspection of "class-like" PSI elements across languages.
 *
 * The IDE Index MCP plugin runs in any JetBrains IDE (PyCharm, WebStorm, GoLand,
 * PhpStorm, RustRover, etc.) — not all of them ship the Java plugin. Direct
 * `is PsiClass` / `is PsiMethod` checks emit bytecode references that crash
 * with `NoClassDefFoundError` at first invocation when the Java plugin isn't
 * on the classpath. Use these helpers instead — class lookups are cached and
 * silently absent when a plugin isn't installed.
 *
 * Recognised "class-like" types (returns kind string + extracts qualifiedName):
 * - Java: PsiClass (with isInterface/isEnum/isRecord/isAnnotationType)
 * - Kotlin: KtClassOrObject
 * - Python: PyClass
 * - JavaScript/TypeScript: JSClass
 * - PHP: PhpClass
 * - Go: GoTypeSpec
 * - Rust: RsTraitItem, RsStructItem, RsEnumItem, RsImplItem
 *
 * Recognised "method-like" types:
 * - Java: PsiMethod
 * - Kotlin: KtNamedFunction, KtConstructor, KtProperty (accessors)
 * - Python: PyFunction
 * - JavaScript/TypeScript: JSFunction
 * - PHP: Function (interface, parent of Method)
 * - Go: GoFunctionOrMethodDeclaration (or GoNamedSignatureOwner)
 * - Rust: RsFunction
 */
internal object ClassLikePsi {

    private fun loadClass(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    // Class-like types
    private val PSI_CLASS by lazy { loadClass("com.intellij.psi.PsiClass") }
    private val KT_CLASS_OR_OBJECT by lazy { loadClass("org.jetbrains.kotlin.psi.KtClassOrObject") }
    private val PY_CLASS by lazy { loadClass("com.jetbrains.python.psi.PyClass") }
    private val JS_CLASS by lazy { loadClass("com.intellij.lang.javascript.psi.ecmal4.JSClass") }
    private val PHP_CLASS by lazy { loadClass("com.jetbrains.php.lang.psi.elements.PhpClass") }
    private val GO_TYPE_SPEC by lazy { loadClass("com.goide.psi.GoTypeSpec") }
    private val RS_TRAIT_ITEM by lazy { loadClass("org.rust.lang.core.psi.RsTraitItem") }
    private val RS_STRUCT_ITEM by lazy { loadClass("org.rust.lang.core.psi.RsStructItem") }
    private val RS_ENUM_ITEM by lazy { loadClass("org.rust.lang.core.psi.RsEnumItem") }
    private val RS_IMPL_ITEM by lazy { loadClass("org.rust.lang.core.psi.RsImplItem") }

    private val classLikeTypes: List<Class<*>> by lazy {
        listOfNotNull(
            PSI_CLASS, KT_CLASS_OR_OBJECT, PY_CLASS, JS_CLASS, PHP_CLASS, GO_TYPE_SPEC,
            RS_TRAIT_ITEM, RS_STRUCT_ITEM, RS_ENUM_ITEM, RS_IMPL_ITEM
        )
    }

    fun isClassLike(element: PsiElement): Boolean =
        classLikeTypes.any { it.isInstance(element) }

    /**
     * Walks up the PSI tree from [element] looking for the smallest enclosing
     * class-like ancestor. Returns null if none found.
     */
    fun walkUpToClassLike(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (isClassLike(current)) return current
            current = current.parent
        }
        return null
    }

    /**
     * Returns a kind string for a class-like element (UPPERCASE per legacy
     * convention) or `"class"` when the element isn't class-like and we can't
     * tell — preserves wire-format compatibility.
     */
    fun describeKind(element: PsiElement): String {
        // Java PsiClass — disambiguate via reflective method invocation.
        PSI_CLASS?.takeIf { it.isInstance(element) }?.let {
            val cls = element
            val isInterface = invokeBoolean(cls, "isInterface")
            val isEnum = invokeBoolean(cls, "isEnum")
            val isRecord = invokeBoolean(cls, "isRecord")
            val isAnnotationType = invokeBoolean(cls, "isAnnotationType")
            val isAbstract = element.javaClass.simpleName.let { _ ->
                runCatching {
                    val m = cls.javaClass.getMethod("hasModifierProperty", String::class.java)
                    m.invoke(cls, "abstract") as Boolean
                }.getOrDefault(false)
            }
            return when {
                isAnnotationType -> "ANNOTATION"
                isInterface -> "INTERFACE"
                isEnum -> "ENUM"
                isRecord -> "RECORD"
                isAbstract -> "ABSTRACT_CLASS"
                else -> "CLASS"
            }
        }
        // Kotlin
        if (KT_CLASS_OR_OBJECT?.isInstance(element) == true) return kotlinKind(element)
        // Python
        if (PY_CLASS?.isInstance(element) == true) return "CLASS"
        // JS/TS — JSClass also implements PsiClass on some platforms; we already returned above.
        if (JS_CLASS?.isInstance(element) == true) return jsKind(element)
        // PHP — PhpClass implements PsiClass on some platforms; already returned above.
        if (PHP_CLASS?.isInstance(element) == true) return phpKind(element)
        // Go
        if (GO_TYPE_SPEC?.isInstance(element) == true) return goKind(element)
        // Rust
        if (RS_TRAIT_ITEM?.isInstance(element) == true) return "TRAIT"
        if (RS_STRUCT_ITEM?.isInstance(element) == true) return "STRUCT"
        if (RS_ENUM_ITEM?.isInstance(element) == true) return "ENUM"
        if (RS_IMPL_ITEM?.isInstance(element) == true) return "IMPL"
        return "CLASS"
    }

    /**
     * Returns the qualifiedName for a class-like element, or null if not extractable.
     * Delegates to [com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil]
     * which knows per-language conventions (Rust `crate::Foo`, Go `pkg.Foo`,
     * PHP `\Ns\Foo`, Java FQN, Kotlin FqName via the platform's QualifiedNameProvider EP).
     */
    fun describeQualifiedName(element: PsiElement): String? =
        com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil.getQualifiedName(element)

    private fun kotlinKind(element: PsiElement): String {
        // KtClass.isInterface, isEnum, etc. — but KtObjectDeclaration is "OBJECT".
        val name = element.javaClass.simpleName
        return when {
            invokeBoolean(element, "isAnnotation") -> "ANNOTATION"
            invokeBoolean(element, "isInterface") -> "INTERFACE"
            invokeBoolean(element, "isEnum") -> "ENUM"
            invokeBoolean(element, "isData") -> "DATA_CLASS"
            invokeBoolean(element, "isSealed") -> "SEALED_CLASS"
            name == "KtObjectDeclaration" -> "OBJECT"
            else -> "CLASS"
        }
    }

    private fun jsKind(element: PsiElement): String =
        if (invokeBoolean(element, "isInterface")) "INTERFACE" else "CLASS"

    private fun phpKind(element: PsiElement): String = when {
        invokeBoolean(element, "isInterface") -> "INTERFACE"
        invokeBoolean(element, "isTrait") -> "TRAIT"
        invokeBoolean(element, "isEnum") -> "ENUM"
        invokeBoolean(element, "isAbstract") -> "ABSTRACT_CLASS"
        else -> "CLASS"
    }

    private fun goKind(element: PsiElement): String {
        // Go: GoTypeSpec wraps a struct or interface or other type.
        val type = runCatching { element.javaClass.getMethod("getType").invoke(element) as? PsiElement }.getOrNull()
        val typeName = type?.javaClass?.simpleName ?: ""
        return when {
            "Interface" in typeName -> "INTERFACE"
            "Struct" in typeName -> "STRUCT"
            else -> "TYPE"
        }
    }

    private fun invokeBoolean(target: Any, method: String): Boolean = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Boolean ?: false
    }.getOrDefault(false)

    // -------------------- method-like helpers --------------------

    private val PSI_METHOD by lazy { loadClass("com.intellij.psi.PsiMethod") }
    private val KT_NAMED_FUNCTION by lazy { loadClass("org.jetbrains.kotlin.psi.KtNamedFunction") }
    private val KT_CONSTRUCTOR by lazy { loadClass("org.jetbrains.kotlin.psi.KtConstructor") }
    private val PY_FUNCTION by lazy { loadClass("com.jetbrains.python.psi.PyFunction") }
    private val JS_FUNCTION by lazy { loadClass("com.intellij.lang.javascript.psi.JSFunction") }
    private val PHP_FUNCTION by lazy { loadClass("com.jetbrains.php.lang.psi.elements.Function") }
    private val GO_NAMED_SIGNATURE_OWNER by lazy { loadClass("com.goide.psi.GoNamedSignatureOwner") }
    private val RS_FUNCTION by lazy { loadClass("org.rust.lang.core.psi.RsFunction") }

    private val methodLikeTypes: List<Class<*>> by lazy {
        listOfNotNull(
            PSI_METHOD, KT_NAMED_FUNCTION, KT_CONSTRUCTOR, PY_FUNCTION,
            JS_FUNCTION, PHP_FUNCTION, GO_NAMED_SIGNATURE_OWNER, RS_FUNCTION
        )
    }

    fun isMethodLike(element: PsiElement): Boolean =
        methodLikeTypes.any { it.isInstance(element) }

    fun walkUpToMethodLike(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (isMethodLike(current)) return current
            current = current.parent
        }
        return null
    }

    /**
     * For a method-like element, returns `ClassName.methodName(paramTypes)`
     * (legacy wire format). Falls back to the bare element name if class
     * info isn't available.
     */
    fun describeMethodName(element: PsiElement): String? {
        if (!isMethodLike(element)) return null
        val name = (element as? com.intellij.psi.PsiNamedElement)?.name ?: return null
        // Try to find containing class-like ancestor. Prefer the FQN of the class
        // to match legacy wire format (e.g. `demo.Circle.method(...)` vs `Circle.method(...)`).
        val cls = walkUpToClassLike(element.parent ?: element)
        val clsName = cls?.let { describeQualifiedName(it) } ?: (cls as? com.intellij.psi.PsiNamedElement)?.name
        val params = runCatching {
            // PsiMethod-style: getParameterList().getParameters() each has getType().getPresentableText()
            val list = element.javaClass.getMethod("getParameterList").invoke(element)
            val params = list?.javaClass?.getMethod("getParameters")?.invoke(list) as? Array<*>
            params?.joinToString(", ") { p ->
                runCatching {
                    val type = p?.javaClass?.getMethod("getType")?.invoke(p)
                    type?.javaClass?.getMethod("getPresentableText")?.invoke(type) as? String ?: "?"
                }.getOrDefault("?")
            }
        }.getOrNull()
        return buildString {
            if (clsName != null) append(clsName).append(".")
            append(name)
            append("(")
            if (params != null) append(params)
            append(")")
        }
    }

    /**
     * For a method-like element, returns `qualifiedClassName#methodName` (legacy
     * shape). Returns null if no qualified class can be derived.
     */
    fun describeMethodQualifiedName(element: PsiElement): String? {
        if (!isMethodLike(element)) return null
        val cls = walkUpToClassLike(element.parent ?: element) ?: return null
        val clsFqn = describeQualifiedName(cls) ?: return null
        val name = (element as? com.intellij.psi.PsiNamedElement)?.name ?: return null
        return "$clsFqn#$name"
    }
}
