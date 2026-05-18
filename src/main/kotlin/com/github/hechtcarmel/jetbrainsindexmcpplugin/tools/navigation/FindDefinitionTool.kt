package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.DefinitionResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServiceRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PsiUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.serialization.json.JsonObject

class FindDefinitionTool : AbstractMcpTool() {

    override val name = ToolNames.FIND_DEFINITION

    override val description = """
        Navigate to where a symbol is defined (Go to Definition). Use when you see a symbol reference and need to find its declaration—works for classes, methods, variables, imports.

        Returns: file path, line/column of definition, code preview, and symbol name.

        Example: {"file": "src/Main.java", "line": 15, "column": 10}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Project-relative file path, or a dependency/library absolute path or jar:// URL previously returned by the plugin.")
        .lineAndColumn()
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        requireSmartMode(project)

        return suspendingReadAction {
            val element = resolveElementFromArguments(project, arguments, allowLibraryFilesForPosition = true).getOrElse {
                return@suspendingReadAction createErrorResult(it.message ?: ErrorMessages.COULD_NOT_RESOLVE_SYMBOL)
            }

            // Symbol-based resolution returns the declaration directly (PsiNamedElement).
            // Position-based resolution returns a leaf token that needs reference resolution.
            val resolvedElement = element as? PsiNamedElement
                ?: (PsiUtils.resolveTargetElement(element)
                    ?: return@suspendingReadAction createErrorResult(ErrorMessages.SYMBOL_NOT_RESOLVED))

            // Prefer source files (.java) over compiled files (.class) for library classes
            val targetElement = PsiUtils.getNavigationElement(resolvedElement)

            // Try the target element first, then its navigationElement (for Kotlin light classes
            // and import directives where the resolved element may be a compiled class without a virtual file)
            val effectiveTarget = if (targetElement.containingFile?.virtualFile != null) {
                targetElement
            } else {
                val navElement = targetElement.navigationElement
                if (navElement != targetElement && navElement.containingFile?.virtualFile != null) {
                    navElement
                } else {
                    targetElement
                }
            }

            // Handle package/directory references (e.g., cursor on package segment in import statement)
            if (effectiveTarget is PsiDirectory) {
                val dirPath = getRelativePath(project, effectiveTarget.virtualFile)
                return@suspendingReadAction createJsonResult(DefinitionResult(
                    file = dirPath,
                    line = 1,
                    column = 1,
                    name = effectiveTarget.name ?: dirPath,
                    kind = "PACKAGE",
                    preview = "Package directory: $dirPath",
                ))
            }
            // PsiPackage is Java-plugin-only; guard with Class.forName / isInstance to avoid NoClassDefFoundError in non-Java IDEs.
            // getDirectories remains reflective (loading package directories is out of scope for the QualifiedNameProvider migration).
            try {
                val psiPackageClass = Class.forName("com.intellij.psi.PsiPackage")
                if (psiPackageClass.isInstance(effectiveTarget)) {
                    val qualifiedName = QualifiedNameUtil.getQualifiedName(effectiveTarget) ?: "unknown"
                    val dirs = psiPackageClass
                        .getMethod("getDirectories", GlobalSearchScope::class.java)
                        .invoke(effectiveTarget, GlobalSearchScope.projectScope(project)) as Array<*>
                    val dir = dirs.firstOrNull() as? PsiDirectory
                    if (dir != null) {
                        val dirPath = getRelativePath(project, dir.virtualFile)
                        return@suspendingReadAction createJsonResult(DefinitionResult(
                            file = dirPath,
                            line = 1,
                            column = 1,
                            name = qualifiedName,
                            kind = "PACKAGE",
                            preview = "Package: $qualifiedName",
                        ))
                    }
                }
            } catch (_: ClassNotFoundException) {
                // Java plugin not available — skip PsiPackage handling
            }

            val targetFile = effectiveTarget.containingFile?.virtualFile
                ?: return@suspendingReadAction createErrorResult(ErrorMessages.DEFINITION_FILE_NOT_FOUND)

            val document = PsiDocumentManager.getInstance(project)
                .getDocument(effectiveTarget.containingFile)
                ?: return@suspendingReadAction createErrorResult(ErrorMessages.DEFINITION_DOCUMENT_NOT_FOUND)

            val targetLine = document.getLineNumber(effectiveTarget.textOffset) + 1
            val targetColumn = effectiveTarget.textOffset -
                document.getLineStartOffset(targetLine - 1) + 1

            val preview = document.getText(
                TextRange(document.getLineStartOffset(targetLine - 1), document.getLineEndOffset(targetLine - 1))
            ).trim()

            val name = if (effectiveTarget is PsiNamedElement) {
                effectiveTarget.name ?: "unknown"
            } else {
                effectiveTarget.text.take(50)
            }
            val qualifiedName = QualifiedNameUtil.getQualifiedName(effectiveTarget)
            val kind = LanguageServiceRegistry.getKind(effectiveTarget)
            val enclosingScope = if (qualifiedName == null) PsiUtils.getEnclosingScope(effectiveTarget) else null

            createJsonResult(DefinitionResult(
                file = getRelativePath(project, targetFile),
                line = targetLine,
                column = targetColumn,
                name = name,
                kind = kind,
                preview = preview,
                qualifiedName = qualifiedName,
                enclosingScope = enclosingScope
            ))
        }
    }
}
