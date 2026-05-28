package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.go

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
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

class GoSuperMethodsProvider : SuperMethodsProvider {

    private val goMethodDeclarationClass: Class<*>? by lazy {
        try { Class.forName("com.goide.psi.GoMethodDeclaration") } catch (_: ClassNotFoundException) { null }
    }

    /**
     * GoSuperMethodSearch is NOT registered under DefinitionsScopedSearch EP — only
     * GoMethodInheritorsSearch and GoInheritorsSearch are (go-plugin/lib/go-plugin.jar plugin.xml).
     * We must call GoSuperMethodSearch.GO_SUPER_METHOD_SEARCH directly via reflection.
     *
     * GoGotoSuperHandler.showPopup(GoMethodDeclaration) uses:
     *   GoSuperMethodSearch.GO_SUPER_METHOD_SEARCH.processQuery(
     *       GoGotoUtil.param(goMethod), processor)
     *
     * GoGotoUtil.param(element) = new DefinitionsScopedSearch.SearchParameters(element)
     * Single-arg constructor sets checkDeep=true internally.
     */
    private val goSuperMethodSearch: Any? by lazy {
        try {
            val searchClass = Class.forName("com.goide.go.GoSuperMethodSearch")
            searchClass.getDeclaredField("GO_SUPER_METHOD_SEARCH").get(null)
        } catch (_: Exception) { null }
    }

    private val processQueryMethod: java.lang.reflect.Method? by lazy {
        try {
            goSuperMethodSearch?.javaClass?.getMethod("processQuery", Any::class.java, Processor::class.java)
        } catch (_: Exception) { null }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val goMethodClass = goMethodDeclarationClass ?: return null
        val search = goSuperMethodSearch ?: return null
        val method = processQueryMethod ?: return null

        @Suppress("UNCHECKED_CAST")
        val goMethod = if (goMethodClass.isInstance(element)) element
        else PsiTreeUtil.getParentOfType(element, goMethodClass as Class<out PsiElement>)
            ?: return null

        // DefinitionsScopedSearch.SearchParameters(element) single-arg constructor
        // sets checkDeep=true. This matches GoGotoUtil.param() used by the IDE's GotoSuper.
        val params = DefinitionsScopedSearch.SearchParameters(goMethod)
        val results = mutableListOf<PsiElement>()

        // Call processQuery via the Object overload which accepts raw types.
        // GoSuperMethodSearch extends QueryExecutorBase<GoNamedSignatureOwner, SearchParameters>
        // and returns GoMethodSpec instances (interface method specs satisfied by this struct method).
        // Dedup: when a struct embeds an interface via multiple inheritance paths
        // (e.g. ChainBase embedded by Mid1, Mid2, and Leaf), the same interface method
        // declaration fires through the processor multiple times. Collapse on
        // (qname or identityHashCode) + file:line, matching the sibling providers.
        val visited = mutableSetOf<String>()
        val processor = Processor<Any> { result ->
            if (result is PsiElement) {
                val qname = QualifiedNameUtil.getQualifiedName(result)
                    ?: "${result.javaClass.simpleName}@${System.identityHashCode(result)}"
                val file = result.containingFile?.virtualFile?.let { getRelativePath(project, it) } ?: "?"
                val line = getLineNumber(project, result)?.toString() ?: "?"
                val key = "$qname@$file:$line"
                if (visited.add(key)) results.add(result)
            }
            true
        }
        runCatching { method.invoke(search, params, processor) }

        // A resolved Go method that satisfies no interface is a valid result with an
        // empty hierarchy (mirrors Java/Kotlin/Python), not "no method found".
        val file = goMethod.containingFile?.virtualFile
        val methodData = MethodData(
            name = getName(goMethod) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(goMethod),
            kind = LanguageServices.getKind(goMethod),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, goMethod) ?: 0,
            column = getColumnNumber(project, goMethod) ?: 0,
        )

        return SuperMethodsData(
            method = methodData,
            hierarchy = results.map { toSuperMethodData(it, project) },
        )
    }

    private fun toSuperMethodData(resultElement: PsiElement, project: Project): SuperMethodData {
        val file = resultElement.containingFile?.virtualFile
        return SuperMethodData(
            name = getName(resultElement) ?: "unknown",
            qualifiedName = QualifiedNameUtil.getQualifiedName(resultElement),
            kind = LanguageServices.getKind(resultElement),
            file = file?.let { getRelativePath(project, it) },
            line = getLineNumber(project, resultElement),
            column = getColumnNumber(project, resultElement),
        )
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
