package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement

/**
 * Returns the fully qualified reference string for a PSI element, matching
 * IntelliJ's "Copy Reference" action. Delegates to the first registered
 * [QualifiedNameProvider] that returns a non-null, non-blank string.
 *
 * Format is language-specific (per the language plugin's provider):
 *   Java   — com.example.Foo#bar(java.lang.String)
 *   Python — test.BasicSolver.run
 *   PHP    — \Namespace\Foo::bar
 *
 * Returns null when no provider handles the element (the same situations
 * where the IDE's Copy Reference menu item is disabled).
 *
 * Must be called inside a read action.
 */
object QualifiedNameUtil {

    private val LOG = logger<QualifiedNameUtil>()

    fun getQualifiedName(element: PsiElement): String? {
        for (provider in QualifiedNameProvider.EP_NAME.extensionList) {
            try {
                val result = provider.getQualifiedName(element)
                if (!result.isNullOrBlank()) return result
            } catch (e: Exception) {
                LOG.debug("QualifiedNameProvider ${provider.javaClass.simpleName} threw for ${element.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }
}
