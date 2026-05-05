package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiField
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList

internal object JavaNodeDecorator : NodeDecorator {
    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? = when (value) {
        is PsiClass -> decorateClass(value)
        is PsiMethod -> decorateMethod(value)
        is PsiField -> decorateField(value)
        is PsiClassInitializer -> DecoratedNode(name = "initializer", kind = "class initializer", modifiers = explicitModifiers(value.modifierList))
        else -> null
    }

    private fun decorateClass(cls: PsiClass): DecoratedNode {
        val kind = when {
            cls.isAnnotationType -> "@interface"
            cls.isInterface -> "interface"
            cls.isEnum -> "enum"
            cls.isRecord -> "record"
            else -> "class"
        }
        val name = cls.name ?: if (cls is PsiAnonymousClass) "Anonymous" else "<anonymous>"
        val modifiers = explicitModifiers(cls.modifierList)
        val signature = buildClassSignature(cls)
        return DecoratedNode(name = name, kind = kind, modifiers = modifiers, signature = signature)
    }

    private fun decorateMethod(method: PsiMethod): DecoratedNode {
        val kind = if (method.isConstructor) "constructor" else "method"
        val modifiers = explicitModifiers(method.modifierList)
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }
        val signature = if (method.isConstructor) {
            "($params)"
        } else {
            val ret = method.returnType?.presentableText ?: "void"
            "$ret ($params)"
        }
        return DecoratedNode(name = method.name, kind = kind, modifiers = modifiers, signature = signature)
    }

    private fun decorateField(field: PsiField): DecoratedNode {
        val modifiers = explicitModifiers(field.modifierList)
        return DecoratedNode(
            name = field.name,
            kind = "field",
            modifiers = modifiers,
            signature = field.type.presentableText,
        )
    }

    private fun buildClassSignature(cls: PsiClass): String? {
        val parts = mutableListOf<String>()
        cls.extendsList?.referenceElements?.takeIf { it.isNotEmpty() }?.let { refs ->
            parts.add("extends ${refs.joinToString(", ") { it.text }}")
        }
        cls.implementsList?.referenceElements?.takeIf { it.isNotEmpty() }?.let { refs ->
            parts.add("implements ${refs.joinToString(", ") { it.text }}")
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun explicitModifiers(list: PsiModifierList?): List<String> {
        if (list == null) return emptyList()
        return list.children.filterIsInstance<PsiKeyword>().map { it.text }
    }
}
