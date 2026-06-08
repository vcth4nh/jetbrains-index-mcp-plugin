# Final adversarial review of live-test harness

Scope: `/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test`.

Requested focus:

- ID convention violations after the rename.
- README vs `CLAUDE.md` contradictions.
- Offline migration correctness from positional to id-keyed `expected.jsonl`.
- Reconfirmation of deferred future-rot items.
- New issues introduced by the rename / migration.

Verification performed:

- Parsed every `live-test/*/input.jsonl` and checked prefix/tool consistency, duplicate IDs, legacy `audit-` / `refs-` prefixes, hierarchy direction prefix consistency, and obvious variant-placement violations.
- Compared all current id-keyed `expected.jsonl` rows against the old positional `HEAD:live-test/<lang>/expected.jsonl` rows by matching current inputs to old inputs on `(tool, params)`, then applying current `normalize()` path normalization before comparing payloads.
- Ran `uv run ./live-test/run.py --check-fixtures`.

## Findings

| Severity | file:line | finding | fix |
|---|---|---|---|
| LOW | `live-test/go/input.jsonl:50`, `live-test/python/input.jsonl:66`, `live-test/rust/input.jsonl:61` | `hier-callee-makeDefault-d3` kept old camelCase in languages whose baseline IDs use `MakeDefault` / `make-default`. | Rename to language-native IDs and matching expected IDs: `hier-callee-MakeDefault-d3`, `hier-callee-make-default-d3`. |
| LOW | `live-test/go/input.jsonl:56` | `find-class-prefix-Shape` puts the `prefix` variant before the entity, directly violating the documented suffix rule. | Rename to `find-class-Shape-prefix` in input and expected. |
| LOW | `live-test/go/input.jsonl:55` | `usage-baseShape.Describe.promoted` uses dot syntax for `promoted`, which is not member access and is not a cataloged variant. | Rename to `usage-baseShape.Describe-via-promoted` or add `-promoted` to the variant catalog. |
| LOW | `live-test/javascript/input.jsonl:71` | `def-Number-parseInt-library` uses hyphenated member access and non-catalog `library` suffix. | Rename to `def-Number.parseInt-libraries-scope`. |
| LOW | `live-test/kotlin/input.jsonl:44`, `live-test/kotlin/input.jsonl:45` | `super-IntCoerce-apply` / `super-AbsCoerce-apply` use hyphens where the convention requires dotted member access. | Rename to `super-IntCoerce.apply` and `super-AbsCoerce.apply`. |
| LOW | `live-test/python/input.jsonl:22`, `live-test/php/input.jsonl:44` | Magic constructor IDs do not preserve source identifiers: `init` / `construct` instead of `__init__` / `__construct`. | Rename to `super-Square.__init__` and `super-Square.__construct`. |
| LOW | `live-test/javascript/input.jsonl:14`, `live-test/java/input.jsonl:48`, `live-test/java/input.jsonl:81` | `direct-overrides-only`, `errors`, and `warnings` are active suffixes but absent from the `CLAUDE.md` variant catalog. | Add them to `CLAUDE.md` or rename those IDs to cataloged suffixes. |
| LOW | `live-test/README.md:38`, `live-test/README.md:39` | Quick-start shows standalone `--bless-errors` and `--prune`; both only do anything with `--bless`, which `CLAUDE.md` correctly describes. | Change examples to `./run.py --bless --bless-errors` and `./run.py --bless --prune`. |
| HIGH | `live-test/run.py:51`, `live-test/CLAUDE.md:111` | Cross-platform path rot remains open. Code only normalizes Linux `/home/...`; doc says macOS/Windows leak through catch-all, but the catch-all also only matches `/home`. | Add macOS/Windows/SDKMAN/asdf/rustup/non-Toolbox patterns and fix the doc wording. |
| MEDIUM | `live-test/run.py:158` | MCP envelope robustness is only partially addressed. Non-JSON/type checks landed, but `structuredContent`, `isError`, and multi-content responses are still ignored. | Parse the current MCP result schema, including `isError`, `structuredContent`, and all content items. |
| HIGH | `live-test/java/expected.jsonl:30`, `live-test/python/expected.jsonl:5` | Library version drift inside stubs remains open: paths are tokenized, but stub `line`, `column`, and overload set snapshots still churn. | For tokenized library files, compare stable fields only or strip library `line`/`column`. |
| MEDIUM | `live-test/run.py:173`, `live-test/run.py:180` | Schema rename robustness remains open; request fields are hardcoded and never checked against MCP tool schemas. | Fetch tool schemas before running and validate params against the live schema. |
| MEDIUM | `live-test/run.py:469`, `live-test/CLAUDE.md:126` | Fixture-edit token-level anchor check remains open. Current check catches bounds/whitespace only, not wrong-token shifts. | Store expected anchor token per positional probe and verify it offline. |
| MEDIUM | `live-test/run.py:40`, `live-test/run.py:116` | Sort-vs-ordered hierarchy semantics remain open; `hierarchy`, `calls`, and `children` are still globally sorted. | Stop sorting tree/order-sensitive arrays; sort only set-like result arrays. |
| HIGH | `live-test/java/input.jsonl:33`, `live-test/java/input.jsonl:37` | Broad `find_class` / `find_symbol` bloat remains open; adding fixture symbols will churn baseline search rows. | Make invariant rows exact/subset assertions; keep broad-query behavior in isolated fixtures. |
| HIGH | `live-test/java/expected.jsonl:81` | Diagnostics English-text instability remains open; warning messages and `analysisMessage` are snapshotted verbatim. | Snapshot stable diagnostic fields or normalize/drop localized message text. |

## Clean categories

- No CRITICAL findings.
- No stray `audit-` or `refs-` prefixes remain in current `input.jsonl`.
- No prefix/tool mismatches or duplicate input IDs found.
- README vs `CLAUDE.md`: no contradiction found beyond the standalone `--bless-errors` / `--prune` examples.
- Offline migration correctness is clean: all 549 current IDs matched the corresponding old positional result payload after current path normalization.
- `uv run ./live-test/run.py --check-fixtures` reports `0 issues` for all eight languages:
  - `go`: 56 inputs, 56 expected.
  - `java`: 81 inputs, 81 expected.
  - `javascript`: 71 inputs, 71 expected.
  - `kotlin`: 67 inputs, 67 expected.
  - `php`: 70 inputs, 70 expected.
  - `python`: 73 inputs, 73 expected.
  - `rust`: 65 inputs, 65 expected.
  - `typescript`: 66 inputs, 66 expected.

## Deferred rot status

| Deferred item | Status |
|---|---|
| Cross-platform paths (macOS/Windows/SDKMAN/asdf/rustup/non-Toolbox) | Still open. Documentation now mentions it, but the code remains Linux `/home`-specific and the doc overstates catch-all behavior for macOS/Windows. |
| MCP envelope robustness | Partially addressed. Non-JSON envelope and type guards landed; `structuredContent`, `isError`, and multi-content handling remain open. |
| Library version drift inside stubs | Still open. Path tokens landed; library line/column/name/overload churn remains. |
| Schema rename robustness | Still open. Tool params are still hardcoded and unchecked against live schemas. |
| Fixture-edit token-level anchor check | Still open. Offline check catches missing files, bounds, and whitespace only. |
| Sort vs ordered hierarchy semantics | Still open. Tree arrays are still globally sorted. |
| `find_class` / `find_symbol` broad-query bloat | Still open. Broad query rows remain. |
| Diagnostics English-text instability | Still open. Java diagnostics snapshot English message text verbatim. |

