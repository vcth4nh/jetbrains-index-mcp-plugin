package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models

import kotlinx.serialization.Serializable

/**
 * One node in the file structure tree.
 *
 * Rendered by `TreeFormatter` as: `<kind?> <modifiers...> <name>[ <signature>] (line N)`.
 * Per-language decorators (`tools/navigation/structure/*NodeDecorator.kt`) populate these
 * fields from the underlying PSI element. When no decorator handles a value, only `name`
 * is filled and the line collapses to `<name> (line N)`.
 *
 * @property name Bare identifier — class name, method name, field name, etc.
 * @property kind Element kind keyword (e.g. `class`, `interface`, `method`, `field`,
 *   `constructor`, `def`, `fun`). Null when unknown.
 * @property modifiers Explicit modifiers in source order (`public`, `private`,
 *   `final`, `abstract`, `open`, `override`, …). Empty when none.
 * @property signature Optional suffix — return type + params for methods, type for
 *   fields, `extends X implements Y` for classes, etc.
 * @property line 1-based line number where the underlying PSI element is defined.
 * @property children Child nodes, recursively decorated.
 */
@Serializable
data class StructureNode(
    val name: String,
    val kind: String? = null,
    val modifiers: List<String> = emptyList(),
    val signature: String? = null,
    val line: Int,
    val children: List<StructureNode> = emptyList()
)

/**
 * Output model for the file structure tool.
 *
 * @property file The file path relative to project root.
 * @property language The display language name (e.g., `Java`, `Python`, `Kotlin`).
 * @property structure The formatted tree string.
 */
@Serializable
data class FileStructureResult(
    val file: String,
    val language: String,
    val structure: String
)
