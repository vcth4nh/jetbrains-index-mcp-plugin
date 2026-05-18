package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServiceRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class PythonLanguageService : LanguageService() {
    override val languageIds: Set<String> by lazy {
        resolveLanguageIdViaGetInstance("com.jetbrains.python.PythonLanguage")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Python"
    override fun isAvailable(): Boolean = PluginDetectors.python.isAvailable

    override val supportsSuperMethods: Boolean = true

    private val pyTargetExpressionClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyTargetExpression") } catch (_: ClassNotFoundException) { null }
    }

    private val pyClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyClass") } catch (_: ClassNotFoundException) { null }
    }

    private val pyFunctionClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyFunction") } catch (_: ClassNotFoundException) { null }
    }

    private val pyTypeEvalContextClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.types.TypeEvalContext") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val pyClass = pyTargetExpressionClass ?: return null
        if (!pyClass.isInstance(element)) return null
        return try {
            val containingClass = element.javaClass.getMethod("getContainingClass").invoke(element)
            if (containingClass != null) "FIELD" else "VARIABLE"
        } catch (_: Exception) { "FIELD" }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val pyFunction = findContainingPyFunction(element) ?: return null
        val containingClass = findContainingPyClass(pyFunction) ?: return null

        val file = pyFunction.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(pyFunction) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(pyFunction),
            kind = LanguageServiceRegistry.getKind(pyFunction),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, pyFunction) ?: 0,
            column = getColumnNumber(project, pyFunction) ?: 0,
        )

        val hierarchy = buildHierarchy(project, pyFunction)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    // --- Super methods helpers ---

    private fun buildHierarchy(
        project: Project,
        pyFunction: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        try {
            val containingClass = findContainingPyClass(pyFunction) ?: return emptyList()
            val methodName = getName(pyFunction) ?: return emptyList()

            val superClasses = getSuperClasses(containingClass)
            superClasses?.filterIsInstance<PsiElement>()?.forEach { superClass ->
                val superClassName = QualifiedNameUtil.getQualifiedName(superClass) ?: getName(superClass)
                val key = "$superClassName.$methodName"
                if (key in visited) return@forEach
                visited.add(key)

                val superMethod = findMethodInClass(superClass, methodName)
                if (superMethod != null) {
                    val file = superMethod.containingFile?.virtualFile

                    hierarchy.add(SuperMethodData(
                        name = methodName,
                        qualifiedName = QualifiedNameUtil.getQualifiedName(superMethod),
                        kind = LanguageServiceRegistry.getKind(superMethod),
                        file = file?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, superMethod),
                        column = getColumnNumber(project, superMethod),
                    ))

                    hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
                }
            }
        } catch (_: Exception) {
            // Handle gracefully
        }

        return hierarchy
    }

    // --- Python PSI helpers ---

    private fun findContainingPyFunction(element: PsiElement): PsiElement? {
        if (pyFunctionClass?.isInstance(element) == true) return element
        val pyFunction = pyFunctionClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, pyFunction as Class<out PsiElement>)
    }

    private fun findContainingPyClass(element: PsiElement): PsiElement? {
        if (pyClassClass?.isInstance(element) == true) return element
        val pyClass = pyClassClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, pyClass as Class<out PsiElement>)
    }

    private fun getName(element: PsiElement): String? {
        return try {
            val method = element.javaClass.getMethod("getName")
            method.invoke(element) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun getSuperClasses(
        pyClass: PsiElement,
        context: Any? = createCodeAnalysisContext(pyClass.project, pyClass.containingFile)
    ): Array<*>? {
        val typeEvalContextClass = pyTypeEvalContextClass ?: return null
        return try {
            val method = pyClass.javaClass.getMethod("getSuperClasses", typeEvalContextClass)
            method.invoke(pyClass, context) as? Array<*>
        } catch (_: Exception) {
            null
        }
    }

    private fun findMethodInClass(
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
                if (result != null) return result
            } catch (_: Exception) {
                // Fall back to enumerating methods below.
            }
        }

        return try {
            val getMethodsMethod = pyClass.javaClass.getMethod("getMethods")
            val methods = getMethodsMethod.invoke(pyClass) as? Array<*> ?: return null
            methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            try {
                val fallbackMethod = typeEvalContextClass.getMethod("codeInsightFallback", Project::class.java)
                fallbackMethod.invoke(null, project)
            } catch (_: Exception) {
                null
            }
        }
    }

    // --- Position helpers ---

    private fun getRelativePath(project: Project, file: com.intellij.openapi.vfs.VirtualFile): String {
        return ProjectUtils.getToolFilePath(project, file)
    }

    private fun getLineNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun getColumnNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        val lineNumber = document.getLineNumber(element.textOffset)
        return element.textOffset - document.getLineStartOffset(lineNumber) + 1
    }
}
