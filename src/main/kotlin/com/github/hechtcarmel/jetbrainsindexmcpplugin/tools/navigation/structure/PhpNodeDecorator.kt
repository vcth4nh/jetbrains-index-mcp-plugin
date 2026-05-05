package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * PHP decorator. Reflective on `com.jetbrains.php.lang.psi.elements.*`.
 *
 *  - `PhpClass` → `class|interface|trait|enum <mods> <name>[ extends X][ implements Y]`
 *  - `Method`   → `method <mods> <name> (params)` (or `constructor`)
 *  - `Field`    → `field <mods> <name>` (with optional `static`)
 *  - `ClassConstant` → `constant <name>`
 *  - `Function` (top-level) → `function <name> (params)`
 */
internal object PhpNodeDecorator : NodeDecorator {

    private val phpClass: Class<*>? by lazy { load("com.jetbrains.php.lang.psi.elements.PhpClass") }
    private val phpMethod: Class<*>? by lazy { load("com.jetbrains.php.lang.psi.elements.Method") }
    private val phpField: Class<*>? by lazy { load("com.jetbrains.php.lang.psi.elements.Field") }
    private val phpClassConst: Class<*>? by lazy { load("com.jetbrains.php.lang.psi.elements.ClassConstant") }
    private val phpFunction: Class<*>? by lazy { load("com.jetbrains.php.lang.psi.elements.Function") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            phpClass?.isInstance(value) == true -> decorateClass(value)
            phpMethod?.isInstance(value) == true -> decorateMethod(value)
            phpField?.isInstance(value) == true -> decorateField(value)
            phpClassConst?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "constant")
            phpFunction?.isInstance(value) == true -> decorateFunction(value)
            else -> null
        }
    }

    private fun decorateClass(cls: Any): DecoratedNode {
        val name = invokeString(cls, "getName") ?: "<anonymous>"
        val kind = when {
            invokeBool(cls, "isEnum") == true -> "enum"
            invokeBool(cls, "isInterface") == true -> "interface"
            invokeBool(cls, "isTrait") == true -> "trait"
            invokeBool(cls, "isAbstract") == true -> "abstract class"
            invokeBool(cls, "isFinal") == true -> "final class"
            else -> "class"
        }
        val signatureParts = mutableListOf<String>()
        runCatching {
            val supers = cls.javaClass.getMethod("getSuperFQN").invoke(cls) as? String
            if (!supers.isNullOrBlank()) signatureParts.add("extends $supers")
        }
        runCatching {
            val impls = cls.javaClass.getMethod("getInterfaceNames").invoke(cls) as? Array<*>
            if (impls != null && impls.isNotEmpty()) {
                signatureParts.add("implements ${impls.filterNotNull().joinToString(", ")}")
            }
        }
        return DecoratedNode(
            name = name,
            kind = kind,
            signature = if (signatureParts.isEmpty()) null else signatureParts.joinToString(" "),
        )
    }

    private fun decorateMethod(method: Any): DecoratedNode {
        val name = invokeString(method, "getName") ?: "<anonymous>"
        val isCtor = name == "__construct"
        val params = collectParams(method)
        val mods = phpAccessModifiers(method)
        return DecoratedNode(
            name = name,
            kind = if (isCtor) "constructor" else "method",
            modifiers = mods,
            signature = "($params)",
        )
    }

    private fun decorateField(field: Any): DecoratedNode {
        val name = invokeString(field, "getName") ?: "<anonymous>"
        val mods = phpAccessModifiers(field)
        return DecoratedNode(name = name, kind = "field", modifiers = mods)
    }

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        val params = collectParams(fn)
        return DecoratedNode(name = name, kind = "function", signature = "($params)")
    }

    private fun phpAccessModifiers(member: Any): List<String> {
        val mods = mutableListOf<String>()
        val access = runCatching {
            member.javaClass.getMethod("getAccess").invoke(member)?.toString()?.lowercase()
        }.getOrNull()
        if (!access.isNullOrBlank()) mods.add(access)
        if (invokeBool(member, "isStatic") == true) mods.add("static")
        if (invokeBool(member, "isAbstract") == true) mods.add("abstract")
        if (invokeBool(member, "isFinal") == true) mods.add("final")
        return mods
    }

    private fun collectParams(fn: Any): String = runCatching {
        val params = fn.javaClass.getMethod("getParameters").invoke(fn) as? Array<*> ?: return@runCatching ""
        params.mapNotNull { p -> p?.let { invokeString(it, "getText") } }.joinToString(", ")
    }.getOrDefault("")

    private fun load(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    private fun invokeString(target: Any, method: String): String? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun invokeBool(target: Any, method: String): Boolean? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Boolean
    }.getOrNull()
}
