package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.toArgumentFailure
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.*
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.LambdaUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

/**
 * Registration entry point for Java language handlers.
 *
 * This class is loaded via reflection when the Java plugin is available.
 * It registers all Java-specific handlers with the [LanguageHandlerRegistry].
 */
object JavaHandlers {

    private val LOG = logger<JavaHandlers>()

    /**
     * Registers all Java handlers with the registry.
     *
     * Called via reflection from [LanguageHandlerRegistry].
     */
    @JvmStatic
    fun register(registry: LanguageHandlerRegistry) {
        if (!PluginDetectors.java.isAvailable) {
            LOG.info("Java plugin not available, skipping Java handler registration")
            return
        }

        registry.registerImplementationsHandler(JavaImplementationsHandler())
        registry.registerSymbolReferenceHandler(JavaSymbolReferenceHandler())
        registry.registerSuperMethodsHandler(JavaSuperMethodsHandler())

        LOG.info("Registered Java handlers")
    }
}

// Base class for Java handlers with common utilities
abstract class BaseJavaHandler<T> : LanguageHandler<T> {

    companion object {
        private val LOG = logger<BaseJavaHandler<*>>()

        /**
         * Maximum depth for traversing parent chain when searching for Kotlin PSI elements.
         * 50 levels is sufficient for even deeply nested code structures.
         */
        private const val MAX_PARENT_TRAVERSAL_DEPTH = 50

        /**
         * Maximum depth for searching parent chain for references.
         * 3 levels covers common cases: identifier -> expression -> call expression.
         */
        private const val MAX_REFERENCE_SEARCH_DEPTH = 3

        // Kotlin PSI classes (loaded via reflection to avoid compile-time dependency)
        private val ktClassOrObjectClass: Class<*>? by lazy {
            try {
                Class.forName("org.jetbrains.kotlin.psi.KtClassOrObject")
            } catch (e: ClassNotFoundException) {
                LOG.debug("Kotlin KtClassOrObject class not found: ${e.message}")
                null
            }
        }

        private val ktNamedFunctionClass: Class<*>? by lazy {
            try {
                Class.forName("org.jetbrains.kotlin.psi.KtNamedFunction")
            } catch (e: ClassNotFoundException) {
                LOG.debug("Kotlin KtNamedFunction class not found: ${e.message}")
                null
            }
        }

        private val ktPropertyAccessorClass: Class<*>? by lazy {
            try {
                Class.forName("org.jetbrains.kotlin.psi.KtPropertyAccessor")
            } catch (e: ClassNotFoundException) {
                LOG.debug("Kotlin KtPropertyAccessor class not found: ${e.message}")
                null
            }
        }

        private val ktPropertyClass: Class<*>? by lazy {
            try {
                Class.forName("org.jetbrains.kotlin.psi.KtProperty")
            } catch (e: ClassNotFoundException) {
                LOG.debug("Kotlin KtProperty class not found: ${e.message}")
                null
            }
        }

        // toLightClass extension function location
        private val lightClassExtensionsClass: Class<*>? by lazy {
            try {
                Class.forName("org.jetbrains.kotlin.asJava.LightClassUtilsKt")
            } catch (e: ClassNotFoundException) {
                LOG.debug("Kotlin LightClassUtilsKt class not found: ${e.message}")
                null
            }
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

    protected fun getClassKind(psiClass: PsiClass): String {
        return when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.isEnum -> "ENUM"
            psiClass.isAnnotationType -> "ANNOTATION"
            psiClass.isRecord -> "RECORD"
            psiClass.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

    protected fun findContainingClass(element: PsiElement): PsiClass? {
        if (element is PsiClass) return element
        return PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
    }

    protected fun findContainingMethod(element: PsiElement): PsiMethod? {
        if (element is PsiMethod) return element
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    /**
     * Resolves a method from a position, using semantic reference resolution first.
     *
     * This correctly handles:
     * - Method calls: `obj.doWork()` → resolves to the `doWork` method declaration
     * - Method declarations: cursor ON a method → returns that method
     * - Kotlin functions: finds KtNamedFunction and converts to light method
     *
     * @param element The leaf PSI element at a position
     * @return The resolved PsiMethod, or null if not found
     */
    protected fun resolveMethod(element: PsiElement): PsiMethod? {
        // If element is already a method, return it
        if (element is PsiMethod) return element

        // Try reference resolution first (for method calls/references)
        val resolved = resolveReference(element)
        if (resolved is PsiMethod) return resolved

        // Fallback based on language
        return if (element.language.id == "kotlin") {
            resolveKotlinMethod(element)
        } else {
            PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        }
    }

    /**
     * Resolves a Kotlin function to its light method (PsiMethod).
     * Walks up the parent chain to find a KtNamedFunction, KtPropertyAccessor, or KtProperty
     * and converts via toLightMethods().
     *
     * Important: local `val`/`var` declarations are also KtProperty nodes, but toLightMethods()
     * returns empty for them (no JVM method). In that case we continue walking up the parent chain
     * instead of returning null, so that we can find the enclosing KtNamedFunction (e.g. a test
     * method containing `val result = service.publishSchedule(...)`).
     */
    protected fun resolveKotlinMethod(element: PsiElement): PsiMethod? {
        val lightClassExtensions = lightClassExtensionsClass ?: return null

        // Find Kotlin declaration in parent chain
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < MAX_PARENT_TRAVERSAL_DEPTH) {
            // Check for KtNamedFunction, KtPropertyAccessor, or KtProperty
            val isKotlinDeclaration = (ktNamedFunctionClass?.isInstance(current) == true) ||
                (ktPropertyAccessorClass?.isInstance(current) == true) ||
                (ktPropertyClass?.isInstance(current) == true)

            if (isKotlinDeclaration) {
                // Convert to light method via toLightMethods() extension function.
                // For local val/var (KtProperty without a backing JVM method), toLightMethods()
                // returns empty. In that case we continue walking up to find the enclosing function
                // rather than returning null and losing the reference.
                try {
                    val toLightMethodsMethod = lightClassExtensions.getMethod("toLightMethods", PsiElement::class.java)
                    val lightMethods = toLightMethodsMethod.invoke(null, current) as? List<*>
                    val lightMethod = lightMethods?.firstOrNull() as? PsiMethod
                    if (lightMethod != null) return lightMethod
                    // Empty result (e.g. local val/var) — continue walking up the parent chain
                } catch (e: ReflectiveOperationException) {
                    LOG.debug("Failed to get light method for Kotlin element: ${e.javaClass.simpleName}: ${e.message}")
                    // Continue walking up on reflection failure
                }
            }
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Resolves a class from a position, using semantic reference resolution first.
     *
     * This correctly handles:
     * - Type references: `MyClass obj` → resolves to `MyClass` declaration
     * - Class declarations: cursor ON a class → returns that class
     * - Kotlin classes: finds KtClassOrObject and converts to light class
     *
     * @param element The leaf PSI element at a position
     * @return The resolved PsiClass, or null if not found
     */
    protected fun resolveClass(element: PsiElement): PsiClass? {
        // If element is already a class, return it
        if (element is PsiClass) return element

        // Try reference resolution first (for type references)
        val resolved = resolveReference(element)
        if (resolved is PsiClass) return resolved

        // Fallback based on language
        return if (element.language.id == "kotlin") {
            resolveKotlinClass(element)
        } else {
            PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        }
    }

    /**
     * Resolves a Kotlin class/object to its light class (PsiClass).
     */
    private fun resolveKotlinClass(element: PsiElement): PsiClass? {
        val ktClassOrObject = ktClassOrObjectClass ?: return null
        val lightClassExtensions = lightClassExtensionsClass ?: return null

        // Find KtClassOrObject in parent chain
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < MAX_PARENT_TRAVERSAL_DEPTH) {
            if (ktClassOrObject.isInstance(current)) {
                // Convert to light class via toLightClass extension function
                return try {
                    val toLightClassMethod = lightClassExtensions.getMethod("toLightClass", ktClassOrObject)
                    toLightClassMethod.invoke(null, current) as? PsiClass
                } catch (e: ReflectiveOperationException) {
                    LOG.debug("Failed to get light class for Kotlin class: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Resolves a reference from an element or its parents.
     *
     * Similar to [com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PsiUtils.findReferenceInParent]
     * but intentionally kept separate because this class adds Kotlin light class resolution
     * via [resolveMethod] and [resolveClass].
     *
     * @param element The starting element
     * @return The resolved element, or null
     */
    private fun resolveReference(element: PsiElement): PsiElement? {
        // Try direct reference
        element.reference?.resolve()?.let { return it }

        // Try parent references (some PSI structures have reference on parent)
        var current: PsiElement? = element
        repeat(MAX_REFERENCE_SEARCH_DEPTH) {
            current = current?.parent ?: return null
            current?.reference?.resolve()?.let { return it }
        }
        return null
    }

    protected fun isJavaOrKotlinLanguage(element: PsiElement): Boolean {
        val langId = element.language.id
        return langId == "JAVA" || langId == "kotlin"
    }
}

/**
 * Java implementation of [ImplementationsHandler].
 */
class JavaImplementationsHandler : BaseJavaHandler<List<ImplementationData>>(), ImplementationsHandler {

    override val languageId = "JAVA"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isJavaOrKotlinLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.java.isAvailable

    override fun findImplementations(
        element: PsiElement,
        project: Project,
        scope: BuiltInSearchScope
    ): List<ImplementationData>? {
        // Use reference-aware resolution: if cursor is on a method call/reference,
        // resolve to the actual method being referenced, not the containing method
        val method = resolveMethod(element)
        if (method != null) {
            return findMethodImplementations(project, method, createNavigationSearchScope(project, scope))
        }

        // Use reference-aware resolution for classes too
        val psiClass = resolveClass(element)
        if (psiClass != null) {
            return findClassImplementations(project, psiClass, createNavigationSearchScope(project, scope))
        }

        return null
    }

    private fun findMethodImplementations(
        project: Project,
        method: PsiMethod,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        val results = mutableListOf<ImplementationData>()
        try {
            OverridingMethodsSearch.search(method, searchScope, true).forEach(Processor { overridingMethod ->
                val file = overridingMethod.containingFile?.virtualFile
                if (file != null && shouldIncludeNavigationElement(searchScope, overridingMethod)) {
                    results.add(ImplementationData(
                        name = (overridingMethod.containingClass?.let { ClassPresentationUtil.getNameForClass(it, true) }
                            ?: "unknown") + "." + overridingMethod.name,
                        file = getRelativePath(project, file),
                        line = getLineNumber(project, overridingMethod) ?: 0,
                        column = getColumnNumber(project, overridingMethod) ?: 0,
                        kind = "METHOD",
                        language = if (overridingMethod.navigationElement.language.id == "kotlin") "Kotlin" else "Java",
                        qualifiedName = QualifiedNameUtil.getQualifiedName(overridingMethod)
                    ))
                }
                results.size < 100
            })
        } catch (_: Exception) {
            // Handle gracefully
        }

        // If the method is the SAM of a functional interface, also surface lambda /
        // method-reference assignments — matches IDE Goto-Implementation semantics.
        method.containingClass?.let { containingClass ->
            if (LambdaUtil.getFunctionalInterfaceMethod(containingClass) == method) {
                addFunctionalExpressionImpls(project, containingClass, searchScope, results)
            }
        }
        return results
    }

    private fun findClassImplementations(
        project: Project,
        psiClass: PsiClass,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        val results = mutableListOf<ImplementationData>()
        try {
            ClassInheritorsSearch.search(psiClass, searchScope, true).forEach(Processor { inheritor ->
                val file = inheritor.containingFile?.virtualFile
                if (file != null && shouldIncludeNavigationElement(searchScope, inheritor)) {
                    results.add(ImplementationData(
                        name = ClassPresentationUtil.getNameForClass(inheritor, true),
                        file = getRelativePath(project, file),
                        line = getLineNumber(project, inheritor) ?: 0,
                        column = getColumnNumber(project, inheritor) ?: 0,
                        kind = getClassKind(inheritor),
                        language = if (inheritor.navigationElement.language.id == "kotlin") "Kotlin" else "Java",
                        qualifiedName = QualifiedNameUtil.getQualifiedName(inheritor)
                    ))
                }
                results.size < 100
            })
        } catch (_: Exception) {
            // Handle gracefully
        }

        addFunctionalExpressionImpls(project, psiClass, searchScope, results)
        return results
    }

    private fun addFunctionalExpressionImpls(
        project: Project,
        psiClass: PsiClass,
        searchScope: GlobalSearchScope,
        results: MutableList<ImplementationData>,
        limit: Int = 100
    ) {
        if (!LambdaUtil.isFunctionalClass(psiClass)) return
        try {
            FunctionalExpressionSearch.search(psiClass, searchScope).forEach(Processor { funExpr ->
                val file = funExpr.containingFile?.virtualFile ?: return@Processor true
                val isMethodRef = funExpr is PsiMethodReferenceExpression
                val kind = if (isMethodRef) "METHOD_REFERENCE" else "LAMBDA"
                val enclosingMethod = PsiTreeUtil.getParentOfType(funExpr, PsiMethod::class.java)
                val enclosingClass = PsiTreeUtil.getParentOfType(funExpr, PsiClass::class.java)
                val name = buildString {
                    append(if (isMethodRef) "MethodRef" else "Lambda")
                    enclosingMethod?.let { append(" in ").append(it.name).append("()") }
                    enclosingClass?.let { append(" in ").append(ClassPresentationUtil.getNameForClass(it, true)) }
                }
                val resolvedQname: String? = if (funExpr is PsiMethodReferenceExpression) {
                    val resolved = funExpr.resolve() as? PsiElement
                    resolved?.let { QualifiedNameUtil.getQualifiedName(it) }
                } else null  // lambda has no FQN
                results.add(ImplementationData(
                    name = name,
                    file = getRelativePath(project, file),
                    line = getLineNumber(project, funExpr) ?: 0,
                    column = getColumnNumber(project, funExpr) ?: 0,
                    kind = kind,
                    language = "Java",
                    qualifiedName = resolvedQname
                ))
                results.size < limit
            })
        } catch (_: Exception) {
            // Handle gracefully
        }
    }
}

/**
 * Java implementation of [SuperMethodsHandler].
 */
class JavaSuperMethodsHandler : BaseJavaHandler<SuperMethodsData>(), SuperMethodsHandler {

    override val languageId = "JAVA"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isJavaOrKotlinLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.java.isAvailable

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        // Use reference-aware resolution: if cursor is on a method call,
        // resolve to the actual method being referenced
        val method = resolveMethod(element) ?: return null
        val containingClass = method.containingClass ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = method.name,
            signature = buildMethodSignature(method),
            containingClass = ClassPresentationUtil.getNameForClass(containingClass, true),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
            language = if (method.navigationElement.language.id == "kotlin") "Kotlin" else "Java"
        )

        val hierarchy = buildHierarchy(project, method)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    private fun buildHierarchy(
        project: Project,
        method: PsiMethod,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        for (superMethod in method.findSuperMethods()) {
            val key = QualifiedNameUtil.getQualifiedName(superMethod)
                ?: "${superMethod.containingClass?.let { ClassPresentationUtil.getNameForClass(it, true) }}.${superMethod.name}"
            if (key in visited) continue
            visited.add(key)

            val containingClass = superMethod.containingClass
            val file = superMethod.containingFile?.virtualFile

            hierarchy.add(SuperMethodData(
                name = superMethod.name,
                signature = buildMethodSignature(superMethod),
                containingClass = containingClass?.let { ClassPresentationUtil.getNameForClass(it, true) } ?: "unknown",
                containingClassKind = containingClass?.let { getClassKind(it) } ?: "UNKNOWN",
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, superMethod),
                column = getColumnNumber(project, superMethod),
                isInterface = containingClass?.isInterface == true,
                depth = depth,
                language = if (superMethod.navigationElement.language.id == "kotlin") "Kotlin" else "Java"
            ))

            hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
        }

        return hierarchy
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "${method.name}($params): $returnType"
    }
}

/**
 * Java implementation of [SymbolReferenceHandler].
 *
 * Resolves fully qualified symbol references (e.g., `com.example.MyClass#method(String)`)
 * to PSI elements using Java PSI APIs.
 */
class JavaSymbolReferenceHandler : BaseJavaHandler<PsiNamedElement>(), SymbolReferenceHandler {

    companion object {
        // A valid Java identifier: starts with a letter, underscore, or dollar sign
        private const val IDENTIFIER = """[a-zA-Z_$][a-zA-Z0-9_$]*"""

        // Dotted qualified name: at least two segments (package + class) separated by dots
        private const val QUALIFIED_NAME = """$IDENTIFIER(\.$IDENTIFIER)+"""

        // example: int, boolean
        private const val PRIMITIVE_TYPE = """(byte|short|int|long|float|double|boolean|char)"""

        // example: String, int[], Object..., package.ClassName, package.ClassName[]
        private const val PARAMETER_TYPE = """\s*($PRIMITIVE_TYPE|$QUALIFIED_NAME|$IDENTIFIER)(\[\])*(\.\.\.)?\s*"""

        // example: (int, String[]), (List, java.util.Map)
        private const val PARAMETER_LIST = """\(($PARAMETER_TYPE(,$PARAMETER_TYPE)*)?\)"""

        // example: com.example.MyClass, com.example.MyClass#fieldName, com.example.MyClass#methodName(int, String)
        internal val JAVA_SYMBOL_PATTERN = """^$QUALIFIED_NAME(#$IDENTIFIER($PARAMETER_LIST)?)?$""".toRegex()

        private val SYMBOL_EXAMPLES = listOf(
            "'com.example.ClassName'",
            "'com.example.ClassName#memberName'",
            "'com.example.ClassName#methodName(int[], String, java.util.List)'"
        )
    }

    override val languageId = "JAVA"
    override val languageName = "Java"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isJavaOrKotlinLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.java.isAvailable

    override fun resolveSymbol(project: Project, symbol: String): Result<PsiNamedElement> {
        val erasedSymbol = stripGenerics(symbol.trim())

        if (!JAVA_SYMBOL_PATTERN.matches(erasedSymbol)) {
            return ErrorMessages.invalidSymbolFormat(symbol, SYMBOL_EXAMPLES).toArgumentFailure()
        }

        val memberSeparatorIndex = erasedSymbol.indexOf('#')
        val classFqn: String
        val memberPart: String?

        if (memberSeparatorIndex >= 0) {
            classFqn = erasedSymbol.substring(0, memberSeparatorIndex)
            memberPart = erasedSymbol.substring(memberSeparatorIndex + 1)
        } else {
            classFqn = erasedSymbol
            memberPart = null
        }

        val psiClass = findClass(project, classFqn)
            ?: return ErrorMessages.typeNotFound(classFqn, project.name).toArgumentFailure()

        if (memberPart == null) {
            return Result.success(psiClass)
        }

        val parenOpenIndex = memberPart.indexOf('(')
        val parenCloseIndex = memberPart.lastIndexOf(')')
        if (parenOpenIndex >= 0 && parenCloseIndex > parenOpenIndex) {
            return resolveMethodWithParams(psiClass, classFqn, memberPart, parenOpenIndex, parenCloseIndex)
        }

        return resolveMemberByName(psiClass, classFqn, memberPart)
    }

    private fun findClass(project: Project, classFqn: String): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        return facade.findClass(classFqn, GlobalSearchScope.projectScope(project))
            ?: facade.findClass(classFqn, GlobalSearchScope.allScope(project))
    }

    private fun resolveMethodWithParams(
        psiClass: PsiClass,
        classFqn: String,
        memberPart: String,
        parenOpenIndex: Int,
        parenCloseIndex: Int
    ): Result<PsiNamedElement> {
        val methodName = memberPart.substring(0, parenOpenIndex)
        val paramsString = memberPart.substring(parenOpenIndex + 1, parenCloseIndex)
        val requestedParams = if (paramsString.isBlank()) emptyList()
        else paramsString.split(',').map { it.trim() }

        val allMethods = findMostDerivedMethods(psiClass, methodName)

        if (allMethods.isEmpty()) {
            return ErrorMessages.memberNotFoundInType(methodName, classFqn).toArgumentFailure()
        }

        val matchingMethods = allMethods.filter { method ->
            matchesParameterTypes(method, requestedParams)
        }

        return when {
            matchingMethods.isEmpty() -> {
                val signatures = buildDisambiguatingSignatures(methodName, allMethods)
                ErrorMessages.noMethodsMatch(memberPart, classFqn, signatures).toArgumentFailure()
            }
            matchingMethods.size == 1 -> Result.success(matchingMethods[0])
            else -> {
                val signatures = buildDisambiguatingSignatures(methodName, allMethods, matchingMethods)
                ErrorMessages.multipleMethodsMatch(memberPart, classFqn, signatures).toArgumentFailure()
            }
        }
    }

    private fun resolveMemberByName(
        psiClass: PsiClass,
        classFqn: String,
        memberName: String
    ): Result<PsiNamedElement> {
        // Try field/enum constant first (true = search supertypes)
        val field = psiClass.findFieldByName(memberName, true)
        if (field != null && isInheritedBy(field, psiClass)) {
            return Result.success(field)
        }

        // Try methods (searches supertypes, collapses overrides to most derived; includes constructors)
        val methods = findMostDerivedMethods(psiClass, memberName)

        return when {
            methods.isEmpty() -> ErrorMessages.memberNotFoundInType(memberName, classFqn).toArgumentFailure()
            methods.size == 1 -> Result.success(methods[0])
            else -> {
                val signatures = buildDisambiguatingSignatures(memberName, methods)
                ErrorMessages.multipleMethodsMatch(memberName, classFqn, signatures).toArgumentFailure()
            }
        }
    }

    private fun findMostDerivedMethods(psiClass: PsiClass, name: String): List<PsiMethod> {
        if (name == psiClass.name) {
            // If member name matches class name, it's a constructor. Return all constructors.
            return psiClass.constructors.toList()
        }

        val allMethods = if (psiClass.language.id == "JAVA") {
            psiClass.findMethodsByName(name, true).toList()
        } else {
            // Languages like Kotlin may mangle method names (e.g., for properties).
            // So we check both the method name and the navigation element's name.
            psiClass.allMethods.filter {
                it.name == name || (it.navigationElement as? PsiNamedElement)?.name == name
            }
        }

        val visibleMethods= allMethods.filter { isInheritedBy(it, psiClass) }

        if (visibleMethods.size <= 1) return visibleMethods

        val superMethods = mutableSetOf<PsiMethod>()
        for (method in visibleMethods) {
            if (method in superMethods) continue
            superMethods.addAll(method.findSuperMethods())
        }
        return visibleMethods.filter { it !in superMethods }
    }

    /**
     * Returns true when [member] is visible to [queryClass] through inheritance.
     *
     * Java visibility rules for inherited members:
     * - **private**: visible only within the declaring class
     * - **package-private** (default): visible only if [queryClass] is in the same package
     * - **protected / public**: always inherited
     */
    private fun isInheritedBy(member: PsiMember, queryClass: PsiClass): Boolean {
        val declaringClass = member.containingClass ?: return false
        if (declaringClass == queryClass) return true
        if (member.hasModifierProperty(PsiModifier.PRIVATE)) return false
        if (member.hasModifierProperty(PsiModifier.PUBLIC) || member.hasModifierProperty(PsiModifier.PROTECTED)) return true
        // Package-private: visible only if both classes are in the same package
        val memberPackage = (declaringClass.containingFile as? PsiJavaFile)?.packageName
        val queryPackage = (queryClass.containingFile as? PsiJavaFile)?.packageName
        return memberPackage != null && memberPackage == queryPackage
    }

    private fun matchesParameterTypes(method: PsiMethod, requestedTypes: List<String>): Boolean {
        if (requestedTypes.size != method.parameterList.parameters.size) return false
        if (requestedTypes.isEmpty()) return true

        return requestedTypes.zip(method.parameterList.parameters).all { (requested, parameter) ->
            val presentable = stripGenerics(parameter.type.presentableText)
            val canonical = stripGenerics(parameter.type.canonicalText)
            requested == presentable || requested == canonical
        }
    }

    private fun buildDisambiguatingSignatures(
        methodName: String,
        allMethods: List<PsiMethod>,
        matchingMethods: List<PsiMethod> = allMethods
    ): List<String> {
        val ambiguousTypes = allMethods
            .asSequence()
            .flatMap { it.parameterList.parameters.asSequence() }
            .groupBy(
                { stripGenerics(it.type.presentableText) },
                { stripGenerics(it.type.canonicalText) })
            .filter { it.value.distinct().size > 1 }
            .keys

        return matchingMethods.map { method ->
            val parameterTypes = method.parameterList.parameters.joinToString(", ") { parameter ->
                val presentable = stripGenerics(parameter.type.presentableText)
                if (presentable in ambiguousTypes) stripGenerics(parameter.type.canonicalText) else presentable
            }
            "$methodName($parameterTypes)"
        }
    }

    internal fun stripGenerics(input: String): String {
        if ('<' !in input) return input
        val sb = StringBuilder(input.length)
        var depth = 0
        for (ch in input) {
            when {
                ch == '<' -> depth++
                ch == '>' -> depth--
                // Only append characters that are outside of generic angle brackets
                depth == 0 -> sb.append(ch)
                // Unbalanced generics, just return original string
                depth < 0 -> return input
            }
        }
        if (depth != 0) return input
        return sb.toString()
    }
}
