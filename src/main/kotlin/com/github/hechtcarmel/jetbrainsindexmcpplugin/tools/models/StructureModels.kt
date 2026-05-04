package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models

import kotlinx.serialization.Serializable

/**
 * One node in the file structure tree, mirroring the IDE's Structure view.
 *
 * @property name The presentable text the IDE would show in its Structure tool window
 *                (e.g. `myMethod(String, int): boolean`, `MyClass`).
 * @property signature Optional supplementary text the IDE attaches to the presentation
 *                     (`ItemPresentation.locationString` — e.g. `extends Foo`, `throws X`).
 * @property line 1-based line number where the underlying PSI element is defined.
 * @property children Child nodes from the IDE's `StructureViewTreeElement.children`.
 */
@Serializable
data class StructureNode(
    val name: String,
    val signature: String?,
    val line: Int,
    val children: List<StructureNode> = emptyList()
)

/**
 * Output model for the file structure tool.
 *
 * @property file The file path relative to project root.
 * @property language The language ID (e.g., `JAVA`, `Python`, `kotlin`).
 * @property structure The formatted tree string.
 */
@Serializable
data class FileStructureResult(
    val file: String,
    val language: String,
    val structure: String
)
