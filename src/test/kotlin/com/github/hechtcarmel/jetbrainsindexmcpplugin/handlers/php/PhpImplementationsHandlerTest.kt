package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ImplementationData
import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PhpImplementationsHandlerTest : BasePlatformTestCase() {

    fun testResolvesAtUsagePosition() {
        // Skip if PHP plugin isn't loaded.
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.MethodReference")
        } catch (_: ClassNotFoundException) {
            return
        }

        myFixture.configureByText(
            "code.php",
            """
            <?php
            interface Greeter {
                public function greet(): string;
            }
            class Hello implements Greeter {
                public function greet(): string { return "hi"; }
            }
            class Howdy implements Greeter {
                public function greet(): string { return "howdy"; }
            }
            function run(Greeter ${'$'}g): string {
                return ${'$'}g->greet();
            }
            """.trimIndent()
        )

        // Position the caret on the usage `greet` inside `${'$'}g->greet()`.
        val text = myFixture.editor.document.text
        val runStart = text.indexOf("function run")
        val greetCallOffset = text.indexOf("greet()", startIndex = runStart)
        myFixture.editor.caretModel.moveToOffset(greetCallOffset + 1)  // inside identifier
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull(element)

        val handler = PhpImplementationsHandler()
        val result = ReadAction.compute<List<ImplementationData>?, Throwable> {
            handler.findImplementations(
                element!!,
                project,
                BuiltInSearchScope.PROJECT_FILES
            )
        }

        assertNotNull(result)
        // Both Hello.greet and Howdy.greet must be returned.
        assertEquals(2, result!!.size)
        val names = result.map { it.name }
        assertTrue("Expected Hello in $names", names.any { it.contains("Hello") })
        assertTrue("Expected Howdy in $names", names.any { it.contains("Howdy") })
    }
}
