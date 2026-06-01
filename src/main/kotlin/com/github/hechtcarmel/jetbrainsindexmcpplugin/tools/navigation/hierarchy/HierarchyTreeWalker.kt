package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

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
 * Resolves the "logical element" represented by a [HierarchyNodeDescriptor].
 * This abstraction is needed because `HierarchyNodeDescriptor.psiElement` may
 * point at the call site (e.g. a `PsiReferenceExpression`) rather than the
 * containing method — the IDE's per-language browsers expose
 * `getElementFromDescriptor(descriptor)` for this. See [BrowserBackedResolver].
 */
internal interface LogicalElementResolver {
    fun resolve(descriptor: HierarchyNodeDescriptor): PsiElement?
}

/** Walker output: the populated descriptor tree plus a resolver to extract logical elements. */
internal class HierarchyWalkResult(
    val root: HierarchyNodeDescriptor,
    val resolver: LogicalElementResolver
)

/**
 * [LogicalElementResolver] used for the IDE-driven path. Resolution order:
 *
 * 1. `descriptor.getEnclosingElement()` — the IDE's own notion of the
 *    "logical owner" of a hierarchy node (e.g. Java/Rust/JS call-hierarchy
 *    descriptors expose this). Returns the enclosing method/class regardless
 *    of whether the descriptor's `psiElement` points at a call-site reference.
 *
 * 2. Browser's `protected getElementFromDescriptor(descriptor)` — fallback for
 *    descriptors that don't expose getEnclosingElement directly (e.g. Java's
 *    type-hierarchy descriptor uses `getPsiClass()` which the browser routes
 *    to). Each language's browser knows how to extract the logical element
 *    from its own descriptor type.
 *
 * 3. `descriptor.psiElement` — last-resort fallback.
 *
 * Rationale: Rust's `RsCallHierarchyBrowser.getElementFromDescriptor()` returns
 * `descriptor.psiElement` (the call-site reference), while Java's returns
 * `descriptor.getEnclosingElement()` (the calling method). Preferring
 * `getEnclosingElement` directly normalises this — Rust callers now point at
 * the calling fn, matching Java/Kotlin/Python/PHP/Go semantics.
 */
internal class BrowserBackedResolver(private val browser: HierarchyBrowserBaseEx) : LogicalElementResolver {
    private val getElementMethod: java.lang.reflect.Method? = run {
        var c: Class<*>? = browser.javaClass
        while (c != null) {
            try {
                return@run c.getDeclaredMethod("getElementFromDescriptor", HierarchyNodeDescriptor::class.java)
                    .apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        null
    }

    override fun resolve(descriptor: HierarchyNodeDescriptor): PsiElement? {
        // Prefer descriptor.getEnclosingElement() — uniform "logical owner" across languages.
        val enclosing = runCatching {
            descriptor.javaClass.getMethod("getEnclosingElement").invoke(descriptor) as? PsiElement
        }.getOrNull()
        if (enclosing != null) return enclosing

        // Fallback: ask the browser.
        val method = getElementMethod ?: return descriptor.psiElement
        return runCatching { method.invoke(browser, descriptor) as? PsiElement }
            .getOrNull() ?: descriptor.psiElement
    }
}

/** Resolver that just reads `descriptor.psiElement` — used by the Rust fallback which builds synthetic descriptors with the right element. */
internal object IdentityElementResolver : LogicalElementResolver {
    override fun resolve(descriptor: HierarchyNodeDescriptor): PsiElement? = descriptor.psiElement
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
        scope: HierarchyScope,
        maxDepth: Int
    ): Result<HierarchyWalkResult> = runCatching { walkInner(project, element, kind, scope, maxDepth) }
        .getOrElse { Result.failure(it) }
        .let { result ->
            // Flatten: walkInner returns Result<...>; runCatching wraps any unexpected
            // throw as Result.failure; combine so callers always see a single Result.
            result
        }

    private fun walkInner(
        project: Project,
        element: PsiElement,
        kind: HierarchyKind,
        scope: HierarchyScope,
        maxDepth: Int
    ): Result<HierarchyWalkResult> {
        val language = element.language
        val provider = when {
            kind.isCall -> LanguageCallHierarchy.INSTANCE.forLanguage(language)
            else -> LanguageTypeHierarchy.INSTANCE.forLanguage(language)
        }
        if (provider == null) {
            // Strategy II fallback: Rust type hierarchy has no IDE provider.
            if (kind.isType && language.id.equals("Rust", ignoreCase = true)) {
                return runCatching { RustTypeHierarchyFallback.walk(project, element, kind, scope, maxDepth) }
                    .getOrElse { Result.failure(it) }
            }
            return Result.failure(
                IllegalStateException(
                    "No ${if (kind.isCall) "call" else "type"} hierarchy provider for language: ${language.id}"
                )
            )
        }

        // Walk up the leaf to the natural target type FIRST (each language's
        // provider.getTarget walks up only when an Editor is in the DataContext;
        // we're headless, so we have to do it ourselves). This avoids Class
        // Cast failures inside provider.createHierarchyBrowser when it tries
        // (PsiClass)leafToken etc.
        val coercedElement = if (kind.isCall) {
            ClassLikePsi.walkUpToMethodLike(element) ?: element
        } else {
            ClassLikePsi.walkUpToClassLike(element) ?: element
        }

        val target = resolveTarget(provider, project, coercedElement)
            ?: return Result.failure(IllegalStateException("Provider rejected element for $kind"))

        val browser = createBrowserHeadless(provider, target)
            ?: return Result.failure(IllegalStateException("createHierarchyBrowser returned non-HierarchyBrowserBaseEx"))

        val typeName = typeStringFor(kind)

        // Per-language tree structures read scope-type via `browser.getCurrentScopeType()`,
        // which itself reads `myCurrentSheet.get().myScope`. A freshly-constructed browser
        // has no current sheet, so the scope would default to SCOPE_ALL. Reflectively poke
        // the sheet for our `typeName` to install our chosen scope before invoking
        // createHierarchyTreeStructure.
        applyBrowserScope(browser, typeName, scope.ideScopeType)

        val structure = invokeCreateHierarchyTreeStructure(browser, typeName, target)
            ?: return Result.failure(IllegalStateException("Browser refused element ${element.text} for $kind"))

        runCatching { structure.baseDescriptor.update() }
        walkRecursive(structure, structure.baseDescriptor, maxDepth, mutableSetOf())
        return Result.success(HierarchyWalkResult(structure.baseDescriptor, BrowserBackedResolver(browser)))
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

    private fun createBrowserHeadless(provider: Any, target: PsiElement): HierarchyBrowserBaseEx? {
        // HierarchyProvider.createHierarchyBrowser(PsiElement): HierarchyBrowser
        //
        // Called from inside a read action. The IDE's deadlock-prevention guard rejects
        // `invokeAndWait` from a read action, so we cannot switch to EDT here. Instead
        // we construct the browser on the calling thread. This works in practice because
        // (a) the per-language browser ctors just call super(project, element); (b) the
        // platform `HierarchyBrowserBaseEx` ctor allocates Swing components but does not
        // display them — Swing tolerates off-EDT construction of unmounted JComponents.
        // The reflective `createHierarchyTreeStructure` call later is read-action-safe.
        val createBrowser = runCatching {
            provider.javaClass.getMethod("createHierarchyBrowser", PsiElement::class.java)
        }.getOrElse {
            LOG.warn("createHierarchyBrowser method not found on ${provider.javaClass.name}", it)
            return null
        }
        return runCatching { createBrowser.invoke(provider, target) as? HierarchyBrowserBaseEx }
            .onFailure { LOG.warn("createHierarchyBrowser invocation failed", it) }
            .getOrNull()
    }

    /**
     * Reflectively configures the browser's "current sheet" with the requested
     * scope type-string so per-language tree structures read it via
     * [HierarchyBrowserBaseEx.getCurrentScopeType] when their constructor runs.
     *
     * Best-effort: silently no-ops if the IDE's private field shape changes.
     * The fallback is the browser's default scope (SCOPE_ALL).
     */
    private fun applyBrowserScope(
        browser: HierarchyBrowserBaseEx,
        typeName: String,
        scopeStr: String
    ) {
        runCatching {
            val baseClass = generateSequence(browser.javaClass as Class<*>) { it.superclass }
                .firstOrNull { it.simpleName == "HierarchyBrowserBaseEx" } ?: return
            val sheetMapField = baseClass.getDeclaredField("myType2Sheet").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val sheets = sheetMapField.get(browser) as? Map<String, Any> ?: return
            val sheet = sheets[typeName] ?: return
            val scopeField = sheet.javaClass.getDeclaredField("myScope").apply { isAccessible = true }
            scopeField.set(sheet, scopeStr)
            val currentSheetField = baseClass.getDeclaredField("myCurrentSheet").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val currentSheetRef = currentSheetField.get(browser) as? java.util.concurrent.atomic.AtomicReference<Any>
            currentSheetRef?.set(sheet)
        }.onFailure { LOG.warn("Failed to set hierarchy browser scope reflectively (typeName=$typeName, scope=$scopeStr)", it) }
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
            // Trigger the descriptor's presentation update so getHighlightedText()
            // is populated — the IDE normally does this in the UI tree renderer.
            runCatching { child.update() }
            walkRecursive(structure, child, depthLeft - 1, visited)
        }
    }
}
