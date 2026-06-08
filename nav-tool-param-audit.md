# Navigation Tool Parameter Audit

Branch: `explore/nav-tool-params`. Question: *for each code-navigation tool, which
parameters can we (faithfully) add?*

Method: read every tool's `inputSchema` + `doExecute()` for the authoritative current
surface, then ground each candidate against the backing IDE API (verified via `javap`
against the bundled IntelliJ IC 2025.1.3 platform jars) and the IDE action it mirrors.
"Mimic the IDE" is the default per `CLAUDE.md`; usefulness can override it only with
sign-off.

---

## Part 1 — Current parameter surface (authoritative, from source)

| Tool | Required (runtime) | Optional (declared **and** read) | Backing API / IDE action |
|------|--------------------|----------------------------------|--------------------------|
| `ide_find_usages` | file+line+column¹ | **scope**, pageSize/maxResults, cursor | `FindUsagesHandlerSearch` + `ReferencesSearch` (Alt+F7) |
| `ide_find_definition` | file+line+column | — | `TargetElementUtil` resolve (Go-to-Declaration, Ctrl+B) |
| `ide_find_class` | query¹ | **scope**, **language**, **matchMode**, pageSize/limit, cursor | `PopupFaithfulClassSearch` (Go-to-Class, Ctrl+N) |
| `ide_find_file` | query¹ | **scope**, pageSize/limit, cursor | `ChooseByNameContributor.FILE_EP_NAME` (Go-to-File) |
| `ide_find_symbol` | query¹ | **scope**, **language**, pageSize/limit, cursor | `PopupFaithfulSymbolSearch` (Go-to-Symbol) |
| `ide_search_text` | query¹ | **context**, **caseSensitive**, pageSize/limit, cursor | `PsiSearchHelper.processElementsWithWord` (word index) |
| `ide_find_implementations` | file+line+column¹ | **scope**, pageSize, cursor | `DefinitionsScopedSearch` (Ctrl+Alt+B) |
| `ide_find_super_methods` | file+line+column | — | `LanguageServices.findSuperMethods` (Go-to-Super, Ctrl+U) |
| `ide_call_hierarchy` | file+line+column+**direction** | **maxDepth**, **scope** | `HierarchyTreeWalker` / `LanguageCallHierarchy` |
| `ide_type_hierarchy` | className **or** file+line+column | **scope** | `HierarchyTreeWalker` / `LanguageTypeHierarchy` |
| `ide_file_structure` | file | **show[]**, **sort** | `LanguageStructureViewBuilder` (Structure view) |
| `ide_read_file` | file **or** qualifiedName | **startLine**, **endLine** | VFS / `PsiUtils` |

`project_path` is implicit on every tool. `cursor`/`pageSize` are the pagination pair.
¹ Schema marks nothing `required` on paginated tools (cursor-vs-fresh is validated at
runtime); the "Required" column lists what a *fresh* call needs.

**The asymmetry that drives this audit:** `call_hierarchy` exposes `direction` +
`maxDepth`; `type_hierarchy` exposes neither — it hardcodes *both directions* and
*depth 5*. And `search_text` is the only search tool with **no `scope`** — it hardcodes
`createFilteredScope(project)`.

---

## Part 2 — Candidate parameters, by tier

### Tier 1 — high value, already plumbed in our own engine, faithful

**`ide_type_hierarchy` → `direction`** (`supertypes` | `subtypes` | `both`, default `both`)
- The engine already has `HierarchyKind.SUPERTYPES` / `SUBTYPES`; `TypeHierarchyTool`
  calls `HierarchyTreeWalker.walk(...)` once for each, unconditionally. A `direction`
  param just picks which walk(s) to run.
- Mirrors the IDE Type Hierarchy browser's Supertypes / Subtypes / Both tabs
  (`TypeHierarchyBrowserBase.getSupertypesHierarchyType()` / `getSubtypesHierarchyType()`,
  already used in `HierarchyTreeWalker.typeStringFor`).
- Payoff: callers wanting just ancestors (or just subclasses) stop paying for — and
  reading — the other half. **This is the real, useful half of issue #27.**

**`ide_type_hierarchy` → `maxDepth`** (default 5, cap ~20)
- `HierarchyTreeWalker.walk(..., maxDepth)` already takes it; the tool hardcodes `5`.
  Same shape `call_hierarchy` already exposes. Symmetry + cost/noise control.

**`ide_search_text` → `scope`** (the standard 4 `BuiltInSearchScope` values)
- Tool hardcodes `createFilteredScope(project)`; `processElementsWithWord` already takes
  a `GlobalSearchScope`. Every *other* search tool exposes `scope`; IDE Find-in-Files has
  a scope selector. Pure consistency + grounded.

### Tier 2 — cheap, consistency-driven, faithful

**`ide_find_symbol` → `matchMode`** (`substring`|`prefix`|`exact`|`camelCase`)
- `find_class` already does this as a post-filter (`createMatcher`/`createNameFilter`);
  `find_symbol` shares the popup approach but lacks it. Parity, same implementation shape.

**`ide_find_implementations` → `directOnly`** (boolean, default `false` = transitive)
- `DefinitionsScopedSearch.search(target, scope, checkDeep)` — verified the boolean is
  `isCheckDeep()`. Tool hardcodes `true`. Passing `false` yields direct implementers
  only — useful to cut a huge hierarchy down to the immediate layer.

**`ide_find_file` → `matchMode`** *(low value)*
- Currently always fuzzy (`NameUtil.buildMatcher`). Available for parity, but file search
  is conventionally fuzzy — weak motivation.

### Tier 3 — useful but a real tradeoff (needs sign-off)

**`ide_find_usages` → `accessType`** (`any`|`read`|`write`, default `any`)
- `ReadWriteAccessDetector.findDetector(el).getReferenceAccess(el, ref)` → READ / WRITE /
  READ_WRITE; post-filter on results. Answers "where is this field *written*?" — genuinely
  valuable. Caveat: detectors are per-language (Java/Kotlin solid; absent languages would
  just return everything). Faithful to the IDE's read/write usage highlighting.

**`ide_find_usages` → `includeTextOccurrences`** (boolean)
- IDE Find Usages "Search for text occurrences" (`FindUsagesOptions.isSearchForTextOccurrences`,
  universal base field). But our path is `ReferencesSearch`-only — this needs an *added*
  `PsiSearchHelper` text pass, i.e. net-new search work, not just a flag flip.

**`ide_find_definition` → `target`** (`declaration` | `type_declaration`, default `declaration`)
- Adds Go-to-Type-Declaration (Ctrl+Shift+B) via
  `TypeDeclarationProvider.getSymbolTypeDeclarations(el)` (verified; EP-based, universal).
  Jump from a variable straight to its type's declaration — useful. **Mimic tension:** this
  is arguably a *different action* than "find definition." Decision needed: extend this
  tool with a mode, or a separate tool? → escalate before building.

### Anti-candidates — plausible-looking, should **not** add

- **`ide_find_definition` → `scope`** — Go-to-Declaration resolves a *single* reference
  target; there is no search to scope. The #27 fixture's `scope` was junk; "supporting" it
  would fabricate behavior. **Recommend won't-fix.**
- **`ide_search_text` → `regex` / `substring`** — the word-index backend
  (`processElementsWithWord`) is exact-whole-word by construction. Regex/substring means
  abandoning the index — that's a different tool, not a parameter.
- **`ide_find_file` → `language`** — Go-to-File has no language filter; not faithful.

---

## Part 3 — Issue #27 resolution

#27 tracked two "silently-ignored" params surfaced by PR2's strict validation:

- **`direction` on `type_hierarchy`** → **real, recommended** (Tier 1). Implement it
  (and `maxDepth` alongside, since it's the same one-line plumbing gap).
- **`scope` on `find_definition`** → **not meaningful** (anti-candidate). Close this half
  as won't-fix and document why.

So #27 should be narrowed to "expose `direction` (+`maxDepth`) on `ide_type_hierarchy`."

---

---

## Part 4 — Native IDE support (researched per-IDE)

> `search_text` is **dropped** from scope per direction. Everything below is verified via
> `javap` against the actual bundled plugin jars under
> `~/.local/share/JetBrains/Toolbox/apps/<ide>/` and IC 2025.1.3 — i.e. what each IDE
> *actually registers*, not inference.

### 4a. Hierarchy providers (the native direction surface)

From each plugin's `plugin.xml` `<callHierarchyProvider>` / `<typeHierarchyProvider>`
registrations:

| Language | `callHierarchy` | `typeHierarchy` |
|----------|:---------------:|:---------------:|
| Java     | ✓ | ✓ |
| Kotlin   | ✓ | ✓ |
| Go       | ✓ | ✓ |
| Python   | ✓ | ✓ |
| PHP      | ✓ | ✓ |
| JS / TS  | ✓ | ✓ |
| **Rust** | ✓ | **✗ — no provider registered** |

So **`direction` (supertypes/subtypes, callers/callees) is natively backed in every
supported language**, except Rust type hierarchy — which has no native provider and is
served by our own `RustTypeHierarchyFallback`. This registration data *confirms* that
fallback against ground truth.

Native hierarchy **scope** vocabulary (`HierarchyBrowserBaseEx` constants):
`Production` (`SCOPE_PROJECT`), `Test`, `All`, `This Class`, `This Module`. Note this is a
*different* vocabulary from our search-scope enum — see 4c.

### 4b. Find Usages options — sharply language-specific

Each language's native Find Usages dialog = the public toggles on its `FindUsagesOptions`
subclass. What's actually registered:

| Language | Native element-specific options (beyond base) | Source class |
|----------|-----------------------------------------------|--------------|
| **Java** | method: `overridingMethods`, `implementingMethods`, `includeOverloadUsages`, `searchForBaseMethod`, `includeInherited`; variable: `readAccess`, `writeAccess`, `searchForAccessors`; class: `methodsUsages`, `fieldsUsages`, `derivedClasses`, `implementingClasses`, `derivedInterfaces`; base: `skipImportStatements` | `c.i.find.findUsages.Java*FindUsagesOptions` |
| **Kotlin** | function: `searchOverrides`; property: `isReadWriteAccess`, `searchOverrides`; class: `searchConstructorUsages`; all: `searchExpected` (expect/actual) | `o.j.k.idea.base.searching.usages.Kotlin*FindUsagesOptions` |
| **PHP** | `includeChildMethods` | `c.j.php.lang.findUsages.PhpFindUsagesOptions` |
| **JS / TS** | `excludeDynamicUsages`, `showComponentUsages` | `c.i.lang.javascript.findUsages.JSFindUsagesOptions` |
| **Go** | — none (no subclass) → generic dialog | — |
| **Python** | — none (no subclass) → generic dialog | — |
| **Rust** | — none (no subclass) → generic dialog | — |
| **ALL (base)** | `searchScope`, **`isSearchForTextOccurrences`** | `c.i.find.findUsages.FindUsagesOptions` |

**Consequence for `ide_find_usages`:** the only option natively available in *every*
language is **text-occurrences** (base class). `read/write access` is native in **Java +
Kotlin only**. Everything else (overrides, derived classes, child methods, dynamic
usages) is one- or two-language-specific. So any find-usages option param must be
**language-conditional** to stay faithful — there is no rich universal surface.

### 4c. Universal (platform) surfaces — same across all IDEs

- **Go to Class / File / Symbol**: native scope is essentially *project* vs *all (include
  non-project/library items)* — the popup's "include non-project items" toggle. Our 4-value
  `scope` enum **over-specifies** here (production/test splits aren't a native go-to concept,
  though they still resolve as `GlobalSearchScope`s). Candidate *modification*, not urgent.
- **Find Implementations**: `DefinitionsScopedSearch.search(el, scope, checkDeep)` — the
  `checkDeep` (transitive) boolean is the one native knob; tool hardcodes `true`.
- **Find Definition (Ctrl+B)** and **Find Super (Ctrl+U)**: no native parameters. (Go-to-Type-
  Declaration, Ctrl+Shift+B, is a *separate* native action — `TypeDeclarationProvider`.)

---

## Part 5 — Revised recommendations (native-grounded; `search_text` dropped)

**Add — natively backed everywhere it applies, already plumbed in our engine:**
1. **`ide_type_hierarchy` → `direction` (`supertypes`|`subtypes`|`both`) + `maxDepth`.**
   Native provider tabs in every language (Rust via our fallback). Resolves the real half
   of #27. Lowest risk, highest payoff.

**Add — universal but needs a new search pass:**
2. **`ide_find_usages` → `includeTextOccurrences` (boolean).** The one find-usages option
   native in *all* languages (`FindUsagesOptions.isSearchForTextOccurrences`). Requires an
   added `PsiSearchHelper` text pass on our `ReferencesSearch` path.

**Add — language-conditional (native only in some IDEs; document the limitation):**
3. **`ide_find_usages` → `accessType` (`any`|`read`|`write`).** Native in Java
   (`isReadAccess`/`isWriteAccess`) + Kotlin (`isReadWriteAccess`); elsewhere degrades to
   `any`. Implement via `ReadWriteAccessDetector` post-filter.
4. **`ide_find_implementations` → `directOnly`** (`checkDeep=false`). Native transitivity knob.

**Modify existing:**
5. **`ide_find_symbol` → add `matchMode`** for parity with `find_class` (our post-filter, not
   a native concept, but a consistency win).
6. **(Optional) hierarchy `scope`** — consider aligning to native vocabulary
   (`Production`/`Test`/`All`/`This Class`/`This Module`) instead of the current
   search-scope mapping. Flag for discussion; current mapping works.

**Out of scope / won't-fix:**
- `search_text` params (dropped per direction).
- `find_definition` `scope` (#27 — not a native concept; Go-to-Declaration resolves one ref).
- `find_definition` type-declaration *as a mode* — it's a distinct native action; if wanted,
  a separate tool, not a param. Escalate.

**Suggested order:** (1) type_hierarchy direction+maxDepth → (4) find_implementations
directOnly + (5) find_symbol matchMode → (3) find_usages accessType → (2) find_usages
includeTextOccurrences.
