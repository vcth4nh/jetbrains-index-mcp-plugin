# Live MCP Test Harness

Snapshot-based regression suite for the IDE Index MCP plugin. Drives real
HTTP POST requests against running JetBrains IDEs and diffs the responses
against committed `expected.jsonl` files.

Run after every plugin version bump.

## Requirements

- `bash` 4.0+
- `curl`
- `jq`
- The dev plugin installed in each IDE you intend to test against
- The corresponding fixture project open in that IDE, fully indexed

## Quick start

```bash
./run.sh                          # runs every language, fails on diff
./run.sh --bless                  # write expected.jsonl from server output
./run.sh --language python        # one language only
./run.sh --tool ide_find_definition   # one tool across all languages
```

## Per-IDE fixture setup

| Language fixture | Open in | Default port |
|---|---|---|
| `python/` | PyCharm | 29172 |
| `java/`, `kotlin/` | IntelliJ IDEA | 29170 |
| `javascript/`, `typescript/` | WebStorm | 29173 |
| `go/` | GoLand | 29174 |
| `php/` | PhpStorm | 29175 |
| `rust/` | RustRover | 29178 |

Override the port with `--url http://127.0.0.1:PORT/index-mcp/streamable-http`.

## How tests are organized

Per fixture directory:
- `src/` — sample sources (normal patterns + language quirks)
- `input.jsonl` — one MCP `tools/call` per line: `{id, tool, params}`
- `expected.jsonl` — server response snapshots, same line index

`expected.jsonl` is produced by `./run.sh --bless`. Review the diff carefully
before committing — every change to a snapshot is either an intentional
behavior shift (bless and commit) or a regression (file an issue).
