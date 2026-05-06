package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.*
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * Registration entry point for PHP language handlers.
 *
 * This class is loaded via reflection when the PHP plugin is available.
 * It registers all PHP-specific handlers with the [LanguageHandlerRegistry].
 *
 * ## PHP PSI Classes Used (via reflection)
 *
 * - `com.jetbrains.php.lang.psi.elements.PhpClass` - PHP class declarations
 * - `com.jetbrains.php.lang.psi.elements.Method` - PHP method declarations
 * - `com.jetbrains.php.lang.psi.elements.Function` - PHP function declarations
 * - `com.jetbrains.php.lang.psi.elements.Field` - PHP class field/property
 * - `com.jetbrains.php.lang.psi.elements.PhpNamedElement` - Any named PHP element
 * - `com.jetbrains.php.lang.psi.elements.MethodReference` - Method call reference
 * - `com.jetbrains.php.lang.psi.elements.FunctionReference` - Function call reference
 *
 * ## PHP-Specific Concepts
 *
 * - **Single Inheritance**: PHP uses single class inheritance with interfaces
 * - **Traits**: PHP traits are similar to mixins, shown in hierarchy
 * - **Interfaces**: Full interface/implementation support
 * - **Late Static Binding**: Properly handled by PhpStorm's type inference
 */
object PhpHandlers {

    private val LOG = logger<PhpHandlers>()

    /**
     * Registers all PHP handlers with the registry.
     *
     * Called via reflection from [LanguageHandlerRegistry].
     */
    @JvmStatic
    fun register(registry: LanguageHandlerRegistry) {
        if (!PluginDetectors.php.isAvailable) {
            LOG.info("PHP plugin not available, skipping PHP handler registration")
            return
        }

        try {
            // Verify PHP classes are accessible before registering
            Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass")
            Class.forName("com.jetbrains.php.lang.psi.elements.Method")

            registry.registerImplementationsHandler(PhpImplementationsHandler())
            registry.registerSuperMethodsHandler(PhpSuperMethodsHandler())

            LOG.info("Registered PHP handlers")
        } catch (e: ClassNotFoundException) {
            LOG.warn("PHP PSI classes not found, skipping registration: ${e.message}")
        } catch (e: Exception) {
            LOG.warn("Failed to register PHP handlers: ${e.message}")
        }
    }
}

/**
 * Base class for PHP handlers with common utilities.
 *
 * Uses reflection to access PHP PSI classes to avoid compile-time dependencies.
 */
abstract class BasePhpHandler<T> : LanguageHandler<T> {

    protected val LOG = logger<BasePhpHandler<*>>()

    /**
     * Checks if the element is from PHP language.
     */
    protected fun isPhpLanguage(element: PsiElement): Boolean {
        return element.language.id == "PHP"
    }

    // Lazy-loaded PHP PSI classes via reflection

    protected val phpClassClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass")
        } catch (e: ClassNotFoundException) {
            LOG.debug("PhpClass not found")
            null
        }
    }

    protected val methodClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.Method")
        } catch (e: ClassNotFoundException) {
            LOG.debug("Method not found")
            null
        }
    }

    protected val functionClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.Function")
        } catch (e: ClassNotFoundException) {
            LOG.debug("Function not found")
            null
        }
    }

    protected val fieldClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.Field")
        } catch (e: ClassNotFoundException) {
            LOG.debug("Field not found")
            null
        }
    }

    protected val phpNamedElementClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.lang.psi.elements.PhpNamedElement")
        } catch (e: ClassNotFoundException) {
            LOG.debug("PhpNamedElement not found")
            null
        }
    }

    /**
     * PhpIndex class for accessing PHP symbols and their relationships.
     * This is the central API for finding subclasses/implementations in PHP.
     */
    protected val phpIndexClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.php.PhpIndex")
        } catch (e: ClassNotFoundException) {
            LOG.debug("PhpIndex not found")
            null
        }
    }

    // Helper methods

    /**
     * Gets the PhpIndex instance for the given project.
     * PhpIndex is the central API for accessing PHP symbols.
     */
    protected fun getPhpIndex(project: Project): Any? {
        return try {
            val phpIndexCls = phpIndexClass ?: return null
            val getInstanceMethod = phpIndexCls.getMethod("getInstance", Project::class.java)
            getInstanceMethod.invoke(null, project)
        } catch (e: Exception) {
            LOG.debug("Error getting PhpIndex instance: ${e.message}")
            null
        }
    }

    /**
     * Gets all subclasses/implementations of a PHP class or interface using PhpIndex.
     *
     * @param project The current project
     * @param fqn The fully qualified name of the class/interface (e.g., "\App\Contracts\Describable")
     * @return Collection of PhpClass elements that extend/implement the given class/interface
     */
    protected fun getAllSubclasses(project: Project, fqn: String): Collection<PsiElement> {
        return try {
            val phpIndex = getPhpIndex(project) ?: return emptyList()

            val getAllSubclassesMethod = phpIndex.javaClass.getMethod("getAllSubclasses", String::class.java)
            val result = getAllSubclassesMethod.invoke(phpIndex, fqn)

            @Suppress("UNCHECKED_CAST")
            (result as? Collection<*>)?.filterIsInstance<PsiElement>() ?: emptyList()
        } catch (e: Exception) {
            LOG.debug("Error getting subclasses for $fqn: ${e.message}")
            emptyList()
        }
    }

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

    /**
     * Checks if element is a PhpClass using reflection.
     */
    protected fun isPhpClass(element: PsiElement): Boolean {
        return phpClassClass?.isInstance(element) == true
    }

    /**
     * Checks if element is a Method using reflection.
     */
    protected fun isMethod(element: PsiElement): Boolean {
        return methodClass?.isInstance(element) == true
    }

    /**
     * Checks if element is a Function using reflection.
     */
    protected fun isFunction(element: PsiElement): Boolean {
        return functionClass?.isInstance(element) == true
    }

    /**
     * Checks if element is a Field using reflection.
     */
    protected fun isField(element: PsiElement): Boolean {
        return fieldClass?.isInstance(element) == true
    }

    /**
     * Finds containing PhpClass using reflection.
     */
    protected fun findContainingPhpClass(element: PsiElement): PsiElement? {
        if (isPhpClass(element)) return element
        val phpClass = phpClassClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, phpClass as Class<out PsiElement>)
    }

    /**
     * Finds containing Method using reflection.
     */
    protected fun findContainingMethod(element: PsiElement): PsiElement? {
        if (isMethod(element)) return element
        val method = methodClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, method as Class<out PsiElement>)
    }

    /**
     * Finds containing Function using reflection.
     */
    protected fun findContainingFunction(element: PsiElement): PsiElement? {
        if (isFunction(element)) return element
        val function = functionClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, function as Class<out PsiElement>)
    }

    /**
     * Finds containing method or function.
     */
    protected fun findContainingCallable(element: PsiElement): PsiElement? {
        return findContainingMethod(element) ?: findContainingFunction(element)
    }

    /**
     * Resolves a method PSI element from a usage position. Returns null if [element]
     * is neither a method declaration nor a method reference resolvable to a method.
     *
     * Mirrors JavaImplementationsHandler.resolveMethod: at a usage like `$obj->method()`,
     * the leaf token is the identifier; walk parents looking for a Reference and resolve it.
     * Falls back to syntactic walk via [findContainingMethod] for declaration sites.
     */
    protected fun resolveMethod(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 4) {
            val ref = (current as? PsiReference) ?: current.reference
            if (ref != null) {
                val resolved = try { ref.resolve() } catch (_: Exception) { null }
                if (resolved != null && isPhpMethodLike(resolved)) return resolved
            }
            current = current.parent
            depth++
        }
        // Fall back to syntactic walk (works at declaration sites).
        return findContainingMethod(element)
    }

    /**
     * Resolves a class/interface/trait PSI element from a usage position.
     *
     * Mirrors JavaImplementationsHandler.resolveClass: tries reference resolution first
     * to handle usages like type-hints, falls back to [findContainingPhpClass] otherwise.
     */
    protected fun resolveClass(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 4) {
            val ref = (current as? PsiReference) ?: current.reference
            if (ref != null) {
                val resolved = try { ref.resolve() } catch (_: Exception) { null }
                if (resolved != null && isPhpClassLike(resolved)) return resolved
            }
            current = current.parent
            depth++
        }
        return findContainingPhpClass(element)
    }

    private fun isPhpMethodLike(element: PsiElement): Boolean {
        val name = element.javaClass.name
        return name.contains("Method") || name.contains("Function")
    }

    private fun isPhpClassLike(element: PsiElement): Boolean {
        val name = element.javaClass.name
        return name.contains("PhpClass") ||
            name.contains("ClassImpl") ||
            name.contains("Trait") ||
            name.contains("Interface")
    }

    /**
     * Gets the name of a PHP element via reflection.
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
     * Gets the superclass of a PhpClass via reflection.
     */
    protected fun getSuperClass(phpClass: PsiElement): PsiElement? {
        return try {
            val method = phpClass.javaClass.getMethod("getSuperClass")
            method.invoke(phpClass) as? PsiElement
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets implemented interfaces of a PhpClass via reflection.
     */
    protected fun getImplementedInterfaces(phpClass: PsiElement): Array<*>? {
        return try {
            val method = phpClass.javaClass.getMethod("getImplementedInterfaces")
            method.invoke(phpClass) as? Array<*>
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets traits used by a PhpClass via reflection.
     */
    protected fun getTraits(phpClass: PsiElement): Array<*>? {
        return try {
            val method = phpClass.javaClass.getMethod("getTraits")
            method.invoke(phpClass) as? Array<*>
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a PhpClass is an interface via reflection.
     */
    protected fun isInterface(phpClass: PsiElement): Boolean {
        return try {
            val method = phpClass.javaClass.getMethod("isInterface")
            method.invoke(phpClass) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a PhpClass is a trait via reflection.
     */
    protected fun isTrait(phpClass: PsiElement): Boolean {
        return try {
            val method = phpClass.javaClass.getMethod("isTrait")
            method.invoke(phpClass) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a PhpClass is abstract via reflection.
     */
    protected fun isAbstract(phpClass: PsiElement): Boolean {
        return try {
            val method = phpClass.javaClass.getMethod("isAbstract")
            method.invoke(phpClass) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a PhpClass is an enum via reflection.
     */
    protected fun isEnum(phpClass: PsiElement): Boolean {
        return try {
            val method = phpClass.javaClass.getMethod("isEnum")
            method.invoke(phpClass) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the containing class of a method via reflection.
     */
    protected fun getContainingClass(method: PsiElement): PsiElement? {
        return try {
            val getContainingClassMethod = method.javaClass.getMethod("getContainingClass")
            getContainingClassMethod.invoke(method) as? PsiElement
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determines the kind of a PHP class element.
     *
     * Order matters: interface/trait/enum are mutually exclusive declaration forms
     * and take priority over abstract. PHP forbids abstract enums at the language
     * level, so isEnum-before-isAbstract is also a defensive correctness guard.
     */
    protected fun determineClassKind(element: PsiElement): String {
        return when {
            isInterface(element) -> "INTERFACE"
            isTrait(element) -> "TRAIT"
            isEnum(element) -> "ENUM"
            isAbstract(element) -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

    /**
     * Determines the kind of a PHP element.
     */
    protected fun determineElementKind(element: PsiElement): String {
        return when {
            isPhpClass(element) -> determineClassKind(element)
            isMethod(element) -> "METHOD"
            isFunction(element) -> "FUNCTION"
            isField(element) -> "FIELD"
            else -> "SYMBOL"
        }
    }

    /**
     * Finds a method by name in a PhpClass using reflection.
     * Uses `findMethodByName()` API, falling back to searching `getOwnMethods()`.
     */
    protected fun findMethodInClass(phpClass: PsiElement, methodName: String): PsiElement? {
        return try {
            val findMethodByNameMethod = phpClass.javaClass.getMethod("findMethodByName", String::class.java)
            findMethodByNameMethod.invoke(phpClass, methodName) as? PsiElement
        } catch (e: Exception) {
            // Fallback to getOwnMethods and search manually
            try {
                val getMethodsMethod = phpClass.javaClass.getMethod("getOwnMethods")
                val methods = getMethodsMethod.invoke(phpClass) as? Array<*> ?: return null
                methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
            } catch (e2: Exception) {
                null
            }
        }
    }
}

/**
 * PHP implementation of [ImplementationsHandler].
 *
 * Finds implementations of:
 * - Interfaces (classes that implement the interface)
 * - Abstract classes (classes that extend the abstract class)
 * - Abstract/interface methods (concrete method implementations)
 */
class PhpImplementationsHandler : BasePhpHandler<List<ImplementationData>>(), ImplementationsHandler {

    override val languageId = "PHP"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isPhpLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.php.isAvailable && phpClassClass != null

    override fun findImplementations(
        element: PsiElement,
        project: Project,
        scope: BuiltInSearchScope
    ): List<ImplementationData>? {
        LOG.debug("Finding implementations for element at ${element.containingFile?.name}")
        val searchScope = createNavigationSearchScope(project, scope)

        // Try reference resolution first (handles usage positions like `$obj->method()`).
        val method = resolveMethod(element)
        if (method != null) {
            LOG.debug("Finding method implementations for ${getName(method)}")
            return findMethodImplementations(project, method, searchScope)
        }

        // Reference-aware resolution for classes too (handles type-hint usages).
        val phpClass = resolveClass(element)
        if (phpClass != null) {
            LOG.debug("Finding class implementations for ${getName(phpClass)}")
            return findClassImplementations(project, phpClass, searchScope)
        }

        return null
    }

    private fun findMethodImplementations(
        project: Project,
        method: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        return try {
            val methodName = getName(method) ?: return emptyList()
            val containingClass = getContainingClass(method) ?: return emptyList()
            // PhpIndex.getAllSubclasses expects the PhpClass FQN form (e.g. "\Namespace\Foo").
            // PhpQualifiedNameProvider currently produces this format; if that ever diverges,
            // this path will silently return empty results — fall back to PhpClass#getFQN directly.
            val classFqn = QualifiedNameUtil.getQualifiedName(containingClass) ?: return emptyList()

            val results = mutableListOf<ImplementationData>()

            // Use PhpIndex to get all subclasses, then find methods with the same name
            val subclasses = getAllSubclasses(project, classFqn)

            subclasses
                .filter { shouldIncludeNavigationElement(searchScope, it) }
                .take(100)
                .forEach { subclass ->
                // Find method with same name in this subclass
                val overridingMethod = findMethodInClass(subclass, methodName)
                if (overridingMethod != null) {
                    val file = overridingMethod.containingFile?.virtualFile
                    if (file != null) {
                        val className = getName(subclass) ?: ""
                        results.add(ImplementationData(
                            name = if (className.isNotEmpty()) "$className::$methodName" else methodName,
                            qualifiedName = QualifiedNameUtil.getQualifiedName(overridingMethod),
                            file = getRelativePath(project, file),
                            line = getLineNumber(project, overridingMethod) ?: 0,
                            column = getColumnNumber(project, overridingMethod) ?: 0,
                            kind = "METHOD",
                            language = "PHP"
                        ))
                    }
                }
            }

            LOG.debug("Found ${results.size} method implementations for $methodName in $classFqn using PhpIndex")
            results
        } catch (e: Exception) {
            LOG.warn("Error finding method implementations: ${e.message}")
            emptyList()
        }
    }

    private fun findClassImplementations(
        project: Project,
        phpClass: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        return try {
            // PhpIndex.getAllSubclasses expects the PhpClass FQN form (e.g. "\Namespace\Foo").
            // PhpQualifiedNameProvider currently produces this format; if that ever diverges,
            // this path will silently return empty results — fall back to PhpClass#getFQN directly.
            val fqn = QualifiedNameUtil.getQualifiedName(phpClass) ?: return emptyList()
            val results = mutableListOf<ImplementationData>()

            // Use PhpIndex.getAllSubclasses() - the correct API for finding PHP implementations
            val subclasses = getAllSubclasses(project, fqn)

            subclasses
                .filter { shouldIncludeNavigationElement(searchScope, it) }
                .take(100)
                .forEach { subclass ->
                val file = subclass.containingFile?.virtualFile
                if (file != null) {
                    results.add(ImplementationData(
                        name = QualifiedNameUtil.getQualifiedName(subclass) ?: getName(subclass) ?: "unknown",
                        qualifiedName = QualifiedNameUtil.getQualifiedName(subclass),
                        file = getRelativePath(project, file),
                        line = getLineNumber(project, subclass) ?: 0,
                        column = getColumnNumber(project, subclass) ?: 0,
                        kind = determineClassKind(subclass),
                        language = "PHP"
                    ))
                }
            }

            LOG.debug("Found ${results.size} implementations for $fqn using PhpIndex")
            results
        } catch (e: Exception) {
            LOG.warn("Error finding class implementations: ${e.message}")
            emptyList()
        }
    }
}

/**
 * PHP implementation of [SuperMethodsHandler].
 *
 * Finds all parent methods that a method overrides or implements:
 * - Methods from parent classes (extends)
 * - Methods from implemented interfaces (implements)
 */
class PhpSuperMethodsHandler : BasePhpHandler<SuperMethodsData>(), SuperMethodsHandler {

    override val languageId = "PHP"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isPhpLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.php.isAvailable && methodClass != null

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val method = findContainingMethod(element) ?: return null
        val containingClass = getContainingClass(method) ?: return null

        LOG.debug("Finding super methods for ${getName(method)}")

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(method) ?: "unknown",
            signature = buildMethodSignature(method),
            containingClass = QualifiedNameUtil.getQualifiedName(containingClass) ?: getName(containingClass) ?: "unknown",
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
            language = "PHP"
        )

        val hierarchy = buildHierarchy(project, method)
        LOG.debug("Found ${hierarchy.size} super methods")

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    private fun buildHierarchy(
        project: Project,
        method: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        try {
            val containingClass = getContainingClass(method) ?: return emptyList()
            val methodName = getName(method) ?: return emptyList()

            // Check superclass
            val superClass = getSuperClass(containingClass)
            if (superClass != null) {
                val superClassName = QualifiedNameUtil.getQualifiedName(superClass) ?: getName(superClass)
                val key = "$superClassName::$methodName"
                if (key !in visited) {
                    visited.add(key)

                    val superMethod = findMethodInClass(superClass, methodName)
                    if (superMethod != null) {
                        val file = superMethod.containingFile?.virtualFile

                        hierarchy.add(SuperMethodData(
                            name = methodName,
                            signature = buildMethodSignature(superMethod),
                            containingClass = superClassName ?: "unknown",
                            containingClassKind = determineClassKind(superClass),
                            file = file?.let { getRelativePath(project, it) },
                            line = getLineNumber(project, superMethod),
                            column = getColumnNumber(project, superMethod),
                            isInterface = isInterface(superClass),
                            depth = depth,
                            language = "PHP"
                        ))

                        hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
                    }
                }
            }

            // Check interfaces
            val interfaces = getImplementedInterfaces(containingClass)
            interfaces?.filterIsInstance<PsiElement>()?.forEach { iface ->
                val ifaceName = QualifiedNameUtil.getQualifiedName(iface) ?: getName(iface)
                val key = "$ifaceName::$methodName"
                if (key !in visited) {
                    visited.add(key)

                    val ifaceMethod = findMethodInClass(iface, methodName)
                    if (ifaceMethod != null) {
                        val file = ifaceMethod.containingFile?.virtualFile

                        hierarchy.add(SuperMethodData(
                            name = methodName,
                            signature = buildMethodSignature(ifaceMethod),
                            containingClass = ifaceName ?: "unknown",
                            containingClassKind = "INTERFACE",
                            file = file?.let { getRelativePath(project, it) },
                            line = getLineNumber(project, ifaceMethod),
                            column = getColumnNumber(project, ifaceMethod),
                            isInterface = true,
                            depth = depth,
                            language = "PHP"
                        ))

                        // Interfaces can extend other interfaces
                        hierarchy.addAll(buildHierarchy(project, ifaceMethod, visited, depth + 1))
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error building hierarchy: ${e.message}")
        }

        return hierarchy
    }

    private fun buildMethodSignature(method: PsiElement): String {
        return try {
            // Try to get parameters
            val getParametersMethod = method.javaClass.getMethod("getParameters")
            val parameters = getParametersMethod.invoke(method) as? Array<*> ?: emptyArray<Any>()

            val params = parameters.filterIsInstance<PsiElement>().mapNotNull { param ->
                try {
                    val getName = param.javaClass.getMethod("getName")
                    val name = getName.invoke(param) as? String ?: return@mapNotNull null

                    // Try to get type
                    val type = try {
                        val getType = param.javaClass.getMethod("getDeclaredType")
                        val typeElement = getType.invoke(param)
                        if (typeElement != null) {
                            val toStringMethod = typeElement.javaClass.getMethod("toString")
                            toStringMethod.invoke(typeElement) as? String
                        } else null
                    } catch (e: Exception) { null }

                    if (type != null) "$type \$$name" else "\$$name"
                } catch (e: Exception) {
                    null
                }
            }.joinToString(", ")

            val methodName = getName(method) ?: "unknown"
            "$methodName($params)"
        } catch (e: Exception) {
            getName(method) ?: "unknown"
        }
    }
}
