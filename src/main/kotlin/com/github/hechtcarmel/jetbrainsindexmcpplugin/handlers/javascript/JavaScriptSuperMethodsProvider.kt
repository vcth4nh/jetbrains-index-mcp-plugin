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

    private val jsFieldClass: Class<*>? by lazy {
        try { Class.forName("com.intellij.lang.javascript.psi.JSField") } catch (_: ClassNotFoundException) { null }
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
        val (member, isField) = findContainingMember(element) ?: return null

        val file = member.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(member) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(member),
            kind = LanguageServices.getKind(member),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, member) ?: 0,
            column = getColumnNumber(project, member) ?: 0,
        )

        return SuperMethodsData(method = methodData, hierarchy = buildHierarchy(project, member, isField = isField))
    }

    private fun buildHierarchy(
        project: Project,
        jsFunction: PsiElement,
        visited: MutableSet<String> = mutableSetOf(),
        isField: Boolean = false,
    ): List<SuperMethodData> {
        val overridden = invokeFindNearestOverridden(jsFunction, onlyFunctions = !isField)
        val results = if (overridden.isNotEmpty()) overridden else invokeFindImplemented(jsFunction)

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
            // Walk the full transitive chain — matches the Java/Kotlin providers and
            // WebStorm's gutter (JSInheritanceUtil.iterateOverriddenMembersUp recursive=true).
            out.addAll(buildHierarchy(project, superMember, visited, isField = jsFieldClass?.isInstance(superMember) == true))
        }
        return out
    }

    private fun invokeFindNearestOverridden(jsFunction: PsiElement, onlyFunctions: Boolean): List<PsiElement> {
        val m = findNearestOverriddenMethod ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, jsFunction, onlyFunctions) as? Collection<PsiElement>)?.toList() ?: emptyList()
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

    /** Returns the nearest enclosing JS function or field, plus a flag indicating if it is a field. */
    private fun findContainingMember(element: PsiElement): Pair<PsiElement, Boolean>? {
        val fnCls = jsFunctionClass
        val fieldCls = jsFieldClass
        // Check the element itself first
        if (fnCls != null && fnCls.isInstance(element)) return Pair(element, false)
        if (fieldCls != null && fieldCls.isInstance(element)) return Pair(element, true)
        // Walk up to the nearest enclosing function or field
        var current = element.parent
        while (current != null) {
            if (fnCls != null && fnCls.isInstance(current)) return Pair(current, false)
            if (fieldCls != null && fieldCls.isInstance(current)) return Pair(current, true)
            current = current.parent
        }
        return null
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
