package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * Python decorator. Reflective so the plugin still loads in IDEs without the Python plugin.
 *
 * Supported PSI types (matched by class name):
 *  - `com.jetbrains.python.psi.PyClass`        → `class <name>[ (Parent, ...)]`
 *  - `com.jetbrains.python.psi.PyFunction`     → `def <name> (param, ...)`
 *  - `com.jetbrains.python.psi.PyTargetExpression` → instance attributes (`self.x = ...`)
 */
internal object PythonNodeDecorator : NodeDecorator {

    private val pyClassClass: Class<*>? by lazy { tryLoad("com.jetbrains.python.psi.PyClass") }
    private val pyFunctionClass: Class<*>? by lazy { tryLoad("com.jetbrains.python.psi.PyFunction") }
    private val pyTargetClass: Class<*>? by lazy { tryLoad("com.jetbrains.python.psi.PyTargetExpression") }

    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        if (value == null) return null
        return when {
            pyClassClass?.isInstance(value) == true -> decorateClass(value)
            pyFunctionClass?.isInstance(value) == true -> decorateFunction(value)
            pyTargetClass?.isInstance(value) == true -> decorateTarget(value)
            else -> null
        }
    }

    private fun decorateClass(cls: Any): DecoratedNode {
        val name = invokeString(cls, "getName") ?: "<anonymous>"
        val signature = runCatching {
            // PyClass.getSuperClassExpressions(): PyExpression[]
            val supers = cls.javaClass.getMethod("getSuperClassExpressions")
                .invoke(cls) as? Array<*> ?: return@runCatching null
            val names = supers.mapNotNull { it?.let { e -> invokeString(e, "getText") }?.takeIf { it.isNotBlank() } }
            if (names.isEmpty()) null else "(${names.joinToString(", ")})"
        }.getOrNull()
        return DecoratedNode(name = name, kind = "class", signature = signature)
    }

    private fun decorateFunction(fn: Any): DecoratedNode {
        val name = invokeString(fn, "getName") ?: "<anonymous>"
        val params = runCatching {
            val list = fn.javaClass.getMethod("getParameterList").invoke(fn) ?: return@runCatching ""
            val params = list.javaClass.getMethod("getParameters").invoke(list) as? Array<*> ?: return@runCatching ""
            params.mapNotNull { it?.let { p -> invokeString(p, "getText") } }.joinToString(", ")
        }.getOrDefault("")
        return DecoratedNode(name = name, kind = "def", signature = "($params)")
    }

    private fun decorateTarget(target: Any): DecoratedNode {
        val name = invokeString(target, "getName") ?: "<anonymous>"
        return DecoratedNode(name = name, kind = "field")
    }

    private fun tryLoad(fqn: String): Class<*>? = runCatching { Class.forName(fqn) }.getOrNull()

    private fun invokeString(target: Any, method: String): String? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
