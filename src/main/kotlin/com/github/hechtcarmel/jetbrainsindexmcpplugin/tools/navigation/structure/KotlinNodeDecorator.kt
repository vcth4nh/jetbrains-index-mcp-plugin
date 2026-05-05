package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement

/**
 * Kotlin decorator. Reflective on `org.jetbrains.kotlin.psi.*`.
 *
 * Recognized types:
 *  - `KtClass`          → `class <mods> <name>[ : Super, ...]` / `interface` / `enum class`
 *  - `KtObjectDeclaration` → `object <name>` (or `companion object`)
 *  - `KtNamedFunction`  → `fun <mods> <name> (params)`
 *  - `KtProperty`       → `val <name> : <type>` (or `var`)
 *  - `KtClassInitializer` → `init`
 *  - `KtTypeAlias`      → `typealias <name>`
 */
internal object KotlinNodeDecorator : NodeDecorator {

    private val ktClass: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtClass") }
    private val ktObject: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtObjectDeclaration") }
    private val ktNamedFunction: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtNamedFunction") }
    private val ktProperty: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtProperty") }
    private val ktClassInitializer: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtClassInitializer") }
    private val ktTypeAlias: Class<*>? by lazy { load("org.jetbrains.kotlin.psi.KtTypeAlias") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            ktClass?.isInstance(value) == true -> decorateClass(value)
            ktObject?.isInstance(value) == true -> decorateObject(value)
            ktNamedFunction?.isInstance(value) == true -> decorateFunction(value)
            ktProperty?.isInstance(value) == true -> decorateProperty(value)
            ktClassInitializer?.isInstance(value) == true ->
                DecoratedNode(name = "init", kind = "class initializer")
            ktTypeAlias?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "typealias")
            else -> null
        }
    }

    private fun decorateClass(cls: Any): DecoratedNode {
        val name = invokeString(cls, "getName") ?: "<anonymous>"
        val isInterface = invokeBool(cls, "isInterface") ?: false
        val isEnum = invokeBool(cls, "isEnum") ?: false
        val isAnnotation = invokeBool(cls, "isAnnotation") ?: false
        val isData = invokeBool(cls, "isData") ?: false
        val isSealed = invokeBool(cls, "isSealed") ?: false
        val kind = when {
            isInterface -> "interface"
            isEnum -> "enum"
            isAnnotation -> "annotation"
            isData -> "data class"
            isSealed -> "sealed class"
            else -> "class"
        }
        val mods = explicitModifiers(cls).filterNot { it in setOf("interface", "enum", "annotation", "data", "sealed") }
        val supers = collectSuperTypeText(cls)
        val signature = if (supers.isEmpty()) null else ": ${supers.joinToString(", ")}"
        return DecoratedNode(name = name, kind = kind, modifiers = mods, signature = signature)
    }

    private fun decorateObject(obj: Any): DecoratedNode {
        val name = invokeString(obj, "getName") ?: "<anonymous>"
        val isCompanion = invokeBool(obj, "isCompanion") ?: false
        val mods = explicitModifiers(obj).filterNot { it == "companion" }
        val kind = if (isCompanion) "companion object" else "object"
        return DecoratedNode(name = name, kind = kind, modifiers = mods)
    }

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        val mods = explicitModifiers(fn)
        val params = collectValueParameters(fn)
        return DecoratedNode(name = name, kind = "fun", modifiers = mods, signature = "($params)")
    }

    private fun decorateProperty(prop: Any): DecoratedNode {
        val name = invokeString(prop, "getName") ?: "<anonymous>"
        val isVar = invokeBool(prop, "isVar") ?: false
        val mods = explicitModifiers(prop)
        val type = runCatching {
            val ref = prop.javaClass.getMethod("getTypeReference").invoke(prop) as? PsiElement
            ref?.text
        }.getOrNull()
        val signature = type?.takeIf { it.isNotBlank() }?.let { ": $it" }
        return DecoratedNode(
            name = name,
            kind = if (isVar) "var" else "val",
            modifiers = mods,
            signature = signature,
        )
    }

    private fun explicitModifiers(decl: Any): List<String> = runCatching {
        val list = decl.javaClass.getMethod("getModifierList").invoke(decl) ?: return emptyList()
        val text = list.javaClass.getMethod("getText").invoke(list) as? String ?: return emptyList()
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("@") }
    }.getOrDefault(emptyList())

    private fun collectSuperTypeText(cls: Any): List<String> = runCatching {
        val entries = cls.javaClass.getMethod("getSuperTypeListEntries").invoke(cls) as? List<*> ?: return emptyList()
        entries.mapNotNull { entry ->
            entry?.let { invokeString(it, "getText") }
        }
    }.getOrDefault(emptyList())

    private fun collectValueParameters(fn: Any): String = runCatching {
        val list = fn.javaClass.getMethod("getValueParameterList").invoke(fn) ?: return ""
        val params = list.javaClass.getMethod("getParameters").invoke(list) as? List<*> ?: return ""
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
