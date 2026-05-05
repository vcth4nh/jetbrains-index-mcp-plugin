package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * JS/TS decorator. Reflective on `com.intellij.lang.javascript.psi.*`.
 *
 *  - `JSClass`        → `class <mods> <name>[ extends X][ implements Y]`
 *  - `JSFunction`     → `method <mods> <name> (params)` (or `function` at top level)
 *  - `JSField`        → `field <mods> <name>`
 *  - `JSVariable`     → `variable <name>`
 *  - TS interfaces / type aliases / enums via JSClass with `isInterface()` / class-name probe.
 */
internal object JavaScriptNodeDecorator : NodeDecorator {

    private val jsClass: Class<*>? by lazy { load("com.intellij.lang.javascript.psi.JSClass") }
    private val jsFunction: Class<*>? by lazy { load("com.intellij.lang.javascript.psi.JSFunction") }
    private val jsField: Class<*>? by lazy { load("com.intellij.lang.javascript.psi.JSField") }
    private val jsVariable: Class<*>? by lazy { load("com.intellij.lang.javascript.psi.JSVariable") }
    private val typeScriptInterface: Class<*>? by lazy { load("com.intellij.lang.javascript.psi.ecmal4.JSClass") }
    private val typeScriptTypeAlias: Class<*>? by lazy { load("com.intellij.lang.typescript.psi.TypeScriptTypeAlias") }
    private val typeScriptEnum: Class<*>? by lazy { load("com.intellij.lang.typescript.psi.TypeScriptEnum") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            typeScriptTypeAlias?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "type")
            typeScriptEnum?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "enum")
            jsClass?.isInstance(value) == true -> decorateClass(value)
            jsFunction?.isInstance(value) == true -> decorateFunction(value)
            jsField?.isInstance(value) == true -> decorateField(value)
            jsVariable?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "variable")
            else -> null
        }
    }

    private fun decorateClass(cls: Any): DecoratedNode {
        val name = invokeString(cls, "getName") ?: "<anonymous>"
        val isInterface = runCatching {
            // Some JSClass impls expose this; falls back to false otherwise.
            cls.javaClass.getMethod("isInterface").invoke(cls) as? Boolean
        }.getOrNull() ?: false
        val mods = jsModifiers(cls)
        val signatureParts = mutableListOf<String>()
        collectClassReferenceTexts(cls, "getExtendsList").takeIf { it.isNotEmpty() }
            ?.let { signatureParts.add("extends ${it.joinToString(", ")}") }
        collectClassReferenceTexts(cls, "getImplementsList").takeIf { it.isNotEmpty() }
            ?.let { signatureParts.add("implements ${it.joinToString(", ")}") }
        return DecoratedNode(
            name = name,
            kind = if (isInterface) "interface" else "class",
            modifiers = mods,
            signature = if (signatureParts.isEmpty()) null else signatureParts.joinToString(" "),
        )
    }

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        val mods = jsModifiers(fn)
        // Heuristic: if the function's parent (resolved via getContext or getParent) is a JSClass, it's a method.
        val isMethod = runCatching {
            val parent = fn.javaClass.getMethod("getContext").invoke(fn) ?: fn.javaClass.getMethod("getParent").invoke(fn)
            parent != null && jsClass?.isInstance(parent) == true
        }.getOrDefault(false)
        val params = runCatching {
            val list = fn.javaClass.getMethod("getParameterList").invoke(fn) ?: return@runCatching ""
            val params = list.javaClass.getMethod("getParameters").invoke(list) as? Array<*> ?: return@runCatching ""
            params.mapNotNull { it?.let { p -> invokeString(p, "getText") } }.joinToString(", ")
        }.getOrDefault("")
        return DecoratedNode(
            name = name,
            kind = if (isMethod) "method" else "function",
            modifiers = mods,
            signature = "($params)",
        )
    }

    private fun decorateField(field: Any): DecoratedNode {
        val name = invokeString(field, "getName") ?: "<anonymous>"
        val mods = jsModifiers(field)
        return DecoratedNode(name = name, kind = "field", modifiers = mods)
    }

    /**
     * Pulls explicit access keywords out of `JSAttributeListOwner`'s attribute list, when present.
     * Falls back to empty when reflection misses (modifier-less elements).
     */
    private fun jsModifiers(decl: Any): List<String> = runCatching {
        val list = decl.javaClass.getMethod("getAttributeList").invoke(decl) ?: return emptyList()
        val text = list.javaClass.getMethod("getText").invoke(list) as? String ?: return emptyList()
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("@") }
    }.getOrDefault(emptyList())

    private fun collectClassReferenceTexts(cls: Any, getter: String): List<String> = runCatching {
        val list = cls.javaClass.getMethod(getter).invoke(cls) ?: return emptyList()
        val refs = list.javaClass.getMethod("getMembers").invoke(list) as? Array<*>
            ?: list.javaClass.getMethod("getReferencedClassesElements").invoke(list) as? Array<*>
            ?: return emptyList()
        refs.mapNotNull { r -> r?.let { invokeString(it, "getText") } }
    }.getOrDefault(emptyList())

    private fun load(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    private fun invokeString(target: Any, method: String): String? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
