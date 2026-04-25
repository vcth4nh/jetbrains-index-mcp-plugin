package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.TypeHierarchyData
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaTypeHierarchyHandlerTest : BasePlatformTestCase() {

    private val handler = JavaTypeHierarchyHandler()

    private fun findClass(fqn: String): PsiClass? = ReadAction.compute<PsiClass?, Throwable> {
        JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    }

    fun testEnumSupertypeIncludesJavaLangEnum() {
        myFixture.configureByText(
            "Action.java",
            """
            package com.example;
            public enum Action { EXEC, LOOKUP }
            """.trimIndent()
        )
        val action = findClass("com.example.Action")
        assertNotNull(action)

        val result = ReadAction.compute<TypeHierarchyData?, Throwable> {
            handler.getTypeHierarchy(action!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)

        // Inherent JDK supertype java.lang.Enum<Action> must be present despite project-only scope.
        val supertypeNames = result!!.supertypes.map { it.qualifiedName ?: it.name }
        assertTrue(
            "Expected java.lang.Enum in supertypes, got: $supertypeNames",
            supertypeNames.any { it.startsWith("java.lang.Enum") }
        )
    }

    fun testPlainClassSupertypeOmitsJavaLangObjectByDefault() {
        myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo {}
            """.trimIndent()
        )
        val foo = findClass("com.example.Foo")
        assertNotNull(foo)

        val result = ReadAction.compute<TypeHierarchyData?, Throwable> {
            handler.getTypeHierarchy(foo!!, project, BuiltInSearchScope.PROJECT_FILES)
        }

        val names = result!!.supertypes.map { it.qualifiedName ?: it.name }
        assertFalse(
            "java.lang.Object should NOT appear in supertypes for a plain top-level class, got: $names",
            names.contains("java.lang.Object")
        )
    }

    fun testAnonymousClassSubtypeNameIsHumanReadable() {
        myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo {
                Runnable r = new Runnable() {
                    @Override public void run() {}
                };
            }
            """.trimIndent()
        )
        val runnable = findClass("java.lang.Runnable")
        assertNotNull(runnable)

        val result = ReadAction.compute<TypeHierarchyData?, Throwable> {
            handler.getTypeHierarchy(runnable!!, project, BuiltInSearchScope.PROJECT_FILES)
        }

        val anonSubtype = result!!.subtypes.firstOrNull()
        assertNotNull("Should find the anonymous Runnable", anonSubtype)
        assertFalse("name must not be 'unknown'", anonSubtype!!.name == "unknown")
        assertFalse(anonSubtype.name.contains("\$null"))
    }
}
