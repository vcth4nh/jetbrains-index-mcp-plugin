package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * Go decorator. Reflective on `com.goide.psi.*`.
 *
 *  - `GoFunctionDeclaration` → `func <name> (params)`
 *  - `GoMethodDeclaration`   → `method <name> (params)` (with receiver in signature)
 *  - `GoTypeSpec`            → `struct <name>` / `interface <name>` / `type <name>`
 *  - `GoVarSpec` / `GoConstSpec` → `var` / `const`
 *  - `GoFieldDeclaration`    → `field <name>`
 */
internal object GoNodeDecorator : NodeDecorator {

    private val goFunctionDecl: Class<*>? by lazy { load("com.goide.psi.GoFunctionDeclaration") }
    private val goMethodDecl: Class<*>? by lazy { load("com.goide.psi.GoMethodDeclaration") }
    private val goTypeSpec: Class<*>? by lazy { load("com.goide.psi.GoTypeSpec") }
    private val goVarSpec: Class<*>? by lazy { load("com.goide.psi.GoVarSpec") }
    private val goConstSpec: Class<*>? by lazy { load("com.goide.psi.GoConstSpec") }
    private val goFieldDecl: Class<*>? by lazy { load("com.goide.psi.GoFieldDeclaration") }
    private val goStructType: Class<*>? by lazy { load("com.goide.psi.GoStructType") }
    private val goInterfaceType: Class<*>? by lazy { load("com.goide.psi.GoInterfaceType") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            goMethodDecl?.isInstance(value) == true -> decorateMethod(value)
            goFunctionDecl?.isInstance(value) == true -> decorateFunction(value)
            goTypeSpec?.isInstance(value) == true -> decorateType(value)
            goVarSpec?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "var")
            goConstSpec?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "const")
            goFieldDecl?.isInstance(value) == true ->
                DecoratedNode(name = invokeString(value, "getName") ?: "<anonymous>", kind = "field")
            else -> null
        }
    }

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        return DecoratedNode(name = name, kind = "func", signature = goSignatureText(fn))
    }

    private fun decorateMethod(method: Any): DecoratedNode {
        val name = invokeString(method, "getName") ?: "<anonymous>"
        val recv = runCatching {
            method.javaClass.getMethod("getReceiver").invoke(method)?.let { invokeString(it, "getText") }
        }.getOrNull()
        val sig = goSignatureText(method)
        val signature = if (recv.isNullOrBlank()) sig else "$recv $sig"
        return DecoratedNode(name = name, kind = "method", signature = signature)
    }

    private fun goSignatureText(fn: Any): String? = runCatching {
        val sig = fn.javaClass.getMethod("getSignature").invoke(fn) ?: return@runCatching null
        invokeString(sig, "getText")
    }.getOrNull()

    private fun decorateType(spec: Any): DecoratedNode {
        val name = invokeString(spec, "getName") ?: "<anonymous>"
        val specType = runCatching { spec.javaClass.getMethod("getSpecType").invoke(spec) }.getOrNull()
        val inner = runCatching {
            specType?.javaClass?.getMethod("getType")?.invoke(specType)
        }.getOrNull()
        val kind = when {
            inner != null && goStructType?.isInstance(inner) == true -> "struct"
            inner != null && goInterfaceType?.isInstance(inner) == true -> "interface"
            else -> "type"
        }
        return DecoratedNode(name = name, kind = kind)
    }

    private fun load(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    private fun invokeString(target: Any, method: String): String? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
