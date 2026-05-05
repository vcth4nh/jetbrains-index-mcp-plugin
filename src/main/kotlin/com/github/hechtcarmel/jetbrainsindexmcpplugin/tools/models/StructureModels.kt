package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models

import kotlinx.serialization.Serializable

// One node in the file structure tree.
//
// TreeFormatter renders each node as: kind modifiers name signature (line N).
// Per-language decorators in tools/navigation/structure populate kind, modifiers,
// and signature from the underlying PSI element. Unknown values are omitted.
//
// name        bare identifier (class name, method name, field name, etc.)
// kind        element keyword like class / interface / method / field / def / fun
// modifiers   explicit source-order modifiers (public, private, final, abstract, ...)
// signature   suffix text such as method params, field type, or "extends X implements Y"
// line        1-based source line where the underlying PSI element begins
// children    nested nodes, recursively decorated
@Serializable
data class StructureNode(
    val name: String,
    val kind: String? = null,
    val modifiers: List<String> = emptyList(),
    val signature: String? = null,
    val line: Int,
    val children: List<StructureNode> = emptyList()
)

// Output model returned by the file_structure tool.
@Serializable
data class FileStructureResult(
    val file: String,
    val language: String,
    val structure: String
)
