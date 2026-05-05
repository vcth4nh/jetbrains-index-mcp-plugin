package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * Rust decorator. Reflective on `org.rust.lang.core.psi.*`.
 *
 *  - `RsFunction`    → `fn <name> (params)` (or `method` inside impl)
 *  - `RsStructItem`  → `struct <name>`
 *  - `RsEnumItem`    → `enum <name>`
 *  - `RsTraitItem`   → `trait <name>`
 *  - `RsImplItem`    → `impl <signature>`
 *  - `RsConstant`    → `const|static <name>`
 *  - `RsTypeAlias`   → `type <name>`
 *  - `RsModItem`     → `mod <name>`
 *  - `RsNamedFieldDecl` → `field <name>`
 */
internal object RustNodeDecorator : NodeDecorator {

    private val rsFunction: Class<*>? by lazy { load("org.rust.lang.core.psi.RsFunction") }
    private val rsStructItem: Class<*>? by lazy { load("org.rust.lang.core.psi.RsStructItem") }
    private val rsEnumItem: Class<*>? by lazy { load("org.rust.lang.core.psi.RsEnumItem") }
    private val rsTraitItem: Class<*>? by lazy { load("org.rust.lang.core.psi.RsTraitItem") }
    private val rsImplItem: Class<*>? by lazy { load("org.rust.lang.core.psi.RsImplItem") }
    private val rsConstant: Class<*>? by lazy { load("org.rust.lang.core.psi.RsConstant") }
    private val rsTypeAlias: Class<*>? by lazy { load("org.rust.lang.core.psi.RsTypeAlias") }
    private val rsModItem: Class<*>? by lazy { load("org.rust.lang.core.psi.RsModItem") }
    private val rsNamedFieldDecl: Class<*>? by lazy { load("org.rust.lang.core.psi.RsNamedFieldDecl") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            rsFunction?.isInstance(value) == true -> decorateFunction(value)
            rsStructItem?.isInstance(value) == true -> simple(value, "struct")
            rsEnumItem?.isInstance(value) == true -> simple(value, "enum")
            rsTraitItem?.isInstance(value) == true -> simple(value, "trait")
            rsImplItem?.isInstance(value) == true -> decorateImpl(value)
            rsConstant?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "const", modifiers = rsModifiers(value))
            rsTypeAlias?.isInstance(value) == true -> simple(value, "type")
            rsModItem?.isInstance(value) == true -> simple(value, "mod")
            rsNamedFieldDecl?.isInstance(value) == true -> simple(value, "field")
            else -> null
        }
    }

    private fun simple(elem: Any, kind: String): DecoratedNode = DecoratedNode(
        name = invokeString(elem, "getName") ?: "<anonymous>",
        kind = kind,
        modifiers = rsModifiers(elem),
    )

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        // Heuristic: a function inside an `impl` block is a method.
        val parent = runCatching { fn.javaClass.getMethod("getParent").invoke(fn) }.getOrNull()
        val isMethod = parent != null && rsImplItem?.isInstance(parent) == true
        val params = runCatching {
            val list = fn.javaClass.getMethod("getValueParameterList").invoke(fn) ?: return@runCatching ""
            invokeString(list, "getText") ?: ""
        }.getOrDefault("")
        return DecoratedNode(
            name = name,
            kind = if (isMethod) "method" else "fn",
            modifiers = rsModifiers(fn),
            signature = if (params.isBlank()) null else params,
        )
    }

    private fun decorateImpl(impl: Any): DecoratedNode {
        // RsImplItem.text usually starts with `impl Trait for Type` or `impl Type`.
        // Strip the leading "impl " keyword and use the rest as the name.
        val text = invokeString(impl, "getText")?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val name = text.removePrefix("impl ").substringBefore("{").trim().ifEmpty { "<impl>" }
        return DecoratedNode(name = name, kind = "impl")
    }

    private fun rsModifiers(elem: Any): List<String> = runCatching {
        // RsOuterAttributeOwner / RsVisOwner: getVis() returns the optional "pub" modifier.
        val vis = elem.javaClass.getMethod("getVis").invoke(elem) ?: return emptyList()
        val text = invokeString(vis, "getText") ?: return emptyList()
        listOf(text)
    }.getOrDefault(emptyList())

    private fun load(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    private fun invokeString(target: Any, method: String): String? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
