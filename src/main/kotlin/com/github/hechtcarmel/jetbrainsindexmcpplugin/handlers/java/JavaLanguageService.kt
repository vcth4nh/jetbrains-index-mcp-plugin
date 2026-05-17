package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.util.PsiTreeUtil

class JavaLanguageService : LanguageService() {

    companion object {
        private val LOG = logger<JavaLanguageService>()

        private const val MAX_PARENT_TRAVERSAL_DEPTH = 50
        private const val MAX_REFERENCE_SEARCH_DEPTH = 3

        // Kotlin PSI classes (loaded via reflection to avoid compile-time dependency)
        private val ktNamedFunctionClass: Class<*>? by lazy {
            try { Class.forName("org.jetbrains.kotlin.psi.KtNamedFunction") } catch (_: ClassNotFoundException) { null }
        }
        private val ktPropertyAccessorClass: Class<*>? by lazy {
            try { Class.forName("org.jetbrains.kotlin.psi.KtPropertyAccessor") } catch (_: ClassNotFoundException) { null }
        }
        private val ktPropertyClass: Class<*>? by lazy {
            try { Class.forName("org.jetbrains.kotlin.psi.KtProperty") } catch (_: ClassNotFoundException) { null }
        }
        private val lightClassExtensionsClass: Class<*>? by lazy {
            try { Class.forName("org.jetbrains.kotlin.asJava.LightClassUtilsKt") } catch (_: ClassNotFoundException) { null }
        }
    }

    override val languageIds: Set<String> by lazy {
        resolveLanguageId("com.intellij.lang.java.JavaLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Java"
    override fun isAvailable(): Boolean = PluginDetectors.java.isAvailable

    override val supportsSuperMethods: Boolean = true

    override fun resolveKind(element: PsiElement): String? {
        if (element !is PsiClass) return null
        return when {
            element.isAnnotationType -> "ANNOTATION"
            element.isInterface -> "INTERFACE"
            element.isEnum -> "ENUM"
            element.isRecord -> "RECORD"
            element.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        val method = resolveMethod(element) ?: return null
        val containingClass = method.containingClass ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = method.name,
            signature = buildMethodSignature(method),
            containingClass = ClassPresentationUtil.getNameForClass(containingClass, true),
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0,
            column = getColumnNumber(project, method) ?: 0,
            language = if (method.navigationElement.language.id == "kotlin") "Kotlin" else "Java"
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
        method: PsiMethod,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        for (superMethod in method.findSuperMethods()) {
            val key = QualifiedNameUtil.getQualifiedName(superMethod)
                ?: "${superMethod.containingClass?.let { ClassPresentationUtil.getNameForClass(it, true) }}.${superMethod.name}"
            if (key in visited) continue
            visited.add(key)

            val containingClass = superMethod.containingClass
            val file = superMethod.containingFile?.virtualFile

            hierarchy.add(SuperMethodData(
                name = superMethod.name,
                signature = buildMethodSignature(superMethod),
                containingClass = containingClass?.let { ClassPresentationUtil.getNameForClass(it, true) } ?: "unknown",
                containingClassKind = containingClass?.let { getClassKind(it) } ?: "UNKNOWN",
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, superMethod),
                column = getColumnNumber(project, superMethod),
                isInterface = containingClass?.isInterface == true,
                depth = depth,
                language = if (superMethod.navigationElement.language.id == "kotlin") "Kotlin" else "Java"
            ))

            hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
        }

        return hierarchy
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") {
            "${it.type.presentableText} ${it.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "${method.name}($params): $returnType"
    }

    // --- Method/class resolution helpers (shared with Kotlin) ---

    private fun resolveMethod(element: PsiElement): PsiMethod? {
        if (element is PsiMethod) return element

        val resolved = resolveReference(element)
        if (resolved is PsiMethod) return resolved

        return if (element.language.id == "kotlin") {
            resolveKotlinMethod(element)
        } else {
            PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        }
    }

    private fun resolveKotlinMethod(element: PsiElement): PsiMethod? {
        val lightClassExtensions = lightClassExtensionsClass ?: return null

        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < MAX_PARENT_TRAVERSAL_DEPTH) {
            val isKotlinDeclaration = (ktNamedFunctionClass?.isInstance(current) == true) ||
                (ktPropertyAccessorClass?.isInstance(current) == true) ||
                (ktPropertyClass?.isInstance(current) == true)

            if (isKotlinDeclaration) {
                try {
                    val toLightMethodsMethod = lightClassExtensions.getMethod("toLightMethods", PsiElement::class.java)
                    val lightMethods = toLightMethodsMethod.invoke(null, current) as? List<*>
                    val lightMethod = lightMethods?.firstOrNull() as? PsiMethod
                    if (lightMethod != null) return lightMethod
                } catch (e: ReflectiveOperationException) {
                    LOG.debug("Failed to get light method for Kotlin element: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun resolveReference(element: PsiElement): PsiElement? {
        element.reference?.resolve()?.let { return it }

        var current: PsiElement? = element
        repeat(MAX_REFERENCE_SEARCH_DEPTH) {
            current = current?.parent ?: return null
            current?.reference?.resolve()?.let { return it }
        }
        return null
    }

    private fun getClassKind(psiClass: PsiClass): String {
        return when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.isEnum -> "ENUM"
            psiClass.isAnnotationType -> "ANNOTATION"
            psiClass.isRecord -> "RECORD"
            psiClass.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

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
