package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class LanguageService {

    abstract val languageIds: Set<String>
    abstract val displayName: String
    abstract fun isAvailable(): Boolean

    open val supportsSuperMethods: Boolean = false

    fun getKind(element: PsiElement): String {
        resolveKind(element)?.let { return it }
        val ideType = com.intellij.lang.findUsages.LanguageFindUsages.getType(element)
        if (ideType.isNotEmpty()) return normalizeKind(ideType)
        return fallbackKindFromClassName(element.javaClass.simpleName)
    }

    protected open fun resolveKind(element: PsiElement): String? = null

    open fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? = null

    protected fun resolveLanguageId(className: String, fieldName: String): String? {
        return try {
            val langClass = Class.forName(className)
            val lang = langClass.getDeclaredField(fieldName).get(null)
            lang.javaClass.getMethod("getID").invoke(lang) as String
        } catch (_: Exception) { null }
    }

    protected fun resolveLanguageIdViaGetInstance(className: String): String? {
        return try {
            val langClass = Class.forName(className)
            val lang = langClass.getMethod("getInstance").invoke(null)
            lang.javaClass.getMethod("getID").invoke(lang) as String
        } catch (_: Exception) { null }
    }

    companion object {
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
                lower.contains("interface") -> "INTERFACE"
                lower.contains("method") -> "METHOD"
                lower.contains("function") -> "FUNCTION"
                lower.contains("class") -> "CLASS"
                else -> ideType.uppercase().replace(" ", "_")
            }
        }

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
}
