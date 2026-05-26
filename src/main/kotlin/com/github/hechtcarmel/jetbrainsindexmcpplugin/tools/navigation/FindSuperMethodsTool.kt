package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServices
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.MethodInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SuperMethodInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SuperMethodsResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PsiUtils
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

/**
 * Tool for finding super methods across multiple languages.
 *
 * Supports: Java, Kotlin, Python, JavaScript, TypeScript, PHP
 *
 * Delegates to language-specific services via [LanguageServiceRegistry].
 */
class FindSuperMethodsTool : AbstractMcpTool() {

    override val name = ToolNames.FIND_SUPER_METHODS

    override val description = """
        Find parent methods that a method overrides or implements. Use to navigate up the inheritance chain—from implementation to interface, or from override to original declaration.

        Languages: Java, Kotlin, Python, JavaScript, TypeScript, PHP.

        NOT supported for Rust: Rust uses trait implementations rather than classical inheritance, so there are no "super methods" in the traditional sense. Use ide_find_definition or ide_type_hierarchy instead.

        Returns: full hierarchy chain from immediate parent to root, with file locations (line/column), qualified names, and element kinds.

        Example: {"file": "src/UserServiceImpl.java", "line": 25, "column": 10}
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

            // Tool-layer gate: reject position-based invocations where the caret is not on a
            // resolvable target. See CallHierarchyTool for rationale.
            if (PsiUtils.resolveTargetElement(element) == null) {
                return@suspendingReadAction createErrorResult(
                    "No method found at position. Ensure the position is within a method declaration or body."
                )
            }

            val superMethodsData = LanguageServices.findSuperMethods(element, project)
            if (superMethodsData == null) {
                return@suspendingReadAction createErrorResult(
                    "No method found at position. Ensure the position is within a method declaration or body."
                )
            }

            // Convert handler result to tool result
            createJsonResult(SuperMethodsResult(
                method = MethodInfo(
                    name = superMethodsData.method.name,
                    qualifiedName = superMethodsData.method.qualifiedName,
                    kind = superMethodsData.method.kind,
                    file = superMethodsData.method.file,
                    line = superMethodsData.method.line,
                    column = superMethodsData.method.column,
                ),
                hierarchy = superMethodsData.hierarchy.map { superMethod ->
                    SuperMethodInfo(
                        name = superMethod.name,
                        qualifiedName = superMethod.qualifiedName,
                        kind = superMethod.kind,
                        file = superMethod.file,
                        line = superMethod.line,
                        column = superMethod.column,
                    )
                },
            ))
        }
    }
}
