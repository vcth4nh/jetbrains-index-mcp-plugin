package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import junit.framework.TestCase

class HierarchyScopeMappingUnitTest : TestCase() {

    fun testProjectFilesMapsToScopeProject() {
        assertEquals(
            HierarchyBrowserBaseEx.SCOPE_PROJECT,
            HierarchyScopeMapping.toIdeScopeType(BuiltInSearchScope.PROJECT_FILES)
        )
    }

    fun testProjectAndLibrariesMapsToScopeAll() {
        assertEquals(
            HierarchyBrowserBaseEx.SCOPE_ALL,
            HierarchyScopeMapping.toIdeScopeType(BuiltInSearchScope.PROJECT_AND_LIBRARIES)
        )
    }

    fun testProjectProductionFilesMapsToScopeProject() {
        assertEquals(
            HierarchyBrowserBaseEx.SCOPE_PROJECT,
            HierarchyScopeMapping.toIdeScopeType(BuiltInSearchScope.PROJECT_PRODUCTION_FILES)
        )
    }

    fun testProjectTestFilesMapsToScopeTest() {
        assertEquals(
            HierarchyBrowserBaseEx.SCOPE_TEST,
            HierarchyScopeMapping.toIdeScopeType(BuiltInSearchScope.PROJECT_TEST_FILES)
        )
    }
}
