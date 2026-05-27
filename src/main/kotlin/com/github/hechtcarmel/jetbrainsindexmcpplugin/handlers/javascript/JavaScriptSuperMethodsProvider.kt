package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.javascript

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

/**
 * JavaScript/TypeScript super-methods provider. Delegates to
 * `com.intellij.lang.javascript.psi.resolve.JSInheritanceUtil.findNearestOverriddenMembers`
 * (with `findImplementedMembers` as fallback) — the same data layer
 * `JavaScriptGotoSuperHandler.getSuperMembers` and the JS line-marker use.
 *
 * TypeScript shares the handler via JSLanguageDialect base-language fallback.
 */
class JavaScriptSuperMethodsProvider : SuperMethodsProvider {

    companion object {
        private val LOG = logger<JavaScriptSuperMethodsProvider>()
    }

    private val jsFunctionClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.JSFunction") } catch (_: ClassNotFoundException) { null }
    }

    private val jsPsiElementBaseClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.JSPsiElementBase") } catch (_: ClassNotFoundException) { null }
    }

    private val jsQualifiedNamedElementClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement") } catch (_: ClassNotFoundException) { null }
    }

    private val inheritanceUtilClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.resolve.JSInheritanceUtil") } catch (_: ClassNotFoundException) { null }
    }

    private val findNearestOverriddenMethod: java.lang.reflect.Method? by lazy {
        try {
            val base = jsPsiElementBaseClass ?: return@lazy null
            inheritanceUtilClass?.getMethod("findNearestOverriddenMembers", base, java.lang.Boolean.TYPE)
        } catch (_: Throwable) { null }
    }

    private val findImplementedMethod: java.lang.reflect.Method? by lazy {
        try {
            val qne = jsQualifiedNamedElementClass ?: return@lazy null
            inheritanceUtilClass?.getMethod("findImplementedMembers", qne)
        } catch (_: Throwable) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val jsFunction = findContainingJSFunction(element) ?: return null

        val file = jsFunction.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(jsFunction) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(jsFunction),
            kind = LanguageServices.getKind(jsFunction),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, jsFunction) ?: 0,
            column = getColumnNumber(project, jsFunction) ?: 0,
        )

        return SuperMethodsData(method = methodData, hierarchy = buildHierarchy(project, jsFunction))
    }

    private fun buildHierarchy(project: Project, jsFunction: PsiElement): List<SuperMethodData> {
        val overridden = invokeFindNearestOverridden(jsFunction)
        val results = if (overridden.isNotEmpty()) overridden else invokeFindImplemented(jsFunction)

        val visited = mutableSetOf<String>()
        val out = mutableListOf<SuperMethodData>()
        for (superMember in results) {
            val key = QualifiedNameUtil.getQualifiedName(superMember)
                ?: "${superMember.javaClass.simpleName}@${System.identityHashCode(superMember)}"
            if (!visited.add(key)) continue
            val file = superMember.containingFile?.virtualFile
            out.add(SuperMethodData(
                name = getName(superMember) ?: "unknown",
                qualifiedName = QualifiedNameUtil.getQualifiedName(superMember),
                kind = LanguageServices.getKind(superMember),
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, superMember),
                column = getColumnNumber(project, superMember),
            ))
        }
        return out
    }

    private fun invokeFindNearestOverridden(jsFunction: PsiElement): List<PsiElement> {
        val m = findNearestOverriddenMethod ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, jsFunction, true) as? Collection<PsiElement>)?.toList() ?: emptyList()
        } catch (t: Throwable) {
            LOG.debug("JSInheritanceUtil.findNearestOverriddenMembers invocation failed", t)
            emptyList()
        }
    }

    private fun invokeFindImplemented(jsFunction: PsiElement): List<PsiElement> {
        val m = findImplementedMethod ?: return emptyList()
        val qneClass = jsQualifiedNamedElementClass ?: return emptyList()
        if (!qneClass.isInstance(jsFunction)) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, jsFunction) as? Collection<PsiElement>)?.toList() ?: emptyList()
        } catch (t: Throwable) {
            LOG.debug("JSInheritanceUtil.findImplementedMembers invocation failed", t)
            emptyList()
        }
    }

    private fun findContainingJSFunction(element: PsiElement): PsiElement? {
        val cls = jsFunctionClass ?: return null
        if (cls.isInstance(element)) return element
        @Suppress("UNCHECKED_CAST")
        return PsiTreeUtil.getParentOfType(element, cls as Class<out PsiElement>)
    }

    private fun getName(element: PsiElement): String? = runCatching {
        element.javaClass.getMethod("getName").invoke(element) as? String
    }.getOrNull()

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
