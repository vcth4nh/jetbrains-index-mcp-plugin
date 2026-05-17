package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
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

        val qualifiedName = QualifiedNameUtil.getQualifiedName(targetElement)

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
        return LanguageServiceRegistry.getKind(element)
    }

    private fun getContainerName(element: PsiElement): String? {
        // Go: method receiver is a sibling, not an ancestor
        if (element.language.id == "go") {
            try {
                val methodDeclClass = Class.forName("com.goide.psi.GoMethodDeclaration")
                if (methodDeclClass.isInstance(element)) {
                    val receiver = methodDeclClass.getMethod("getReceiver").invoke(element) ?: return null
                    val type = receiver.javaClass.getMethod("getType").invoke(receiver) as? PsiElement ?: return null
                    return type.text?.removePrefix("*")?.trim()
                }
            } catch (_: ClassNotFoundException) {
                // GoLand not loaded — fall through to generic walk
            } catch (_: Exception) {
                // Any reflection failure — fall through
            }
        }

        // Rust: walk to nearest RsImplItem / RsTraitItem / RsModItem
        if (element.language.id == "Rust") {
            try {
                val implItemClass = try { Class.forName("org.rust.lang.core.psi.RsImplItem") } catch (_: ClassNotFoundException) { null }
                val traitItemClass = try { Class.forName("org.rust.lang.core.psi.RsTraitItem") } catch (_: ClassNotFoundException) { null }
                val modItemClass = try { Class.forName("org.rust.lang.core.psi.RsModItem") } catch (_: ClassNotFoundException) { null }
                var current: PsiElement? = element.parent
                while (current != null) {
                    if (implItemClass?.isInstance(current) == true ||
                        traitItemClass?.isInstance(current) == true ||
                        modItemClass?.isInstance(current) == true
                    ) {
                        val nameMethod = current.javaClass.getMethod("getName")
                        return nameMethod.invoke(current) as? String
                    }
                    current = current.parent
                }
            } catch (_: Exception) {
                // fall through to generic walk
            }
        }

        // Generic parent-walk fallback (existing behavior)
        return try {
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
