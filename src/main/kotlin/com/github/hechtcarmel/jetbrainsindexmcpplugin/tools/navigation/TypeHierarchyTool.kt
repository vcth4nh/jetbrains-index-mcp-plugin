package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScopeResolver
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.TypeElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.TypeHierarchyResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.ClassLikePsi
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.HierarchyKind
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.HierarchyTreeWalker
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
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
 * Tool for retrieving type hierarchies across multiple languages.
 *
 * Supports: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Rust
 *
 * Delegates to the IDE's LanguageTypeHierarchy extension point via [HierarchyTreeWalker].
 */
class TypeHierarchyTool : AbstractMcpTool() {

    override val name = "ide_type_hierarchy"

    override val description = """
        Get the complete inheritance hierarchy for a class or interface. Use when you need to understand class relationships, find parent classes, or discover all subclasses.

        Languages: Java, Kotlin, Python, JavaScript, TypeScript, PHP, Rust.

        Rust note: className parameter not supported for Rust; use file + line + column instead.

        Returns: target class info, full supertype chain (recursive), and all subtypes in the project.

        Parameters: Either className (e.g., "com.example.MyClass") OR file + line + column. scope (optional, default: "project_files"; supported: project_files, project_and_libraries, project_production_files, project_test_files).

        Example: {"className": "com.example.UserService", "scope": "project_and_libraries"} or {"file": "src/MyClass.java", "line": 10, "column": 14}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .stringProperty("className", "Fully qualified class name (e.g., 'com.example.MyClass' for Java or 'App\\\\Models\\\\User' for PHP). RECOMMENDED - use this if you know the class name.")
        .file(required = false, description = "Path to file relative to project root (e.g., 'src/main/java/com/example/MyClass.java'). Use with line and column.")
        .intProperty("line", "1-based line number where the class is defined. Required if using file parameter.")
        .intProperty("column", "1-based column number. Required if using file parameter.")
        .scopeProperty("Search scope. Default: project_files.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        requireSmartMode(project)

        val className = arguments["className"]?.jsonPrimitive?.content
        val file = arguments["file"]?.jsonPrimitive?.content
        val rawScope = rawScopeValue(arguments[ParamNames.SCOPE])
        val scope = try {
            BuiltInSearchScopeResolver.parse(arguments, BuiltInSearchScope.PROJECT_FILES)
        } catch (_: IllegalArgumentException) {
            return createInvalidScopeError(rawScope)
        } catch (_: IllegalStateException) {
            return createInvalidScopeError(rawScope)
        }

        return suspendingReadAction {
            ProgressManager.checkCanceled()

            val element = resolveTargetElement(project, arguments) ?: run {
                val errorMsg = when {
                    className != null -> "Class '$className' not found in project '${project.name}'. Verify the fully qualified name is correct and the class is part of this project."
                    file != null -> "No class found at the specified file/line/column position."
                    else -> "Provide either 'className' (e.g., 'com.example.MyClass') or 'file' + 'line' + 'column'."
                }
                return@suspendingReadAction createErrorResult(errorMsg)
            }

            ProgressManager.checkCanceled()

            // Two walker calls: supertypes (recurse up to maxDepth) and subtypes (flat list).
            val maxDepth = 5

            val superResult = HierarchyTreeWalker
                .walk(project, element, HierarchyKind.SUPERTYPES, scope, maxDepth)
                .getOrElse {
                    return@suspendingReadAction createErrorResult(it.message ?: "Failed to build supertype hierarchy")
                }
            val subResult = HierarchyTreeWalker
                .walk(project, element, HierarchyKind.SUBTYPES, scope, maxDepth)
                .getOrElse {
                    return@suspendingReadAction createErrorResult(it.message ?: "Failed to build subtype hierarchy")
                }

            val supertypeDescriptors = superResult.root.cachedChildren
                ?.filterIsInstance<HierarchyNodeDescriptor>().orEmpty()
            // Flatten subtypes recursively. The IDE returns a tree (e.g. Shape → Rectangle → Square),
            // but our wire format expects a flat list of all descendants at the top level. Walk the
            // descriptor tree and collect each subtype with `supertypes = null`.
            val subtypes = mutableListOf<TypeElement>()
            fun collectSubtypes(desc: HierarchyNodeDescriptor) {
                convertDescriptorToTypeElement(desc, subResult.resolver, recurseSupertypes = false, remainingDepth = 0)
                    ?.let { subtypes.add(it) }
                desc.cachedChildren
                    ?.filterIsInstance<HierarchyNodeDescriptor>()
                    ?.forEach { collectSubtypes(it) }
            }
            subResult.root.cachedChildren
                ?.filterIsInstance<HierarchyNodeDescriptor>()
                ?.forEach { collectSubtypes(it) }

            val supertypes = supertypeDescriptors.mapNotNull {
                convertDescriptorToTypeElement(it, superResult.resolver, recurseSupertypes = true, remainingDepth = maxDepth)
            }

            // For file/line/column resolution, `element` is a leaf token. Walk up
            // to the smallest class-like ancestor so `buildRootTypeElement` can
            // extract its name/qualifiedName. For className resolution, `element`
            // is already a class — walkUp returns it unchanged.
            val rootElement = ClassLikePsi.walkUpToClassLike(element) ?: element
            val rootTypeElement = buildRootTypeElement(rootElement)
                ?: return@suspendingReadAction createErrorResult("Could not extract class info from element")

            createJsonResult(TypeHierarchyResult(
                element = rootTypeElement,
                supertypes = supertypes,
                subtypes = subtypes
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
                BuiltInSearchScope.supportedWireValues().forEach { add(JsonPrimitive(it)) }
            })
        })

    private fun resolveTargetElement(project: Project, arguments: JsonObject): PsiElement? {
        // Try className first (Java/Kotlin specific)
        val className = arguments["className"]?.jsonPrimitive?.content
        if (className != null) {
            return findClassByName(project, className)
        }

        // Otherwise use file/line/column (works for all languages)
        val file = arguments["file"]?.jsonPrimitive?.content ?: return null
        val line = arguments["line"]?.jsonPrimitive?.int ?: return null
        val column = arguments["column"]?.jsonPrimitive?.int ?: return null

        return findPsiElement(project, file, line, column)
    }

    /**
     * Converts a [HierarchyNodeDescriptor] to a wire-format [TypeElement]. When [recurseSupertypes]
     * is true, recursively walks the descriptor's cached children into the [TypeElement.supertypes]
     * field (used for the supertypes branch). When false, [TypeElement.supertypes] is left null
     * (used for the flat subtypes list).
     */
    private fun convertDescriptorToTypeElement(
        descriptor: HierarchyNodeDescriptor,
        resolver: com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy.LogicalElementResolver,
        recurseSupertypes: Boolean,
        remainingDepth: Int
    ): TypeElement? {
        val psi = resolver.resolve(descriptor) ?: return null
        val name = (psi as? PsiNamedElement)?.name ?: psi.text.take(60)
        val virtualFile = psi.containingFile?.virtualFile

        val supertypes = if (recurseSupertypes && remainingDepth > 0) {
            descriptor.cachedChildren
                ?.filterIsInstance<HierarchyNodeDescriptor>()
                ?.mapNotNull { convertDescriptorToTypeElement(it, resolver, recurseSupertypes = true, remainingDepth = remainingDepth - 1) }
                ?.takeIf { it.isNotEmpty() }
        } else null

        return TypeElement(
            name = name,
            file = virtualFile?.let { ProjectUtils.getRelativePath(psi.project, it) },
            kind = ClassLikePsi.describeKind(psi),
            language = com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.displayLanguageName(psi.language.id),
            supertypes = supertypes,
            qualifiedName = ClassLikePsi.describeQualifiedName(psi)
        )
    }

    private fun buildRootTypeElement(psi: PsiElement): TypeElement? {
        val name = (psi as? PsiNamedElement)?.name ?: return null
        val virtualFile = psi.containingFile?.virtualFile
        return TypeElement(
            name = name,
            file = virtualFile?.let { ProjectUtils.getRelativePath(psi.project, it) },
            kind = ClassLikePsi.describeKind(psi),
            language = com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.displayLanguageName(psi.language.id),
            supertypes = null,
            qualifiedName = ClassLikePsi.describeQualifiedName(psi)
        )
    }
}
