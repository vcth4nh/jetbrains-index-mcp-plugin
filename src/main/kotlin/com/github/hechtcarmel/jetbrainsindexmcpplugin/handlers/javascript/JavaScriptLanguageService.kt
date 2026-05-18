package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.javascript

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServiceRegistry
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

class JavaScriptLanguageService : LanguageService() {

    companion object {
        private val LOG = logger<JavaScriptLanguageService>()
    }

    override val languageIds: Set<String> by lazy {
        buildSet {
            resolveLanguageId("com.intellij.lang.javascript.JavascriptLanguage", "INSTANCE")?.let { add(it) }
            resolveLanguageId("com.intellij.lang.javascript.TypeScriptLanguage", "INSTANCE")?.let { add(it) }
            listOf("ECMAScript 6", "JSX Harmony", "TypeScript JSX").forEach { add(it) }
        }
    }
    override val displayName = "JavaScript/TypeScript"
    override fun isAvailable(): Boolean = PluginDetectors.javaScript.isAvailable

    override val supportsSuperMethods: Boolean = true

    // JS/TS kind resolution is already handled well by the platform's
    // JSNamedElementKind via LanguageFindUsages.getType(). No override needed.

    // Lazy-loaded JS PSI classes via reflection

    private val jsClassClass: Class<*>? by lazy {
        try {
            Class.forName("com.intellij.lang.javascript.psi.ecmal4.JSClass")
        } catch (_: ClassNotFoundException) {
            try { Class.forName("com.intellij.lang.javascript.psi.JSClass") } catch (_: ClassNotFoundException) { null }
        }
    }

    private val jsFunctionClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.JSFunction") } catch (_: ClassNotFoundException) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val jsFunction = findContainingJSFunction(element) ?: return null
        val containingClass = findContainingJSClass(jsFunction) ?: return null

        val file = jsFunction.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(jsFunction) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(jsFunction),
            kind = LanguageServiceRegistry.getKind(jsFunction),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, jsFunction) ?: 0,
            column = getColumnNumber(project, jsFunction) ?: 0,
        )

        val hierarchy = buildHierarchy(project, jsFunction)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    // --- Super methods helpers ---

    private fun buildHierarchy(
        project: Project,
        jsFunction: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        try {
            val containingClass = findContainingJSClass(jsFunction) ?: return emptyList()
            val methodName = getName(jsFunction) ?: return emptyList()

            // Get superclasses and look for methods with the same name
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

            // Also check implemented interfaces
            val interfaces = getImplementedInterfaces(containingClass)
            interfaces?.filterIsInstance<PsiElement>()?.forEach { iface ->
                val ifaceName = QualifiedNameUtil.getQualifiedName(iface) ?: getName(iface)
                val key = "$ifaceName.$methodName"
                if (key in visited) return@forEach
                visited.add(key)

                val superMethod = findMethodInClass(iface, methodName)
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
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error building hierarchy: ${e.message}")
        }

        return hierarchy
    }

    // --- JS PSI helpers ---

    private fun findContainingJSClass(element: PsiElement): PsiElement? {
        if (jsClassClass?.isInstance(element) == true) return element
        val jsClass = jsClassClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, jsClass as Class<out PsiElement>)
    }

    private fun findContainingJSFunction(element: PsiElement): PsiElement? {
        if (jsFunctionClass?.isInstance(element) == true) return element
        val jsFunction = jsFunctionClass ?: return null
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, jsFunction as Class<out PsiElement>)
    }

    private fun getName(element: PsiElement): String? {
        return try {
            val method = element.javaClass.getMethod("getName")
            method.invoke(element) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun getSuperClasses(jsClass: PsiElement): Array<*>? {
        return try {
            val method = jsClass.javaClass.getMethod("getSuperClasses")
            method.invoke(jsClass) as? Array<*>
        } catch (_: Exception) {
            null
        }
    }

    private fun getImplementedInterfaces(jsClass: PsiElement): Array<*>? {
        return try {
            val method = jsClass.javaClass.getMethod("getImplementedInterfaces")
            method.invoke(jsClass) as? Array<*>
        } catch (_: Exception) {
            null
        }
    }

    private fun findMethodInClass(jsClass: PsiElement, methodName: String): PsiElement? {
        return try {
            val findFunctionMethod = jsClass.javaClass.getMethod("findFunctionByName", String::class.java)
            findFunctionMethod.invoke(jsClass, methodName) as? PsiElement
        } catch (_: Exception) {
            try {
                val getFunctionsMethod = jsClass.javaClass.getMethod("getFunctions")
                val functions = getFunctionsMethod.invoke(jsClass) as? Array<*> ?: return null
                functions.filterIsInstance<PsiElement>().find { getName(it) == methodName }
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
