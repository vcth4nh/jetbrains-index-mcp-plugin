package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement

object QualifiedNameUtil {
    private val LOG = logger<QualifiedNameUtil>()

    private val pyQualifiedNameOwnerClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.python.psi.PyQualifiedNameOwner")
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    /**
     * Returns the qualified name of [element] using the IDE's QualifiedNameProvider extension point.
     * Falls back to PyCharm's PyQualifiedNameOwner interface (covers PyTargetExpression cases like
     * `self.x = ...` instance attributes that the upstream PyQualifiedNameProvider doesn't expose).
     *
     * Must be called inside a read action.
     *
     * @return qualified name, or null when no provider handles the element.
     */
    fun getQualifiedName(element: PsiElement): String? {
        for (provider in QualifiedNameProvider.EP_NAME.extensionList) {
            try {
                val result = provider.getQualifiedName(element)
                if (!result.isNullOrBlank()) return result
            } catch (e: Exception) {
                LOG.debug(
                    "QualifiedNameProvider ${provider.javaClass.simpleName} threw for " +
                        "${element.javaClass.simpleName}: ${e.message}"
                )
            }
        }

        // Fallback: PyTargetExpression and similar implement PyQualifiedNameOwner directly,
        // but PyQualifiedNameProvider only exposes PyClass / PyFunction.
        val pyOwner = pyQualifiedNameOwnerClass
        if (pyOwner != null && pyOwner.isInstance(element)) {
            try {
                val result = pyOwner.getMethod("getQualifiedName").invoke(element) as? String
                if (!result.isNullOrBlank()) return result
            } catch (e: Exception) {
                LOG.debug("PyQualifiedNameOwner fallback threw for ${element.javaClass.simpleName}: ${e.message}")
            }
        }

        return null
    }
}
