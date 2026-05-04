package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope

/**
 * Converts an IDE [NavigationItem] (returned from [PopupFaithfulSymbolSearch]) into our
 * wire-format [SymbolData]. Returns `null` when the item cannot be converted or is filtered out:
 *  - PsiElement cannot be extracted from the NavigationItem
 *  - The element's containing file is outside [scope]
 *  - The element's language doesn't match [languageFilter] (case-insensitive)
 *  - The element has no extractable name
 *
 * Pure code motion from `OptimizedSymbolSearch.convertToSymbolData()` and its private helpers.
 */
internal object SymbolDataConverter {

    fun convert(
        item: NavigationItem,
        project: Project,
        scope: GlobalSearchScope,
        languageFilter: Set<String>? = null
    ): SymbolData? {
        val element = when (item) {
            is PsiElement -> item
            else -> {
                // Try to extract PsiElement from NavigationItem
                try {
                    val method = item.javaClass.getMethod("getElement")
                    method.invoke(item) as? PsiElement
                } catch (e: Exception) {
                    null
                }
            }
        } ?: return null

        val targetElement = element.navigationElement ?: element
        val language = getLanguageName(targetElement)

        // Apply language filter if specified (case-insensitive — the tool's `language`
        // parameter is user-facing and may be "kotlin", "Kotlin", "KOTLIN", etc.)
        if (languageFilter != null && languageFilter.none { it.equals(language, ignoreCase = true) }) {
            return null
        }

        val file = targetElement.containingFile?.virtualFile ?: return null
        if (!scope.contains(file)) return null
        val relativePath = ProjectUtils.getToolFilePath(project, file)

        val name = when (targetElement) {
            is PsiNamedElement -> targetElement.name
            else -> {
                try {
                    val method = targetElement.javaClass.getMethod("getName")
                    method.invoke(targetElement) as? String
                } catch (e: Exception) {
                    null
                }
            }
        } ?: return null

        val directQualifiedName = try {
            val method = targetElement.javaClass.getMethod("getQualifiedName")
            method.invoke(targetElement) as? String
        } catch (e: Exception) {
            null
        }
        val qualifiedName = directQualifiedName ?: buildQualifiedNameFromContainer(targetElement, name)

        val line = getLineNumber(project, targetElement) ?: 1
        val kind = determineKind(targetElement)
        val containerName = getContainerName(targetElement)

        return SymbolData(
            name = name,
            qualifiedName = qualifiedName,
            kind = kind,
            file = relativePath,
            line = line,
            column = getColumnNumber(project, targetElement) ?: 1,
            containerName = containerName,
            language = language
        )
    }

    private fun buildQualifiedNameFromContainer(element: PsiElement, name: String): String? {
        var parent = element.parent

        while (parent != null) {
            try {
                val method = parent.javaClass.getMethod("getQualifiedName")
                val parentQualifiedName = method.invoke(parent) as? String
                if (!parentQualifiedName.isNullOrBlank()) {
                    return "$parentQualifiedName.$name"
                }
            } catch (_: Exception) {
                // Ignore and continue walking up the PSI tree.
            }
            parent = parent.parent
        }

        return null
    }

    private fun getLanguageName(element: PsiElement): String {
        return when (element.language.id) {
            "JAVA" -> "Java"
            "kotlin" -> "Kotlin"
            "Python" -> "Python"
            "JavaScript", "ECMAScript 6", "JSX Harmony" -> "JavaScript"
            "TypeScript", "TypeScript JSX" -> "TypeScript"
            "go" -> "Go"
            "PHP" -> "PHP"
            "Rust" -> "Rust"
            else -> element.language.displayName
        }
    }

    private fun getLineNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun getColumnNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        val lineNumber = document.getLineNumber(element.textOffset)
        return element.textOffset - document.getLineStartOffset(lineNumber) + 1
    }

    private fun determineKind(element: PsiElement): String {
        val className = element.javaClass.simpleName.lowercase()
        return when {
            // Rust types
            className.contains("structitem") -> "STRUCT"
            className.contains("traititem") -> "TRAIT"
            className.contains("enumitem") -> "ENUM"
            className.contains("implitem") -> "IMPL"
            className.contains("moditem") -> "MODULE"
            // Common types
            className.contains("class") -> "CLASS"
            className.contains("interface") -> "INTERFACE"
            className.contains("enum") -> "ENUM"
            className.contains("struct") -> "STRUCT"
            className.contains("trait") -> "TRAIT"
            className.contains("method") -> "METHOD"
            className.contains("function") -> "FUNCTION"
            className.contains("field") -> "FIELD"
            className.contains("variable") -> "VARIABLE"
            className.contains("property") -> "PROPERTY"
            className.contains("constant") -> "CONSTANT"
            else -> "SYMBOL"
        }
    }

    private fun getContainerName(element: PsiElement): String? {
        return try {
            // Try to find containing class/type
            var parent = element.parent
            while (parent != null) {
                val parentClassName = parent.javaClass.simpleName.lowercase()
                if (parentClassName.contains("class") || parentClassName.contains("type")) {
                    val nameMethod = parent.javaClass.getMethod("getName")
                    return nameMethod.invoke(parent) as? String
                }
                parent = parent.parent
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
