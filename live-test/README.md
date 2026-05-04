# Live MCP Test Harness

Snapshot-based regression suite for the IDE Index MCP plugin. Drives real
HTTP POST requests against running JetBrains IDEs and diffs the responses
against committed `expected.jsonl` files.

Run after every plugin version bump.

## Requirements

- Python 3.10+ (stdlib only — no third-party deps)
- The dev plugin installed in each IDE you intend to test against (see below).
- The corresponding fixture project open in that IDE, fully indexed.

## Per-IDE fixture setup

| Language fixture | Open in | Default port |
|---|---|---|
| `python/` | PyCharm | 29172 |
| `java/`, `kotlin/` | IntelliJ IDEA | 29170 |
| `javascript/`, `typescript/` | WebStorm | 29173 |
| `go/` | GoLand | 29174 |
| `php/` | PhpStorm | 29175 |
| `rust/` | RustRover | 29178 |

Each fixture is a real IDE-openable project. Open it, wait for indexing,
then run the harness.

## Quick start

```bash
./run.py                          # runs every language, fails on diff
./run.py --bless                  # rewrite expected.jsonl from server output
./run.py --language python        # one language only
./run.py --tool ide_find_definition   # one tool across all languages
./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http   # override URL
```

## Version-bump workflow

1. Bump `pluginVersion` in `gradle.properties`.
2. `./gradlew buildPlugin` and install the resulting ZIP into each IDE
   (Settings → Plugins → ⚙ → Install Plugin from Disk…).
3. Restart each IDE (or use the in-IDE plugin reload).
4. Re-open every fixture; wait for indexing to finish.
5. Run the harness:

   ```bash
   ./live-test/run.py
   ```

6. If failures appear:
   - Read each diff carefully. Is the change intentional (matches the
     CHANGELOG entry for the new version) or a regression?
   - Intentional: `./live-test/run.py --bless` and commit alongside the
     version bump.
   - Regression: file an issue or revert the change.

Each run also writes `live-test/<lang>/output.jsonl` with the raw
normalized response per entry. Useful for inspecting current
responses without re-blessing or scraping diff output.
`output.jsonl` is gitignored.

## Troubleshooting

- **`PRECHECK: cannot reach …`** — the IDE's MCP server isn't running on
  the expected port. Check that the dev plugin is installed and enabled,
  and that the IDE is open. Override the port with `--url` if you've
  configured a non-default value in Settings → Tools → Index MCP Server.
- **`PRECHECK: project is in dumb mode`** — wait for indexing to finish
  in the IDE, then retry.
- **`MISSING (no expected.jsonl line N)`** — `expected.jsonl` is shorter
  than `input.jsonl`. Likely you added a new entry to `input.jsonl` and
  haven't blessed yet. Run `--bless` to regenerate.
- **All entries `FAIL` after a JDK / language toolchain update** — the
  toolchain change shifted JDK source line numbers. Re-bless once
  intentionally.

## Known limitations captured as ground truth

Some fixtures encode IntelliJ / language-plugin behaviors that are
*captured intentionally* — re-blessing is not the right response when
they show up. They document quirks of the platform, not bugs to fix:

- **Java `call-hier-makeDefault` callees empty**: constructor invocations
  inside a method body don't surface as callees. Same in Kotlin / TS / PHP
  fixtures' analogous entries.
- **Java `find_symbol` for overridden methods**: collapses to the
  topmost super; concrete overrides on subclasses are not separately
  surfaced.
- **PHP `call-hier-makeDefault` callees empty**: `new` expressions
  aren't tracked as callees (same root cause as Java's).
- **Python `find_implementations` on a `Protocol`**: returns empty —
  Protocols are structural, so PyCharm has no nominal implementer set.
- **Python `find_definition` on builtins inside lambda / dict bodies**:
  some positions return `tool_error_text: No named element at position`.
  Captured as the documented limitation.
- **TypeScript `find_implementations` via object literal**: classes/objects
  satisfying an interface structurally (no `implements` clause) are not
  surfaced.
- **Go `type_hierarchy`**: returns empty `supertypes` / `subtypes` —
  Go uses implicit (structural) interfaces, so the `Drawable` ↔
  `Circle` relationship doesn't appear here. Use `ide_find_class` for
  Go interface implementations.
- **Go `qualifiedName` is universally `null`**: GoLand does not register
  a `QualifiedNameProvider` for Go elements, and the plugin's
  `QualifiedNameUtil` has no Go-specific fallback. Tracked separately as
  a plugin enhancement.
- **Rust `qualifiedName` partially `null`**: when the Rust provider can't
  compute an FQN. The `name` field is unaffected.
- **Rust `find_implementations` on a generic trait bound** (e.g.
  `<C: Coercer>`): returns "No method or class found at position".
  Bound positions don't expose the trait through this API; anchor on
  the trait declaration directly instead.
- **Kotlin `call-hier-makeDefault` callees empty**: same constructor
  filtering as Java's — `Circle(...)`, `Rectangle(...)`, `Square(...)`
  invocations don't appear.
- **Kotlin `qualifiedName` uses `#` for methods** (e.g.
  `demo.Shape#area`): correct — matches IntelliJ's "Copy Reference"
  format and is consistent with Java.
- **JDK / toolchain paths in supertype results**: `type_hierarchy` for
  classes that extend `java.lang.Object` records an absolute path to a
  JDK `.class` file (Adoptium, mise-installed openjdk, etc.). Path
  changes when the toolchain changes; re-bless is the right response.
- **PyCharm / WebStorm stdlib paths**: similar to above for
  Python/JavaScript/TypeScript builtins (Number.parseInt → JetBrains
  helpers stdlib path; Python `int` → PyCharm typeshed path).

## Why not in CI?

The harness POSTs to live IDE-hosted servers, so it requires running
IDEs. CI runners don't carry a desktop IDE. Headless IDE execution
(`./gradlew runIde`) plus a fixture-loading script could enable this in
the future; deferred for v1.
