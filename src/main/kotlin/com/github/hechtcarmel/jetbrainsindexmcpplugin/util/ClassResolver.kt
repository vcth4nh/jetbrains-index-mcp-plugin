package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Resolves any element by its qualified name using IntelliJ's QualifiedNameProvider
 * extension point. Symmetric reverse of [QualifiedNameUtil.getQualifiedName] — every
 * language plugin's QualifiedNameProvider implements both forward and reverse direction.
 *
 * Works for any language whose plugin registers a QualifiedNameProvider (Java, Kotlin,
 * Python, PHP, JS/TS, Rust, Go, etc.).
 */
object ClassResolver {

    private val LOG = logger<ClassResolver>()

    /**
     * Finds an element by its fully qualified name. Returns null if no provider matches.
     *
     * @param project The project context.
     * @param qualifiedName Fully qualified name in the language's natural format
     *   (e.g., `com.example.MyClass`, `\App\Models\User`, `noise.MyParser`).
     * @return The resolved [PsiElement], or null. Caller is responsible for instanceof checks
     *   if a specific element kind is required (e.g., `PsiClass` / `PyClass`).
     */
    fun findClassByName(project: Project, qualifiedName: String): PsiElement? {
        for (provider in QualifiedNameProvider.EP_NAME.extensionList) {
            try {
                val resolved = provider.qualifiedNameToElement(qualifiedName, project)
                if (resolved != null) return resolved
            } catch (e: Exception) {
                LOG.debug(
                    "QualifiedNameProvider ${provider.javaClass.simpleName} threw for FQN " +
                        "$qualifiedName: ${e.message}"
                )
            }
        }
        return null
    }
}
