package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ImplementationData
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaImplementationsHandlerTest : BasePlatformTestCase() {

    private val handler = JavaImplementationsHandler()

    private fun findClass(fqn: String): PsiClass? = ReadAction.compute<PsiClass?, Throwable> {
        JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    }

    fun testAnonymousRunnableImplDisplayName() {
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

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(runnable!!, project, BuiltInSearchScope.PROJECT_FILES)
        }

        assertNotNull(result)
        assertEquals(1, result!!.size)
        val name = result[0].name
        assertFalse("name must not be 'unknown'", name == "unknown")
        assertFalse("name must not contain literal 'null'", name.contains("null"))
        assertTrue(
            "Expected ClassPresentationUtil-style anonymous-class label, got '$name'",
            name.contains("Anonymous", ignoreCase = true) || name.contains("Foo")
        )
        assertNull(result[0].qualifiedName)
    }

    fun testEnumConstantOverrideMethodNotNullDotMethod() {
        myFixture.configureByText(
            "Action.java",
            """
            package com.example;
            public class Action {
                public enum Kind {
                    EXEC { @Override public void perform() {} },
                    LOOKUP { @Override public void perform() {} };
                    public abstract void perform();
                }
            }
            """.trimIndent()
        )
        val abstractPerform = ReadAction.compute<PsiMethod?, Throwable> {
            val action = findClass("com.example.Action")
            val kind = action!!.findInnerClassByName("Kind", false)
            kind!!.findMethodsByName("perform", false).firstOrNull { it.hasModifierProperty("abstract") }
        }
        assertNotNull(abstractPerform)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(abstractPerform!!, project, BuiltInSearchScope.PROJECT_FILES)
        }

        assertNotNull(result)
        assertEquals(2, result!!.size)
        result.forEach { loc ->
            assertFalse(
                "Method-impl name must not start with 'null.': '${loc.name}'",
                loc.name.startsWith("null.")
            )
            assertTrue(
                "Expected meaningful method label containing 'perform', got '${loc.name}'",
                loc.name.contains("perform")
            )
        }
    }

    fun testKotlinClassReportsLanguageKotlin() {
        // Sanity check the language.id fix: Kotlin source classes navigated via KtUltraLightClass
        // should report language="Kotlin", not "Java".
        val ktAvailable = try {
            Class.forName("org.jetbrains.kotlin.psi.KtClass")
            true
        } catch (_: ClassNotFoundException) { false }
        if (!ktAvailable) return

        myFixture.configureByText(
            "Base.kt",
            """
            package com.example
            open class Base
            class Child : Base()
            """.trimIndent()
        )
        val base = findClass("com.example.Base")
        assertNotNull(base)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(base!!, project, BuiltInSearchScope.PROJECT_FILES)
        }

        assertNotNull(result)
        assertTrue("Should find Child", result!!.isNotEmpty())
        assertEquals("Kotlin", result[0].language)
    }
}
