# Live MCP Test Harness — Design

**Date:** 2026-04-26
**Status:** Approved (ready for implementation plan)

## Purpose

After every plugin version bump, the developer needs a fast, reproducible way to
verify that the running MCP server still answers the same way for a curated set
of code-navigation and intelligence queries. The current test suite covers
in-process unit/platform tests but cannot detect wire-format regressions in the
HTTP-served `tools/call` responses (e.g., the v5.0.0 `qualifiedName` change to
`Foo#bar(java.lang.String)` form).

This harness is a **golden-file regression suite** that:

- Drives real HTTP `POST` requests to the running IDE-hosted MCP server.
- Covers all 8 plugin-supported languages.
- Covers 10 read-only navigation/intelligence tools per language.
- Asserts on snapshot-blessed JSON responses with deterministic normalization.
- Runs from a single bash script using `curl` and `jq` — zero language-specific
  test framework dependencies.

## Scope

**In scope (v1):**

- 8 languages: Python, Java, Kotlin, JavaScript, TypeScript, Go, PHP, Rust.
- 10 tools (subject to per-language availability — see matrix below):
  `ide_find_definition`, `ide_find_references`, `ide_find_class`,
  `ide_find_symbol`, `ide_diagnostics`, `ide_type_hierarchy`,
  `ide_call_hierarchy`, `ide_find_implementations`, `ide_find_super_methods`,
  `ide_file_structure`.
- Per-language fixture project: a real IDE-openable project (with the
  language's package manifest) containing two source files — `normal.{ext}`
  exercising vanilla OOP/functional patterns, and `quirks.{ext}` exercising
  language-specific edge cases ported from `sink-enum/testcases/*/quirks.*`.

**Out of scope (v1):**

- `ide_find_file` and `ide_search_text` — universal text/file search, not
  language-aware, low signal for this harness.
- Refactoring tools (`ide_refactor_rename`, `ide_refactor_safe_delete`,
  `ide_move_file`, `ide_reformat_code`, `ide_optimize_imports`,
  `ide_convert_java_to_kotlin`) — they mutate source; orthogonal regression
  surface.
- Editor-state tools (`ide_get_active_file`, `ide_open_file`).
- Build/test tools (`ide_build_project`).
- C# and Ruby fixtures — the plugin has no language handlers for them.
- CI integration. The harness requires running IDEs; CI is deferred.

## Tool support matrix

The harness only authors test entries for supported (lang × tool) cells.
Sourced from CLAUDE.md "Implemented Tools" section.

| Tool | Java | Kotlin | Python | JS | TS | Go | PHP | Rust |
|---|---|---|---|---|---|---|---|---|
| `ide_find_definition` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_find_references` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_find_class` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_find_symbol` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_diagnostics` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_type_hierarchy` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_call_hierarchy` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ide_find_implementations` | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| `ide_find_super_methods` | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | — |
| `ide_file_structure` | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | — |

## Repo layout

A new top-level directory `live-test/` in the plugin repo:

```
live-test/
├── README.md                       # how to run, prerequisites, bless workflow
├── run.sh                          # the only script
├── python/
│   ├── pyproject.toml              # so PyCharm sees a real project
│   ├── src/
│   │   ├── normal.py
│   │   └── quirks.py
│   ├── input.jsonl                 # one MCP call per line
│   └── expected.jsonl              # one normalized server response per line
├── java/
│   ├── pom.xml
│   ├── src/main/java/demo/
│   │   ├── Normal.java
│   │   └── Quirks.java
│   ├── input.jsonl
│   └── expected.jsonl
├── kotlin/                         # build.gradle.kts + src/main/kotlin/demo/
├── javascript/                     # package.json + src/
├── typescript/                     # package.json + tsconfig.json + src/
├── go/                             # go.mod + ./
├── php/                            # composer.json + src/
└── rust/                           # Cargo.toml + src/
```

## Source-file strategy

**`normal.{ext}`** — authored fresh per language, ~80–120 LOC. Covers the
structural patterns the navigation/intelligence tools need to exercise:

- Abstract base class + 2 concrete subclasses (drives `type_hierarchy`,
  `find_implementations`).
- Interface with multiple implementations (drives `find_implementations`,
  `find_super_methods`).
- Method override chain across 3 levels (drives `find_super_methods`).
- Function with 3+ call sites (drives `find_references`, `call_hierarchy`).
- Field accessed from multiple methods (drives `find_references`).
- Top-level function calling helpers (drives `call_hierarchy callees`).

Domain: a non-security toy (e.g., shape-area calculator) — kept neutral on
purpose.

**`quirks.{ext}`** — ported from `sink-enum/testcases/*/quirks.*`. Six
languages have an existing source file to port: Python, JS (.js + .mjs), TS,
Java, Go, PHP. Two languages (Kotlin, Rust) have no sink-enum quirks file and
require fresh authoring (Kotlin draws from Java + adds Kotlin-specific
patterns; Rust covers closures, trait dispatch, `match`, `Box<dyn Trait>`).

When porting, **swap security-relevant target functions for neutral builtins**.
The structural pattern (lambda dispatch, name rebinding, decorator wrap,
ternary, etc.) is what the harness exercises — the symbol being resolved
doesn't need to be dangerous. Examples:

- `eval(x)` → `int(x)`
- `os.system(cmd)` → `os.path.exists(cmd)`
- `pickle.loads(d)` → `json.loads(d)`
- `subprocess.run(x)` → `print(x)`
- `yaml.load(d)` → `dict(d)`

`SINK:` annotation comments are removed during port.

## Test plan format

**`input.jsonl`** — one MCP `tools/call` invocation per line:

```jsonl
{"id": "find-def-name-rebinding", "tool": "ide_find_definition", "params": {"file": "src/quirks.py", "line": 19, "column": 5}}
{"id": "find-refs-helper-fn",    "tool": "ide_find_references", "params": {"file": "src/normal.py", "line": 12, "column": 9}}
{"id": "type-hier-Animal",       "tool": "ide_type_hierarchy",  "params": {"file": "src/normal.py", "line": 5,  "column": 7}}
```

- `id` — short label, used only in diff output.
- `tool` — MCP tool name.
- `params` — tool input. File paths are **relative to the language project
  root**. The runner injects `project_path` (absolute) automatically before
  posting.

**`expected.jsonl`** — same line index as `input.jsonl` (line 1 ↔ line 1).
Stores the **unwrapped tool result** — `result.content[0].text` from the
JSON-RPC envelope is parsed back to JSON before snapshotting. The envelope
adds noise without value.

```jsonl
{"locations":[{"file":"${PROJECT_ROOT}/src/quirks.py","line":19,"column":10,"qualifiedName":"builtins.exec"}]}
{"references":[{"file":"${PROJECT_ROOT}/src/normal.py","line":12,"column":9},{"file":"${PROJECT_ROOT}/src/normal.py","line":47,"column":4}]}
```

Diff is line-by-line; mismatches are reported as
`test '<id>' (line N) failed`.

Error responses are valid expected lines too — if a test is meant to fail
(e.g., caret on whitespace), the JSON-RPC error object is what we bless and
diff against.

## Normalization

Applied to both blessed and live responses before any comparison.

**Drop fields** (noisy / non-deterministic — confirmed empirically by probing
a running 4.10.4 server against `sink-enum/testcases/java`):

- `preview` — multi-line source text, fragile.
- `nextCursor` — opaque base64'd ephemeral session-cache key.
- `stale` — depends on PSI mutation state during the session.
- Pagination noise: `hasMore`, `truncated`, `totalCollected`, `offset`,
  `pageSize`.

**Keep fields** (stable, useful for regression):

- `file`, `line`, `column`.
- `qualifiedName`, `name`, `symbolName`, `kind`, `language`, `astPath`.
- `context` (single source line, stable as long as the fixture source is).
- `totalCount` (useful invariant — "this query should always return N hits").

**Sort** these arrays by `(file, line, column)` to absorb PSI-traversal
nondeterminism: `usages`, `references`, `implementations`, `subtypes`,
`supertypes`, `classes`, `symbols`, `files`, `matches`.

**Substitute** the project's absolute path with literal `${PROJECT_ROOT}` —
matters when results escape the fixture (e.g., `find_definition` lands in JDK
source). For pure in-fixture tests, the server already returns relative paths.

Single jq filter implements drop + sort:

```jq
walk(
  if type == "object"
  then del(.preview, .nextCursor, .stale, .hasMore, .truncated, .totalCollected, .offset, .pageSize)
       | with_entries(
           if (.key | IN("usages","references","implementations","subtypes","supertypes","classes","symbols","files","matches"))
           then .value |= sort_by((.file // ""), (.line // 0), (.column // 0))
           else . end)
  else . end
)
```

Path replacement is one `sed`. Total normalization = one jq pass + one sed.

## Per-language port mapping

Hard-coded in `run.sh` as a bash associative array. Override via `--url`.

| Language | IDE | Port |
|---|---|---|
| python | PyCharm | 29172 |
| java, kotlin | IntelliJ IDEA | 29170 |
| javascript, typescript | WebStorm | 29173 |
| go | GoLand | 29174 |
| php | PhpStorm | 29175 |
| rust | RustRover | 29178 |

## Runner behavior

`./run.sh [--language LANG] [--bless] [--tool TOOL] [--url URL]`

CLI flags:

- `--language LANG` — restrict to a single language directory (e.g.,
  `--language python`). Default: every `live-test/*/input.jsonl` that exists.
- `--tool TOOL` — restrict to lines whose `tool` field equals `TOOL` (e.g.,
  `--tool ide_find_definition`). Default: all lines.
- `--bless` — write live responses to `expected.jsonl` instead of diffing.
- `--url URL` — override the port lookup for the current language (useful
  when running an IDE on a non-default port).

Behavior:

1. Resolve language list — from `--language`, or all `live-test/*/input.jsonl`
   that exist.
2. For each language:
   - Look up port from inline assoc array (or `--url` override).
   - Call `ide_index_status` as a precondition. Abort that language with a
     clear message if dumb-mode or unreachable.
   - Compute absolute `project_path` once: `realpath "$LIVE_TEST_ROOT/$lang"`.
   - For each line in `input.jsonl` (filtered by `--tool` if set):
     - Inject `project_path` into params.
     - POST to `http://127.0.0.1:$PORT/index-mcp/streamable-http`.
     - Extract `result.content[0].text` and `result.isError`. Parse text as
       JSON.
     - Run normalization (jq filter + sed for path substitution).
     - Two modes:
       - **Default**: read corresponding line from `expected.jsonl`, compare
         with `diff`. Print pass/fail with the test `id`.
       - **`--bless`**: append the normalized response to a tempfile. After
         all tests in the language, `mv tempfile expected.jsonl` atomically.
3. Final summary: per-language `passed/failed/total`. Exit non-zero on any
   failure.

**Failure output:**

```
[python] FAIL find-def-name-rebinding (line 3)
  --- expected
  +++ actual
  -    "qualifiedName": "builtins.exec"
  +    "qualifiedName": "builtins.eval"
[python] PASS find-refs-helper-fn (line 4)
...
[python] 12/14 passed
[java]   28/28 passed
ALL: 89/91 passed
```

## Workflow — version-bump regression

```
# Bump pluginVersion in gradle.properties to 5.1.0
# Build & install the new plugin into IDEs
# Restart each IDE (or rely on plugin hot-reload)
# Re-open fixture projects, wait for indexing
./live-test/run.sh                # diff against committed expected.jsonl
# If failures: review each diff, decide intentional vs regression
# Intentional changes:
./live-test/run.sh --bless        # re-snapshot + commit alongside the bump
```

## Prerequisites

Manual setup, documented in `live-test/README.md`:

1. Install the dev plugin into each relevant IDE
   (`./gradlew runIde` or install built ZIP).
2. Open each fixture project in its IDE — PyCharm opens
   `live-test/python/`, IntelliJ opens both `live-test/java/` and
   `live-test/kotlin/`, WebStorm opens both `live-test/javascript/` and
   `live-test/typescript/`, GoLand `live-test/go/`, PhpStorm
   `live-test/php/`, RustRover `live-test/rust/`.
3. Wait for indexing to finish.

`bash` (4.0+, for associative arrays), `curl`, and `jq` must be on PATH.

Note on multi-fixture IDEs: IntelliJ hosts both `live-test/java/` and
`live-test/kotlin/`; WebStorm hosts both `live-test/javascript/` and
`live-test/typescript/`. The MCP server is application-wide and routes by
`project_path`, so opening each fixture as its own project window works —
no per-fixture server.

## Volume estimate

8 languages × ~10 tools × ~7 calls per tool = **~500 test entries** total.
Initial bless is one shot; reviewing the resulting 500 expected lines on
first commit is the main human-time cost. Subsequent diffs after
version bumps are typically small.

## Non-goals

- Headless / CI execution. Requires running IDEs.
- Automated coverage of refactoring tools (mutation, harder to snapshot).
- Cross-machine reproducibility beyond `${PROJECT_ROOT}` substitution. JDK
  source paths from `mise` etc. are explicitly out of scope (the harness
  avoids tests that resolve into external library source).
