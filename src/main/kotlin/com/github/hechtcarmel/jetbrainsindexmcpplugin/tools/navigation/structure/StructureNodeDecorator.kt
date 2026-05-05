package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.structure

import com.intellij.navigation.ItemPresentation

/**
 * Decoded fields for one node in the file structure tree.
 *
 * Per-language decorators inspect the underlying PSI element and return
 * one of these. `name` is the only required field; everything else is
 * optional and will be omitted from the formatted output if blank/empty.
 */
internal data class DecoratedNode(
    val name: String,
    val kind: String? = null,
    val modifiers: List<String> = emptyList(),
    val signature: String? = null,
)

/**
 * Per-language hook that converts a `StructureViewTreeElement.value` (typically a
 * PSI element) into a [DecoratedNode] carrying explicit kind / modifier / signature
 * fields. Returning `null` falls through to a presentation-only default.
 */
internal interface NodeDecorator {
    fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode?
}

/**
 * Picks the right decorator for a given canonical language id (lowercased
 * `displayLanguageName(...)` output). Unknown languages get [DefaultNodeDecorator].
 */
internal object NodeDecorators {
    fun forLanguage(canonicalLanguageId: String): NodeDecorator = when (canonicalLanguageId) {
        "java" -> JavaNodeDecorator
        "kotlin" -> KotlinNodeDecorator
        "python" -> PythonNodeDecorator
        "javascript", "typescript" -> JavaScriptNodeDecorator
        "php" -> PhpNodeDecorator
        "go" -> GoNodeDecorator
        "rust" -> RustNodeDecorator
        else -> DefaultNodeDecorator
    }
}

/**
 * Falls back to the IDE's `ItemPresentation.presentableText` (with optional
 * `locationString` as a signature). Used for languages without a dedicated decorator.
 */
internal object DefaultNodeDecorator : NodeDecorator {
    override fun decorate(value: Any?, fallback: ItemPresentation): DecoratedNode? {
        val name = fallback.presentableText?.takeIf { it.isNotBlank() } ?: return null
        val sig = fallback.locationString?.takeIf { it.isNotBlank() }
        return DecoratedNode(name = name, signature = sig)
    }
}
