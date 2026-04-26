# Live MCP Test Harness

Snapshot-based regression suite for the IDE Index MCP plugin. Drives real
HTTP POST requests against running JetBrains IDEs and diffs the responses
against committed `expected.jsonl` files.

Run after every plugin version bump.

## Requirements

- `bash` 4.0+
- `curl`
- `jq`
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
./run.sh                          # runs every language, fails on diff
./run.sh --bless                  # rewrite expected.jsonl from server output
./run.sh --language python        # one language only
./run.sh --tool ide_find_definition   # one tool across all languages
./run.sh --url http://127.0.0.1:29170/index-mcp/streamable-http   # override URL
```

## Version-bump workflow

1. Bump `pluginVersion` in `gradle.properties`.
2. `./gradlew buildPlugin` and install the resulting ZIP into each IDE
   (Settings → Plugins → ⚙ → Install Plugin from Disk…).
3. Restart each IDE (or use the in-IDE plugin reload).
4. Re-open every fixture; wait for indexing to finish.
5. Run the harness:

   ```bash
   ./live-test/run.sh
   ```

6. If failures appear:
   - Read each diff carefully. Is the change intentional (matches the
     CHANGELOG entry for the new version) or a regression?
   - Intentional: `./live-test/run.sh --bless` and commit alongside the
     version bump.
   - Regression: file an issue or revert the change.

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

## Why not in CI?

The harness POSTs to live IDE-hosted servers, so it requires running
IDEs. CI runners don't carry a desktop IDE. Headless IDE execution
(`./gradlew runIde`) plus a fixture-loading script could enable this in
the future; deferred for v1.
