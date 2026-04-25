package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ClassResolverTest : BasePlatformTestCase() {

    fun testResolvesJavaClassByFqn() {
        myFixture.configureByText(
            "MyService.java",
            """
            package com.example;
            public class MyService {}
            """.trimIndent()
        )

        val resolved = ReadAction.compute<com.intellij.psi.PsiElement?, Throwable> {
            ClassResolver.findClassByName(project, "com.example.MyService")
        }

        assertNotNull(resolved)
        assertTrue("Expected PsiClass", resolved is PsiClass)
        assertEquals("MyService", (resolved as PsiClass).name)
    }

    fun testReturnsNullForUnknownFqn() {
        val resolved = ReadAction.compute<com.intellij.psi.PsiElement?, Throwable> {
            ClassResolver.findClassByName(project, "com.example.DoesNotExist")
        }
        assertNull(resolved)
    }

    fun testResolvesPythonClassByFqnWhenPythonPluginPresent() {
        // Skip if Python plugin isn't loaded.
        try {
            Class.forName("com.jetbrains.python.psi.PyClass")
        } catch (_: ClassNotFoundException) {
            return
        }

        myFixture.configureByText(
            "noise.py",
            """
            class MyParser:
                pass
            """.trimIndent()
        )

        val resolved = ReadAction.compute<com.intellij.psi.PsiElement?, Throwable> {
            ClassResolver.findClassByName(project, "noise.MyParser")
        }

        assertNotNull("Python class should resolve via EP iteration", resolved)
        // The returned element should at least have name "MyParser".
        val nameMethod = resolved!!.javaClass.methods.firstOrNull { it.name == "getName" && it.parameterCount == 0 }
        assertEquals("MyParser", nameMethod?.invoke(resolved))
    }
}
