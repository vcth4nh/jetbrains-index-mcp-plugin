package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx

/**
 * Maps our [BuiltInSearchScope] to the IDE's hierarchy scope-type strings
 * (constants on [HierarchyBrowserBaseEx]). Best-effort: SCOPE_PROJECT in the IDE
 * specifically means "production sources only", so it's used for both PROJECT_FILES
 * and PROJECT_PRODUCTION_FILES. The IDE has no direct equivalent of "all of project
 * including tests minus libraries" — closest is SCOPE_PROJECT.
 */
internal object HierarchyScopeMapping {
    fun toIdeScopeType(scope: BuiltInSearchScope): String = when (scope) {
        BuiltInSearchScope.PROJECT_FILES -> HierarchyBrowserBaseEx.SCOPE_PROJECT
        BuiltInSearchScope.PROJECT_AND_LIBRARIES -> HierarchyBrowserBaseEx.SCOPE_ALL
        BuiltInSearchScope.PROJECT_PRODUCTION_FILES -> HierarchyBrowserBaseEx.SCOPE_PROJECT
        BuiltInSearchScope.PROJECT_TEST_FILES -> HierarchyBrowserBaseEx.SCOPE_TEST
    }
}
