package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServices
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsProvider
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class PhpSuperMethodsProvider : SuperMethodsProvider {

    companion object {
        private val LOG = logger<PhpSuperMethodsProvider>()
    }

    private val methodClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.Method") } catch (_: ClassNotFoundException) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val method = findContainingMethod(element) ?: return null
        getContainingClass(method) ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(method) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(method),
            kind = LanguageServices.getKind(method),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
        )

        return SuperMethodsData(method = methodData, hierarchy = buildHierarchy(project, method))
    }

    private fun buildHierarchy(
        project: Project,
        method: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()
        try {
            val containingClass = getContainingClass(method) ?: return emptyList()
            val methodName = getName(method) ?: return emptyList()

            // Superclass
            val superClass = getSuperClass(containingClass)
            if (superClass != null) {
                val superClassName = QualifiedNameUtil.getQualifiedName(superClass) ?: getName(superClass)
                val key = "$superClassName::$methodName"
                if (visited.add(key)) {
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
            }

            // Interfaces
            getImplementedInterfaces(containingClass)
                ?.filterIsInstance<PsiElement>()
                ?.forEach { iface ->
                    val ifaceName = QualifiedNameUtil.getQualifiedName(iface) ?: getName(iface)
                    val key = "$ifaceName::$methodName"
                    if (visited.add(key)) {
                        val ifaceMethod = findMethodInClass(iface, methodName)
                        if (ifaceMethod != null) {
                            val file = ifaceMethod.containingFile?.virtualFile
                            hierarchy.add(SuperMethodData(
                                name = methodName,
                                qualifiedName = QualifiedNameUtil.getQualifiedName(ifaceMethod),
                                kind = LanguageServices.getKind(ifaceMethod),
                                file = file?.let { getRelativePath(project, it) },
                                line = getLineNumber(project, ifaceMethod),
                                column = getColumnNumber(project, ifaceMethod),
                            ))
                            hierarchy.addAll(buildHierarchy(project, ifaceMethod, visited))
                        }
                    }
                }
        } catch (e: Exception) {
            LOG.debug("Error building PHP super-method hierarchy: ${e.message}")
        }
        return hierarchy
    }

    private fun findContainingMethod(element: PsiElement): PsiElement? {
        if (methodClass?.isInstance(element) == true) return element
        val method = methodClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, method as Class<out PsiElement>)
    }

    private fun getContainingClass(method: PsiElement): PsiElement? = runCatching {
        method.javaClass.getMethod("getContainingClass").invoke(method) as? PsiElement
    }.getOrNull()

    private fun getName(element: PsiElement): String? = runCatching {
        element.javaClass.getMethod("getName").invoke(element) as? String
    }.getOrNull()

    private fun getSuperClass(phpClass: PsiElement): PsiElement? = runCatching {
        phpClass.javaClass.getMethod("getSuperClass").invoke(phpClass) as? PsiElement
    }.getOrNull()

    private fun getImplementedInterfaces(phpClass: PsiElement): Array<*>? = runCatching {
        phpClass.javaClass.getMethod("getImplementedInterfaces").invoke(phpClass) as? Array<*>
    }.getOrNull()

    private fun findMethodInClass(phpClass: PsiElement, methodName: String): PsiElement? {
        return try {
            phpClass.javaClass.getMethod("findMethodByName", String::class.java)
                .invoke(phpClass, methodName) as? PsiElement
        } catch (_: Exception) {
            try {
                val methods = phpClass.javaClass.getMethod("getOwnMethods").invoke(phpClass) as? Array<*> ?: return null
                methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
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
