# IDE Index MCP - Tools Reference

This reference documents **return shapes and tool selection** for all IDE MCP tools.
Parameters are provided by the live JSON schema in `tools/list` — consult that payload
for parameter names, types, defaults, and enum values.

**Path conventions**: file paths for project files are relative to the project root;
library/dependency paths returned by the plugin (absolute paths or `jar://` URLs) must
be passed back unchanged. Line and column numbers are **1-based**.

---

## Navigation Tools

### ide_find_usages
Find all usages of a symbol (semantic, not text search).

**Returns**: `{ usages: [{ file, line, column, preview, usageType, enclosingScope }], totalCount, truncated, nextCursor?, hasMore, totalCollected, offset, pageSize, stale }`

`truncated` mirrors `hasMore`; when `hasMore` is `true`, pass `nextCursor` to fetch the next page.
`usageType` values: `METHOD_CALL`, `FIELD_ACCESS`, `IMPORT`, `PARAMETER`, `VARIABLE`, `REFERENCE`.

### ide_find_definition
Go to where a symbol is defined.

**Returns**: `{ file, line, column, name, kind, preview, qualifiedName?, enclosingScope? }`

Handles packages, compiled classes, and library sources (`jar://` URLs).

### ide_find_class
Search for classes/interfaces by name using IDE's class index (Ctrl+N / Cmd+O equivalent).

**Returns**: `{ classes: [{name, qualifiedName, kind, file, line, column}], totalCount, query }`

Project results use relative paths; dependency/library results may use absolute paths or `jar://` URLs.
Exact (case-insensitive) by default; with `fuzzySearch: true`, IDE camelCase/substring matching applies (`USvc` → `UserService`).

### ide_find_file
Search for files by name using IDE's file index (Ctrl+Shift+N / Cmd+Shift+O equivalent).

**Returns**: `{ files: [{name, path, directory}], totalCount, query }`

Project results use relative paths; dependency/library results may use absolute paths or `jar://` URLs.

### ide_search_text
Search for exact words using IDE's pre-built word index. O(1) lookups, not file scanning.

**Returns**: `{ matches: [{file, line, column, context, contextType}], totalCount, query }`

`contextType` values: `CODE`, `COMMENT`, `STRING_LITERAL`.
**Selection note**: exact-word only (uses word index, not regex). Use `Grep` for regex patterns.

### ide_find_implementations
Find implementations of interfaces, abstract classes, or abstract methods.

**Returns**: `{ implementations: [{name, qualifiedName?, file, line, column, kind}], totalCount, nextCursor?, hasMore, totalCollected, offset, pageSize, stale }`

**Languages**: Java, Kotlin, Python, JS/TS, PHP, Rust (not Go).

### ide_find_symbol (disabled by default)
Search for any code symbol (classes, methods, fields, functions) by name.

**Returns**: `{ symbols: [{name, qualifiedName, kind, file, line, column}], totalCount, query }`

**Languages**: Java, Kotlin, Python, JS/TS, Go, PHP, Rust, plus other IDE-supplied symbol contributors where available.
Project results use relative paths; dependency/library results may use absolute paths or `jar://` URLs.

### ide_find_super_methods
Find parent methods that a given method overrides or implements.

**Returns**: `{ method: {name, qualifiedName?, kind, file, line, column}, hierarchy: [{name, qualifiedName?, kind, file?, line?, column?}] }`

**Languages**: Java, Kotlin, Python, JS/TS, PHP. Go returns interface methods a type satisfies. Rust returns trait fn/const/type alias the impl satisfies.

### ide_type_hierarchy
Get complete type inheritance hierarchy (supertypes and subtypes).

**Returns**: `{ element: {name, qualifiedName?, enclosingScope?, kind, file?, line?, column?, supertypes?}, supertypes: [{name, qualifiedName?, enclosingScope?, kind, file?, line?, column?, supertypes?}], subtypes: [{name, qualifiedName?, enclosingScope?, kind, file?, line?, column?, supertypes?}] }`

Provide either `className` (FQN, preferred) or `file`+`line`+`column`. Unlike other read-only navigation tools, file mode does not resolve dependency/library absolute paths or `jar://` URLs.
**Languages**: Java, Kotlin, Python, JS/TS, PHP, Rust.

### ide_call_hierarchy
Build call tree showing who calls a method or what a method calls.

**Returns**: `{ element: {name, qualifiedName?, enclosingScope?, kind, file, line, column}, calls: [{name, qualifiedName?, enclosingScope?, kind, file, line, column, children?: [...]}] }`

**Languages**: Java, Kotlin, Python, JS/TS, Go, PHP, Rust.

### ide_file_structure (disabled by default)
Get hierarchical file structure like IDE's Structure panel.

**Returns**: `{ file, language, structure }` (formatted tree with types, modifiers, signatures, line numbers)

**Languages**: Java, Kotlin, Python, JS/TS, Markdown.

### ide_read_file (disabled by default)
Read file content by path or qualified name, including library/jar sources.

**Returns**: `{ file, content, language, lineCount, startLine?, endLine?, isLibraryFile }`

Provide either `file` or `qualifiedName`.

---

## Intelligence Tools

### ide_diagnostics
Analyze a file for errors, warnings, and available quick fixes/intentions.

**Returns**: `{ problems: [{message, severity, file, line, column, endLine?, endColumn?}], intentions: [{name, description?}], problemCount, intentionCount, analysisFresh, analysisTimedOut, analysisMessage }`

`severity` values: `ERROR`, `WARNING`, `WEAK_WARNING`. Open files use fresh daemon highlights; closed files use public batch analysis so `WEAK_WARNING` results and quick-fix intentions may be less complete.

---

## Refactoring Tools

### ide_refactor_rename
Rename a symbol and update ALL references (semantic rename, not find-replace). Works across ALL languages.

**Returns**: `{ success, affectedFiles: [paths], changesCount, message }`

Auto-renames getters/setters, overriding methods, constructor params ↔ fields, test classes. Supports IDE undo (Ctrl+Z).

### ide_move_file
Move a file to a new directory. Language-aware reference, import, and package/namespace updates when the IDE provides a semantic move backend for that file type.

**Returns**: `{ success, affectedFiles: [paths], changesCount, message }`

Supports IDE undo (Ctrl+Z).

### ide_refactor_safe_delete (Java/Kotlin only)
Delete a symbol or file, checking for usages first.

**Returns (success)**: `{ success, affectedFiles, changesCount, message }`
**Returns (blocked)**: `{ canDelete: false, elementName, elementType, usageCount, blockingUsages: [{file, line, column, context}], message }`

**Availability**: IntelliJ IDEA, Android Studio (requires Java plugin).

### ide_reformat_code (disabled by default)
Reformat code per project style (.editorconfig, IDE settings). Equivalent to Ctrl+Alt+L / Cmd+Opt+L.

**Returns**: `{ success, affectedFiles, changesCount, message }`

### ide_optimize_imports (disabled by default)
Remove unused imports and organize them without reformatting code (Ctrl+Alt+O equivalent).

**Returns**: `{ success, affectedFiles, changesCount, message }`

### ide_convert_java_to_kotlin (disabled by default)
Convert Java files to Kotlin using IntelliJ's built-in J2K converter.

**Returns**: `{ files: [{requestedPath, status, kotlinFile?, linesConverted?, javaFileDeleted?, reason?}], summary: {totalRequested, converted, skipped, failed} }`

`status` values: `CONVERTED`, `SKIPPED`, `FAILED`. Success fields (`kotlinFile`, `linesConverted`, `javaFileDeleted`) are set only when `status == CONVERTED`; `reason` is set only when `status != CONVERTED`.
**Availability**: IntelliJ IDEA, Android Studio (requires both Java and Kotlin plugins).

---

## Project Tools

### ide_index_status
Check if IDE is ready for code intelligence operations.

**Returns**: `{ isDumbMode, isIndexing, indexingProgress? }`

When `isDumbMode: true`, most tools will fail — wait and retry.

### ide_sync_files
Force sync IDE's virtual file system with external file changes.

**Returns**: `{ syncedPaths, syncedAll, message }`

Call when files were created or modified outside the IDE and search tools miss them.

### ide_build_project (disabled by default)
Build project using IDE's build system (JPS, Gradle, Maven).

**Returns**: `{ success, aborted, errors?, warnings?, buildMessages: [{category, message, file?, line?, column?}], truncated, rawOutput?, durationMs }`

`errors`/`warnings` are `null` when no messages were captured (not 0).

### ide_install_plugin (disabled by default)
Install a locally built plugin distribution (`.zip`) into this IDE's custom plugins directory.

**Returns**: `{ installed, source, pluginDir, pluginId?, pluginVersion?, restartRequired, message }`

Always reports `restartRequired: true`. Pair with `ide_restart`.

### ide_restart (disabled by default)
Restart this IDE, scheduled after the response is flushed.

**Returns**: `{ restarting, delaySeconds, message }`

Used with `ide_install_plugin` for the local plugin dev loop.

---

## Editor Tools

### ide_get_active_file (disabled by default)
Get currently active file(s) in editor with cursor position and selection.

**Returns**: `{ activeFiles: [{file, line?, column?, selectedText?, hasSelection, language?}] }`

### ide_open_file (disabled by default)
Open a file in the editor with optional navigation.

**Returns**: `{ file, opened, message }`
