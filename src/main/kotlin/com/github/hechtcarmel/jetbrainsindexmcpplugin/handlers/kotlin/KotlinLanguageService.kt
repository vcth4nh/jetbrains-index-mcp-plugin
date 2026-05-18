package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.kotlin

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
import com.intellij.psi.PsiMethod

class KotlinLanguageService : LanguageService() {

    companion object {
        private val LOG = logger<KotlinLanguageService>()
        private const val MAX_PARENT_TRAVERSAL_DEPTH = 50
        private const val MAX_REFERENCE_SEARCH_DEPTH = 3

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
        resolveLanguageId("org.jetbrains.kotlin.idea.KotlinLanguage", "INSTANCE")
            ?.let { setOf(it) } ?: emptySet()
    }
    override val displayName = "Kotlin"
    override fun isAvailable(): Boolean = PluginDetectors.kotlin.isAvailable

    override val supportsSuperMethods: Boolean = true

    private val ktClassClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtClass") } catch (_: ClassNotFoundException) { null }
    }
    private val ktObjectDeclarationClass: Class<*>? by lazy {
        try { Class.forName("org.jetbrains.kotlin.psi.KtObjectDeclaration") } catch (_: ClassNotFoundException) { null }
    }

    override fun resolveKind(element: PsiElement): String? {
        val ktClass = ktClassClass
        if (ktClass != null && ktClass.isInstance(element)) {
            return try {
                val isAnnotation = invokeBoolean(element, "isAnnotation")
                val isInterface = invokeBoolean(element, "isInterface")
                val isEnum = invokeBoolean(element, "isEnum")
                val isData = invokeBoolean(element, "isData")
                val isSealed = invokeBoolean(element, "isSealed")
                val isAbstract = kotlinHasModifier(element, "abstract")
                when {
                    isAnnotation -> "ANNOTATION"
                    isInterface -> "INTERFACE"
                    isEnum -> "ENUM"
                    isData -> "DATA_CLASS"
                    isSealed -> "SEALED_CLASS"
                    isAbstract -> "ABSTRACT_CLASS"
                    else -> "CLASS"
                }
            } catch (_: Exception) { null }
        }
        if (ktObjectDeclarationClass?.isInstance(element) == true) return "OBJECT"
        return null
    }

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        // Convert Kotlin function to light PsiMethod, then use Java-style super method lookup
        val method = resolveKotlinMethod(element) ?: return null
        val containingClass = method.containingClass ?: return null

        val file = method.containingFile?.virtualFile
        val methodData = MethodData(
            name = method.name,
            qualifiedName = QualifiedNameUtil.getQualifiedName(method),
            kind = LanguageServiceRegistry.getKind(method),
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
        method: PsiMethod,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()

        for (superMethod in method.findSuperMethods()) {
            val key = QualifiedNameUtil.getQualifiedName(superMethod)
                ?: "${superMethod.containingClass?.qualifiedName}.${superMethod.name}"
            if (key in visited) continue
            visited.add(key)

            val file = superMethod.containingFile?.virtualFile

            hierarchy.add(SuperMethodData(
                name = superMethod.name,
                qualifiedName = QualifiedNameUtil.getQualifiedName(superMethod),
                kind = LanguageServiceRegistry.getKind(superMethod),
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, superMethod),
                column = getColumnNumber(project, superMethod),
            ))

            hierarchy.addAll(buildHierarchy(project, superMethod, visited, depth + 1))
        }

        return hierarchy
    }

    private fun resolveKotlinMethod(element: PsiElement): PsiMethod? {
        // If already a PsiMethod (light method), return it
        if (element is PsiMethod) return element

        // Try reference resolution first (for method calls/references)
        val resolved = resolveReference(element)
        if (resolved is PsiMethod) return resolved

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

    private fun kotlinHasModifier(element: PsiElement, modifier: String): Boolean = runCatching {
        val modList = element.javaClass.getMethod("getModifierList").invoke(element) as? PsiElement
        modList?.text?.let { text -> Regex("\\b${Regex.escape(modifier)}\\b").containsMatchIn(text) } == true
    }.getOrDefault(false)
}
