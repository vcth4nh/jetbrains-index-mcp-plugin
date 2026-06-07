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
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

/**
 * Tool for finding super methods across multiple languages.
 *
 * Supports: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Go (interface satisfaction — methods and types), Rust (trait fn/const/type alias overrides).
 *
 * Delegates to language-specific providers via the [SuperMethodsProvider] extension point.
 */
class FindSuperMethodsTool : AbstractMcpTool() {

    override val name = ToolNames.FIND_SUPER_METHODS

    override val description = """
        Navigate UP the hierarchy from a code element — what it overrides, implements, or extends. Mirrors the IDE's "Go to Super" (Ctrl+U). Anchor on a method (→ super-methods, full transitive chain), a class/interface/struct/trait (→ direct supertypes), a lambda (→ the single abstract method it implements), or a field/constant (→ the overridden member).

        Languages: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Go (a method → the interface methods it satisfies; a type → the interfaces it satisfies), Rust (trait fn/const/type alias the impl satisfies).

        Returns: the matching supers with file locations (line/column), qualified names, and element kinds. Empty when the element has no super.

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

            // Anchor resolution is delegated entirely to the language provider: it returns null
            // only when the caret is not on a super-navigable element (method, class, field/const,
            // or lambda), and an empty hierarchy when the element is valid but has no super.
            val superMethodsData = LanguageServices.findSuperMethods(element, project)
            if (superMethodsData == null) {
                return@suspendingReadAction createErrorResult(
                    "No super-navigable element at position. Place the caret on a method, class, field/const, or lambda."
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
