# Refactoring Overhaul Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce code duplication, improve organization, and fix protocol issues across the IDE Index MCP Plugin without breaking existing functionality.

**Architecture:** Extract shared patterns into reusable utilities (PluginDetector, SchemaBuilder, ProjectResolver, ClassResolver), consolidate duplicate registration code into data-driven loops, fix JSON-RPC protocol compliance, and reorganize packages for clarity.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, Ktor CIO, JSON-RPC 2.0, MCP 2024-11-05

---

### Task 1: Create Generic PluginDetector and PluginDetectors Registry

**Files:**
- Create: `src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/util/PluginDetector.kt`
- Create: `src/main/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/util/PluginDetectors.kt`
- Test: `src/test/kotlin/com/github/hechtcarmel/jetbrainsindexmcpplugin/util/PluginDetectorsUnitTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/.../util/PluginDetectorsUnitTest.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import junit.framework.TestCase

class PluginDetectorsUnitTest : TestCase() {

    fun testPluginDetectorBasicProperties() {
        val detector = PluginDetector("Test", listOf("com.test.plugin"))
        assertEquals("Test", detector.name)
    }

    fun testPluginDetectorWithFallbackClass() {
        val detector = PluginDetector("Test", listOf("com.test.plugin"), fallbackClass = "com.nonexistent.Class")
        // In unit test env without platform, should be false
        assertFalse(detector.isAvailable)
    }

    fun testIfAvailableReturnsNullWhenUnavailable() {
        val detector = PluginDetector("Test", listOf("com.nonexistent.plugin"))
        val result = detector.ifAvailable { "found" }
        assertNull(result)
    }

    fun testIfAvailableOrElseReturnsFallbackWhenUnavailable() {
        val detector = PluginDetector("Test", listOf("com.nonexistent.plugin"))
        val result = detector.ifAvailableOrElse("default") { "found" }
        assertEquals("default", result)
    }

    fun testPluginDetectorsRegistryHasAllLanguages() {
        // Verify all expected detectors exist
        assertNotNull(PluginDetectors.java)
        assertNotNull(PluginDetectors.python)
        assertNotNull(PluginDetectors.javaScript)
        assertNotNull(PluginDetectors.go)
        assertNotNull(PluginDetectors.php)
        assertNotNull(PluginDetectors.rust)
    }

    fun testPluginDetectorsHaveCorrectNames() {
        assertEquals("Java", PluginDetectors.java.name)
        assertEquals("Python", PluginDetectors.python.name)
        assertEquals("JavaScript", PluginDetectors.javaScript.name)
        assertEquals("Go", PluginDetectors.go.name)
        assertEquals("PHP", PluginDetectors.php.name)
        assertEquals("Rust", PluginDetectors.rust.name)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*.PluginDetectorsUnitTest" -x runPluginVerifier`
Expected: FAIL — classes don't exist yet

**Step 3: Implement PluginDetector class**

Create `src/main/kotlin/.../util/PluginDetector.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId

/**
 * Generic plugin availability detector.
 *
 * Checks if any of the given plugin IDs are installed and enabled.
 * Optionally falls back to loading a PSI class directly (for bundled plugins
 * like RustRover where plugin IDs may differ).
 *
 * @param name Display name for logging
 * @param pluginIds Plugin IDs to check, in order of preference
 * @param fallbackClass Optional fully-qualified class name to try loading as fallback
 */
class PluginDetector(
    val name: String,
    private val pluginIds: List<String>,
    private val fallbackClass: String? = null
) {
    private val log = logger<PluginDetector>()

    val isAvailable: Boolean by lazy { checkAvailable() }

    inline fun <T> ifAvailable(action: () -> T): T? =
        if (isAvailable) action() else null

    inline fun <T> ifAvailableOrElse(default: T, action: () -> T): T =
        if (isAvailable) action() else default

    private fun checkAvailable(): Boolean {
        // Strategy 1: Check plugin IDs
        for (pluginId in pluginIds) {
            try {
                val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
                if (plugin != null && plugin.isEnabled) {
                    log.info("$name plugin detected via plugin ID ($pluginId)")
                    return true
                }
            } catch (e: Exception) {
                log.debug("Failed to check $name plugin $pluginId: ${e.message}")
            }
        }

        // Strategy 2: Fallback — try loading a PSI class directly
        if (fallbackClass != null) {
            try {
                Class.forName(fallbackClass)
                log.info("$name support detected via PSI class ($fallbackClass)")
                return true
            } catch (e: ClassNotFoundException) {
                log.debug("$name PSI class not found: $fallbackClass")
            } catch (e: Exception) {
                log.debug("Failed to check $name PSI class: ${e.message}")
            }
        }

        log.info("$name plugin not available — $name-specific features will be disabled")
        return false
    }
}
```

Create `src/main/kotlin/.../util/PluginDetectors.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

/**
 * Central registry of all language plugin detectors.
 *
 * Usage:
 * ```kotlin
 * if (PluginDetectors.java.isAvailable) { /* use Java APIs */ }
 * PluginDetectors.python.ifAvailable { /* use Python APIs */ }
 * ```
 */
object PluginDetectors {
    val java = PluginDetector(
        name = "Java",
        pluginIds = listOf("com.intellij.java", "com.intellij.modules.java")
    )

    val python = PluginDetector(
        name = "Python",
        pluginIds = listOf("Pythonid", "PythonCore")
    )

    val javaScript = PluginDetector(
        name = "JavaScript",
        pluginIds = listOf("JavaScript")
    )

    val go = PluginDetector(
        name = "Go",
        pluginIds = listOf("org.jetbrains.plugins.go")
    )

    val php = PluginDetector(
        name = "PHP",
        pluginIds = listOf("com.jetbrains.php")
    )

    val rust = PluginDetector(
        name = "Rust",
        pluginIds = listOf("com.jetbrains.rust", "org.jetbrains.rust", "org.rust.lang", "com.intellij.rust"),
        fallbackClass = "org.rust.lang.core.psi.RsFile"
    )
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*.PluginDetectorsUnitTest" -x runPluginVerifier`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/.../util/PluginDetector.kt src/main/kotlin/.../util/PluginDetectors.kt src/test/kotlin/.../util/PluginDetectorsUnitTest.kt
git commit -m "refactor: add generic PluginDetector and PluginDetectors registry"
```

---

### Task 2: Migrate All Code from Old Detectors to PluginDetectors

**Files:**
- Modify: `src/main/kotlin/.../util/JavaPluginDetector.kt` — delegate to `PluginDetectors.java`
- Modify: `src/main/kotlin/.../util/PythonPluginDetector.kt` — delegate to `PluginDetectors.python`
- Modify: `src/main/kotlin/.../util/JavaScriptPluginDetector.kt` — delegate to `PluginDetectors.javaScript`
- Modify: `src/main/kotlin/.../util/GoPluginDetector.kt` — delegate to `PluginDetectors.go`
- Modify: `src/main/kotlin/.../util/PhpPluginDetector.kt` — delegate to `PluginDetectors.php`
- Modify: `src/main/kotlin/.../util/RustPluginDetector.kt` — delegate to `PluginDetectors.rust`

**Step 1: Replace each old detector's implementation with delegation**

For each of the 6 files, replace the entire body with delegation. Example for `JavaPluginDetector.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

/**
 * @deprecated Use [PluginDetectors.java] instead.
 */
object JavaPluginDetector {
    val isJavaPluginAvailable: Boolean get() = PluginDetectors.java.isAvailable

    inline fun <T> ifJavaAvailable(block: () -> T): T? = PluginDetectors.java.ifAvailable(block)

    inline fun <T> ifJavaAvailableOrElse(default: T, block: () -> T): T =
        PluginDetectors.java.ifAvailableOrElse(default, block)
}
```

Do the same pattern for all 6 detectors — each becomes ~10 lines delegating to `PluginDetectors.*`.

**Step 2: Run all unit tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS (old public API preserved via delegation)

**Step 3: Commit**

```bash
git add src/main/kotlin/.../util/*PluginDetector.kt
git commit -m "refactor: delegate old plugin detectors to PluginDetectors registry"
```

---

### Task 3: Data-Driven Handler Registration in LanguageHandlerRegistry

**Files:**
- Modify: `src/main/kotlin/.../handlers/LanguageHandlerRegistry.kt`

**Step 1: Replace 6 identical registration methods with data-driven loop**

Replace lines 279-375 (the 6 `registerXxxHandlers()` methods) with:

```kotlin
private data class HandlerRegistration(val className: String, val displayName: String)

private val handlerRegistrations = listOf(
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java.JavaHandlers", "Java"),
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python.PythonHandlers", "Python"),
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.javascript.JavaScriptHandlers", "JavaScript"),
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.go.GoHandlers", "Go"),
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php.PhpHandlers", "PHP"),
    HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.RustHandlers", "Rust"),
)

private fun registerLanguageHandlers(className: String, displayName: String) {
    try {
        val handlerClass = Class.forName(className)
        val registerMethod = handlerClass.getMethod("register", LanguageHandlerRegistry::class.java)
        registerMethod.invoke(null, this)
        LOG.info("$displayName handlers registered")
    } catch (e: ClassNotFoundException) {
        LOG.info("$displayName handlers not available ($displayName plugin not installed)")
    } catch (e: Exception) {
        LOG.warn("Failed to register $displayName handlers: ${e.message}")
    }
}
```

Update `registerHandlers()` to call:
```kotlin
for (reg in handlerRegistrations) {
    registerLanguageHandlers(reg.className, reg.displayName)
}
```

**Step 2: Run tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/.../handlers/LanguageHandlerRegistry.kt
git commit -m "refactor: data-driven handler registration in LanguageHandlerRegistry"
```

---

### Task 4: Data-Driven Tool Registration in ToolRegistry

**Files:**
- Modify: `src/main/kotlin/.../tools/ToolRegistry.kt`

**Step 1: Replace 6 reflection blocks in registerLanguageNavigationTools() with data-driven loop**

Replace lines 245-287 with:

```kotlin
private data class ConditionalTool(
    val className: String,
    val isAvailable: () -> Boolean
)

private val languageNavigationTools = listOf(
    ConditionalTool("...tools.navigation.TypeHierarchyTool") { LanguageHandlerRegistry.hasTypeHierarchyHandlers() },
    ConditionalTool("...tools.navigation.FindImplementationsTool") { LanguageHandlerRegistry.hasImplementationsHandlers() },
    ConditionalTool("...tools.navigation.CallHierarchyTool") { LanguageHandlerRegistry.hasCallHierarchyHandlers() },
    ConditionalTool("...tools.navigation.FindSymbolTool") { LanguageHandlerRegistry.hasSymbolSearchHandlers() },
    ConditionalTool("...tools.navigation.FindSuperMethodsTool") { LanguageHandlerRegistry.hasSuperMethodsHandlers() },
    ConditionalTool("...tools.navigation.FileStructureTool") { LanguageHandlerRegistry.hasStructureHandlers() },
)

private fun registerLanguageNavigationTools() {
    try {
        for (tool in languageNavigationTools) {
            if (tool.isAvailable()) {
                val toolClass = Class.forName(tool.className)
                register(toolClass.getDeclaredConstructor().newInstance() as McpTool)
            }
        }
        LOG.info("Registered language navigation tools")
    } catch (e: Exception) {
        LOG.warn("Failed to register language navigation tools: ${e.message}")
    }
}
```

**Step 2: Update ToolRegistry to use PluginDetectors for Java check**

Replace `JavaPluginDetector.isJavaPluginAvailable` with `PluginDetectors.java.isAvailable` in `registerBuiltInTools()`.

**Step 3: Run tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/main/kotlin/.../tools/ToolRegistry.kt
git commit -m "refactor: data-driven tool registration in ToolRegistry"
```

---

### Task 5: Create SchemaBuilder Utility

**Files:**
- Create: `src/main/kotlin/.../tools/schema/SchemaBuilder.kt`
- Test: `src/test/kotlin/.../tools/schema/SchemaBuilderUnitTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.SchemaConstants
import junit.framework.TestCase
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SchemaBuilderUnitTest : TestCase() {

    fun testBasicSchema() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .build()

        assertEquals(SchemaConstants.TYPE_OBJECT, schema[SchemaConstants.TYPE]?.jsonPrimitive?.content)
        val properties = schema[SchemaConstants.PROPERTIES]?.jsonObject
        assertNotNull(properties)
        assertNotNull("Should have project_path", properties?.get(ParamNames.PROJECT_PATH))
    }

    fun testFileLineColumnSchema() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .file()
            .lineAndColumn()
            .build()

        val properties = schema[SchemaConstants.PROPERTIES]?.jsonObject!!
        assertNotNull(properties[ParamNames.FILE])
        assertNotNull(properties[ParamNames.LINE])
        assertNotNull(properties[ParamNames.COLUMN])

        val required = schema[SchemaConstants.REQUIRED]?.jsonArray?.map { it.jsonPrimitive.content }!!
        assertTrue("file should be required", required.contains(ParamNames.FILE))
        assertTrue("line should be required", required.contains(ParamNames.LINE))
        assertTrue("column should be required", required.contains(ParamNames.COLUMN))
    }

    fun testCustomProperty() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .stringProperty("query", "Search query", required = true)
            .intProperty("limit", "Max results")
            .build()

        val properties = schema[SchemaConstants.PROPERTIES]?.jsonObject!!
        assertNotNull(properties["query"])
        assertNotNull(properties["limit"])

        val required = schema[SchemaConstants.REQUIRED]?.jsonArray?.map { it.jsonPrimitive.content }!!
        assertTrue("query should be required", required.contains("query"))
        assertFalse("limit should not be required", required.contains("limit"))
    }

    fun testEnumProperty() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .enumProperty("mode", "Match mode", listOf("exact", "prefix", "substring"))
            .build()

        val properties = schema[SchemaConstants.PROPERTIES]?.jsonObject!!
        val modeProp = properties["mode"]?.jsonObject!!
        val enumValues = modeProp["enum"]?.jsonArray?.map { it.jsonPrimitive.content }!!
        assertEquals(3, enumValues.size)
        assertTrue(enumValues.contains("exact"))
    }

    fun testBooleanProperty() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .booleanProperty("caseSensitive", "Case-sensitive search")
            .build()

        val properties = schema[SchemaConstants.PROPERTIES]?.jsonObject!!
        val prop = properties["caseSensitive"]?.jsonObject!!
        assertEquals(SchemaConstants.TYPE_BOOLEAN, prop[SchemaConstants.TYPE]?.jsonPrimitive?.content)
    }

    fun testProjectPathAlwaysPresent() {
        val schema = SchemaBuilder.tool()
            .projectPath()
            .file()
            .build()

        val required = schema[SchemaConstants.REQUIRED]?.jsonArray?.map { it.jsonPrimitive.content }!!
        assertFalse("project_path should NOT be required", required.contains(ParamNames.PROJECT_PATH))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*.SchemaBuilderUnitTest" -x runPluginVerifier`
Expected: FAIL

**Step 3: Implement SchemaBuilder**

Create `src/main/kotlin/.../tools/schema/SchemaBuilder.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.SchemaConstants
import kotlinx.serialization.json.*

/**
 * Fluent builder for MCP tool input schemas.
 *
 * Eliminates repetitive JSON schema construction across tools.
 *
 * Usage:
 * ```kotlin
 * override val inputSchema = SchemaBuilder.tool()
 *     .projectPath()
 *     .file()
 *     .lineAndColumn()
 *     .intProperty("maxResults", "Maximum results to return")
 *     .build()
 * ```
 */
class SchemaBuilder private constructor() {
    private val properties = linkedMapOf<String, JsonObject>()
    private val requiredFields = mutableListOf<String>()

    fun projectPath() = apply {
        properties[ParamNames.PROJECT_PATH] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
            put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_PROJECT_PATH)
        }
    }

    fun file(required: Boolean = true, description: String = SchemaConstants.DESC_FILE) = apply {
        properties[ParamNames.FILE] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
            put(SchemaConstants.DESCRIPTION, description)
        }
        if (required) requiredFields.add(ParamNames.FILE)
    }

    fun lineAndColumn() = apply {
        properties[ParamNames.LINE] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
            put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_LINE)
        }
        properties[ParamNames.COLUMN] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
            put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_COLUMN)
        }
        requiredFields.add(ParamNames.LINE)
        requiredFields.add(ParamNames.COLUMN)
    }

    fun stringProperty(name: String, description: String, required: Boolean = false) = apply {
        properties[name] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
            put(SchemaConstants.DESCRIPTION, description)
        }
        if (required) requiredFields.add(name)
    }

    fun intProperty(name: String, description: String, required: Boolean = false) = apply {
        properties[name] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
            put(SchemaConstants.DESCRIPTION, description)
        }
        if (required) requiredFields.add(name)
    }

    fun booleanProperty(name: String, description: String, required: Boolean = false) = apply {
        properties[name] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_BOOLEAN)
            put(SchemaConstants.DESCRIPTION, description)
        }
        if (required) requiredFields.add(name)
    }

    fun enumProperty(name: String, description: String, values: List<String>, required: Boolean = false) = apply {
        properties[name] = buildJsonObject {
            put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
            put(SchemaConstants.DESCRIPTION, description)
            putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
        if (required) requiredFields.add(name)
    }

    fun property(name: String, schema: JsonObject, required: Boolean = false) = apply {
        properties[name] = schema
        if (required) requiredFields.add(name)
    }

    fun build(): JsonObject = buildJsonObject {
        put(SchemaConstants.TYPE, SchemaConstants.TYPE_OBJECT)
        putJsonObject(SchemaConstants.PROPERTIES) {
            for ((name, schema) in properties) {
                put(name, schema)
            }
        }
        putJsonArray(SchemaConstants.REQUIRED) {
            for (field in requiredFields) {
                add(JsonPrimitive(field))
            }
        }
    }

    companion object {
        fun tool() = SchemaBuilder()
    }
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "*.SchemaBuilderUnitTest" -x runPluginVerifier`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/.../tools/schema/SchemaBuilder.kt src/test/kotlin/.../tools/schema/SchemaBuilderUnitTest.kt
git commit -m "refactor: add SchemaBuilder utility for tool input schemas"
```

---

### Task 6: Migrate Tools to Use SchemaBuilder

**Files:**
- Modify: All 21 tool files in `tools/navigation/`, `tools/refactoring/`, `tools/editor/`, `tools/intelligence/`, `tools/project/`

**Step 1: Migrate each tool's inputSchema to use SchemaBuilder**

For each tool, replace the manual `buildJsonObject { ... }` schema with `SchemaBuilder.tool()...build()`.

Example for `FindUsagesTool.kt` — replace lines 50-79:
```kotlin
override val inputSchema = SchemaBuilder.tool()
    .projectPath()
    .file()
    .lineAndColumn()
    .intProperty("maxResults", "Maximum number of references to return. Default: $DEFAULT_MAX_RESULTS, max: $MAX_ALLOWED_RESULTS.")
    .build()
```

Example for `FindDefinitionTool.kt` — replace lines 45-78:
```kotlin
override val inputSchema = SchemaBuilder.tool()
    .projectPath()
    .file()
    .lineAndColumn()
    .booleanProperty(ParamNames.FULL_ELEMENT_PREVIEW, "If true, returns the complete element code instead of a preview snippet. Optional, defaults to false.")
    .intProperty(ParamNames.MAX_PREVIEW_LINES, "Maximum lines for fullElementPreview. Truncates large classes/functions. Default: 50, Max: 500. Only used when fullElementPreview=true.")
    .build()
```

For tools with custom schemas (e.g., `SearchTextTool` with enum), use `.enumProperty()`.

For tools with unusual schemas (like `SafeDeleteTool` with complex `target_type`), use the raw `.property()` method to pass a custom `JsonObject`.

**CRITICAL**: Do NOT change any `doExecute()` logic, parameter names, descriptions, or tool behavior. Only change the schema construction method.

**Step 2: Run ALL existing tests to verify schemas are identical**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS — the existing `ToolsUnitTest` tests validate schemas still have correct properties, required arrays, enum values, etc.

**Step 3: Commit**

```bash
git add src/main/kotlin/.../tools/
git commit -m "refactor: migrate all tool schemas to use SchemaBuilder"
```

---

### Task 7: Extract ClassResolver from AbstractMcpTool

**Files:**
- Create: `src/main/kotlin/.../util/ClassResolver.kt`
- Modify: `src/main/kotlin/.../tools/AbstractMcpTool.kt`

**Step 1: Extract findClassByName and its helpers into ClassResolver**

Move `findClassByName()`, `findClassByNameWithPhpPlugin()`, and `findClassByNameWithJavaPlugin()` from `AbstractMcpTool.kt` (lines 439-611) into a new `ClassResolver` object.

Create `src/main/kotlin/.../util/ClassResolver.kt`:

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

/**
 * Resolves class elements by fully qualified name across languages.
 *
 * Supports PHP (via reflection to PhpIndex) and Java/Kotlin (via JavaPsiFacade).
 * Uses reflection to avoid compile-time dependencies on language-specific plugins.
 */
object ClassResolver {
    // Move the 3 methods here verbatim from AbstractMcpTool.
    // Change visibility from private to internal/public as needed.
    // Replace PhpPluginDetector references with PluginDetectors.php
    // Replace JavaPluginDetector references with PluginDetectors.java
}
```

**Step 2: Update AbstractMcpTool to delegate to ClassResolver**

In `AbstractMcpTool.kt`, replace the 3 removed methods with:

```kotlin
protected fun findClassByName(project: Project, qualifiedName: String): PsiElement? {
    return ClassResolver.findClassByName(project, qualifiedName)
}
```

**Step 3: Run tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add src/main/kotlin/.../util/ClassResolver.kt src/main/kotlin/.../tools/AbstractMcpTool.kt
git commit -m "refactor: extract ClassResolver from AbstractMcpTool"
```

---

### Task 8: Extract ProjectResolver from JsonRpcHandler

**Files:**
- Create: `src/main/kotlin/.../server/ProjectResolver.kt`
- Modify: `src/main/kotlin/.../server/JsonRpcHandler.kt`
- Test: `src/test/kotlin/.../server/ProjectResolverUnitTest.kt`

**Step 1: Write a failing test**

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.server

import junit.framework.TestCase

class ProjectResolverUnitTest : TestCase() {

    fun testNormalizePathRemovesTrailingSlash() {
        assertEquals("/home/user/project", ProjectResolver.normalizePath("/home/user/project/"))
        assertEquals("/home/user/project", ProjectResolver.normalizePath("/home/user/project"))
    }

    fun testNormalizePathConvertsBackslashes() {
        assertEquals("C:/Users/project", ProjectResolver.normalizePath("C:\\Users\\project"))
        assertEquals("C:/Users/project", ProjectResolver.normalizePath("C:\\Users\\project\\"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*.ProjectResolverUnitTest" -x runPluginVerifier`
Expected: FAIL

**Step 3: Extract ProjectResolver**

Move `resolveProject()`, `findProjectByModuleContentRoot()`, `buildAvailableProjectsArray()`, `normalizePath()`, and `ProjectResolutionResult` from `JsonRpcHandler.kt` into `server/ProjectResolver.kt`.

```kotlin
package com.github.hechtcarmel.jetbrainsindexmcpplugin.server

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ErrorMessages
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * Resolves which IntelliJ project to use for a tool call.
 *
 * Supports:
 * - Exact basePath match
 * - Module content root match (workspace projects)
 * - Subdirectory match
 * - Auto-select when only one project is open
 */
class ProjectResolver {
    // Move all project resolution logic here from JsonRpcHandler
    // Make normalizePath companion/internal for testing

    companion object {
        private val LOG = logger<ProjectResolver>()
        private val json = Json { encodeDefaults = true; prettyPrint = false }

        fun normalizePath(path: String): String {
            return path.trimEnd('/', '\\').replace('\\', '/')
        }
    }

    data class Result(
        val project: Project? = null,
        val errorResult: ToolCallResult? = null,
        val isError: Boolean = false
    )

    fun resolve(projectPath: String?): Result {
        // Move resolveProject() logic here verbatim
    }

    // Move findProjectByModuleContentRoot() and buildAvailableProjectsArray() here
}
```

**Step 4: Update JsonRpcHandler to use ProjectResolver**

In `JsonRpcHandler.kt`:
- Add `private val projectResolver = ProjectResolver()` field
- Replace `resolveProject(projectPath)` call with `projectResolver.resolve(projectPath)`
- Remove the moved methods and inner class

**Step 5: Run all tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS (including `JsonRpcHandlerUnitTest` and `WorkspaceResolutionUnitTest`)

**Step 6: Commit**

```bash
git add src/main/kotlin/.../server/ProjectResolver.kt src/main/kotlin/.../server/JsonRpcHandler.kt src/test/kotlin/.../server/ProjectResolverUnitTest.kt
git commit -m "refactor: extract ProjectResolver from JsonRpcHandler"
```

---

### Task 9: Consolidate Error Response Builders in JsonRpcHandler

**Files:**
- Modify: `src/main/kotlin/.../server/JsonRpcHandler.kt`

**Step 1: Replace 4 error response methods with single factory**

Replace `createParseErrorResponse()`, `createInvalidParamsResponse()`, `createMethodNotFoundResponse()`, `createInternalErrorResponse()` with:

```kotlin
private fun createErrorResponse(
    id: JsonElement? = null,
    code: Int,
    message: String
) = JsonRpcResponse(
    id = id,
    error = JsonRpcError(code = code, message = message)
)
```

Update all call sites:
- `createParseErrorResponse()` → `createErrorResponse(code = JsonRpcErrorCodes.PARSE_ERROR, message = ErrorMessages.PARSE_ERROR)`
- `createInvalidParamsResponse(id, msg)` → `createErrorResponse(id, JsonRpcErrorCodes.INVALID_PARAMS, msg)`
- `createMethodNotFoundResponse(id, method)` → `createErrorResponse(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, ErrorMessages.methodNotFound(method))`
- `createInternalErrorResponse(id, msg)` → `createErrorResponse(id, JsonRpcErrorCodes.INTERNAL_ERROR, msg)`

**Step 2: Run tests**

Run: `./gradlew test --tests "*.JsonRpcHandlerUnitTest" -x runPluginVerifier`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/.../server/JsonRpcHandler.kt
git commit -m "refactor: consolidate error response builders in JsonRpcHandler"
```

---

### Task 10: Fix Protocol Issues in KtorMcpServer

**Files:**
- Modify: `src/main/kotlin/.../server/KtorMcpServer.kt`
- Modify: `src/main/kotlin/.../server/JsonRpcHandler.kt`

**Step 1: Replace manual JSON string construction with proper serialization**

In `KtorMcpServer.kt`, replace `createJsonRpcError()` (lines 268-275):

```kotlin
private val json = Json { encodeDefaults = true; prettyPrint = false }

private fun createJsonRpcError(id: JsonElement?, code: Int, message: String): String {
    val response = JsonRpcResponse(
        id = id,
        error = JsonRpcError(code = code, message = message)
    )
    return json.encodeToString(response)
}
```

Update all callers — change `createJsonRpcError(null, ...)` to `createJsonRpcError(null as JsonElement?, ...)`.

**Step 2: Return 202 Accepted for notifications in streamable HTTP mode**

In `handleStreamableHttpRequest()`, after the `if (response != null)` check, add:

```kotlin
if (response != null) {
    call.respondText(response, ContentType.Application.Json)
} else {
    call.respond(HttpStatusCode.Accepted)
}
```

**Step 3: Add JSON-RPC version validation**

In `JsonRpcHandler.handleRequest()`, after parsing the request, add:

```kotlin
if (request.jsonrpc != "2.0") {
    return json.encodeToString(createErrorResponse(
        id = request.id,
        code = JsonRpcErrorCodes.INVALID_REQUEST,
        message = "Invalid JSON-RPC version: ${request.jsonrpc}. Expected \"2.0\"."
    ))
}
```

Add `INVALID_REQUEST = -32600` to `JsonRpcErrorCodes` if not present.

**Step 4: Run all tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/.../server/KtorMcpServer.kt src/main/kotlin/.../server/JsonRpcHandler.kt src/main/kotlin/.../server/models/JsonRpcModels.kt
git commit -m "fix: replace manual JSON construction, add 202 for notifications, validate jsonrpc version"
```

---

### Task 11: Move Transport Files to server/transport/ Package

**Files:**
- Move: `server/KtorMcpServer.kt` → `server/transport/KtorMcpServer.kt`
- Move: `server/KtorSseSessionManager.kt` → `server/transport/KtorSseSessionManager.kt`

**Step 1: Move files and update package declarations**

Change package from `...server` to `...server.transport` in both files.

**Step 2: Update all imports across the codebase**

Search for `import ...server.KtorMcpServer` and `import ...server.KtorSseSessionManager` and update to `...server.transport.*`.

Key files to update:
- `server/McpServerService.kt`
- Any test files referencing these classes

**Step 3: Run all tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: move transport classes to server/transport/ package"
```

---

### Task 12: Delete Old Plugin Detector Files

**Files:**
- Delete: `util/JavaPluginDetector.kt`
- Delete: `util/PythonPluginDetector.kt`
- Delete: `util/JavaScriptPluginDetector.kt`
- Delete: `util/GoPluginDetector.kt`
- Delete: `util/PhpPluginDetector.kt`
- Delete: `util/RustPluginDetector.kt`

**Step 1: Find all remaining references to old detectors**

Search for `JavaPluginDetector`, `PythonPluginDetector`, etc. across the codebase. Replace all with `PluginDetectors.java`, `PluginDetectors.python`, etc.

Key locations:
- `tools/ToolRegistry.kt` — `JavaPluginDetector.isJavaPluginAvailable` → `PluginDetectors.java.isAvailable`
- `tools/AbstractMcpTool.kt` — `JavaPluginDetector`/`PhpPluginDetector` references (should already be in ClassResolver)
- `handlers/LanguageHandlerRegistry.kt` — may reference detectors
- Any language handler files

**Step 2: Delete the 6 old files**

**Step 3: Run all tests**

Run: `./gradlew test --tests "*UnitTest*" -x runPluginVerifier`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: delete deprecated individual plugin detector files"
```

---

### Task 13: Final Cleanup and Verification

**Files:**
- Modify: `CLAUDE.md` — update project structure documentation

**Step 1: Remove unused imports across all modified files**

Scan all modified files for unused imports and remove them.

**Step 2: Run full test suite**

Run: `./gradlew test -x runPluginVerifier`
Expected: ALL PASS

**Step 3: Run plugin verifier**

Run: `./gradlew runPluginVerifier`
Expected: No new compatibility issues

**Step 4: Build the plugin**

Run: `./gradlew build -x runPluginVerifier`
Expected: BUILD SUCCESSFUL

**Step 5: Update CLAUDE.md project structure section**

Update the project structure in `CLAUDE.md` to reflect:
- New `tools/schema/` directory with `SchemaBuilder.kt`
- New `server/transport/` directory with `KtorMcpServer.kt` and `KtorSseSessionManager.kt`
- New `server/ProjectResolver.kt`
- New `util/PluginDetector.kt` and `util/PluginDetectors.kt`
- New `util/ClassResolver.kt`
- Removed old `util/*PluginDetector.kt` files

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor: final cleanup, update docs, verify build"
```

---

## Summary

| Task | Description | Est. LOC change |
|------|-------------|----------------|
| 1 | Create PluginDetector + PluginDetectors | +80 new |
| 2 | Delegate old detectors | 6 files × 100→10 lines = -540 |
| 3 | Data-driven handler registration | -70 |
| 4 | Data-driven tool registration | -30 |
| 5 | Create SchemaBuilder | +120 new |
| 6 | Migrate tool schemas | -100+ across 21 files |
| 7 | Extract ClassResolver | net 0 (move) |
| 8 | Extract ProjectResolver | net 0 (move) |
| 9 | Consolidate error builders | -25 |
| 10 | Protocol fixes | +15 |
| 11 | Package reorganization | 0 (moves) |
| 12 | Delete old detectors | -540 |
| 13 | Final cleanup | varies |
| **Total** | | **~-700 lines net** |
