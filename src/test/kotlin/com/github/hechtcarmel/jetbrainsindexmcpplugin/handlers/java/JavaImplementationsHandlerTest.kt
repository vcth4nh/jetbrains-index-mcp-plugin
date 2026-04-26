package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ImplementationData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.testutil.JAVA_PROJECT_DESCRIPTOR
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Overrides [getProjectDescriptor] so the test runtime brings a mock JDK onto the classpath;
 * `java.lang.Runnable` (and friends) must resolve for the anon-class fixture to bind to its
 * supertype.
 */
class JavaImplementationsHandlerTest : BasePlatformTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_PROJECT_DESCRIPTOR

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

    fun testFunctionalInterfaceLambdaImpls() {
        // A @FunctionalInterface used with a lambda assignment and a method reference must
        // surface both as "implementations". IntelliJ's Goto-Implementation includes them
        // (via FunctionalExpressionSearch); our handler must too.
        myFixture.configureByText(
            "Sample.java",
            """
            package com.example;
            public class Sample {
                @FunctionalInterface
                interface Executor { void execute(String cmd); }

                static void useLambda() {
                    Executor exec = s -> System.out.println(s);
                    exec.execute("hi");
                }

                static void useMethodRef() {
                    Executor exec = System.out::println;
                    exec.execute("hi");
                }
            }
            """.trimIndent()
        )

        // Caret on the interface itself.
        val executor = ReadAction.compute<PsiClass?, Throwable> {
            findClass("com.example.Sample")?.findInnerClassByName("Executor", false)
        }
        assertNotNull("Test fixture must define Executor", executor)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(executor!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)
        assertEquals("Expected 2 functional impls (1 lambda + 1 method ref), got ${result!!.size}", 2, result.size)

        val kinds = result.map { it.kind }.toSet()
        assertTrue("Expected LAMBDA in kinds: $kinds", kinds.contains("LAMBDA"))
        assertTrue("Expected METHOD_REFERENCE in kinds: $kinds", kinds.contains("METHOD_REFERENCE"))

        result.forEach { impl ->
            assertFalse("name must not be 'unknown': '${impl.name}'", impl.name == "unknown")
            assertTrue(
                "Expected name to mention enclosing context, got '${impl.name}'",
                impl.name.contains("useLambda") || impl.name.contains("useMethodRef") || impl.name.contains("Sample")
            )
        }
    }

    fun testFunctionalInterfaceSamMethodLambdaImpls() {
        // Caret on the SAM (abstract single-method) — find_implementations must include
        // lambda/method-reference assignments to the functional interface.
        myFixture.configureByText(
            "Sample.java",
            """
            package com.example;
            public class Sample {
                @FunctionalInterface
                interface Executor { void execute(String cmd); }

                static void useLambda() {
                    Executor exec = s -> {};
                    exec.execute("hi");
                }
            }
            """.trimIndent()
        )

        val executeMethod = ReadAction.compute<PsiMethod?, Throwable> {
            findClass("com.example.Sample")
                ?.findInnerClassByName("Executor", false)
                ?.findMethodsByName("execute", false)?.firstOrNull()
        }
        assertNotNull(executeMethod)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(executeMethod!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)
        // Either the lambda is treated as an implementation directly, or the SAM resolution
        // bridges to FunctionalExpressionSearch on the containing interface. Either way ≥1.
        assertTrue(
            "Expected at least 1 lambda impl when querying SAM method, got ${result!!.size}",
            result.isNotEmpty()
        )
    }

    fun testFunctionalInterfaceMixedClassAndLambdaImpls() {
        // When the interface has both a concrete class implementor AND a lambda assignment,
        // both must appear in the result, no double-counting.
        myFixture.configureByText(
            "Sample.java",
            """
            package com.example;
            public class Sample {
                @FunctionalInterface
                interface Executor { void execute(String cmd); }

                static class ConcreteExec implements Executor {
                    @Override public void execute(String cmd) {}
                }

                static void useLambda() {
                    Executor exec = s -> {};
                }
            }
            """.trimIndent()
        )
        val executor = ReadAction.compute<PsiClass?, Throwable> {
            findClass("com.example.Sample")?.findInnerClassByName("Executor", false)
        }
        assertNotNull(executor)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(executor!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)
        assertEquals("Expected exactly 2 impls (1 class + 1 lambda), got ${result!!.size}", 2, result.size)

        val kinds = result.map { it.kind }.toSet()
        assertTrue("Expected LAMBDA among kinds: $kinds", kinds.contains("LAMBDA"))
        assertTrue("Expected non-LAMBDA class impl among kinds: $kinds", kinds.any { it != "LAMBDA" && it != "METHOD_REFERENCE" })
    }

    fun testFunctionalInterfaceDefaultMethodDoesNotSurfaceLambdas() {
        // Querying find_implementations on a *default* method of a functional interface
        // must NOT trigger lambda search — lambdas only override the SAM.
        myFixture.configureByText(
            "Sample.java",
            """
            package com.example;
            public class Sample {
                @FunctionalInterface
                interface Executor {
                    void execute(String cmd);          // SAM
                    default void describe() {}         // default — not the SAM
                }

                static void useLambda() {
                    Executor exec = s -> {};
                }
            }
            """.trimIndent()
        )

        val describeMethod = ReadAction.compute<PsiMethod?, Throwable> {
            findClass("com.example.Sample")
                ?.findInnerClassByName("Executor", false)
                ?.findMethodsByName("describe", false)?.firstOrNull()
        }
        assertNotNull(describeMethod)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(describeMethod!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)

        val functionalKinds = result!!.filter { it.kind == "LAMBDA" || it.kind == "METHOD_REFERENCE" }
        assertTrue(
            "Querying a non-SAM (default) method must not return lambdas; got: $functionalKinds",
            functionalKinds.isEmpty()
        )
    }

    fun testFunctionalInterfaceLambdaInFieldInitializer() {
        // Lambda in a field initializer has no enclosing PsiMethod. Name builder must not
        // crash and must still produce a sensible label.
        myFixture.configureByText(
            "Sample.java",
            """
            package com.example;
            public class Sample {
                @FunctionalInterface
                interface Executor { void execute(String cmd); }

                static final Executor STATIC_EXEC = s -> {};
                final Executor instanceExec = s -> System.out.println(s);
            }
            """.trimIndent()
        )

        val executor = ReadAction.compute<PsiClass?, Throwable> {
            findClass("com.example.Sample")?.findInnerClassByName("Executor", false)
        }
        assertNotNull(executor)

        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(executor!!, project, BuiltInSearchScope.PROJECT_FILES)
        }
        assertNotNull(result)
        assertEquals("Expected 2 lambda impls in field initializers, got ${result!!.size}", 2, result.size)

        result.forEach { impl ->
            assertEquals("LAMBDA", impl.kind)
            assertFalse("Name must not be blank: '${impl.name}'", impl.name.isBlank())
            assertFalse("Name must not be 'unknown': '${impl.name}'", impl.name == "unknown")
            assertTrue("Name should mention enclosing class 'Sample': '${impl.name}'", impl.name.contains("Sample"))
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
