# Architecture

## Tool → backing mechanism

Each tool delegates to a platform API. This is **not** uniformly an extension point —
some tools use a platform service, plain PSI/VFS, or a processor that itself iterates EPs.
The "verified against" column cites the exact call site so a future reader can re-check in one grep.

| Tool | Backing mechanism | verified against |
|---|---|---|
| `ide_find_usages` | EP: `FindUsagesHandlerFactory.EP_NAME` (Alt+F7 equivalence class) + `ReferencesSearch` fallback | `FindUsagesHandlerSearch.kt` / `FindUsagesTool.kt` |
| `ide_find_definition` | none — `PsiUtils.resolveTargetElement` → `PsiElement.navigationElement` (Ctrl+B) | `FindDefinitionTool.kt` |
| `ide_find_class` | headless Go-to-Class popup: `PopupFaithfulClassSearch` → `ChooseByNameModel` (Ctrl+N) | `FindClassTool.kt` / `PopupFaithfulClassSearch.kt` |
| `ide_find_file` | EP iteration: `ChooseByNameContributor.FILE_EP_NAME` (Go-to-File, Ctrl+Shift+N) | `FindFileTool.kt` |
| `ide_find_symbol` | headless Go-to-Symbol popup: `PopupFaithfulSymbolSearch` → `GotoSymbolModel2` (Ctrl+Alt+Shift+N) | `FindSymbolTool.kt` / `PopupFaithfulSymbolSearch.kt` |
| `ide_find_implementations` | EP: `DefinitionsScopedSearch.search()` (Ctrl+Alt+B) | `FindImplementationsTool.kt` |
| `ide_find_super_methods` | `LanguageServiceRegistry.findSuperMethods()` — per-language via reflection (Ctrl+U) | `FindSuperMethodsTool.kt` / `handlers/{java,kotlin,python,javascript,go,php,rust}/*LanguageService.kt` |
| `ide_search_text` | service: `PsiSearchHelper.getInstance(project).processElementsWithWord` (word index) | `SearchTextTool.kt` |
| `ide_type_hierarchy` | EP: `LanguageTypeHierarchy` via `HierarchyTreeWalker`; Rust fallback: `RustTypeHierarchyFallback` (Strategy II) | `TypeHierarchyTool.kt` / `HierarchyTreeWalker.kt` |
| `ide_call_hierarchy` | EP: `LanguageCallHierarchy` via `HierarchyTreeWalker` | `CallHierarchyTool.kt` / `HierarchyTreeWalker.kt` |
| `ide_file_structure` | EP: `LanguageStructureViewBuilder` → `TreeModelWrapper` + `SmartTreeStructure` (Structure view) | `FileStructureTool.kt` |
| `ide_read_file` | none — `PsiUtils.resolveVirtualFileAnywhere` / `PsiManager.findFile` (VFS + PSI) | `ReadFileTool.kt` |
| `ide_diagnostics` | multiple: `DaemonCodeAnalyzerEx` (open files) + `CodeSmellDetector` (closed files) + `BuildDiagnosticsCacheService` + `TestResultsCollector` | `GetDiagnosticsTool.kt` / `DiagnosticsAnalysisService.kt` |
| `ide_index_status` | service: `DumbService.getInstance(project).isDumb` | `GetIndexStatusTool.kt` |
| `ide_sync_files` | none — `VfsUtil.markDirtyAndRefresh` + `LocalFileSystem` + `PsiDocumentManager.commitAllDocuments` | `SyncFilesTool.kt` |
| `ide_build_project` | service: `ProjectTaskManager.getInstance(project)` + `ProjectTaskListener.TOPIC` message bus | `BuildProjectTool.kt` |
| `ide_install_plugin` | none — `PathManager.getPluginsDir()` + JDK `ZipInputStream` (zip-slip guarded) | `InstallPluginTool.kt` |
| `ide_restart` | none — `ApplicationManagerEx.getApplicationEx().restart(true)` (scheduled via `AppExecutorUtil`) | `RestartIdeTool.kt` |
| `ide_refactor_rename` | `RenameProcessor` + `RenamePsiElementProcessor` EP + `AutomaticRenamerFactory` EP iteration | `RenameSymbolTool.kt` |
| `ide_refactor_safe_delete` | none — `ReferencesSearch` usage check + `WriteCommandAction` PSI delete (Java plugin required for gating) | `SafeDeleteTool.kt` |
| `ide_move_file` | `MoveFilesOrDirectoriesProcessor` (delegates to `MoveFileHandler` EP per language; PHP class files route through PhpStorm's class-move processor) | `MoveFileTool.kt` |
| `ide_reformat_code` | `ReformatCodeProcessor` + optional `OptimizeImportsProcessor` + `RearrangeCodeProcessor` (Ctrl+Alt+L) | `ReformatCodeTool.kt` |
| `ide_optimize_imports` | `OptimizeImportsProcessor` (Ctrl+Alt+O) | `OptimizeImportsTool.kt` |
| `ide_convert_java_to_kotlin` | reflection: `JavaToKotlinAction.Handler.convertFiles()` (2025.x) / `JavaToKotlinActionHandler.convertFiles()` (2026.1+) | `ConvertJavaToKotlinTool.kt` |
| `ide_get_active_file` | none — `FileEditorManager.getInstance(project).selectedEditors` | `GetActiveFileTool.kt` |
| `ide_open_file` | none — `OpenFileDescriptor` + `FileEditorManager.openTextEditor` | `OpenFileTool.kt` |
