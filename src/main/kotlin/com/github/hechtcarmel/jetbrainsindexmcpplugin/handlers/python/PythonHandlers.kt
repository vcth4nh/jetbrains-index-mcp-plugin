package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.*
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * Registration entry point for Python language handlers.
 *
 * This class is loaded via reflection when the Python plugin is available.
 * It registers all Python-specific handlers with the [LanguageHandlerRegistry].
 *
 * ## Python PSI Classes Used (via reflection)
 *
 * - `com.jetbrains.python.psi.PyClass` - Python class declarations
 * - `com.jetbrains.python.psi.PyFunction` - Python function/method declarations
 * - `com.jetbrains.python.psi.PyCallExpression` - Function/method calls
 * - `com.jetbrains.python.psi.stubs.PyClassNameIndex` - Index for finding classes by name
 * - `com.jetbrains.python.psi.stubs.PyFunctionNameIndex` - Index for finding functions by name
 * - `com.jetbrains.python.psi.search.PyClassInheritorsSearch` - Search for subclasses
 * - `com.jetbrains.python.psi.search.PyOverridingMethodsSearch` - Search for overriding methods
 */
object PythonHandlers {

    private val LOG = logger<PythonHandlers>()

    /**
     * Registers all Python handlers with the registry.
     *
     * Called via reflection from [LanguageHandlerRegistry].
     */
    @JvmStatic
    fun register(registry: LanguageHandlerRegistry) {
        if (!PluginDetectors.python.isAvailable) {
            LOG.info("Python plugin not available, skipping Python handler registration")
            return
        }

        try {
            // Verify Python classes are accessible before registering
            Class.forName("com.jetbrains.python.psi.PyClass")
            Class.forName("com.jetbrains.python.psi.PyFunction")

            registry.registerImplementationsHandler(PythonImplementationsHandler())
            registry.registerSuperMethodsHandler(PythonSuperMethodsHandler())

            LOG.info("Registered Python handlers")
        } catch (e: ClassNotFoundException) {
            LOG.warn("Python PSI classes not found, skipping registration: ${e.message}")
        } catch (e: Exception) {
            LOG.warn("Failed to register Python handlers: ${e.message}")
        }
    }
}

/**
 * Base class for Python handlers with common utilities.
 *
 * Uses reflection to access Python PSI classes to avoid compile-time dependencies.
 */
abstract class BasePythonHandler<T> : LanguageHandler<T> {

    /**
     * Checks if the element is from a Python language.
     */
    protected fun isPythonLanguage(element: PsiElement): Boolean {
        return element.language.id == "Python"
    }

    protected val pyClassClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.python.psi.PyClass")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    protected val pyFunctionClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.python.psi.PyFunction")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    protected val pyCallExpressionClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.python.psi.PyCallExpression")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    protected val pyTypeEvalContextClass: Class<*>? by lazy {
        try {
            Class.forName("com.jetbrains.python.psi.types.TypeEvalContext")
        } catch (e: ClassNotFoundException) {
            null
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
     * Checks if element is a PyClass using reflection.
     */
    protected fun isPyClass(element: PsiElement): Boolean {
        return pyClassClass?.isInstance(element) == true
    }

    /**
     * Checks if element is a PyFunction using reflection.
     */
    protected fun isPyFunction(element: PsiElement): Boolean {
        return pyFunctionClass?.isInstance(element) == true
    }

    /**
     * Finds containing PyClass using reflection.
     */
    protected fun findContainingPyClass(element: PsiElement): PsiElement? {
        if (isPyClass(element)) return element
        val pyClass = pyClassClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, pyClass as Class<out PsiElement>)
    }

    /**
     * Finds containing PyFunction using reflection.
     */
    protected fun findContainingPyFunction(element: PsiElement): PsiElement? {
        if (isPyFunction(element)) return element
        val pyFunction = pyFunctionClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, pyFunction as Class<out PsiElement>)
    }

    /**
     * Gets the name of a PyClass or PyFunction via reflection.
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
     * Gets superclasses of a PyClass via reflection.
     */
    protected fun getSuperClasses(
        pyClass: PsiElement,
        context: Any? = createCodeAnalysisContext(pyClass.project, pyClass.containingFile)
    ): Array<*>? {
        val typeEvalContextClass = pyTypeEvalContextClass ?: return null
        return try {
            val method = pyClass.javaClass.getMethod("getSuperClasses", typeEvalContextClass)
            method.invoke(pyClass, context) as? Array<*>
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds a method by name in a PyClass via reflection.
     */
    protected fun findMethodInClass(
        pyClass: PsiElement,
        methodName: String,
        context: Any? = createUserInitiatedContext(pyClass.project, pyClass.containingFile)
    ): PsiElement? {
        val typeEvalContextClass = pyTypeEvalContextClass

        if (typeEvalContextClass != null) {
            try {
                val method = pyClass.javaClass.getMethod(
                    "findMethodByName",
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    typeEvalContextClass
                )
                val result = method.invoke(pyClass, methodName, false, context) as? PsiElement
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                // Fall back to enumerating methods below.
            }
        }

        return try {
            val getMethodsMethod = pyClass.javaClass.getMethod("getMethods")
            val methods = getMethodsMethod.invoke(pyClass) as? Array<*> ?: return null
            methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
        } catch (e: Exception) {
            null
        }
    }

    private fun createCodeAnalysisContext(project: Project, origin: PsiFile?): Any? {
        return createTypeEvalContext("codeAnalysis", project, origin)
    }

    private fun createUserInitiatedContext(project: Project, origin: PsiFile?): Any? {
        return createTypeEvalContext("userInitiated", project, origin)
    }

    private fun createTypeEvalContext(factoryMethod: String, project: Project, origin: PsiFile?): Any? {
        val typeEvalContextClass = pyTypeEvalContextClass ?: return null

        return try {
            val method = typeEvalContextClass.getMethod(factoryMethod, Project::class.java, PsiFile::class.java)
            method.invoke(null, project, origin)
        } catch (e: Exception) {
            try {
                val fallbackMethod = typeEvalContextClass.getMethod("codeInsightFallback", Project::class.java)
                fallbackMethod.invoke(null, project)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Python implementation of [ImplementationsHandler].
 */
class PythonImplementationsHandler : BasePythonHandler<List<ImplementationData>>(), ImplementationsHandler {

    override val languageId = "Python"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isPythonLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.python.isAvailable && pyClassClass != null

    override fun findImplementations(
        element: PsiElement,
        project: Project,
        scope: BuiltInSearchScope
    ): List<ImplementationData>? {
        val searchScope = createNavigationSearchScope(project, scope)
        val pyFunction = findContainingPyFunction(element)
        if (pyFunction != null) {
            return findMethodImplementations(project, pyFunction, searchScope)
        }

        val pyClass = findContainingPyClass(element)
        if (pyClass != null) {
            return findClassImplementations(project, pyClass, searchScope)
        }

        return null
    }

    private fun findMethodImplementations(
        project: Project,
        pyFunction: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        return try {
            val searchClass = Class.forName("com.jetbrains.python.psi.search.PyOverridingMethodsSearch")
            val searchMethod = searchClass.getMethod("search", pyFunctionClass, java.lang.Boolean.TYPE)
            val query = searchMethod.invoke(null, pyFunction, true)

            val findAllMethod = query.javaClass.getMethod("findAll")
            val overridingMethods = findAllMethod.invoke(query) as? Collection<*> ?: return emptyList()

            overridingMethods.filterIsInstance<PsiElement>()
                .filter { shouldIncludeNavigationElement(searchScope, it) }
                .take(100)
                .mapNotNull { overridingMethod ->
                    val file = overridingMethod.containingFile?.virtualFile ?: return@mapNotNull null
                    val containingClass = findContainingPyClass(overridingMethod)
                    val className = containingClass?.let { getName(it) } ?: ""
                    val methodName = getName(overridingMethod) ?: "unknown"
                    ImplementationData(
                        name = if (className.isNotEmpty()) "$className.$methodName" else methodName,
                        qualifiedName = QualifiedNameUtil.getQualifiedName(overridingMethod),
                        file = getRelativePath(project, file),
                        line = getLineNumber(project, overridingMethod) ?: 0,
                        column = getColumnNumber(project, overridingMethod) ?: 0,
                        kind = "METHOD",
                        language = "Python"
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findClassImplementations(
        project: Project,
        pyClass: PsiElement,
        searchScope: GlobalSearchScope
    ): List<ImplementationData> {
        return try {
            val searchClass = Class.forName("com.jetbrains.python.psi.search.PyClassInheritorsSearch")
            val searchMethod = searchClass.getMethod("search", pyClassClass, java.lang.Boolean.TYPE)
            val query = searchMethod.invoke(null, pyClass, true)

            val findAllMethod = query.javaClass.getMethod("findAll")
            val inheritors = findAllMethod.invoke(query) as? Collection<*> ?: return emptyList()

            inheritors.filterIsInstance<PsiElement>()
                .filter { shouldIncludeNavigationElement(searchScope, it) }
                .take(100)
                .mapNotNull { inheritor ->
                    val file = inheritor.containingFile?.virtualFile ?: return@mapNotNull null
                    ImplementationData(
                        name = QualifiedNameUtil.getQualifiedName(inheritor) ?: getName(inheritor) ?: "unknown",
                        qualifiedName = QualifiedNameUtil.getQualifiedName(inheritor),
                        file = getRelativePath(project, file),
                        line = getLineNumber(project, inheritor) ?: 0,
                        column = getColumnNumber(project, inheritor) ?: 0,
                        kind = "CLASS",
                        language = "Python"
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Python implementation of [SuperMethodsHandler].
 */
class PythonSuperMethodsHandler : BasePythonHandler<SuperMethodsData>(), SuperMethodsHandler {

    override val languageId = "Python"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isPythonLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.python.isAvailable && pyFunctionClass != null

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val pyFunction = findContainingPyFunction(element) ?: return null
        val containingClass = findContainingPyClass(pyFunction) ?: return null

        val file = pyFunction.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(pyFunction) ?: "unknown",
            signature = buildMethodSignature(pyFunction),
            containingClass = QualifiedNameUtil.getQualifiedName(containingClass) ?: getName(containingClass) ?: "unknown",
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, pyFunction) ?: 0,
            column = getColumnNumber(project, pyFunction) ?: 0,
            language = "Python"
        )

        val hierarchy = buildHierarchy(project, pyFunction)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    private fun buildHierarchy(
        project: Project,
        pyFunction: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        try {
            // Find super methods by looking at parent classes
            val containingClass = findContainingPyClass(pyFunction) ?: return emptyList()
            val methodName = getName(pyFunction) ?: return emptyList()

            val superClasses = getSuperClasses(containingClass)
            superClasses?.filterIsInstance<PsiElement>()?.forEach { superClass ->
                val superClassName = QualifiedNameUtil.getQualifiedName(superClass) ?: getName(superClass)
                val key = "$superClassName.$methodName"
                if (key in visited) return@forEach
                visited.add(key)

                // Find method with same name in superclass
                val superMethod = findMethodInClass(superClass, methodName)
                if (superMethod != null) {
                    val file = superMethod.containingFile?.virtualFile

                    hierarchy.add(SuperMethodData(
                        name = methodName,
                        signature = buildMethodSignature(superMethod),
                        containingClass = superClassName ?: "unknown",
                        containingClassKind = "CLASS",
                        file = file?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, superMethod),
                        column = getColumnNumber(project, superMethod),
                        isInterface = false,
                        depth = depth,
                        language = "Python"
                    ))

                    hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
                }
            }
        } catch (e: Exception) {
            // Handle gracefully
        }

        return hierarchy
    }

    private fun buildMethodSignature(pyFunction: PsiElement): String {
        return try {
            val getParameterListMethod = pyFunction.javaClass.getMethod("getParameterList")
            val parameterList = getParameterListMethod.invoke(pyFunction)
            val getParametersMethod = parameterList.javaClass.getMethod("getParameters")
            val parameters = getParametersMethod.invoke(parameterList) as? Array<*> ?: emptyArray<Any>()

            val params = parameters.filterIsInstance<PsiElement>().mapNotNull { param ->
                try {
                    val getNameMethod = param.javaClass.getMethod("getName")
                    getNameMethod.invoke(param) as? String
                } catch (e: Exception) {
                    null
                }
            }.joinToString(", ")

            val functionName = getName(pyFunction) ?: "unknown"
            "$functionName($params)"
        } catch (e: Exception) {
            getName(pyFunction) ?: "unknown"
        }
    }
}

