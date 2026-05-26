package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServices
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsProvider
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class PythonSuperMethodsProvider : SuperMethodsProvider {

    private val pyClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyClass") } catch (_: ClassNotFoundException) { null }
    }

    private val pyFunctionClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.PyFunction") } catch (_: ClassNotFoundException) { null }
    }

    private val pyTypeEvalContextClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.python.psi.types.TypeEvalContext") } catch (_: ClassNotFoundException) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val pyFunction = findContainingPyFunction(element) ?: return null
        findContainingPyClass(pyFunction) ?: return null

        val file = pyFunction.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(pyFunction) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(pyFunction),
            kind = LanguageServices.getKind(pyFunction),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, pyFunction) ?: 0,
            column = getColumnNumber(project, pyFunction) ?: 0,
        )

        return SuperMethodsData(method = methodData, hierarchy = buildHierarchy(project, pyFunction))
    }

    private fun buildHierarchy(
        project: Project,
        pyFunction: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()
        try {
            val containingClass = findContainingPyClass(pyFunction) ?: return emptyList()
            val methodName = getName(pyFunction) ?: return emptyList()

            val superClasses = getSuperClasses(containingClass)
            superClasses?.filterIsInstance<PsiElement>()?.forEach { superClass ->
                val superClassName = QualifiedNameUtil.getQualifiedName(superClass) ?: getName(superClass)
                val key = "$superClassName.$methodName"
                if (!visited.add(key)) return@forEach

                val superMethod = findMethodInClass(superClass, methodName)
                if (superMethod != null) {
                    val file = superMethod.containingFile?.virtualFile
                    hierarchy.add(SuperMethodData(
                        name = methodName,
                        qualifiedName = QualifiedNameUtil.getQualifiedName(superMethod),
                        kind = LanguageServices.getKind(superMethod),
                        file = file?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, superMethod),
                        column = getColumnNumber(project, superMethod),
                    ))
                    hierarchy.addAll(buildHierarchy(project, superMethod, visited))
                }
            }
        } catch (_: Exception) {
            // Handle gracefully
        }
        return hierarchy
    }

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

    private fun getName(element: PsiElement): String? = runCatching {
        element.javaClass.getMethod("getName").invoke(element) as? String
    }.getOrNull()

    private fun getSuperClasses(
        pyClass: PsiElement,
        context: Any? = createCodeAnalysisContext(pyClass.project, pyClass.containingFile),
    ): Array<*>? {
        val typeEvalContextClass = pyTypeEvalContextClass ?: return null
        return try {
            pyClass.javaClass.getMethod("getSuperClasses", typeEvalContextClass)
                .invoke(pyClass, context) as? Array<*>
        } catch (_: Exception) { null }
    }

    private fun findMethodInClass(
        pyClass: PsiElement,
        methodName: String,
        context: Any? = createUserInitiatedContext(pyClass.project, pyClass.containingFile),
    ): PsiElement? {
        val typeEvalContextClass = pyTypeEvalContextClass

        if (typeEvalContextClass != null) {
            try {
                val method = pyClass.javaClass.getMethod(
                    "findMethodByName",
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    typeEvalContextClass,
                )
                val result = method.invoke(pyClass, methodName, false, context) as? PsiElement
                if (result != null) return result
            } catch (_: Exception) {
                // Fall back to enumerating methods below.
            }
        }

        return try {
            val methods = pyClass.javaClass.getMethod("getMethods").invoke(pyClass) as? Array<*> ?: return null
            methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
        } catch (_: Exception) { null }
    }

    private fun createCodeAnalysisContext(project: Project, origin: PsiFile?): Any? =
        createTypeEvalContext("codeAnalysis", project, origin)

    private fun createUserInitiatedContext(project: Project, origin: PsiFile?): Any? =
        createTypeEvalContext("userInitiated", project, origin)

    private fun createTypeEvalContext(factoryMethod: String, project: Project, origin: PsiFile?): Any? {
        val typeEvalContextClass = pyTypeEvalContextClass ?: return null
        return try {
            typeEvalContextClass.getMethod(factoryMethod, Project::class.java, PsiFile::class.java)
                .invoke(null, project, origin)
        } catch (_: Exception) {
            try {
                typeEvalContextClass.getMethod("codeInsightFallback", Project::class.java)
                    .invoke(null, project)
            } catch (_: Exception) { null }
        }
    }

    private fun getRelativePath(project: Project, file: VirtualFile): String =
        ProjectUtils.getToolFilePath(project, file)

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
