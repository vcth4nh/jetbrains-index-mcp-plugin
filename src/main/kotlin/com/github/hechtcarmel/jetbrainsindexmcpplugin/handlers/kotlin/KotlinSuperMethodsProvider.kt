package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.kotlin

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
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor

class KotlinSuperMethodsProvider : SuperMethodsProvider {

    companion object {
        private const val MAX_PARENT_TRAVERSAL_DEPTH = 50
        private const val MAX_REFERENCE_SEARCH_DEPTH = 3
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val method = resolveKotlinMethod(element) ?: return null
        method.containingClass ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = method.name,
            qualifiedName = QualifiedNameUtil.getQualifiedName(method),
            kind = LanguageServices.getKind(method),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
        )

        return SuperMethodsData(method = methodData, hierarchy = buildHierarchy(project, method))
    }

    private fun resolveKotlinMethod(element: PsiElement): PsiMethod? {
        if (element is PsiMethod) return element

        // Try reference resolution first (for method calls/references)
        var current: PsiElement? = element
        repeat(MAX_REFERENCE_SEARCH_DEPTH) {
            current?.reference?.resolve()?.let { if (it is PsiMethod) return it }
            current = current?.parent ?: return@repeat
        }

        // Walk up to a Kotlin declaration, convert to light method
        current = element
        var depth = 0
        while (current != null && depth < MAX_PARENT_TRAVERSAL_DEPTH) {
            when (val node = current) {
                is KtFunction -> {
                    val lightMethod = LightClassUtil.getLightClassMethod(node)
                    if (lightMethod != null) return lightMethod
                }
                is KtPropertyAccessor -> {
                    val lightMethod = LightClassUtil.getLightClassAccessorMethod(node)
                    if (lightMethod != null) return lightMethod
                }
                is KtProperty -> {
                    val accessors = LightClassUtil.getLightClassPropertyMethods(node)
                    val lightMethod = accessors.getter ?: accessors.setter
                    if (lightMethod != null) return lightMethod
                }
            }
            current = current?.parent
            depth++
        }
        return null
    }

    private fun buildHierarchy(
        project: Project,
        method: PsiMethod,
        visited: MutableSet<String> = mutableSetOf(),
    ): List<SuperMethodData> {
        val out = mutableListOf<SuperMethodData>()
        for (superMethod in method.findSuperMethods()) {
            val key = QualifiedNameUtil.getQualifiedName(superMethod)
                ?: "${superMethod.containingClass?.qualifiedName}.${superMethod.name}"
            if (!visited.add(key)) continue
            val file = superMethod.containingFile?.virtualFile
            out.add(SuperMethodData(
                name = superMethod.name,
                qualifiedName = QualifiedNameUtil.getQualifiedName(superMethod),
                kind = LanguageServices.getKind(superMethod),
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, superMethod),
                column = getColumnNumber(project, superMethod),
            ))
            out.addAll(buildHierarchy(project, superMethod, visited))
        }
        return out
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
