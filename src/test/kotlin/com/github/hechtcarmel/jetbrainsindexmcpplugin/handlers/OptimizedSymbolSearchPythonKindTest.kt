package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Method

/**
 * Verifies that [OptimizedSymbolSearch.determineKind] (accessed via reflection, as the method
 * is private) correctly classifies Python instance attributes as "FIELD" and module-level
 * assignments as "VARIABLE" when the Python plugin is present.
 *
 * Skips silently when the Python plugin is not loaded in the current test environment.
 */
class OptimizedSymbolSearchPythonKindTest : BasePlatformTestCase() {

    /**
     * Retrieve the private `determineKind` method from [OptimizedSymbolSearch] so that
     * we can call it directly without going through the full symbol-search pipeline.
     */
    private fun getDetermineKindMethod(): Method {
        return OptimizedSymbolSearch::class.java.getDeclaredMethod("determineKind", PsiElement::class.java)
            .also { it.isAccessible = true }
    }

    private fun invokeKind(method: Method, element: PsiElement): String =
        method.invoke(OptimizedSymbolSearch, element) as String

    fun testPyTargetExpressionInstanceAttrIsField() {
        // Skip if Python plugin isn't loaded in this test environment.
        val pyTargetExpressionClass = try {
            Class.forName("com.jetbrains.python.psi.PyTargetExpression")
        } catch (_: ClassNotFoundException) {
            return
        }

        myFixture.configureByText(
            "template.py",
            """
            class Template:
                def __init__(self, text):
                    self.text = text
            """.trimIndent()
        )

        val determineKind = getDetermineKindMethod()

        // Find the self.text PyTargetExpression node in the class body.
        @Suppress("UNCHECKED_CAST")
        val targetExpr = ReadAction.compute<PsiElement?, Throwable> {
            PsiTreeUtil.findChildrenOfType(
                myFixture.file,
                pyTargetExpressionClass as Class<PsiElement>
            ).firstOrNull { el ->
                try {
                    el.javaClass.getMethod("getName").invoke(el) == "text"
                } catch (_: Exception) {
                    false
                }
            }
        }
        assertNotNull("Should locate self.text PyTargetExpression in the fixture", targetExpr)

        val kind = ReadAction.compute<String, Throwable> {
            invokeKind(determineKind, targetExpr!!)
        }
        assertEquals(
            "Instance attribute (self.text = ...) should be classified as FIELD, not SYMBOL or VARIABLE",
            "FIELD",
            kind
        )
    }

    fun testPyTargetExpressionModuleLevelIsVariable() {
        // Skip if Python plugin isn't loaded.
        val pyTargetExpressionClass = try {
            Class.forName("com.jetbrains.python.psi.PyTargetExpression")
        } catch (_: ClassNotFoundException) {
            return
        }

        myFixture.configureByText(
            "module_var.py",
            """
            MAX_SIZE = 100
            """.trimIndent()
        )

        val determineKind = getDetermineKindMethod()

        @Suppress("UNCHECKED_CAST")
        val targetExpr = ReadAction.compute<PsiElement?, Throwable> {
            PsiTreeUtil.findChildrenOfType(
                myFixture.file,
                pyTargetExpressionClass as Class<PsiElement>
            ).firstOrNull { el ->
                try {
                    el.javaClass.getMethod("getName").invoke(el) == "MAX_SIZE"
                } catch (_: Exception) {
                    false
                }
            }
        }
        assertNotNull("Should locate MAX_SIZE PyTargetExpression in the fixture", targetExpr)

        val kind = ReadAction.compute<String, Throwable> {
            invokeKind(determineKind, targetExpr!!)
        }
        assertEquals(
            "Module-level assignment (MAX_SIZE = ...) should be classified as VARIABLE",
            "VARIABLE",
            kind
        )
    }
}
