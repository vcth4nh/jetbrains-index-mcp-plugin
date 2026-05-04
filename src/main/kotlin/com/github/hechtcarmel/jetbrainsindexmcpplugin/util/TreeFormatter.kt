package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.StructureNode

/**
 * Format a tree of [StructureNode]s as a 2-space-indented text tree.
 */
object TreeFormatter {

    fun format(nodes: List<StructureNode>, fileName: String): String {
        val lines = mutableListOf<String>()
        lines.add(fileName)
        lines.add("")
        nodes.forEach { formatNode(it, indent = 0, output = lines) }
        return lines.joinToString("\n")
    }

    private fun formatNode(node: StructureNode, indent: Int, output: MutableList<String>) {
        val indentStr = "  ".repeat(indent)
        val signature = if (!node.signature.isNullOrBlank()) " ${node.signature}" else ""
        output.add("$indentStr${node.name}$signature (line ${node.line})")
        node.children.forEach { formatNode(it, indent + 1, output) }
    }
}
