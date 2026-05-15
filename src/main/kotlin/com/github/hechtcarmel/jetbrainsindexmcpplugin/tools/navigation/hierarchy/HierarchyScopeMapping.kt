package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx

/**
 * Maps our [BuiltInSearchScope] to the IDE's hierarchy scope-type strings
 * (constants on [HierarchyBrowserBaseEx]).
 *
 * **Default mapping (`PROJECT_FILES` → `SCOPE_ALL`):** matches the IDE's own
 * default when a user invokes Find Callers / Type Hierarchy from the menu — the
 * "All" tab is selected by default. SCOPE_PROJECT ("Production") would be
 * stricter (production sources only), but most language plugins implement it as
 * `GlobalSearchScopesCore.projectProductionScope(project)` which yields an
 * **empty** scope in projects without configured production source roots — most
 * notably JavaScript/TypeScript projects without Maven/Gradle module setup,
 * where callers came back empty. Users who explicitly want stricter scoping can
 * pass `PROJECT_PRODUCTION_FILES`.
 *
 * Side-effect: hierarchy results may include callers from library sources. For
 * most project-scoped queries this is a no-op (project methods have no library
 * callers); it only matters when the target itself is a library symbol.
 */
internal object HierarchyScopeMapping {
    fun toIdeScopeType(scope: BuiltInSearchScope): String = when (scope) {
        BuiltInSearchScope.PROJECT_FILES -> HierarchyBrowserBaseEx.SCOPE_ALL
        BuiltInSearchScope.PROJECT_AND_LIBRARIES -> HierarchyBrowserBaseEx.SCOPE_ALL
        BuiltInSearchScope.PROJECT_PRODUCTION_FILES -> HierarchyBrowserBaseEx.SCOPE_PROJECT
        BuiltInSearchScope.PROJECT_TEST_FILES -> HierarchyBrowserBaseEx.SCOPE_TEST
    }
}
