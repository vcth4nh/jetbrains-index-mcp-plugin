package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CallElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CallHierarchyResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.HierarchyKind
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.HierarchyScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.HierarchyTreeWalker
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PsiUtils
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Tool for analyzing method call relationships across multiple languages.
 *
 * Supports: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Rust
 *
 * Delegates to the IDE's LanguageCallHierarchy extension point via [HierarchyTreeWalker].
 */
class CallHierarchyTool : AbstractMcpTool() {

    override val name = "ide_call_hierarchy"

    override val description = """
        Build a call hierarchy tree for a method/function. Use to trace execution flow—find what calls this method (callers) or what this method calls (callees).

        Languages: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Rust.

        Rust note: "callers" direction works well; "callees" direction may have limited results due to Rust plugin PSI resolution constraints.

        Returns: recursive tree with method signatures, file locations (line/column), and nested call relationships.

        Parameters: file + line + column (required), direction (required): "callers" or "callees". maxDepth (optional, default: 7, max: 20). scope (optional, default: "all"; supported: all, production, test, this_class, this_module).

        Example: {"file": "src/Service.java", "line": 42, "column": 10, "direction": "callers"}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Project-relative file path, or a dependency/library absolute path or jar:// URL previously returned by the plugin.")
        .lineAndColumn()
        .enumProperty("direction", "Direction: 'callers' (methods that call this method) or 'callees' (methods this method calls)", listOf("callers", "callees"), required = true)
        .intProperty("maxDepth", "How many levels deep to traverse the call hierarchy (default: 7, max: 20)")
        .hierarchyScopeProperty(
            "Hierarchy scope. Default: all. (production = production sources only — empty in projects without production roots, same as the IDE.)",
            HierarchyScope.wireValues(HierarchyScope.CALL_HIERARCHY_SCOPES)
        )
        .build()

    companion object {
        private const val DEFAULT_DEPTH = 7
        private const val MAX_DEPTH = 20
    }

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val direction = arguments["direction"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: direction")
        val depth = (arguments["maxDepth"]?.jsonPrimitive?.int ?: DEFAULT_DEPTH).coerceIn(1, MAX_DEPTH)
        val rawScope = rawScopeValue(arguments[ParamNames.SCOPE])
        val scope = try {
            HierarchyScope.parse(arguments)
        } catch (_: IllegalArgumentException) {
            return createInvalidScopeError(rawScope)
        }
        val kind = when (direction) {
            "callers" -> HierarchyKind.CALLERS
            "callees" -> HierarchyKind.CALLEES
            else -> return createErrorResult("direction must be 'callers' or 'callees'")
        }

        requireSmartMode(project)

        return suspendingReadAction {
            ProgressManager.checkCanceled()

            val element = resolveElementFromArguments(project, arguments, allowLibraryFilesForPosition = true).getOrElse {
                return@suspendingReadAction createErrorResult(it.message ?: ErrorMessages.COULD_NOT_RESOLVE_SYMBOL)
            }

            // Tool-layer gate: reject position-based invocations where the caret is not on a
            // resolvable navigation target (comment, whitespace, literal).
            if (PsiUtils.resolveTargetElement(element) == null) {
                return@suspendingReadAction createErrorResult("No method/function found at position")
            }

            ProgressManager.checkCanceled()

            val walkResult = HierarchyTreeWalker.walk(project, element, kind, scope, depth).getOrElse {
                return@suspendingReadAction createErrorResult(it.message ?: "Failed to build call hierarchy")
            }

            val rootCallElement = convertDescriptorToCallElement(walkResult.root, walkResult.resolver, depth)
                ?: return@suspendingReadAction createErrorResult("No method/function found at position")

            createJsonResult(CallHierarchyResult(
                element = rootCallElement.copy(children = null),
                calls = rootCallElement.children ?: emptyList()
            ))
        }
    }

    private fun rawScopeValue(scopeElement: JsonElement?): String = when (scopeElement) {
        null -> ""
        is JsonPrimitive -> scopeElement.content
        else -> scopeElement.toString()
    }

    private fun createInvalidScopeError(provided: String): ToolCallResult =
        createStructuredErrorResult(buildJsonObject {
            put("error", JsonPrimitive("invalid_scope"))
            put("parameter", JsonPrimitive(ParamNames.SCOPE))
            put("provided", JsonPrimitive(provided))
            put("supportedValues", buildJsonArray {
                HierarchyScope.wireValues(HierarchyScope.CALL_HIERARCHY_SCOPES).forEach { add(JsonPrimitive(it)) }
            })
        })

    /**
     * Converts an IDE [HierarchyNodeDescriptor] tree (as returned by [HierarchyTreeWalker])
     * into our wire-format [CallElement] tree. Recursive; bounded by [remainingDepth].
     * Returns null if the descriptor's PSI element couldn't be resolved or has no source location.
     */
    private fun convertDescriptorToCallElement(
        descriptor: HierarchyNodeDescriptor,
        resolver: com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.LogicalElementResolver,
        remainingDepth: Int
    ): CallElement? {
        val psi = resolver.resolve(descriptor) ?: return null
        val virtualFile = psi.containingFile?.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(psi.project)
            .getDocument(psi.containingFile) ?: return null
        val offset = PsiUtils.identifierOffset(psi)
        val line = document.getLineNumber(offset) + 1
        val column = offset - document.getLineStartOffset(line - 1) + 1

        val children = if (remainingDepth <= 0) emptyList() else descriptor.cachedChildren
            ?.filterIsInstance<HierarchyNodeDescriptor>()
            ?.mapNotNull { convertDescriptorToCallElement(it, resolver, remainingDepth - 1) }
            ?: emptyList()

        val qualifiedName = com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.ClassLikePsi
            .describeQualifiedName(psi)

        return CallElement(
            name = com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.ClassLikePsi
                .descriptorDisplayName(descriptor, psi),
            qualifiedName = qualifiedName,
            enclosingScope = if (qualifiedName == null) PsiUtils.getEnclosingScope(psi) else null,
            kind = com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServices.getKind(psi),
            file = ProjectUtils.getRelativePath(psi.project, virtualFile),
            line = line,
            column = column,
            children = if (children.isEmpty()) null else children
        )
    }

}
