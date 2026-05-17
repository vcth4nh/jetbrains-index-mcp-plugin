package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScopeResolver
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageServiceRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.displayLanguageName
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.PaginationService
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.ProjectResolver
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ImplementationLocation
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ImplementationResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PsiUtils
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Tool for finding implementations of interfaces, abstract classes, or methods across all languages.
 *
 * Delegates to the IDE's [DefinitionsScopedSearch] extension point, so it works for any language
 * whose plugin registers a definitions search provider.
 */
class FindImplementationsTool : AbstractMcpTool() {

    companion object {
        private val LOG = logger<FindImplementationsTool>()
        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = PaginationService.MAX_PAGE_SIZE

        private val psiClassClass: Class<*>? by lazy {
            try { Class.forName("com.intellij.psi.PsiClass") } catch (_: ClassNotFoundException) { null }
        }

        private val psiMethodClass: Class<*>? by lazy {
            try { Class.forName("com.intellij.psi.PsiMethod") } catch (_: ClassNotFoundException) { null }
        }

        private val classPresentationUtilClass: Class<*>? by lazy {
            try { Class.forName("com.intellij.psi.presentation.java.ClassPresentationUtil") } catch (_: ClassNotFoundException) { null }
        }

        private val lambdaUtilClass: Class<*>? by lazy {
            try { Class.forName("com.intellij.psi.LambdaUtil") } catch (_: ClassNotFoundException) { null }
        }

        private val functionalExpressionSearchClass: Class<*>? by lazy {
            try { Class.forName("com.intellij.psi.search.searches.FunctionalExpressionSearch") } catch (_: ClassNotFoundException) { null }
        }

        private val rsImplItemClass: Class<*>? by lazy {
            try { Class.forName("org.rust.lang.core.psi.RsImplItem") } catch (_: ClassNotFoundException) { null }
        }

        private val rsFunctionClass: Class<*>? by lazy {
            try { Class.forName("org.rust.lang.core.psi.RsFunction") } catch (_: ClassNotFoundException) { null }
        }
    }

    override val name = "ide_find_implementations"

    override val description = """
        Find all implementations of an interface, abstract class, or abstract method. Use to discover concrete implementations when working with abstractions.

        Returns: list of implementing classes/methods with file paths, line/column numbers, and kind (class/method).

        Supports pagination: first call returns results + nextCursor. Pass cursor to get the next page.

        Parameters: file + line + column (required for fresh search, ignored when cursor is provided), scope (optional, default: "project_files"; supported: project_files, project_and_libraries, project_production_files, project_test_files), pageSize (optional, default: 100, max: 500), cursor (pagination cursor from a previous response).

        Example: {"file": "src/Repository.java", "line": 8, "column": 18}
        Example: {"file": "src/Repository.java", "line": 8, "column": 18, "scope": "project_and_libraries"}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(required = false, description = "Project-relative file path, or a dependency/library absolute path or jar:// URL previously returned by the plugin. Required for position-based lookup.")
        .lineAndColumn(required = false)
        .scopeProperty("Search scope. Default: project_files.")
        .stringProperty("cursor", "Pagination cursor from a previous response. When provided, returns the next page of results. Search parameters are ignored; project_path and pageSize may still be provided.")
        .intProperty("pageSize", "Results per page. Default: $DEFAULT_PAGE_SIZE, max: $MAX_PAGE_SIZE.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val cursor = arguments["cursor"]?.jsonPrimitive?.content
        if (cursor != null) {
            val pageSize = resolveExplicitPageSize(arguments)
            return buildPaginatedResult<ImplementationLocation, ImplementationResult>(getPageFromCache(cursor, pageSize, project)) { items, page ->
                ImplementationResult(
                    implementations = items,
                    totalCount = page.totalCollected,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    totalCollected = page.totalCollected,
                    offset = page.offset,
                    pageSize = page.pageSize,
                    stale = page.stale
                )
            }
        }

        val pageSize = resolvePageSize(arguments, DEFAULT_PAGE_SIZE)
        val rawScope = rawScopeValue(arguments[ParamNames.SCOPE])
        val scope = try {
            BuiltInSearchScopeResolver.parse(arguments, BuiltInSearchScope.PROJECT_FILES)
        } catch (_: IllegalArgumentException) {
            return createInvalidScopeError(rawScope)
        } catch (_: IllegalStateException) {
            return createInvalidScopeError(rawScope)
        }
        requireSmartMode(project)

        val cursorToken = suspendingReadAction {
            val element = resolveElementFromArguments(project, arguments, allowLibraryFilesForPosition = true).getOrElse {
                return@suspendingReadAction null to createErrorResult(it.message ?: ErrorMessages.COULD_NOT_RESOLVE_SYMBOL)
            }

            val target = PsiUtils.resolveTargetElement(element)
            if (target == null) {
                return@suspendingReadAction null to createErrorResult("No method or class found at position")
            }
            val searchScope = BuiltInSearchScopeResolver.resolveGlobalScope(project, scope)
            val collectLimit = maxOf(PaginationService.DEFAULT_OVERCOLLECT, pageSize)
            val results = mutableListOf<ImplementationLocation>()
            DefinitionsScopedSearch.search(target, searchScope, true).forEach(com.intellij.util.Processor { impl ->
                val location = convertToLocation(impl, project) ?: return@Processor true
                results.add(location)
                results.size < collectLimit
            })

            if (results.size < collectLimit) {
                addFunctionalExpressionImpls(target, searchScope, collectLimit, results, project)
            }

            val serializedResults = results.map { impl ->
                PaginationService.SerializedResult(
                    key = "${impl.file}:${impl.line}:${impl.column}:${impl.name}",
                    data = json.encodeToJsonElement(impl)
                )
            }

            val paginationService = ApplicationManager.getApplication().getService(PaginationService::class.java)
            val token = paginationService.createCursor(
                toolName = name,
                results = serializedResults,
                seenKeys = serializedResults.map { it.key }.toSet(),
                searchExtender = null,
                psiModCount = PsiModificationTracker.getInstance(project).modificationCount,
                projectBasePath = ProjectResolver.normalizePath(project.basePath ?: "")
            )

            token to null
        }

        val (token, errorResult) = cursorToken
        if (errorResult != null) return errorResult

        return buildPaginatedResult<ImplementationLocation, ImplementationResult>(getPageFromCache(token!!, pageSize, project)) { items, page ->
            ImplementationResult(
                implementations = items,
                totalCount = page.totalCollected,
                nextCursor = page.nextCursor,
                hasMore = page.hasMore,
                totalCollected = page.totalCollected,
                offset = page.offset,
                pageSize = page.pageSize,
                stale = page.stale
            )
        }
    }

    private fun convertToLocation(element: PsiElement, project: Project): ImplementationLocation? {
        val file = element.containingFile?.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return null
        val line = document.getLineNumber(element.textOffset) + 1
        val lineStart = document.getLineStartOffset(document.getLineNumber(element.textOffset))
        val column = element.textOffset - lineStart + 1

        val name: String
        val kind: String
        if (rsImplItemClass?.isInstance(element) == true) {
            name = buildRustImplName(element)
            kind = "IMPL"
        } else if (rsFunctionClass?.isInstance(element) == true) {
            val namedElement = element as? PsiNamedElement ?: return null
            val bareName = namedElement.name ?: return null
            kind = LanguageServiceRegistry.getKind(element)
            name = if (kind == "METHOD") buildRustMethodName(element, bareName) else bareName
        } else {
            val namedElement = element as? PsiNamedElement ?: return null
            val bareName = namedElement.name ?: return null
            kind = LanguageServiceRegistry.getKind(element)
            name = buildQualifiedDisplayName(element, bareName, kind)
        }

        return ImplementationLocation(
            name = name,
            file = ProjectUtils.getToolFilePath(project, file),
            line = line,
            column = column,
            kind = kind,
            language = displayLanguageName(element.language.id),
            qualifiedName = QualifiedNameUtil.getQualifiedName(element)
        )
    }

    private fun buildQualifiedDisplayName(element: PsiElement, bareName: String, kind: String): String {
        if (kind != "METHOD" && kind != "FUNCTION") return bareName
        return try {
            val containingClass = try {
                val m = element.javaClass.getMethod("getContainingClass")
                m.invoke(element) as? PsiElement
            } catch (_: Exception) {
                findEnclosingClassLike(element)
            } ?: return bareName

            val className = if (psiClassClass?.isInstance(containingClass) == true && classPresentationUtilClass != null) {
                try {
                    val m = classPresentationUtilClass!!.getMethod("getNameForClass", psiClassClass, Boolean::class.javaPrimitiveType)
                    m.invoke(null, containingClass, true) as? String
                } catch (_: Exception) { null }
            } else null

            val resolvedName = className
                ?: (containingClass as? PsiNamedElement)?.let { QualifiedNameUtil.getQualifiedName(it) ?: it.name }
                ?: try {
                    containingClass.javaClass.getMethod("getName").invoke(containingClass) as? String
                } catch (_: Exception) { null }
                ?: return bareName

            "$resolvedName.$bareName"
        } catch (e: Exception) {
            LOG.debug("Failed to build qualified name for $bareName: ${e.message}")
            bareName
        }
    }

    private fun findEnclosingClassLike(element: PsiElement): PsiElement? {
        var current = element.parent
        var depth = 0
        while (current != null && depth < 10) {
            if (psiClassClass?.isInstance(current) == true) return current
            val simpleName = current.javaClass.simpleName.lowercase()
            if (simpleName.contains("class") && !simpleName.contains("list") && !simpleName.contains("classpath")) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun buildRustMethodName(element: PsiElement, bareName: String): String {
        return try {
            var current = element.parent
            var depth = 0
            while (current != null && depth < 5) {
                if (rsImplItemClass?.isInstance(current) == true) {
                    val typeRef = current.javaClass.getMethod("getTypeReference").invoke(current)
                    val typeName = typeRef?.let { it.javaClass.getMethod("getText").invoke(it) as? String }?.trim()
                    if (typeName != null) return "$typeName::$bareName"
                }
                current = current.parent
                depth++
            }
            bareName
        } catch (_: Exception) { bareName }
    }

    private fun buildRustImplName(implItem: PsiElement): String {
        return try {
            val traitRef = implItem.javaClass.getMethod("getTraitRef").invoke(implItem)
            val typeRef = implItem.javaClass.getMethod("getTypeReference").invoke(implItem)
            val traitName = traitRef?.let { it.javaClass.getMethod("getText").invoke(it) as? String }
            val typeName = typeRef?.let { it.javaClass.getMethod("getText").invoke(it) as? String }
            when {
                traitName != null && typeName != null -> "impl $traitName for $typeName"
                typeName != null -> "impl $typeName"
                else -> "impl"
            }
        } catch (_: Exception) { "impl" }
    }

    private fun addFunctionalExpressionImpls(
        target: PsiElement,
        searchScope: com.intellij.psi.search.GlobalSearchScope,
        collectLimit: Int,
        results: MutableList<ImplementationLocation>,
        project: Project
    ) {
        val psiClass = psiClassClass ?: return
        val psiMethod = psiMethodClass ?: return
        if (lambdaUtilClass == null || functionalExpressionSearchClass == null) return
        try {
            val targetClass: Any? = when {
                psiMethod.isInstance(target) -> target.javaClass.getMethod("getContainingClass").invoke(target)
                psiClass.isInstance(target) -> target
                else -> null
            }
            if (targetClass == null || !psiClass.isInstance(targetClass)) return

            val isFunctional = lambdaUtilClass!!
                .getMethod("isFunctionalClass", psiClass)
                .invoke(null, targetClass) as? Boolean ?: false
            if (!isFunctional) return

            val searchMethod = functionalExpressionSearchClass!!.getMethod("search", psiClass, com.intellij.psi.search.SearchScope::class.java)
            val query = searchMethod.invoke(null, targetClass, searchScope)
            val forEachMethod = query.javaClass.getMethod("forEach", com.intellij.util.Processor::class.java)
            forEachMethod.invoke(query, com.intellij.util.Processor<PsiElement> { funExpr ->
                val location = convertFunctionalExprToLocation(funExpr, project) ?: return@Processor true
                results.add(location)
                results.size < collectLimit
            })
        } catch (e: Exception) {
            LOG.debug("FunctionalExpressionSearch failed: ${e.message}")
        }
    }

    private fun convertFunctionalExprToLocation(element: PsiElement, project: Project): ImplementationLocation? {
        val file = element.containingFile?.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return null
        val line = document.getLineNumber(element.textOffset) + 1
        val lineStart = document.getLineStartOffset(document.getLineNumber(element.textOffset))
        val column = element.textOffset - lineStart + 1

        val kind = when (element.javaClass.simpleName) {
            "PsiMethodReferenceExpressionImpl" -> "METHOD_REFERENCE"
            "PsiLambdaExpressionImpl" -> "LAMBDA"
            else -> if (element.javaClass.simpleName.contains("MethodRef")) "METHOD_REFERENCE"
                    else if (element.javaClass.simpleName.contains("Lambda")) "LAMBDA"
                    else "FUNCTIONAL_EXPRESSION"
        }

        val name = buildFunctionalExprName(element)

        val qualifiedName = if (kind == "METHOD_REFERENCE") {
            try {
                val resolveMethod = element.javaClass.getMethod("resolve")
                val resolved = resolveMethod.invoke(element) as? PsiElement
                resolved?.let { QualifiedNameUtil.getQualifiedName(it) }
            } catch (_: Exception) { null }
        } else null

        return ImplementationLocation(
            name = name,
            file = ProjectUtils.getToolFilePath(project, file),
            line = line,
            column = column,
            kind = kind,
            language = displayLanguageName(element.language.id),
            qualifiedName = qualifiedName
        )
    }

    private fun buildFunctionalExprName(element: PsiElement): String {
        return try {
            val methodName = findEnclosingMethodName(element)
            val className = findEnclosingClassName(element)
            val exprText = element.text?.take(40) ?: "expression"
            buildString {
                append(exprText)
                if (methodName != null) {
                    append(" in $methodName()")
                    if (className != null) append(" in $className")
                }
            }
        } catch (_: Exception) { element.text?.take(40) ?: "expression" }
    }

    private fun findEnclosingMethodName(element: PsiElement): String? {
        var current = element.parent
        var depth = 0
        while (current != null && depth < 20) {
            if (psiMethodClass?.isInstance(current) == true) {
                return (current as? PsiNamedElement)?.name
            }
            if (current is PsiNamedElement) {
                val simpleName = current.javaClass.simpleName.lowercase()
                if (simpleName.contains("method") || simpleName.contains("function")) {
                    return current.name
                }
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun findEnclosingClassName(element: PsiElement): String? {
        var current = element.parent
        var depth = 0
        while (current != null && depth < 30) {
            if (psiClassClass?.isInstance(current) == true) {
                return QualifiedNameUtil.getQualifiedName(current) ?: (current as? PsiNamedElement)?.name
            }
            current = current.parent
            depth++
        }
        return null
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
}
