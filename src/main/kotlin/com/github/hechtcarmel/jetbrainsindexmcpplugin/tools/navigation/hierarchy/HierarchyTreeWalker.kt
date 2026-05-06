package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ThreadingUtils
import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.LanguageCallHierarchy
import com.intellij.ide.hierarchy.LanguageTypeHierarchy
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.lang.reflect.Method

enum class HierarchyKind {
    CALLERS,
    CALLEES,
    SUPERTYPES,
    SUBTYPES;

    val isCall: Boolean get() = this == CALLERS || this == CALLEES
    val isType: Boolean get() = this == SUPERTYPES || this == SUBTYPES
}

/**
 * Generic hierarchy-tree walker that dispatches via the IDE's
 * `LanguageCallHierarchy` / `LanguageTypeHierarchy` extension points,
 * uses `provider.getTarget()` for language-specific pre-coercion (e.g.
 * Kotlin K1 light-class conversion), constructs the language's
 * `HierarchyBrowserBaseEx` headlessly on EDT, and reflectively invokes
 * the `protected createHierarchyTreeStructure(typeName, element)` to
 * obtain a walkable `HierarchyTreeStructure`. The walk is depth-limited
 * with cycle dedupe via PSI element identity. Type strings are sourced
 * from the localized `CallHierarchyBrowserBase.getCallerType()` etc. —
 * NEVER `getTypeHierarchyType()` (combined view) since multiple languages
 * either return null or call EDT-only modal-window analysis for it.
 */
internal object HierarchyTreeWalker {

    private val LOG = logger<HierarchyTreeWalker>()

    /**
     * Walks the IDE's hierarchy tree for [element] in the [kind] direction.
     * Returns the root [HierarchyNodeDescriptor] with all children populated
     * via [HierarchyNodeDescriptor.setCachedChildren] up to [maxDepth].
     *
     * MUST be called from a read action.
     */
    @RequiresReadLock
    fun walk(
        project: Project,
        element: PsiElement,
        kind: HierarchyKind,
        scope: BuiltInSearchScope,
        maxDepth: Int
    ): Result<HierarchyNodeDescriptor> {
        val language = element.language
        val provider = when {
            kind.isCall -> LanguageCallHierarchy.INSTANCE.forLanguage(language)
            else -> LanguageTypeHierarchy.INSTANCE.forLanguage(language)
        } ?: return Result.failure(
            IllegalStateException(
                "No ${if (kind.isCall) "call" else "type"} hierarchy provider for language: ${language.id}"
            )
        )

        val target = resolveTarget(provider, project, element)
            ?: return Result.failure(IllegalStateException("Provider rejected element for $kind"))

        val browser = createBrowserOnEdt(provider, target)
            ?: return Result.failure(IllegalStateException("createHierarchyBrowser returned non-HierarchyBrowserBaseEx"))

        val typeName = typeStringFor(kind)
        val structure = invokeCreateHierarchyTreeStructure(browser, typeName, target)
            ?: return Result.failure(IllegalStateException("Browser refused element ${element.text} for $kind"))

        // Walker doesn't apply scope itself; the IDE's per-language tree structures
        // read scope-type via `browser.getCurrentScopeType()`. Browsers default to
        // SCOPE_ALL when uninitialized; the language-specific scope behaviour is
        // covered by tree structures themselves. Documented in HierarchyScopeMapping.
        // (Future improvement: set browser scope reflectively before calling
        // createHierarchyTreeStructure if scope fidelity becomes a measured gap.)

        walkRecursive(structure, structure.baseDescriptor, maxDepth, mutableSetOf())
        return Result.success(structure.baseDescriptor)
    }

    private fun resolveTarget(provider: Any, project: Project, element: PsiElement): PsiElement? {
        // Build a synthetic DataContext with PSI_ELEMENT + PROJECT, then call
        // provider.getTarget(ctx). This delegates per-language pre-coercion (e.g.
        // walking up to the enclosing function, light-class conversion in Kotlin K1)
        // to the IDE's own logic. Falls back to the raw element if getTarget returns
        // null (rare, but defensive).
        val ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.PSI_ELEMENT, element)
            .build()
        // HierarchyProvider.getTarget(DataContext): PsiElement?
        val getTarget = runCatching {
            provider.javaClass.getMethod("getTarget", com.intellij.openapi.actionSystem.DataContext::class.java)
        }.getOrNull() ?: return element
        val coerced = runCatching { getTarget.invoke(provider, ctx) as? PsiElement }.getOrNull()
        return coerced ?: element
    }

    private fun createBrowserOnEdt(provider: Any, target: PsiElement): HierarchyBrowserBaseEx? {
        // HierarchyProvider.createHierarchyBrowser(PsiElement): HierarchyBrowser
        val createBrowser = runCatching {
            provider.javaClass.getMethod("createHierarchyBrowser", PsiElement::class.java)
        }.getOrElse {
            LOG.warn("createHierarchyBrowser method not found on ${provider.javaClass.name}", it)
            return null
        }
        return ThreadingUtils.runOnEdtAndWait {
            runCatching { createBrowser.invoke(provider, target) }.getOrNull() as? HierarchyBrowserBaseEx
        }
    }

    private fun typeStringFor(kind: HierarchyKind): String = when (kind) {
        HierarchyKind.CALLERS -> CallHierarchyBrowserBase.getCallerType()
        HierarchyKind.CALLEES -> CallHierarchyBrowserBase.getCalleeType()
        HierarchyKind.SUPERTYPES -> TypeHierarchyBrowserBase.getSupertypesHierarchyType()
        HierarchyKind.SUBTYPES -> TypeHierarchyBrowserBase.getSubtypesHierarchyType()
    }

    private fun invokeCreateHierarchyTreeStructure(
        browser: HierarchyBrowserBaseEx,
        typeName: String,
        element: PsiElement
    ): HierarchyTreeStructure? {
        val method = findCreateMethod(browser.javaClass) ?: run {
            LOG.warn("createHierarchyTreeStructure(String, PsiElement) not found on ${browser.javaClass.name}")
            return null
        }
        method.isAccessible = true
        return runCatching { method.invoke(browser, typeName, element) as? HierarchyTreeStructure }
            .onFailure { LOG.warn("createHierarchyTreeStructure invocation failed", it) }
            .getOrNull()
    }

    private fun findCreateMethod(clazz: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(
                    "createHierarchyTreeStructure",
                    String::class.java,
                    PsiElement::class.java
                )
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun walkRecursive(
        structure: HierarchyTreeStructure,
        node: HierarchyNodeDescriptor,
        depthLeft: Int,
        visited: MutableSet<PsiElement>
    ) {
        if (depthLeft <= 0) {
            node.cachedChildren = emptyArray()
            return
        }
        val psi = node.psiElement
        if (psi != null) {
            if (psi in visited) {
                node.cachedChildren = emptyArray()
                return
            }
            visited.add(psi)
        }
        val descriptorChildren = runCatching { structure.getChildElements(node) }
            .getOrDefault(emptyArray<Any>())
            .filterIsInstance<HierarchyNodeDescriptor>()
        node.cachedChildren = descriptorChildren.toTypedArray<Any>()
        for (child in descriptorChildren) {
            walkRecursive(structure, child, depthLeft - 1, visited)
        }
    }
}
