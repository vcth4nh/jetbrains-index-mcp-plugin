package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

object ProjectUtils {

    fun getRelativePath(project: Project, virtualFile: VirtualFile): String {
        // Try module content roots first (workspace sub-projects)
        val contentRootPath = findMatchingContentRoot(project, virtualFile.path)
        if (contentRootPath != null) {
            return virtualFile.path.removePrefix(contentRootPath).removePrefix("/")
        }

        val basePath = project.basePath ?: return virtualFile.path
        return virtualFile.path.removePrefix(basePath).removePrefix("/")
    }

    fun getRelativePath(project: Project, absolutePath: String): String {
        val contentRootPath = findMatchingContentRoot(project, absolutePath)
        if (contentRootPath != null) {
            return absolutePath.removePrefix(contentRootPath).removePrefix("/")
        }

        val basePath = project.basePath ?: return absolutePath
        return absolutePath.removePrefix(basePath).removePrefix("/")
    }

    fun resolveProjectFile(project: Project, relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val fullPath = if (relativePath.startsWith("/")) relativePath else "$basePath/$relativePath"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }

    /**
     * Resolves a file path against the project, trying module content roots
     * when the standard project basePath resolution fails.
     * This supports workspace scenarios where sub-projects have different root directories.
     *
     * @param effectiveBasePath Optional override for the base path (e.g., from project_path argument)
     */
    fun resolveProjectFileWithWorkspace(
        project: Project,
        relativePath: String,
        effectiveBasePath: String? = null
    ): VirtualFile? {
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            return LocalFileSystem.getInstance().findFileByPath(relativePath)
        }

        // Try effective base path first (from project_path argument)
        if (effectiveBasePath != null) {
            val fullPath = "$effectiveBasePath/$relativePath"
            val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            if (file != null) return file
        }

        // Try project basePath
        val basePath = project.basePath
        if (basePath != null) {
            val fullPath = "$basePath/$relativePath"
            val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            if (file != null) return file
        }

        // Try each module content root
        return resolveAgainstContentRoots(project, relativePath)
    }

    fun getProjectBasePath(project: Project): String? {
        return project.basePath
    }

    fun isProjectFile(project: Project, virtualFile: VirtualFile): Boolean {
        val basePath = project.basePath ?: return false
        if (virtualFile.path.startsWith(basePath)) return true

        // Also check module content roots for workspace sub-projects
        return findMatchingContentRoot(project, virtualFile.path) != null
    }

    /**
     * Returns all module content root paths for a project.
     * For workspace projects, this includes paths to all sub-projects.
     */
    fun getModuleContentRoots(project: Project): List<String> {
        return try {
            ModuleManager.getInstance(project).modules.flatMap { module ->
                ModuleRootManager.getInstance(module).contentRoots.map { it.path }
            }
        } catch (e: Exception) {
            listOfNotNull(project.basePath)
        }
    }

    /**
     * Finds the content root path that contains the given absolute file path.
     * Returns null if no content root matches.
     */
    private fun findMatchingContentRoot(project: Project, absolutePath: String): String? {
        try {
            val modules = ModuleManager.getInstance(project).modules
            for (module in modules) {
                val contentRoots = ModuleRootManager.getInstance(module).contentRoots
                for (root in contentRoots) {
                    if (absolutePath.startsWith(root.path)) {
                        return root.path
                    }
                }
            }
        } catch (_: Exception) {
            // ModuleManager may not be available in all contexts
        }
        return null
    }

    /**
     * Tries to resolve a relative path against each module content root.
     */
    private fun resolveAgainstContentRoots(project: Project, relativePath: String): VirtualFile? {
        try {
            val modules = ModuleManager.getInstance(project).modules
            for (module in modules) {
                val contentRoots = ModuleRootManager.getInstance(module).contentRoots
                for (root in contentRoots) {
                    val fullPath = "${root.path}/$relativePath"
                    val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
                    if (file != null) return file
                }
            }
        } catch (_: Exception) {
            // ModuleManager may not be available in all contexts
        }
        return null
    }
}
