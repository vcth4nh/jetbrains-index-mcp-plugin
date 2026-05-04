package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.displayLanguageName
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.FileStructureResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StructureNode
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.TreeFormatter
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reports the hierarchical structure of a file, mirroring the IDE's Structure view.
 *
 * Delegates to `LanguageStructureViewBuilder` (the same dispatcher the IDE uses), so the
 * tree comes from each language plugin's own `PsiStructureViewFactory` — no per-language
 * code in this plugin. Supports any language with a registered factory: Java, Kotlin,
 * Python, JS/TS, Go, Rust, PHP, Markdown, and others.
 */
class FileStructureTool : AbstractMcpTool() {

    override val name = "ide_file_structure"

    override val description = """
        Get the hierarchical structure of a source file (similar to IDE's Structure view).

        Shows classes, methods, fields, functions, headings, and their nesting in a tree format.
        Output exactly mirrors what the IDE shows in its Structure tool window for the file's language.

        Returns: Formatted tree string. Each node carries its presentable name, optional location
        info (e.g. `extends Foo`), and line number.

        Parameters: file (required) - Path relative to project root

        Example: {"file": "src/main/java/com/example/MyClass.java"}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Path to file relative to project root (e.g., 'src/main/java/com/example/MyClass.java'). REQUIRED.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")

        return suspendingReadAction {
            val psiFile = getPsiFile(project, file)
                ?: return@suspendingReadAction createErrorResult("File not found: $file")

            val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile)
                ?: return@suspendingReadAction createErrorResult(
                    "No structure view available for language '${psiFile.language.id}'."
                )

            val nodes = extractStructure(builder, psiFile, project)

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
    ): List<StructureNode> {
        // Most language plugins extend TreeBasedStructureViewBuilder, which lets us obtain the
        // model directly without spinning up a UI StructureView. Fall back to the full StructureView
        // path otherwise (rare, but kept for completeness).
        if (builder is TreeBasedStructureViewBuilder) {
            val model = builder.createStructureViewModel(/* editor = */ null)
            try {
                return walkChildren(model.root, project)
            } finally {
                Disposer.dispose(model)
            }
        }

        val view = builder.createStructureView(/* fileEditor = */ null, project)
        try {
            return walkChildren(view.treeModel.root, project)
        } finally {
            Disposer.dispose(view)
        }
    }

    private fun walkChildren(element: StructureViewTreeElement, project: Project): List<StructureNode> =
        element.children.mapNotNull { child ->
            (child as? StructureViewTreeElement)?.let { walk(it, project) }
        }

    private fun walk(element: StructureViewTreeElement, project: Project): StructureNode {
        val presentation = element.presentation
        val name = presentation.presentableText?.takeIf { it.isNotBlank() } ?: "<unnamed>"
        val signature = presentation.locationString?.takeIf { it.isNotBlank() }
        val line = (element.value as? PsiElement)?.let { lineOf(project, it) } ?: 1
        return StructureNode(
            name = name,
            signature = signature,
            line = line,
            children = walkChildren(element, project),
        )
    }

    private fun lineOf(project: Project, psi: PsiElement): Int {
        val containingFile = psi.containingFile ?: return 1
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile) ?: return 1
        val offset = psi.textOffset
        if (offset < 0 || offset > document.textLength) return 1
        return document.getLineNumber(offset) + 1
    }

}
