package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.CallHierarchyData
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaCallHierarchyHandlerTest : BasePlatformTestCase() {

    private val handler = JavaCallHierarchyHandler()

    fun testAnonymousEnumOverridesNotCollapsedInCallHierarchy() {
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
                void caller1() { Kind.EXEC.perform(); }
                void caller2() { Kind.LOOKUP.perform(); }
            }
            """.trimIndent()
        )
        val abstractPerform = ReadAction.compute<PsiMethod?, Throwable> {
            val action = JavaPsiFacade.getInstance(project).findClass("com.example.Action", GlobalSearchScope.allScope(project))
            val kind = action!!.findInnerClassByName("Kind", false)
            kind!!.findMethodsByName("perform", false).firstOrNull { it.hasModifierProperty("abstract") }
        }
        assertNotNull(abstractPerform)

        val tree = ReadAction.compute<CallHierarchyData?, Throwable> {
            handler.getCallHierarchy(
                abstractPerform!!,
                project,
                "callers",
                3,
                BuiltInSearchScope.PROJECT_FILES
            )
        }
        assertNotNull(tree)

        // Both EXEC's perform call site and LOOKUP's perform call site must be present.
        // If the dedup key collapsed both anonymous overrides, only one call site would be reported.
        val callerNames = tree!!.calls.map { it.name }
        val mentionsEXEC = callerNames.any { it.contains("caller1") || it.contains("EXEC") }
        val mentionsLOOKUP = callerNames.any { it.contains("caller2") || it.contains("LOOKUP") }

        assertTrue(
            "Expected both EXEC and LOOKUP call paths in caller tree, got: $callerNames",
            mentionsEXEC && mentionsLOOKUP
        )
    }
}
