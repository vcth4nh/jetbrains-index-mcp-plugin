package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * One-off Strategy II fallback: Rust ships no LanguageTypeHierarchy provider
 * (audit 2026-05-04 §3.4 + research-time confirmation). This object wraps the
 * legacy custom-PSI walking algorithm from
 * [com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.RustTypeHierarchyHandler]
 * so the walker can return a uniform [HierarchyNodeDescriptor] tree.
 *
 * Task 6 fills in the algorithm; this is the stub that wires the dispatch hook.
 */
internal object RustTypeHierarchyFallback {
    fun walk(
        project: Project,
        element: PsiElement,
        kind: HierarchyKind,
        scope: BuiltInSearchScope,
        maxDepth: Int
    ): Result<HierarchyNodeDescriptor> {
        return Result.failure(IllegalStateException("Rust type hierarchy fallback not yet implemented"))
    }
}
