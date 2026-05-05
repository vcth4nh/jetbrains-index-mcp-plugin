package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.displayLanguageName
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.FileStructureResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StructureNode
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.TreeFormatter
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.Group
import com.intellij.ide.util.treeView.smartTree.ProvidingTreeModel
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure
import com.intellij.ide.util.treeView.smartTree.TreeAction
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reports the hierarchical structure of a file, mirroring the IDE's Structure view.
 *
 * Drives the IDE's own structure-view machinery (`LanguageStructureViewBuilder` →
 * `TreeModelWrapper` + `SmartTreeStructure`), so filters, node providers, and groupers
 * registered by each language plugin are honored. The optional `show` parameter selects
 * which named categories are visible; defaults are tuned for agent code-reading rather
 * than the IDE's human-skimming defaults.
 */
class FileStructureTool : AbstractMcpTool() {

    override val name = "ide_file_structure"

    override val description = """
        Get the hierarchical structure of a source file (similar to IDE's Structure view).

        Output mirrors what the IDE shows in its Structure tool window for the file's language,
        formatted as an indented tree with element names, optional signature info, and line numbers.

        Parameters:
          file (required) - Path relative to project root.
          show (optional) - List of filter names to enable. Omit to use language defaults.
                            If provided, only these filters are active; anything not listed is
                            hidden, even if it'd be on by default.

        Available filters per language (* = on by default):
          python:                   fields*, inherited
          java / kotlin:            inherited, non_public*, properties*
          javascript / typescript:  fields*, inherited, inherited_from_object*
          php:                      anonymous_classes, inherited, lambdas*, constants*, includes*,
                                    private_members*, properties*, protected_members*
          go:                       package_structure*, private_members*
          rust:                     (no filters)

        Examples:
          {"file": "src/main.py"}                                   - language defaults
          {"file": "src/main.py", "show": ["fields", "inherited"]}  - explicit (both visible)
          {"file": "src/main.py", "show": []}                       - hide every filterable category (minimum)
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Path to file relative to project root (e.g., 'src/main/java/com/example/MyClass.java'). REQUIRED.")
        .stringArrayProperty(
            "show",
            "Optional list of filter names to enable. See tool description for per-language filter names. " +
                "Omit to use language defaults. Pass an explicit array to override.",
        )
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")

        val showOverride: Set<String>? = (arguments["show"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()

        return suspendingReadAction {
            val psiFile = getPsiFile(project, file)
                ?: return@suspendingReadAction createErrorResult("File not found: $file")

            val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
                ?: return@suspendingReadAction createErrorResult(
                    "No structure view available for language '${psiFile.language.id}'."
                )

            val nodes = extractStructure(builder, psiFile, project, showOverride)

            if (nodes.isEmpty()) {
                return@suspendingReadAction createSuccessResult(
                    "File is empty or has no parseable structure.\n\n" +
                        "File: ${psiFile.name}\n" +
                        "Language: ${psiFile.language.id}"
                )
            }

            createJsonResult(FileStructureResult(
                file = file,
                language = displayLanguageName(psiFile.language.id),
                structure = TreeFormatter.format(nodes, psiFile.name)
            ))
        }
    }

    private fun extractStructure(
        builder: com.intellij.ide.structureView.StructureViewBuilder,
        psiFile: PsiFile,
        project: Project,
        showOverride: Set<String>?,
    ): List<StructureNode> {
        if (builder !is TreeBasedStructureViewBuilder) {
            // Fallback: full StructureView (rare). No filter support here.
            val view = builder.createStructureView(/* fileEditor = */ null, project)
            try {
                return walkAbstractTreeNode(
                    SmartTreeStructure(project, view.treeModel).rootElement as AbstractTreeNode<*>,
                    project,
                )
            } finally {
                Disposer.dispose(view)
            }
        }

        val model = builder.createStructureViewModel(/* editor = */ null)
        try {
            // Canonicalize JS/TS dialect ids ("ECMAScript 6", "JSX Harmony", "TypeScript JSX")
            // through `displayLanguageName` so the MATCHERS / DEFAULTS lookup hits a single key.
            val canonicalKey = displayLanguageName(psiFile.language.id).lowercase()
            val effectiveShow = showOverride ?: DEFAULTS[canonicalKey] ?: emptySet()
            val activeIdeNames = computeActiveIdeNames(model, canonicalKey, effectiveShow)
            val owner = ShowBasedActionsOwner(activeIdeNames)
            val wrappedModel = TreeModelWrapper(model, owner)
            val structure = SmartTreeStructure(project, wrappedModel)
            val root = structure.rootElement as? AbstractTreeNode<*> ?: return emptyList()
            return walkAbstractTreeNode(root, project)
        } finally {
            Disposer.dispose(model)
        }
    }

    /**
     * Decide which IDE action names should report `isActionActive == true` to `TreeModelWrapper`.
     *
     * `TreeModelWrapper.getFilters()` returns filters whose name is "active" — those are the
     * filters that get APPLIED. `Filter.isVisible(elem) == false` then HIDES that element. So:
     *
     * - For a hide-style filter (i.e. `Filter` with `isReverted() == true`, the standard
     *   convention for "Show X" toggles): the filter is APPLIED → category HIDDEN. Our `show`
     *   list names the categories the user wants VISIBLE, so we mark the filter active iff
     *   the category is *not* in `show` (active = applied = hidden).
     *
     * - For node providers and groupers (and the rare non-reverted filter): being "active"
     *   means the action runs and ADDS / TRANSFORMS nodes. Listing in `show` should activate.
     */
    private fun computeActiveIdeNames(
        model: StructureViewModel,
        languageId: String,
        show: Set<String>,
    ): Set<String> {
        val matchers = MATCHERS[languageId] ?: return emptySet()
        val active = mutableSetOf<String>()
        val allActions: Sequence<TreeAction> = sequence {
            yieldAll(model.filters.asSequence())
            (model as? ProvidingTreeModel)?.nodeProviders?.let { yieldAll(it) }
            yieldAll(model.groupers.asSequence())
        }
        for (action in allActions) {
            val ideName = action.name
            val matched = matchers.firstOrNull { matcher ->
                matcher.patterns.any { ideName.contains(it, ignoreCase = true) }
            } ?: continue
            val isHideFilter = action is com.intellij.ide.util.treeView.smartTree.Filter && action.isReverted()
            val shouldBeActive = if (isHideFilter) {
                matched.normalized !in show
            } else {
                matched.normalized in show
            }
            if (shouldBeActive) active.add(ideName)
        }
        return active
    }

    private fun walkAbstractTreeNode(node: AbstractTreeNode<*>, project: Project): List<StructureNode> =
        node.children.mapNotNull { walkChildNode(it, project) }

    private fun walkChildNode(node: AbstractTreeNode<*>, project: Project): StructureNode? {
        val (name, signature, line) = when (val value = node.value) {
            is StructureViewTreeElement -> {
                val pres = value.presentation
                val rawName = pres.presentableText?.takeIf { it.isNotBlank() } ?: return null
                val sig = pres.locationString?.takeIf { it.isNotBlank() }
                val l = (value.value as? PsiElement)?.let { lineOf(project, it) } ?: 1
                Triple(rawName, sig, l)
            }
            is Group -> {
                val pres = value.presentation
                val rawName = pres.presentableText?.takeIf { it.isNotBlank() } ?: return null
                Triple(rawName, pres.locationString?.takeIf { it.isNotBlank() }, 1)
            }
            else -> return null
        }
        return StructureNode(
            name = name,
            signature = signature,
            line = line,
            children = walkAbstractTreeNode(node, project),
        )
    }

    private fun lineOf(project: Project, psi: PsiElement): Int {
        val containingFile = psi.containingFile ?: return 1
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile) ?: return 1
        val offset = psi.textOffset
        if (offset < 0 || offset > document.textLength) return 1
        return document.getLineNumber(offset) + 1
    }

    private data class FilterCategoryMatcher(val normalized: String, val patterns: List<String>)

    /**
     * `TreeActionsOwner` reports a `TreeAction` as "user-active" when its underlying
     * IDE-side action name is in our derived active-set. The `TreeModelWrapper.isActive`
     * convention then translates this to the right toolbar-style state for filters
     * (with `isReverted=true`), node providers, and groupers — reproducing what the
     * IDE does when the user toggles the corresponding button.
     */
    private class ShowBasedActionsOwner(
        private val activeIdeNames: Set<String>,
    ) : TreeActionsOwner {
        override fun isActionActive(name: String): Boolean = name in activeIdeNames
        override fun setActionActive(name: String, state: Boolean) { /* no-op */ }
    }

    companion object {
        // Keys are `psiFile.language.id.lowercase()` — language IDs vary in case across plugins
        // (`JAVA`, `Python`, `kotlin`, `JavaScript`, `PHP`, `go`, `Rust`) so we normalize on lookup.
        // Match-pattern lists are ordered: more-specific first (e.g., `inherited_from_object`
        // before `inherited`) since the first match wins.
        private val MATCHERS: Map<String, List<FilterCategoryMatcher>> = mapOf(
            "python" to listOf(
                FilterCategoryMatcher("fields", listOf("FIELD")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "java" to listOf(
                FilterCategoryMatcher("non_public", listOf("NON_PUBLIC", "non.public")),
                FilterCategoryMatcher("properties", listOf("PROPERTIES", "PROPERT")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "kotlin" to listOf(
                FilterCategoryMatcher("non_public", listOf("NON_PUBLIC", "non.public")),
                FilterCategoryMatcher("properties", listOf("PROPERTIES", "PROPERT")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "javascript" to listOf(
                FilterCategoryMatcher("inherited_from_object", listOf("OBJECT")),
                FilterCategoryMatcher("fields", listOf("FIELD")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "typescript" to listOf(
                FilterCategoryMatcher("inherited_from_object", listOf("OBJECT")),
                FilterCategoryMatcher("fields", listOf("FIELD")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "php" to listOf(
                FilterCategoryMatcher("anonymous_classes", listOf("ANONYMOUS")),
                FilterCategoryMatcher("private_members", listOf("PRIVATE")),
                FilterCategoryMatcher("protected_members", listOf("PROTECTED")),
                FilterCategoryMatcher("constants", listOf("CONSTANT")),
                FilterCategoryMatcher("includes", listOf("INCLUDE")),
                FilterCategoryMatcher("lambdas", listOf("LAMBDA")),
                FilterCategoryMatcher("properties", listOf("PROPERTIES", "PROPERT")),
                FilterCategoryMatcher("inherited", listOf("INHERITED")),
            ),
            "go" to listOf(
                FilterCategoryMatcher("package_structure", listOf("PACKAGE")),
                FilterCategoryMatcher("private_members", listOf("PRIVATE")),
            ),
        )

        private val DEFAULTS: Map<String, Set<String>> = mapOf(
            "python" to setOf("fields"),
            "java" to setOf("non_public", "properties"),
            "kotlin" to setOf("non_public", "properties"),
            "javascript" to setOf("fields", "inherited_from_object"),
            "typescript" to setOf("fields", "inherited_from_object"),
            "php" to setOf(
                "constants", "includes", "lambdas", "private_members", "properties", "protected_members"
            ),
            "go" to setOf("package_structure", "private_members"),
            // Rust intentionally absent — no filters
        )
    }
}
