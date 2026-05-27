package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

object PluginDetectors {
    val java = PluginDetector(
        name = "Java",
        pluginIds = listOf("com.intellij.java", "com.intellij.modules.java")
    )

    val php = PluginDetector(
        name = "PHP",
        pluginIds = listOf("com.jetbrains.php")
    )

    val rust = PluginDetector(
        name = "Rust",
        pluginIds = listOf("com.jetbrains.rust"),
        fallbackClass = "org.rust.lang.core.psi.RsFile"
    )

    val kotlin = PluginDetector(
        name = "Kotlin",
        pluginIds = listOf("org.jetbrains.kotlin")
    )
}
