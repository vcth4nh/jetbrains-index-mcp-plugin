package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RefactoringResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ProjectUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.util.containers.MultiMap
import com.intellij.usageView.UsageInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Move file tool that uses the IDE's Move refactoring to relocate files
 * while delegating semantic updates to the IDE language plugin when supported.
 *
 * Most languages use [MoveFilesOrDirectoriesProcessor], which delegates file-move
 * semantics to language-specific [com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler]s.
 * PHP class files are special: PhpStorm exposes class/namespace-aware move refactorings
 * through the higher-level [MoveHandler] dispatcher, so we route those files through
 * the semantic move path instead of the plain file-move processor.
 *
 * Three-phase approach:
 * 1. **Read Action**: Validate source file exists
 * 2. **EDT Write Action**: Ensure destination directory exists (create if needed)
 * 3. **EDT**: Execute the appropriate move backend
 */
open class MoveFileTool : AbstractRefactoringTool() {

    override val name = "ide_move_file"

    override val description = """
        Move a file to a new directory using the IDE's refactoring engine. Applies language-aware reference and namespace/package updates when the IDE provides a semantic move backend for that file type.

        Use when relocating files to maintain correct imports and references.

        Parameters:
        - file (REQUIRED): Source file path relative to project root
        - destination (REQUIRED): Target directory path relative to project root. Created automatically if it doesn't exist.

        Returns: success status, list of affected files, and result message.

        Examples:
        - Move file: {"file": "src/main/java/com/old/MyClass.java", "destination": "src/main/java/com/new"}
        - Move config file: {"file": "config/old.yml", "destination": "config/archive"}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .file(description = "Path to the source file to move, relative to project root. REQUIRED.")
        .stringProperty("destination", "Target directory path relative to project root. The file will be moved into this directory. Created automatically if it doesn't exist. REQUIRED.", required = true)
        .build()

    internal enum class MoveBackend {
        GENERIC_FILE_MOVE,
        PHP_SEMANTIC_MOVE
    }

    internal sealed class MoveBackendSelection {
        object GenericFileMove : MoveBackendSelection()
        data class PhpSemanticMove(
            val declarationPointer: SmartPsiElementPointer<PsiElement>,
            val declarationName: String
        ) : MoveBackendSelection()
        data class Unsupported(val message: String) : MoveBackendSelection()
    }

    internal data class MovePreparation(
        val psiFile: PsiFile,
        val targetDirectory: PsiDirectory,
        val sourceRelativePath: String,
        val destinationRelativePath: String,
        val backend: MoveBackend,
        val phpDeclarationPointer: SmartPsiElementPointer<PsiElement>? = null,
        val phpDeclarationName: String? = null
    )

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val destination = arguments["destination"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: destination")

        requireSmartMode(project)

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 1: VFS + READ ACTION - Validate source file
        // ═══════════════════════════════════════════════════════════════════════
        val sourceVirtualFile = resolveFile(project, file)
            ?: return createErrorResult("Source file not found: $file")
        val sourceInfo = suspendingReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(sourceVirtualFile)
            if (psiFile == null || !psiFile.isPhysical) {
                return@suspendingReadAction null
            }
            val relativePath = psiFile.virtualFile?.let { getRelativePath(project, it) } ?: file
            Triple(psiFile, psiFile.name, relativePath)
        } ?: return createErrorResult("Source file not found: $file")

        val (_, fileName, sourceRelativePath) = sourceInfo

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 2: EDT WRITE ACTION - Ensure destination directory exists
        // ═══════════════════════════════════════════════════════════════════════
        val targetDir = ensureDestinationDirectory(project, destination)
            ?: return createErrorResult("Invalid destination '$destination': could not resolve or create directory")

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 3: READ ACTION - Validate move (same dir, name conflict, get PSI dir)
        // ═══════════════════════════════════════════════════════════════════════
        val preparation = suspendingReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(sourceVirtualFile)
                ?: return@suspendingReadAction null to "Source file no longer valid"

            val targetPsiDir = PsiManager.getInstance(project).findDirectory(targetDir)
                ?: return@suspendingReadAction null to "Could not find PSI directory for destination"

            val currentDir = psiFile.containingDirectory
            if (currentDir != null && currentDir.virtualFile.path == targetDir.path) {
                return@suspendingReadAction null to "File '$file' is already in the destination directory"
            }

            if (targetPsiDir.findFile(fileName) != null) {
                return@suspendingReadAction null to "A file named '$fileName' already exists in '$destination'"
            }

            when (val backendSelection = selectMoveBackend(project, psiFile)) {
                is MoveBackendSelection.Unsupported -> null to backendSelection.message
                MoveBackendSelection.GenericFileMove -> {
                    val destinationRelativePath = getRelativePath(project, targetDir)
                    MovePreparation(
                        psiFile = psiFile,
                        targetDirectory = targetPsiDir,
                        sourceRelativePath = sourceRelativePath,
                        destinationRelativePath = destinationRelativePath,
                        backend = MoveBackend.GENERIC_FILE_MOVE
                    ) to null
                }
                is MoveBackendSelection.PhpSemanticMove -> {
                    val destinationRelativePath = getRelativePath(project, targetDir)
                    MovePreparation(
                        psiFile = psiFile,
                        targetDirectory = targetPsiDir,
                        sourceRelativePath = sourceRelativePath,
                        destinationRelativePath = destinationRelativePath,
                        backend = MoveBackend.PHP_SEMANTIC_MOVE,
                        phpDeclarationPointer = backendSelection.declarationPointer,
                        phpDeclarationName = backendSelection.declarationName
                    ) to null
                }
            }
        }

        val (movePrep, error) = preparation
        if (movePrep == null) {
            return createErrorResult(error ?: "Unknown validation error")
        }

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 4: EDT - Execute move processor (manages its own write actions)
        // ═══════════════════════════════════════════════════════════════════════
        return executeMove(project, movePrep)
    }

    /**
     * Ensures the destination directory exists, creating it on EDT if needed.
     * Uses [VfsUtil.createDirectoryIfMissing] which is the standard IDE API for this.
     */
    private suspend fun ensureDestinationDirectory(project: Project, destination: String): VirtualFile? {
        // First try resolving without creating (fast path, no write action needed)
        val existing = resolveFile(project, destination)
        if (existing != null && existing.isDirectory) {
            return existing
        }

        // Create directory on EDT inside a write action
        val basePath = project.basePath ?: return null
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null

        var created: VirtualFile? = null
        edtAction {
            WriteCommandAction.writeCommandAction(project)
                .withName("Create Directory: $destination")
                .withGroupId("MCP Refactoring")
                .run<Throwable> {
                    created = try {
                        VfsUtil.createDirectoryIfMissing(baseDir, destination)
                    } catch (_: Exception) {
                        null
                    }
                }
        }
        return created
    }

    /**
     * Executes the move refactoring on EDT using MoveFilesOrDirectoriesProcessor.
     *
     * The processor manages its own WriteCommandAction internally, so we do NOT
     * wrap it in another WriteCommandAction. We only ensure it runs on EDT.
     */
    private suspend fun executeMove(
        project: Project,
        preparation: MovePreparation
    ): ToolCallResult {
        var success = false
        var errorMessage: String? = null
        var affectedFiles = linkedSetOf<String>()
        val fileName = preparation.psiFile.name

        edtAction {
            try {
                if (!preparation.psiFile.isValid || !preparation.targetDirectory.isValid) {
                    errorMessage = "Source file or target directory is no longer valid"
                    return@edtAction
                }

                val filePointer = SmartPointerManager.createPointer(preparation.psiFile)
                val modifiedFilesBeforeMove = collectUnsavedProjectFiles(project)

                when (preparation.backend) {
                    MoveBackend.GENERIC_FILE_MOVE -> executeGenericFileMove(preparation)
                    MoveBackend.PHP_SEMANTIC_MOVE -> executePhpSemanticMove(project, preparation)
                }

                PsiDocumentManager.getInstance(project).commitAllDocuments()
                affectedFiles = collectAffectedFiles(project, preparation, filePointer, fileName, modifiedFilesBeforeMove)
                FileDocumentManager.getInstance().saveAllDocuments()

                success = true
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }

        return if (success) {
            val newPath = affectedFiles.lastOrNull { it != preparation.sourceRelativePath }
                ?: "${preparation.destinationRelativePath}/$fileName"
            val backendNote = when (preparation.backend) {
                MoveBackend.GENERIC_FILE_MOVE -> " using IDE file move semantics"
                MoveBackend.PHP_SEMANTIC_MOVE -> " using PhpStorm semantic PHP move"
            }
            createJsonResult(
                RefactoringResult(
                    success = true,
                    affectedFiles = affectedFiles.toList(),
                    changesCount = affectedFiles.size,
                    message = "Successfully moved '${preparation.sourceRelativePath}' to '$newPath'$backendNote"
                )
            )
        } else {
            createErrorResult("Move failed: ${errorMessage ?: "Unknown error"}")
        }
    }

    internal open fun selectMoveBackend(
        project: Project,
        psiFile: PsiFile
    ): MoveBackendSelection {
        if (!PluginDetectors.php.isAvailable || !isPhpFile(psiFile)) {
            return MoveBackendSelection.GenericFileMove
        }

        val phpDeclarations = findNamedPhpDeclarations(psiFile)
        if (phpDeclarations.isEmpty()) {
            return MoveBackendSelection.GenericFileMove
        }
        if (phpDeclarations.size > 1) {
            return MoveBackendSelection.Unsupported(
                "PHP semantic move is ambiguous for '${psiFile.name}' because it contains multiple named PHP declarations. " +
                    "Use PhpStorm's interactive Move refactoring for this file."
            )
        }

        val declaration = phpDeclarations.single()
        val declarationName = (declaration as? PsiNamedElement)?.name ?: psiFile.name
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
        return MoveBackendSelection.PhpSemanticMove(pointer, declarationName)
    }

    internal open fun executeGenericFileMove(
        preparation: MovePreparation
    ) {
        val processor = HeadlessMoveProcessor(
            preparation.psiFile.project,
            arrayOf<PsiElement>(preparation.psiFile),
            preparation.targetDirectory,
            true,
            false, // searchInComments
            false, // searchInNonJavaFiles
            null,  // moveCallback
            null   // prepareSuccessfulCallback
        )

        processor.setPreviewUsages(false)
        processor.run()
    }

    internal open fun executePhpSemanticMove(project: Project, preparation: MovePreparation) {
        val declaration = preparation.phpDeclarationPointer?.element
            ?: error("PHP declaration for semantic move is no longer valid")
        val adjusted = MoveHandler.adjustForMove(project, arrayOf(declaration), preparation.targetDirectory)
            ?: error(
                "PhpStorm semantic move could not prepare declaration '${preparation.phpDeclarationName ?: preparation.psiFile.name}'"
            )

        if (!MoveHandler.canMove(adjusted, preparation.targetDirectory)) {
            error(
                "PhpStorm semantic move is unavailable for PHP declaration '${preparation.phpDeclarationName ?: preparation.psiFile.name}'. " +
                    "Use PhpStorm's interactive Move refactoring for this file."
            )
        }

        MoveHandler.doMove(project, adjusted, preparation.targetDirectory, null, null)
    }

    private fun collectAffectedFiles(
        project: Project,
        preparation: MovePreparation,
        movedFilePointer: SmartPsiElementPointer<PsiFile>,
        fileName: String,
        modifiedFilesBeforeMove: Set<String>
    ): LinkedHashSet<String> {
        val affectedFiles = linkedSetOf<String>()
        affectedFiles.add(preparation.sourceRelativePath)

        val modifiedFilesAfterMove = collectUnsavedProjectFiles(project)
        affectedFiles.addAll(modifiedFilesAfterMove - modifiedFilesBeforeMove)

        val movedFile = movedFilePointer.element?.virtualFile
            ?: LocalFileSystem.getInstance().findFileByPath("${preparation.targetDirectory.virtualFile.path}/$fileName")
        if (movedFile != null) {
            affectedFiles.add(getRelativePath(project, movedFile))
        }

        return affectedFiles
    }

    private fun collectUnsavedProjectFiles(project: Project): Set<String> {
        val fileDocumentManager = FileDocumentManager.getInstance()
        return fileDocumentManager.unsavedDocuments
            .mapNotNull(fileDocumentManager::getFile)
            .filter { ProjectUtils.isProjectFile(project, it) }
            .map { getRelativePath(project, it) }
            .toSet()
    }

    private fun isPhpFile(psiFile: PsiFile): Boolean {
        return psiFile.language.id == "PHP" || psiFile.viewProvider.languages.any { it.id == "PHP" }
    }

    private fun findNamedPhpDeclarations(psiFile: PsiFile): List<PsiElement> {
        val phpClassClass = try {
            @Suppress("UNCHECKED_CAST")
            Class.forName("com.jetbrains.php.lang.psi.elements.PhpClass") as Class<out PsiElement>
        } catch (_: ClassNotFoundException) {
            return emptyList()
        }

        return PsiTreeUtil.findChildrenOfType(psiFile, phpClassClass)
            .filter { it.containingFile == psiFile }
            .filter { (it as? PsiNamedElement)?.name != null }
            .sortedBy { it.textOffset }
    }
}

/**
 * Headless move processor that suppresses conflict dialogs for autonomous operation.
 *
 * Overrides [showConflicts] to always proceed (return true) instead of showing
 * a modal dialog that would block the MCP tool execution.
 */
private class HeadlessMoveProcessor(
    project: Project,
    elements: Array<PsiElement>,
    newParent: PsiDirectory,
    searchForReferences: Boolean,
    searchInComments: Boolean,
    searchInNonJavaFiles: Boolean,
    moveCallback: com.intellij.refactoring.move.MoveCallback?,
    prepareSuccessfulCallback: Runnable?
) : MoveFilesOrDirectoriesProcessor(
    project, elements, newParent, searchForReferences,
    searchInComments, searchInNonJavaFiles, moveCallback, prepareSuccessfulCallback
) {
    override fun showConflicts(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): Boolean {
        return true
    }
}
