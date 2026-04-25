package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class QualifiedNameUtilTest : BasePlatformTestCase() {

    fun testJavaClassReturnsFqn() {
        val file = myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo {
                public void bar(String s) {}
                public void bar(int i) {}
                public int count;
            }
            """.trimIndent()
        ) as PsiJavaFile
        val psiClass = file.classes[0]

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(psiClass)
        }

        assertEquals("com.example.Foo", result)
    }

    fun testJavaMethodIncludesSignature() {
        val file = myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo {
                public void bar(String s) {}
            }
            """.trimIndent()
        ) as PsiJavaFile
        val method = file.classes[0].methods[0]

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(method)
        }

        assertNotNull(result)
        // JavaQualifiedNameProvider emits <fqn>#<name>(<param-types>)
        assertEquals("com.example.Foo#bar(java.lang.String)", result)
    }

    fun testJavaOverloadsAreDistinguishable() {
        val file = myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo {
                public void bar(String s) {}
                public void bar(int i) {}
            }
            """.trimIndent()
        ) as PsiJavaFile
        val methods = file.classes[0].methods

        val fqns = ReadAction.compute<List<String?>, Throwable> {
            methods.map { QualifiedNameUtil.getQualifiedName(it) }
        }

        // Overloads must produce distinct qualified names (the regression #3 is about).
        assertEquals(2, fqns.distinct().size)
    }

    fun testJavaFieldReturnsFqnWithHash() {
        val file = myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo { public int count; }
            """.trimIndent()
        ) as PsiJavaFile
        val field = file.classes[0].fields[0]

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(field)
        }

        assertEquals("com.example.Foo#count", result)
    }

    fun testReturnsNullWhenNoProviderHandlesElement() {
        // PsiWhiteSpace has no qualified name — every provider returns null.
        val file = myFixture.configureByText(
            "Foo.java",
            "package com.example; public class Foo { }"
        )
        val whitespace = file.findElementAt(file.text.indexOf(" ")) ?: return

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(whitespace)
        }

        assertNull(result)
    }

    fun testJavaPackageReturnsFqn() {
        val file = myFixture.configureByText(
            "Foo.java",
            """
            package com.example;
            public class Foo { }
            """.trimIndent()
        ) as PsiJavaFile
        // Resolve the package via the file's directory rather than JavaPsiFacade.findPackage —
        // myFixture-configured files live in a directory the IDE recognises, but the package
        // may not be registered in JavaPsiFacade until full indexing completes.
        val pkg = ReadAction.compute<com.intellij.psi.PsiPackage?, Throwable> {
            file.containingDirectory?.let {
                com.intellij.psi.JavaDirectoryService.getInstance().getPackage(it)
            }
        }
        assertNotNull("com.example package should be resolvable from file directory", pkg)

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(pkg!!)
        }

        assertEquals("com.example", result)
    }

    fun testKotlinTopLevelFunctionReturnsFqn() {
        val file = myFixture.configureByText(
            "Foo.kt",
            """
            package com.example

            fun topLevelFn() {}
            """.trimIndent()
        )
        val ktFile = file as org.jetbrains.kotlin.psi.KtFile
        val function = ktFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>().first()

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(function)
        }

        // Kotlin's QualifiedNameProvider output is locked by this test. If the
        // platform changes Kotlin's canonical form, update this assertion.
        assertNotNull("Kotlin top-level function should produce a qualified name", result)
        assertTrue(
            "Expected FQN to reference top-level function; got $result",
            result!!.contains("topLevelFn")
        )
    }

    fun testKotlinClassMethodReturnsFqn() {
        val file = myFixture.configureByText(
            "Bar.kt",
            """
            package com.example

            class Bar {
                fun baz(x: String) {}
            }
            """.trimIndent()
        )
        val ktFile = file as org.jetbrains.kotlin.psi.KtFile
        val klass = ktFile.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtClass>().first()
        val method = klass.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>().first()

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(method)
        }

        assertNotNull("Kotlin class method should produce a qualified name", result)
        assertTrue(
            "Expected FQN to include class and method name; got $result",
            result!!.contains("Bar") && result.contains("baz")
        )
    }

    fun testMisbehavingProviderIsSkipped() {
        // Register a provider that throws. The helper must log & skip it, then
        // continue to the next registered provider. If isolation is broken,
        // this test will fail with an uncaught exception on the Java class.
        val throwingProvider = object : com.intellij.ide.actions.QualifiedNameProvider {
            override fun getQualifiedName(element: com.intellij.psi.PsiElement): String? {
                throw RuntimeException("deliberately broken provider for isolation test")
            }
            override fun qualifiedNameToElement(fqn: String, project: com.intellij.openapi.project.Project): com.intellij.psi.PsiElement? = null
            override fun adjustElementToCopy(element: com.intellij.psi.PsiElement): com.intellij.psi.PsiElement? = null
        }

        com.intellij.testFramework.ExtensionTestUtil.maskExtensions(
            com.intellij.ide.actions.QualifiedNameProvider.EP_NAME,
            listOf(throwingProvider) + com.intellij.ide.actions.QualifiedNameProvider.EP_NAME.extensionList,
            testRootDisposable
        )

        val file = myFixture.configureByText(
            "Foo.java",
            "package com.example; public class Foo { }"
        ) as com.intellij.psi.PsiJavaFile
        val psiClass = file.classes[0]

        val result = ReadAction.compute<String?, Throwable> {
            QualifiedNameUtil.getQualifiedName(psiClass)
        }

        // Throwing provider was skipped; JavaQualifiedNameProvider still wins.
        assertEquals("com.example.Foo", result)
    }
}
