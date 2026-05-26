package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServices
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class PhpLanguageService : LanguageService() {

    companion object {
        private val LOG = logger<PhpLanguageService>()
    }

    override val languageIds: Set<String> by lazy {
        resolveLanguageId("com.jetbrains.php.lang.PhpLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "PHP"
    override fun isAvailable(): Boolean = PluginDetectors.php.isAvailable

    override val supportsSuperMethods: Boolean = true

    private val phpClassClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass") } catch (_: ClassNotFoundException) { null }
    }

    private val methodClass: Class<*>? by lazy {
        try { Class.forName("com.jetbrains.php.lang.psi.elements.Method") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val phpClass = phpClassClass ?: return null
        if (!phpClass.isInstance(element)) return null
        return try {
            val isInterface = invokeBoolean(element, "isInterface")
            val isTrait = invokeBoolean(element, "isTrait")
            val isEnum = invokeBoolean(element, "isEnum")
            val isAbstract = invokeBoolean(element, "isAbstract")
            when {
                isInterface -> "INTERFACE"
                isTrait -> "TRAIT"
                isEnum -> "ENUM"
                isAbstract -> "ABSTRACT_CLASS"
                else -> "CLASS"
            }
        } catch (_: Exception) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val method = findContainingMethod(element) ?: return null
        val containingClass = getContainingClass(method) ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(method) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(method),
            kind = LanguageServices.getKind(method),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
        )

        val hierarchy = buildHierarchy(project, method)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    // --- Super methods helpers ---

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
                            qualifiedName = QualifiedNameUtil.getQualifiedName(superMethod),
                            kind = LanguageServices.getKind(superMethod),
                            file = file?.let { getRelativePath(project, it) },
                            line = getLineNumber(project, superMethod),
                            column = getColumnNumber(project, superMethod),
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
                            qualifiedName = QualifiedNameUtil.getQualifiedName(ifaceMethod),
                            kind = LanguageServices.getKind(ifaceMethod),
                            file = file?.let { getRelativePath(project, it) },
                            line = getLineNumber(project, ifaceMethod),
                            column = getColumnNumber(project, ifaceMethod),
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

    // --- PHP PSI helpers ---

    private fun findContainingMethod(element: PsiElement): PsiElement? {
        if (methodClass?.isInstance(element) == true) return element
        val method = methodClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, method as Class<out PsiElement>)
    }

    private fun getContainingClass(method: PsiElement): PsiElement? {
        return try {
            val getContainingClassMethod = method.javaClass.getMethod("getContainingClass")
            getContainingClassMethod.invoke(method) as? PsiElement
        } catch (_: Exception) {
            null
        }
    }

    private fun getName(element: PsiElement): String? {
        return try {
            val method = element.javaClass.getMethod("getName")
            method.invoke(element) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun getSuperClass(phpClass: PsiElement): PsiElement? {
        return try {
            val method = phpClass.javaClass.getMethod("getSuperClass")
            method.invoke(phpClass) as? PsiElement
        } catch (_: Exception) {
            null
        }
    }

    private fun getImplementedInterfaces(phpClass: PsiElement): Array<*>? {
        return try {
            val method = phpClass.javaClass.getMethod("getImplementedInterfaces")
            method.invoke(phpClass) as? Array<*>
        } catch (_: Exception) {
            null
        }
    }

    private fun findMethodInClass(phpClass: PsiElement, methodName: String): PsiElement? {
        return try {
            val findMethodByNameMethod = phpClass.javaClass.getMethod("findMethodByName", String::class.java)
            findMethodByNameMethod.invoke(phpClass, methodName) as? PsiElement
        } catch (_: Exception) {
            try {
                val getMethodsMethod = phpClass.javaClass.getMethod("getOwnMethods")
                val methods = getMethodsMethod.invoke(phpClass) as? Array<*> ?: return null
                methods.filterIsInstance<PsiElement>().find { getName(it) == methodName }
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

    private fun invokeBoolean(target: Any, method: String): Boolean = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Boolean ?: false
    }.getOrDefault(false)
}
