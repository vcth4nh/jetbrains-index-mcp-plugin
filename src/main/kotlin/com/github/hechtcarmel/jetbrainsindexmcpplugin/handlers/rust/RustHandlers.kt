package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.*
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

/**
 * Registration entry point for Rust language handlers.
 *
 * This class is loaded via reflection when a Rust plugin is available.
 * It registers all Rust-specific handlers with the [LanguageHandlerRegistry].
 *
 * ## Rust PSI Classes Used (via reflection)
 *
 * - `org.rust.lang.core.psi.RsFile` - Rust source files
 * - `org.rust.lang.core.psi.RsStructItem` - Struct declarations
 * - `org.rust.lang.core.psi.RsTraitItem` - Trait declarations
 * - `org.rust.lang.core.psi.RsImplItem` - Impl blocks
 * - `org.rust.lang.core.psi.RsEnumItem` - Enum declarations
 * - `org.rust.lang.core.psi.RsFunction` - Function/method declarations
 * - `org.rust.lang.core.psi.RsModItem` - Module declarations
 * - `org.rust.lang.core.psi.RsCallExpr` - Function call expressions
 * - `org.rust.lang.core.psi.RsMethodCall` - Method call expressions
 *
 * ## Rust-Specific Concepts
 *
 * - **No Inheritance**: Rust uses composition and traits instead of class inheritance
 * - **Traits**: Similar to interfaces but can have default implementations
 * - **Impl Blocks**: Separate blocks for implementing traits on types
 * - **Supertraits**: Traits can require other traits as bounds
 *
 * ## Supported Plugin IDs
 *
 * - `com.jetbrains.rust` - Official JetBrains Rust plugin (RustRover, IDEA Ultimate, CLion)
 */
object RustHandlers {

    private val LOG = logger<RustHandlers>()

    /**
     * Registers all Rust handlers with the registry.
     *
     * Called via reflection from [LanguageHandlerRegistry].
     */
    @JvmStatic
    fun register(registry: LanguageHandlerRegistry) {
        if (!PluginDetectors.rust.isAvailable) {
            LOG.info("Rust plugin not available, skipping Rust handler registration")
            return
        }

        try {
            // Verify Rust classes are accessible before registering
            Class.forName("org.rust.lang.core.psi.RsFile")
            Class.forName("org.rust.lang.core.psi.RsFunction")

            registry.registerImplementationsHandler(RustImplementationsHandler())
            // Note: SuperMethodsHandler is NOT registered for Rust because Rust uses trait
            // implementations rather than classical inheritance. There are no "super methods"
            // in the OOP sense. Users should use ide_find_definition or ide_type_hierarchy instead.

            LOG.info("Registered Rust handlers (TypeHierarchy uses platform EP, SuperMethods not applicable for Rust)")
        } catch (e: ClassNotFoundException) {
            LOG.warn("Rust PSI classes not found, skipping registration: ${e.message}")
        } catch (e: Exception) {
            LOG.warn("Failed to register Rust handlers: ${e.message}")
        }
    }
}

/**
 * Base class for Rust handlers with common utilities.
 *
 * Uses reflection to access Rust PSI classes to avoid compile-time dependencies.
 */
abstract class BaseRustHandler<T> : LanguageHandler<T> {

    protected val LOG = logger<BaseRustHandler<*>>()

    /**
     * Checks if the element is from Rust language.
     */
    protected fun isRustLanguage(element: PsiElement): Boolean {
        return element.language.id == "Rust"
    }

    // Lazy-loaded Rust PSI classes via reflection

    protected val rsFileClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsFile")
    }

    protected val rsStructItemClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsStructItem")
    }

    protected val rsTraitItemClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsTraitItem")
    }

    protected val rsImplItemClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsImplItem")
    }

    protected val rsEnumItemClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsEnumItem")
    }

    protected val rsFunctionClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsFunction")
    }

    protected val rsModItemClass: Class<*>? by lazy {
        loadClass("org.rust.lang.core.psi.RsModItem")
    }

    private fun loadClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            LOG.debug("$className not found")
            null
        }
    }

    // Type checking helpers

    protected fun isRsTrait(element: PsiElement): Boolean {
        return rsTraitItemClass?.isInstance(element) == true
    }

    protected fun isRsStruct(element: PsiElement): Boolean {
        return rsStructItemClass?.isInstance(element) == true
    }

    protected fun isRsEnum(element: PsiElement): Boolean {
        return rsEnumItemClass?.isInstance(element) == true
    }

    protected fun isRsImpl(element: PsiElement): Boolean {
        return rsImplItemClass?.isInstance(element) == true
    }

    protected fun isRsFunction(element: PsiElement): Boolean {
        return rsFunctionClass?.isInstance(element) == true
    }

    protected fun isRsMod(element: PsiElement): Boolean {
        return rsModItemClass?.isInstance(element) == true
    }

    protected fun isRsType(element: PsiElement): Boolean {
        return isRsStruct(element) || isRsEnum(element) || isRsTrait(element)
    }

    // Navigation helpers

    protected fun findContainingRsTrait(element: PsiElement): PsiElement? {
        if (isRsTrait(element)) return element
        val cls = rsTraitItemClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    protected fun findContainingRsImpl(element: PsiElement): PsiElement? {
        if (isRsImpl(element)) return element
        val cls = rsImplItemClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    protected fun findContainingRsFunction(element: PsiElement): PsiElement? {
        if (isRsFunction(element)) return element
        val cls = rsFunctionClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    protected fun findContainingRsStruct(element: PsiElement): PsiElement? {
        if (isRsStruct(element)) return element
        val cls = rsStructItemClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    protected fun findContainingRsEnum(element: PsiElement): PsiElement? {
        if (isRsEnum(element)) return element
        val cls = rsEnumItemClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    // Reflection-based API calls

    /**
     * Gets the name of a Rust element via reflection.
     */
    protected fun getName(element: PsiElement): String? {
        return try {
            val method = element.javaClass.getMethod("getName")
            method.invoke(element) as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the trait reference from an impl block.
     * Returns null for inherent impls (impl Type { ... } without a trait).
     */
    protected fun getTraitRef(implItem: PsiElement): PsiElement? {
        return try {
            val method = implItem.javaClass.getMethod("getTraitRef")
            method.invoke(implItem) as? PsiElement
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the type reference from an impl block.
     * This is the type being implemented for (e.g., MyStruct in "impl Trait for MyStruct").
     */
    protected fun getTypeReference(implItem: PsiElement): PsiElement? {
        return try {
            val method = implItem.javaClass.getMethod("getTypeReference")
            method.invoke(implItem) as? PsiElement
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the supertraits of a trait.
     */
    protected fun getSuperTraits(traitItem: PsiElement): List<PsiElement>? {
        return try {
            val method = traitItem.javaClass.getMethod("getSuperTraits")
            @Suppress("UNCHECKED_CAST")
            method.invoke(traitItem) as? List<PsiElement>
        } catch (e: Exception) {
            // Try alternative method names
            try {
                val method = traitItem.javaClass.getMethod("getTypeParamBounds")
                @Suppress("UNCHECKED_CAST")
                method.invoke(traitItem) as? List<PsiElement>
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Resolves a reference to its target element.
     */
    protected fun resolveReference(element: PsiElement): PsiElement? {
        return try {
            val referenceMethod = element.javaClass.getMethod("getReference")
            val reference = referenceMethod.invoke(element) as? com.intellij.psi.PsiReference
            reference?.resolve()
        } catch (e: Exception) {
            // Try resolve() directly if available
            try {
                val resolveMethod = element.javaClass.getMethod("resolve")
                resolveMethod.invoke(element) as? PsiElement
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Gets the functions/methods within a trait or impl.
     */
    protected fun getFunctions(container: PsiElement): List<PsiElement>? {
        return try {
            // Try different method names used in Rust PSI
            val methodNames = listOf("getFunctions", "getMembers", "getExpandedMembers")
            for (methodName in methodNames) {
                try {
                    val method = container.javaClass.getMethod(methodName)
                    val result = method.invoke(container)
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? List<*>
                    if (list != null) {
                        return list.filterIsInstance<PsiElement>().filter { isRsFunction(it) }
                    }
                } catch (e: NoSuchMethodException) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // Utility methods

    protected fun getRelativePath(project: Project, file: com.intellij.openapi.vfs.VirtualFile): String {
        return ProjectUtils.getToolFilePath(project, file)
    }

    protected fun getLineNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    protected fun getColumnNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        val lineNumber = document.getLineNumber(element.textOffset)
        return element.textOffset - document.getLineStartOffset(lineNumber) + 1
    }

    protected fun determineElementKind(element: PsiElement): String {
        return when {
            isRsTrait(element) -> "TRAIT"
            isRsStruct(element) -> "STRUCT"
            isRsEnum(element) -> "ENUM"
            isRsImpl(element) -> "IMPL"
            isRsFunction(element) -> "FUNCTION"
            isRsMod(element) -> "MODULE"
            else -> "SYMBOL"
        }
    }

}

/**
 * Rust implementation of [ImplementationsHandler].
 *
 * Finds implementations of traits:
 * - For traits: Finds all `impl TraitName for Type` blocks
 * - For trait methods: Finds all implementations of that method
 * - For struct/enum types: Not applicable (Rust has no type inheritance)
 */
class RustImplementationsHandler : BaseRustHandler<List<ImplementationData>>(), ImplementationsHandler {

    override val languageId = "Rust"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isRustLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.rust.isAvailable && rsTraitItemClass != null

    override fun findImplementations(
        element: PsiElement,
        project: Project,
        scope: BuiltInSearchScope
    ): List<ImplementationData>? {
        LOG.debug("Finding implementations for element at ${element.containingFile?.name}")
        val searchScope = createNavigationSearchScope(project, scope)

        // Check if it's a trait
        val trait = findContainingRsTrait(element)
        if (trait != null) {
            // If element is a method within the trait, find method implementations
            val function = findContainingRsFunction(element)
            if (function != null && isWithinTrait(function)) {
                LOG.debug("Finding method implementations for ${getName(function)}")
                return findMethodImplementations(project, function, trait, searchScope)
            }
            // Otherwise, find all trait implementations
            LOG.debug("Finding trait implementations for ${getName(trait)}")
            return findTraitImplementations(project, trait, searchScope)
        }

        // For methods in impl blocks, find the trait method and its implementations
        val function = findContainingRsFunction(element)
        if (function != null) {
            val impl = findContainingRsImpl(function)
            if (impl != null) {
                val traitRef = getTraitRef(impl)
                if (traitRef != null) {
                    val resolvedTrait = resolveReference(traitRef)
                    if (resolvedTrait != null && isRsTrait(resolvedTrait)) {
                        LOG.debug("Finding implementations of trait method ${getName(function)}")
                        return findMethodImplementations(project, function, resolvedTrait, searchScope)
                    }
                }
            }
        }

        return null
    }

    private fun isWithinTrait(function: PsiElement): Boolean {
        return findContainingRsTrait(function) != null
    }

    private fun findTraitImplementations(
        project: Project,
        trait: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        val results = mutableListOf<ImplementationData>()

        try {
            val traitName = getName(trait) ?: "unknown"

            DefinitionsScopedSearch.search(trait, searchScope).forEach(Processor { definition ->
                if (isRsImpl(definition) && shouldIncludeNavigationElement(searchScope, definition)) {
                    val typeRef = getTypeReference(definition)
                    val file = definition.containingFile?.virtualFile

                    if (file != null) {
                        val typeName = typeRef?.text?.trim() ?: "unknown"
                        results.add(ImplementationData(
                            name = "impl $traitName for $typeName",
                            qualifiedName = QualifiedNameUtil.getQualifiedName(definition),
                            file = getRelativePath(project, file),
                            line = getLineNumber(project, definition) ?: 0,
                            column = getColumnNumber(project, definition) ?: 0,
                            kind = "IMPL",
                            language = "Rust"
                        ))
                    }
                }
                results.size < 100
            })

            LOG.debug("Found ${results.size} trait implementations")
        } catch (e: Exception) {
            LOG.warn("Error finding trait implementations: ${e.message}")
        }

        return results
    }

    private fun findMethodImplementations(
        project: Project,
        method: PsiElement,
        trait: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        val results = mutableListOf<ImplementationData>()

        try {
            val methodName = getName(method) ?: return emptyList()

            // Use DefinitionsScopedSearch to find implementations of this method
            DefinitionsScopedSearch.search(method, searchScope).forEach(Processor { definition ->
                if (isRsFunction(definition) && definition != method && shouldIncludeNavigationElement(searchScope, definition)) {
                    val file = definition.containingFile?.virtualFile
                    if (file != null) {
                        val implItem = findContainingRsImpl(definition)
                        val typeName = implItem?.let { getTypeReference(it)?.text?.trim() } ?: ""

                        val displayName = if (typeName.isNotEmpty()) {
                            "$typeName::$methodName"
                        } else {
                            methodName
                        }

                        results.add(ImplementationData(
                            name = displayName,
                            qualifiedName = QualifiedNameUtil.getQualifiedName(definition),
                            file = getRelativePath(project, file),
                            line = getLineNumber(project, definition) ?: 0,
                            column = getColumnNumber(project, definition) ?: 0,
                            kind = "METHOD",
                            language = "Rust"
                        ))
                    }
                }
                results.size < 100
            })

            LOG.debug("Found ${results.size} method implementations for $methodName")
        } catch (e: Exception) {
            LOG.warn("Error finding method implementations: ${e.message}")
        }

        return results
    }
}

/**
 * Rust implementation of [SuperMethodsHandler].
 *
 * Finds the trait method that a given implementation overrides.
 *
 * **Rust-Specific Semantics:**
 * - Methods in `impl Trait for Type` blocks implement trait methods
 * - The "super method" is the method declaration in the trait
 * - Supertraits create a chain of method declarations
 *
 * **Limitations:**
 * - Only applicable for methods in impl blocks that implement a trait
 * - Standalone functions and inherent impl methods have no "super methods"
 */
class RustSuperMethodsHandler : BaseRustHandler<SuperMethodsData>(), SuperMethodsHandler {

    override val languageId = "Rust"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isRustLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.rust.isAvailable && rsFunctionClass != null

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val function = findContainingRsFunction(element) ?: return null
        LOG.debug("Finding super methods for ${getName(function)}")

        // Check if it's directly in a trait (then it's the declaration, not implementation)
        val containingTrait = findContainingRsTrait(function)
        if (containingTrait != null) {
            // This is a trait method declaration - check supertraits
            return findSuperMethodsFromTrait(project, function, containingTrait)
        }

        // Check if it's in an impl block
        val implItem = findContainingRsImpl(function)
        if (implItem == null) {
            // Standalone function - no super methods
            return null
        }

        val traitRef = getTraitRef(implItem)
        if (traitRef == null) {
            // Inherent impl (no trait) - no super methods
            return null
        }

        val trait = resolveReference(traitRef)
        if (trait == null || !isRsTrait(trait)) {
            return null
        }

        val methodName = getName(function) ?: return null
        val file = function.containingFile?.virtualFile

        val typeName = getTypeReference(implItem)?.text?.trim() ?: "unknown"

        val methodData = MethodData(
            name = methodName,
            signature = buildMethodSignature(function),
            containingClass = typeName,
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, function) ?: 0,
            column = getColumnNumber(project, function) ?: 0,
            language = "Rust"
        )

        val hierarchy = buildHierarchy(project, trait, methodName, mutableSetOf())
        LOG.debug("Found ${hierarchy.size} super methods")

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    private fun findSuperMethodsFromTrait(
        project: Project,
        function: PsiElement,
        trait: PsiElement
    ): SuperMethodsData? {
        val methodName = getName(function) ?: return null
        val file = function.containingFile?.virtualFile
        val traitName = getName(trait) ?: "unknown"

        val methodData = MethodData(
            name = methodName,
            signature = buildMethodSignature(function),
            containingClass = traitName,
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, function) ?: 0,
            column = getColumnNumber(project, function) ?: 0,
            language = "Rust"
        )

        // Find in supertraits
        val hierarchy = mutableListOf<SuperMethodData>()
        val superTraits = getSuperTraits(trait) ?: emptyList()

        for (superTraitRef in superTraits) {
            val superTrait = resolveReference(superTraitRef)
            if (superTrait != null && isRsTrait(superTrait)) {
                hierarchy.addAll(buildHierarchy(project, superTrait, methodName, mutableSetOf()))
            }
        }

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    private fun buildHierarchy(
        project: Project,
        trait: PsiElement,
        methodName: String,
        visited: MutableSet<String>,
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        val traitName = getName(trait) ?: return emptyList()
        if (traitName in visited) return emptyList()
        visited.add(traitName)

        // Find the method in this trait
        val traitMethod = findMethodInTrait(trait, methodName)
        if (traitMethod != null) {
            val file = traitMethod.containingFile?.virtualFile
            hierarchy.add(SuperMethodData(
                name = methodName,
                signature = buildMethodSignature(traitMethod),
                containingClass = traitName,
                containingClassKind = "TRAIT",
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, traitMethod),
                column = getColumnNumber(project, traitMethod),
                isInterface = true,  // Traits are like interfaces
                depth = depth,
                language = "Rust"
            ))
        }

        // Check supertraits
        val superTraits = getSuperTraits(trait) ?: emptyList()
        for (superTraitRef in superTraits) {
            val superTrait = resolveReference(superTraitRef)
            if (superTrait != null && isRsTrait(superTrait)) {
                hierarchy.addAll(buildHierarchy(project, superTrait, methodName, visited, depth + 1))
            }
        }

        return hierarchy
    }

    private fun findMethodInTrait(trait: PsiElement, methodName: String): PsiElement? {
        val functions = getFunctions(trait) ?: return null
        return functions.find { getName(it) == methodName }
    }

    private fun buildMethodSignature(function: PsiElement): String {
        return try {
            // Try to get the value parameter list
            val methodNames = listOf("getValueParameterList", "getParameterList")
            for (methodName in methodNames) {
                try {
                    val method = function.javaClass.getMethod(methodName)
                    val paramList = method.invoke(function) as? PsiElement
                    if (paramList != null) {
                        val params = paramList.text ?: "()"
                        val name = getName(function) ?: "unknown"
                        return "fn $name$params"
                    }
                } catch (e: NoSuchMethodException) {
                    continue
                }
            }
            "fn ${getName(function) ?: "unknown"}()"
        } catch (e: Exception) {
            "fn ${getName(function) ?: "unknown"}()"
        }
    }
}
