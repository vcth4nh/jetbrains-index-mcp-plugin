# Live MCP Test Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a snapshot-based regression suite that drives real HTTP `POST` requests against the IDE-hosted MCP server across 8 languages and 10 navigation/intelligence tools, runnable from a single bash script after every plugin version bump.

**Architecture:** A new top-level `live-test/` directory containing one `run.sh` and 8 per-language fixture sub-directories. Each fixture is an IDE-openable project with two source files (`normal` for vanilla patterns, `quirks` for language-specific edge cases ported from `sink-enum/testcases/`), an `input.jsonl` test plan, and a snapshot-blessed `expected.jsonl`. The runner POSTs each input line to `http://127.0.0.1:$PORT/index-mcp/streamable-http`, normalizes the response (drop noisy fields, sort unordered arrays, substitute project paths), and either diffs against `expected.jsonl` or rewrites it under `--bless`.

**Tech Stack:** bash 4+, curl, jq, sed. No language-specific test runner.

**Spec:** `docs/superpowers/specs/2026-04-26-live-mcp-test-harness-design.md`

---

## File Structure

```
live-test/
├── README.md                       # usage + prerequisites
├── run.sh                          # the only script
├── python/
│   ├── pyproject.toml
│   ├── src/normal.py
│   ├── src/quirks.py
│   ├── input.jsonl
│   └── expected.jsonl              # produced by --bless
├── java/
│   ├── pom.xml
│   ├── src/main/java/demo/Normal.java
│   ├── src/main/java/demo/Quirks.java
│   ├── input.jsonl
│   └── expected.jsonl
├── kotlin/
│   ├── build.gradle.kts
│   ├── src/main/kotlin/demo/Normal.kt
│   ├── src/main/kotlin/demo/Quirks.kt
│   ├── input.jsonl
│   └── expected.jsonl
├── javascript/
│   ├── package.json
│   ├── src/normal.js
│   ├── src/quirks.js
│   ├── input.jsonl
│   └── expected.jsonl
├── typescript/
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/normal.ts
│   ├── src/quirks.ts
│   ├── input.jsonl
│   └── expected.jsonl
├── go/
│   ├── go.mod
│   ├── normal.go
│   ├── quirks.go
│   ├── input.jsonl
│   └── expected.jsonl
├── php/
│   ├── composer.json
│   ├── src/Normal.php
│   ├── src/Quirks.php
│   ├── input.jsonl
│   └── expected.jsonl
└── rust/
    ├── Cargo.toml
    ├── src/lib.rs
    ├── src/normal.rs
    ├── src/quirks.rs
    ├── input.jsonl
    └── expected.jsonl
```

---

## Shared toy domain (used by every `normal.{ext}`)

Every language's `normal.{ext}` implements the same tiny "Shapes" domain so test entries can be conceptually parallel across languages:

- Abstract base type `Shape` with abstract method `area()` and a concrete method `describe()` that calls `area()`.
- Concrete `Circle(radius)` inherits/implements `Shape`. `Circle.area()` overrides.
- Concrete `Rectangle(width, height)` inherits/implements `Shape`. `Rectangle.area()` overrides.
- `Square(side)` inherits `Rectangle` (no `area()` override; inherits Rectangle's).
- Interface/trait/protocol `Drawable` with method `draw()`. Both `Circle` and `Rectangle` implement.
- Class `ShapeCollection` with field `shapes` (list of `Shape`) and methods `add(shape)`, `total_area()` (calls `area()` on each), and `largest()` (returns the `Shape` with max area).
- Top-level function `make_default_shapes()` that constructs 3 shapes and returns them as a list.

Why this shape:
- 3-level inheritance chain (`Shape → Rectangle → Square`) drives `find_super_methods`, `type_hierarchy`.
- Interface with multiple implementors (`Drawable`) drives `find_implementations`.
- `area()` overridden in two classes drives `find_implementations`, `find_super_methods`.
- Multiple call sites of `area()` (in `describe`, `total_area`, `largest`) drive `find_references`, `call_hierarchy`.
- `ShapeCollection.shapes` field referenced in three methods drives `find_references` on a field.
- Top-level function calling 3 constructors drives `call_hierarchy callees`.

---

## Task 1: Initialize live-test directory + README skeleton

**Files:**
- Create: `live-test/README.md`

- [ ] **Step 1: Create the directory and a minimal README**

```bash
mkdir -p live-test
```

Write `live-test/README.md`:

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add live-test/README.md
git commit -m "chore(live-test): scaffold directory and README"
```

---

## Task 2: run.sh — argument parsing, port lookup, language discovery

**Files:**
- Create: `live-test/run.sh`

- [ ] **Step 1: Write run.sh with arg parsing and language discovery (no MCP calls yet)**

Write `live-test/run.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIVE_TEST_ROOT="$SCRIPT_DIR"

# Per-language MCP server port (override with --url)
declare -A PORT_BY_LANG=(
    [python]=29172
    [java]=29170
    [kotlin]=29170
    [javascript]=29173
    [typescript]=29173
    [go]=29174
    [php]=29175
    [rust]=29178
)

# CLI flags
FLAG_LANG=""
FLAG_TOOL=""
FLAG_URL=""
FLAG_BLESS=0

usage() {
    cat <<EOF
Usage: $0 [--language LANG] [--tool TOOL] [--url URL] [--bless]

  --language LANG   Restrict to one language (e.g., python). Default: all.
  --tool TOOL       Restrict to one tool (e.g., ide_find_definition).
  --url URL         Override server URL for the run.
  --bless           Rewrite expected.jsonl from server output instead of diffing.
EOF
    exit 2
}

while [ $# -gt 0 ]; do
    case "$1" in
        --language) FLAG_LANG="$2"; shift 2 ;;
        --tool)     FLAG_TOOL="$2"; shift 2 ;;
        --url)      FLAG_URL="$2"; shift 2 ;;
        --bless)    FLAG_BLESS=1; shift ;;
        -h|--help)  usage ;;
        *)          echo "Unknown flag: $1" >&2; usage ;;
    esac
done

# Build URL for a language
url_for() {
    local lang="$1"
    if [ -n "$FLAG_URL" ]; then
        echo "$FLAG_URL"
        return
    fi
    local port="${PORT_BY_LANG[$lang]:-}"
    if [ -z "$port" ]; then
        echo "No port mapped for language '$lang'" >&2
        exit 1
    fi
    echo "http://127.0.0.1:$port/index-mcp/streamable-http"
}

# Discover languages with input.jsonl (inline so `exit 1` reaches the parent shell)
LANGS=()
if [ -n "$FLAG_LANG" ]; then
    if [ -f "$LIVE_TEST_ROOT/$FLAG_LANG/input.jsonl" ]; then
        LANGS=("$FLAG_LANG")
    else
        echo "No input.jsonl for language '$FLAG_LANG'" >&2
        exit 1
    fi
else
    for d in "$LIVE_TEST_ROOT"/*/; do
        [ -d "$d" ] || continue
        name="$(basename "$d")"
        if [ -f "$d/input.jsonl" ]; then
            LANGS+=("$name")
        fi
    done
fi

if [ ${#LANGS[@]} -eq 0 ]; then
    echo "No fixtures found in $LIVE_TEST_ROOT" >&2
    exit 0
fi

for lang in "${LANGS[@]}"; do
    url="$(url_for "$lang")"
    echo "[$lang] would run against $url (no-op so far)"
done
```

- [ ] **Step 2: Make executable and run a dry sanity check**

```bash
chmod +x live-test/run.sh
live-test/run.sh
```

Expected output: `No fixtures found in <path>` (because no `*/input.jsonl` exists yet). Exit 0.

```bash
live-test/run.sh --language python
```

Expected output: `No input.jsonl for language 'python'` and exit 1.

- [ ] **Step 3: Commit**

```bash
git add live-test/run.sh
git commit -m "feat(live-test): run.sh skeleton with arg parsing and language discovery"
```

---

## Task 3: run.sh — HTTP request and result extraction

**Files:**
- Modify: `live-test/run.sh`

- [ ] **Step 1: Add request building and POST helpers above the main loop**

Insert these functions after the `url_for()` function in `live-test/run.sh`:

```bash
# Build a JSON-RPC tools/call request from an input line and project_path
build_request() {
    local input_line="$1"
    local project_path="$2"
    jq -c --arg pp "$project_path" '{
        jsonrpc: "2.0",
        id: 1,
        method: "tools/call",
        params: {
            name: .tool,
            arguments: (.params + {project_path: $pp})
        }
    }' <<< "$input_line"
}

# POST a JSON-RPC request, return the unwrapped tool result as JSON
# Echoes the parsed `result.content[0].text` (or the JSON-RPC error object).
post_and_unwrap() {
    local url="$1"
    local request_json="$2"
    local raw
    raw="$(curl -sS --fail-with-body --max-time 60 \
        -X POST "$url" \
        -H 'Content-Type: application/json' \
        --data "$request_json")"
    # If JSON-RPC error: emit the error object verbatim, marker isError:true
    if jq -e '.error' >/dev/null 2>&1 <<< "$raw"; then
        jq -c '{jsonrpc_error: .error}' <<< "$raw"
        return
    fi
    # tools/call result: extract content[0].text and parse as JSON
    jq -c '.result.content[0].text | fromjson' <<< "$raw"
}
```

- [ ] **Step 2: Replace the no-op loop with actual per-test execution (no normalization or diff yet — just print extracted result)**

Replace the final `for lang` loop in `live-test/run.sh` with:

```bash
for lang in "${LANGS[@]}"; do
    url="$(url_for "$lang")"
    project_path="$LIVE_TEST_ROOT/$lang"
    input_file="$LIVE_TEST_ROOT/$lang/input.jsonl"
    echo "[$lang] $url"
    line_no=0
    while IFS= read -r line; do
        line_no=$((line_no + 1))
        [ -z "$line" ] && continue
        # --tool filter
        if [ -n "$FLAG_TOOL" ]; then
            tool="$(jq -r '.tool' <<< "$line")"
            [ "$tool" = "$FLAG_TOOL" ] || continue
        fi
        request="$(build_request "$line" "$project_path")"
        result="$(post_and_unwrap "$url" "$request")"
        id="$(jq -r '.id' <<< "$line")"
        echo "  [$line_no] $id -> $result"
    done < "$input_file"
done
```

- [ ] **Step 3: Smoke test against a running IDE (manual)**

This requires the implementer to (a) have the dev plugin built and installed, (b) have at least one fixture project open. Since fixtures don't exist yet, point at an arbitrary project temporarily. From the project root:

```bash
mkdir -p live-test/python
echo '{"id": "smoke", "tool": "ide_index_status", "params": {}}' > live-test/python/input.jsonl
PROJ_PATH=$(realpath live-test/python)
# Make sure PyCharm has live-test/python open or substitute --url for an IDE that has any project open.
live-test/run.sh --language python
# Expected: prints `[python] http://127.0.0.1:29172/...` then `[1] smoke -> {...}` with current index status.
# Cleanup smoke fixture before continuing:
rm live-test/python/input.jsonl
rmdir live-test/python
```

- [ ] **Step 4: Commit**

```bash
git add live-test/run.sh
git commit -m "feat(live-test): post and unwrap MCP responses in run.sh"
```

---

## Task 4: run.sh — normalization (jq filter + path substitution)

**Files:**
- Modify: `live-test/run.sh`

- [ ] **Step 1: Add the inline jq filter as a heredoc constant near the top of the script**

After the `PORT_BY_LANG` declaration in `live-test/run.sh`, add:

```bash
# Normalization filter: drop noisy fields, sort PSI-result arrays
read -r -d '' NORMALIZE_FILTER <<'JQ' || true
walk(
  if type == "object"
  then del(.preview, .nextCursor, .stale, .hasMore, .truncated, .totalCollected, .offset, .pageSize)
       | with_entries(
           if (.key | IN("usages","references","implementations","subtypes","supertypes","classes","symbols","files","matches"))
           then .value |= sort_by((.file // ""), (.line // 0), (.column // 0))
           else . end)
  else . end
)
JQ
```

(`read -r -d '' ... <<'JQ' || true` is the conventional bash pattern for a multi-line string into a variable; the `|| true` is required because `read` exits non-zero on EOF when no delimiter byte is found.)

- [ ] **Step 2: Add a `normalize` function below `post_and_unwrap`**

```bash
# Normalize a result JSON: apply jq filter, substitute project absolute path with ${PROJECT_ROOT}, sort keys.
normalize() {
    local result_json="$1"
    local project_path="$2"
    jq -cS "$NORMALIZE_FILTER" <<< "$result_json" \
        | sed "s|$project_path|\${PROJECT_ROOT}|g"
}
```

- [ ] **Step 3: Pipe each tool result through `normalize` in the main loop**

In the main loop, change `result="$(post_and_unwrap "$url" "$request")"` to:

```bash
        raw_result="$(post_and_unwrap "$url" "$request")"
        result="$(normalize "$raw_result" "$project_path")"
```

- [ ] **Step 4: Verify normalization in isolation**

Capture a real response and pipe through the filter to confirm shape:

```bash
# With an IDE running on 29170 and any java project open:
curl -sS -X POST http://127.0.0.1:29170/index-mcp/streamable-http \
    -H 'Content-Type: application/json' \
    --data '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ide_index_status","arguments":{}}}' \
    | jq -cS '.result.content[0].text | fromjson' \
    | jq -cS "$(grep -A 100 'NORMALIZE_FILTER <<.JQ.' live-test/run.sh | sed -n '/^walk/,/^)$/p')"
```

Sanity check: the filter must not throw. The output must be valid JSON.

- [ ] **Step 5: Commit**

```bash
git add live-test/run.sh
git commit -m "feat(live-test): add jq normalization filter and path substitution"
```

---

## Task 5: run.sh — diff mode (read expected.jsonl, line-by-line compare)

**Files:**
- Modify: `live-test/run.sh`

- [ ] **Step 1: Add per-language counters and a comparison routine**

Replace the inner `while` loop body in the main loop with:

```bash
        request="$(build_request "$line" "$project_path")"
        raw_result="$(post_and_unwrap "$url" "$request")"
        result="$(normalize "$raw_result" "$project_path")"
        id="$(jq -r '.id' <<< "$line")"

        if [ "$FLAG_BLESS" -eq 1 ]; then
            echo "$result" >> "$bless_tmp"
            echo "  [$line_no] $id BLESS"
            lang_pass=$((lang_pass + 1))
            continue
        fi

        # Default: diff against expected.jsonl
        expected_line=""
        if [ -f "$expected_file" ]; then
            expected_line="$(sed -n "${line_no}p" "$expected_file")"
        fi
        if [ -z "$expected_line" ]; then
            echo "  [$line_no] $id MISSING (no expected.jsonl line $line_no — bless?)"
            lang_fail=$((lang_fail + 1))
            continue
        fi
        # Normalize the expected line to the same key order as the actual
        expected_norm="$(jq -cS . <<< "$expected_line")"
        actual_norm="$(jq -cS . <<< "$result")"
        if [ "$expected_norm" = "$actual_norm" ]; then
            echo "  [$line_no] $id PASS"
            lang_pass=$((lang_pass + 1))
        else
            echo "  [$line_no] $id FAIL"
            diff <(jq -S . <<< "$expected_line") <(jq -S . <<< "$result") | sed 's/^/    /'
            lang_fail=$((lang_fail + 1))
        fi
```

- [ ] **Step 2: Initialize per-language state and final summary outside the inner loop**

Wrap the inner `while` with setup and summary code so the outer `for lang` loop becomes:

```bash
total_pass=0
total_fail=0
for lang in "${LANGS[@]}"; do
    url="$(url_for "$lang")"
    project_path="$LIVE_TEST_ROOT/$lang"
    input_file="$LIVE_TEST_ROOT/$lang/input.jsonl"
    expected_file="$LIVE_TEST_ROOT/$lang/expected.jsonl"
    bless_tmp="$(mktemp)"
    lang_pass=0
    lang_fail=0
    echo "[$lang] $url"

    line_no=0
    while IFS= read -r line; do
        line_no=$((line_no + 1))
        [ -z "$line" ] && continue
        if [ -n "$FLAG_TOOL" ]; then
            tool="$(jq -r '.tool' <<< "$line")"
            [ "$tool" = "$FLAG_TOOL" ] || continue
        fi
        # ... (the body inserted in Step 1) ...
    done < "$input_file"

    if [ "$FLAG_BLESS" -eq 1 ]; then
        mv "$bless_tmp" "$expected_file"
        echo "[$lang] BLESSED $expected_file"
    else
        rm -f "$bless_tmp"
        echo "[$lang] $lang_pass passed, $lang_fail failed"
    fi
    total_pass=$((total_pass + lang_pass))
    total_fail=$((total_fail + lang_fail))
done

echo "ALL: $total_pass passed, $total_fail failed"
[ "$total_fail" -eq 0 ]
```

- [ ] **Step 3: Smoke test diff mode (manual)**

Recreate the smoke fixture:

```bash
mkdir -p live-test/python
echo '{"id": "smoke", "tool": "ide_index_status", "params": {}}' > live-test/python/input.jsonl
# Without expected.jsonl: should report MISSING
live-test/run.sh --language python || true   # exits non-zero
# Bless creates the file
live-test/run.sh --language python --bless
# Now run again: should PASS
live-test/run.sh --language python
# Cleanup before continuing
rm -rf live-test/python
```

- [ ] **Step 4: Commit**

```bash
git add live-test/run.sh
git commit -m "feat(live-test): diff and bless modes in run.sh"
```

---

## Task 6: run.sh — index_status precondition + final shape polish

**Files:**
- Modify: `live-test/run.sh`

- [ ] **Step 1: Add a precondition that fails fast if the IDE is in dumb mode or unreachable**

Add this helper above the main loop:

```bash
# Returns 0 if the server is reachable and the project is fully indexed (smart mode).
# Returns 1 otherwise. Prints a diagnostic on failure.
check_ready() {
    local url="$1"
    local project_path="$2"
    local req
    req="$(jq -nc --arg pp "$project_path" '{
        jsonrpc:"2.0", id:1, method:"tools/call",
        params:{name:"ide_index_status", arguments:{project_path:$pp}}
    }')"
    local raw
    if ! raw="$(curl -sS --max-time 5 -X POST "$url" -H 'Content-Type: application/json' --data "$req" 2>/dev/null)"; then
        echo "  PRECHECK: cannot reach $url" >&2
        return 1
    fi
    local text
    text="$(jq -r '.result.content[0].text // empty' <<< "$raw")"
    if [ -z "$text" ]; then
        echo "  PRECHECK: unexpected response from $url: $raw" >&2
        return 1
    fi
    local err
    err="$(jq -r '.error // empty' <<< "$text")"
    if [ -n "$err" ]; then
        echo "  PRECHECK: $err — $(jq -r '.message // empty' <<< "$text")" >&2
        return 1
    fi
    local dumb
    dumb="$(jq -r '.dumbMode // empty' <<< "$text")"
    if [ "$dumb" = "true" ]; then
        echo "  PRECHECK: project is in dumb mode (still indexing)" >&2
        return 1
    fi
    return 0
}
```

- [ ] **Step 2: Call `check_ready` at the top of each language iteration; if it fails, mark the language as skipped and move on**

Just inside the `for lang` loop, before the `while` loop, add:

```bash
    if ! check_ready "$url" "$project_path"; then
        echo "[$lang] SKIPPED (precheck failed)"
        total_fail=$((total_fail + 1))
        continue
    fi
```

- [ ] **Step 3: Smoke test the precondition (manual)**

Point at a non-existent port:

```bash
live-test/run.sh --language python --url http://127.0.0.1:1/index-mcp/streamable-http || true
# Expected: PRECHECK: cannot reach ..., language SKIPPED
```

(With the smoke fixture from Task 5; remove again afterward.)

- [ ] **Step 4: Commit**

```bash
git add live-test/run.sh
git commit -m "feat(live-test): index_status precondition check"
```

---

## Task 7: Python fixture

**Files:**
- Create: `live-test/python/pyproject.toml`
- Create: `live-test/python/src/normal.py`
- Create: `live-test/python/src/quirks.py`
- Create: `live-test/python/input.jsonl`
- Create: `live-test/python/expected.jsonl` (via `--bless`)

- [ ] **Step 1: Project skeleton**

`live-test/python/pyproject.toml`:

```toml
[project]
name = "live-test-python"
version = "0.1.0"
description = "Python fixture for live MCP test harness"
requires-python = ">=3.10"
```

(No deps — quirks file uses only stdlib.)

```bash
mkdir -p live-test/python/src
touch live-test/python/src/__init__.py
```

- [ ] **Step 2: Write `live-test/python/src/normal.py`**

Implements the shared shapes domain.

```python
"""Vanilla OOP patterns for the live MCP test harness."""
from abc import ABC, abstractmethod
from typing import Protocol


class Drawable(Protocol):
    def draw(self) -> str: ...


class Shape(ABC):
    @abstractmethod
    def area(self) -> float:
        ...

    def describe(self) -> str:
        return f"{type(self).__name__} with area {self.area()}"


class Circle(Shape):
    def __init__(self, radius: float) -> None:
        self.radius = radius

    def area(self) -> float:
        return 3.14159 * self.radius * self.radius

    def draw(self) -> str:
        return f"circle r={self.radius}"


class Rectangle(Shape):
    def __init__(self, width: float, height: float) -> None:
        self.width = width
        self.height = height

    def area(self) -> float:
        return self.width * self.height

    def draw(self) -> str:
        return f"rect {self.width}x{self.height}"


class Square(Rectangle):
    def __init__(self, side: float) -> None:
        super().__init__(side, side)


class ShapeCollection:
    def __init__(self) -> None:
        self.shapes: list[Shape] = []

    def add(self, shape: Shape) -> None:
        self.shapes.append(shape)

    def total_area(self) -> float:
        return sum(s.area() for s in self.shapes)

    def largest(self) -> Shape | None:
        if not self.shapes:
            return None
        return max(self.shapes, key=lambda s: s.area())


def make_default_shapes() -> list[Shape]:
    return [Circle(1.0), Rectangle(2.0, 3.0), Square(4.0)]
```

- [ ] **Step 3: Write `live-test/python/src/quirks.py`**

Ported from `~/dev/sink-enum/testcases/python/src/quirks.py`. Drop `SINK:` comments. Replace security-relevant target functions with neutral builtins. Keep the structural patterns (name rebinding, getattr indirection, lambda wrapping, dict dispatch, etc.).

```python
"""Language-quirk patterns for the live MCP test harness.

Each function exercises a Python-specific indirection or rebinding pattern.
The target functions are deliberately neutral — the patterns themselves are
what the navigation tools must resolve.
"""
import functools
import operator


def quirk_name_rebinding(x: str) -> int:
    fn = int
    return fn(x)


def quirk_getattr_module(name: str) -> int:
    fn = getattr(operator, "abs")
    return fn(int(name))


def quirk_functools_partial(x: str) -> int:
    coerce = functools.partial(int)
    return coerce(x)


def quirk_dict_dispatch(key: str, x: str) -> int:
    dispatch = {"int": int, "abs": lambda v: abs(int(v))}
    return dispatch[key](x)


def quirk_lambda_wrap(x: str) -> int:
    coerce = lambda v: int(v)
    return coerce(x)


def quirk_list_indexing(x: str) -> int:
    funcs = [int, str, float]
    return funcs[0](x)


def quirk_conditional_expr(x: str, use_int: bool) -> int | float:
    fn = int if use_int else float
    return fn(x)


def quirk_star_import_simulation(x: str) -> int:
    from operator import abs as a
    return a(int(x))


def quirk_decorator_wrap(x: str) -> int:
    def with_logging(fn):
        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            return fn(*args, **kwargs)
        return wrapper
    wrapped = with_logging(int)
    return wrapped(x)


def quirk_class_method(x: str) -> int:
    class Coercer:
        def coerce(self, raw: str) -> int:
            return int(raw)
    return Coercer().coerce(x)


def quirk_walrus(x: str) -> int:
    if (result := int(x)):
        return result
    return 0


def quirk_unpacking(x: str) -> int:
    fn, *_ = [int, float]
    return fn(x)


def quirk_nested_return(x: str) -> int:
    def get_coercer():
        return int
    return get_coercer()(x)


def quirk_map_filter(items: list[str]) -> list[int]:
    return list(map(int, items))


def quirk_reduce(values: list[str]) -> int:
    return functools.reduce(lambda acc, v: acc + int(v), values, 0)


def quirk_chained_getattr(x: str) -> int:
    fn = getattr(getattr(operator, "abs"), "__call__")
    return fn(int(x))


def quirk_multiple_assignment(x: str) -> int:
    a = b = int
    return a(x) + b(x)
```

- [ ] **Step 4: Write `live-test/python/input.jsonl` with ~30 entries**

The entries below each name a specific (file, line, column) anchor. Open the source file in any editor with line/column display to verify positions before committing. Lines and columns are **1-indexed**.

```jsonl
{"id":"def-circle-area",            "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":24,"column":9}}
{"id":"def-rectangle-area",         "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":34,"column":9}}
{"id":"def-shape-describe",         "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":17,"column":9}}
{"id":"def-make-default-shapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":63,"column":5}}
{"id":"def-quirk-name-rebinding",   "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":5}}
{"id":"refs-area-from-describe",    "tool":"ide_find_references",      "params":{"file":"src/normal.py","line":17,"column":51}}
{"id":"refs-shapes-field",          "tool":"ide_find_references",      "params":{"file":"src/normal.py","line":51,"column":14}}
{"id":"refs-shape-class",           "tool":"ide_find_references",      "params":{"file":"src/normal.py","line":11,"column":7}}
{"id":"refs-int-builtin-in-quirks", "tool":"ide_find_references",      "params":{"file":"src/quirks.py","line":14,"column":10}}
{"id":"impls-shape-area",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":13,"column":9}}
{"id":"impls-drawable-draw",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":7,"column":9}}
{"id":"super-circle-area",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":24,"column":9}}
{"id":"super-rectangle-area",       "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":34,"column":9}}
{"id":"type-hier-square",           "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":40,"column":7}}
{"id":"type-hier-rectangle",        "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":28,"column":7}}
{"id":"type-hier-shape",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":11,"column":7}}
{"id":"call-hier-make-default",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":13,"column":9,"direction":"callers","maxDepth":2}}
{"id":"call-hier-total-area",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":50,"column":9,"direction":"callees","maxDepth":2}}
{"id":"find-class-Shape",           "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Rectangle",       "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
{"id":"find-symbol-area",           "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-quirk-rebind",   "tool":"ide_find_symbol",          "params":{"query":"quirk_name_rebinding"}}
{"id":"file-structure-normal",      "tool":"ide_file_structure",       "params":{"file":"src/normal.py"}}
{"id":"file-structure-quirks",      "tool":"ide_file_structure",       "params":{"file":"src/quirks.py"}}
{"id":"diagnostics-normal",         "tool":"ide_diagnostics",          "params":{"file":"src/normal.py"}}
{"id":"diagnostics-quirks",         "tool":"ide_diagnostics",          "params":{"file":"src/quirks.py"}}
{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":31,"column":33}}
{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":36,"column":24}}
{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":41,"column":17}}
```

(Adjust line/column numbers if your editor's view of the source does not match the offsets shown — the implementer must verify each anchor by opening the file. The set of *patterns* to test is fixed; the precise `(line, column)` numbers depend on the source file you commit.)

- [ ] **Step 5: Open the fixture in PyCharm and wait for indexing**

```bash
# Manually: open live-test/python/ as a project in PyCharm, configure the
# Python interpreter (any 3.10+), wait for indexing to finish.
# (No virtualenv needed — there are no third-party deps.)
```

- [ ] **Step 6: Bless and inspect**

```bash
live-test/run.sh --language python --bless
```

Open `live-test/python/expected.jsonl` and skim each line:
- Confirm each test resolves to the intended target (e.g., the `def-circle-area` line should reference `src/normal.py` at the `Circle.area` definition).
- If any line shows an empty `usages: []`, the input anchor likely missed the identifier — adjust the line/column in `input.jsonl` and re-bless.

- [ ] **Step 7: Verify**

```bash
live-test/run.sh --language python
```

Expected: `[python] N passed, 0 failed`.

- [ ] **Step 8: Commit**

```bash
git add live-test/python
git commit -m "feat(live-test): python fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 8: Java fixture

**Files:**
- Create: `live-test/java/pom.xml`
- Create: `live-test/java/src/main/java/demo/Normal.java`
- Create: `live-test/java/src/main/java/demo/Quirks.java`
- Create: `live-test/java/input.jsonl`
- Create: `live-test/java/expected.jsonl` (via `--bless`)

- [ ] **Step 1: Project skeleton (`pom.xml`)**

`live-test/java/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>live-test-java</artifactId>
    <version>0.1.0</version>
    <packaging>jar</packaging>
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
```

```bash
mkdir -p live-test/java/src/main/java/demo
```

- [ ] **Step 2: `live-test/java/src/main/java/demo/Normal.java`**

```java
package demo;

import java.util.ArrayList;
import java.util.List;

interface Drawable {
    String draw();
}

abstract class Shape {
    abstract double area();

    String describe() {
        return getClass().getSimpleName() + " with area " + area();
    }
}

class Circle extends Shape implements Drawable {
    private final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return 3.14159 * radius * radius;
    }

    @Override
    public String draw() {
        return "circle r=" + radius;
    }
}

class Rectangle extends Shape implements Drawable {
    protected final double width;
    protected final double height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return width * height;
    }

    @Override
    public String draw() {
        return "rect " + width + "x" + height;
    }
}

class Square extends Rectangle {
    Square(double side) {
        super(side, side);
    }
}

class ShapeCollection {
    private final List<Shape> shapes = new ArrayList<>();

    void add(Shape shape) {
        shapes.add(shape);
    }

    double totalArea() {
        double sum = 0;
        for (Shape s : shapes) {
            sum += s.area();
        }
        return sum;
    }

    Shape largest() {
        Shape best = null;
        for (Shape s : shapes) {
            if (best == null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

public class Normal {
    public static List<Shape> makeDefaultShapes() {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(1.0));
        shapes.add(new Rectangle(2.0, 3.0));
        shapes.add(new Square(4.0));
        return shapes;
    }
}
```

- [ ] **Step 3: `live-test/java/src/main/java/demo/Quirks.java`**

Ported from `~/dev/sink-enum/testcases/java/src/main/java/testcases/Quirks.java`. Drop `SINK:` comments. Replace security-relevant target calls (Runtime.exec, Files.readAllBytes, ScriptEngine.eval, etc.) with neutral ones (Integer.parseInt, String.toUpperCase, Math.abs).

```java
package demo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Quirks {

    public static int quirkLambda(String x) {
        Function<String, Integer> fn = s -> Integer.parseInt(s);
        return fn.apply(x);
    }

    public static int quirkVar(String x) {
        var coerce = (Function<String, Integer>) Integer::parseInt;
        return coerce.apply(x);
    }

    public static int quirkAnonClass(String x) {
        Function<String, Integer> fn = new Function<>() {
            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }
        };
        return fn.apply(x);
    }

    public static Optional<Integer> quirkOptional(String x) {
        return Optional.of(x).map(Integer::parseInt);
    }

    public static int quirkTernary(String x, boolean stripPlus) {
        Function<String, Integer> fn = stripPlus
            ? s -> Integer.parseInt(s.replace("+", ""))
            : Integer::parseInt;
        return fn.apply(x);
    }

    public static CompletableFuture<Integer> quirkCompletableFuture(String x) {
        return CompletableFuture.supplyAsync(() -> Integer.parseInt(x));
    }

    public static List<Integer> quirkStreamMap(List<String> xs) {
        return xs.stream().map(Integer::parseInt).collect(Collectors.toList());
    }

    public static int quirkMapDispatch(String key, String x) {
        Map<String, Function<String, Integer>> dispatch = new HashMap<>();
        dispatch.put("int", Integer::parseInt);
        dispatch.put("abs", s -> Math.abs(Integer.parseInt(s)));
        return dispatch.get(key).apply(x);
    }

    static class Coercer {
        private final String prefix;
        Coercer(String prefix) { this.prefix = prefix; }
        int coerce(String x) { return Integer.parseInt(x.replace(prefix, "")); }
    }

    @FunctionalInterface
    interface Coerce { int run(String s); }

    public static int quirkFunctionalIface(String x) {
        Coerce c = Integer::parseInt;
        return c.run(x);
    }

    enum CoerceMode {
        INT { int apply(String s) { return Integer.parseInt(s); } },
        ABS { int apply(String s) { return Math.abs(Integer.parseInt(s)); } };
        abstract int apply(String s);
    }

    public static int quirkEnumDispatch(String x) {
        return CoerceMode.INT.apply(x);
    }

    public static int quirkSupplier(String x) {
        Supplier<Integer> supplier = () -> Integer.parseInt(x);
        return supplier.get();
    }
}
```

- [ ] **Step 4: `live-test/java/input.jsonl`**

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":25,"column":12}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":47,"column":12}}
{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":86,"column":35}}
{"id":"refs-area-from-totalArea", "tool":"ide_find_references",      "params":{"file":"src/main/java/demo/Normal.java","line":67,"column":24}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/main/java/demo/Normal.java","line":63,"column":33}}
{"id":"refs-shape-class",         "tool":"ide_find_references",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":18}}
{"id":"impls-drawable",           "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
{"id":"impls-functional-Coerce",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":51,"column":15}}
{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":25,"column":12}}
{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":47,"column":12}}
{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":58,"column":7}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":40,"column":7}}
{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":86,"column":35,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":18,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Normal.java"}}
{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Quirks.java"}}
{"id":"diagnostics-Normal",       "tool":"ide_diagnostics",          "params":{"file":"src/main/java/demo/Normal.java"}}
{"id":"diagnostics-Quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/main/java/demo/Quirks.java"}}
```

- [ ] **Step 5: Open in IntelliJ, bless, verify, commit**

```bash
# Manually: open live-test/java in IntelliJ, wait for indexing.
live-test/run.sh --language java --bless
# Review live-test/java/expected.jsonl
live-test/run.sh --language java
git add live-test/java
git commit -m "feat(live-test): java fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 9: Kotlin fixture

**Files:**
- Create: `live-test/kotlin/build.gradle.kts`
- Create: `live-test/kotlin/settings.gradle.kts`
- Create: `live-test/kotlin/src/main/kotlin/demo/Normal.kt`
- Create: `live-test/kotlin/src/main/kotlin/demo/Quirks.kt`
- Create: `live-test/kotlin/input.jsonl`
- Create: `live-test/kotlin/expected.jsonl` (via `--bless`)

- [ ] **Step 1: Project skeleton**

`live-test/kotlin/settings.gradle.kts`:

```kotlin
rootProject.name = "live-test-kotlin"
```

`live-test/kotlin/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
}

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
```

```bash
mkdir -p live-test/kotlin/src/main/kotlin/demo
```

- [ ] **Step 2: `live-test/kotlin/src/main/kotlin/demo/Normal.kt`**

```kotlin
package demo

interface Drawable {
    fun draw(): String
}

abstract class Shape {
    abstract fun area(): Double

    open fun describe(): String = "${this::class.simpleName} with area ${area()}"
}

class Circle(val radius: Double) : Shape(), Drawable {
    override fun area(): Double = 3.14159 * radius * radius
    override fun draw(): String = "circle r=$radius"
}

open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
    override fun area(): Double = width * height
    override fun draw(): String = "rect ${width}x$height"
}

class Square(side: Double) : Rectangle(side, side)

class ShapeCollection {
    val shapes: MutableList<Shape> = mutableListOf()

    fun add(shape: Shape) {
        shapes.add(shape)
    }

    fun totalArea(): Double = shapes.sumOf { it.area() }

    fun largest(): Shape? = shapes.maxByOrNull { it.area() }
}

fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))
```

- [ ] **Step 3: `live-test/kotlin/src/main/kotlin/demo/Quirks.kt`**

Authored fresh (sink-enum has no Kotlin quirks file). Covers Kotlin-specific patterns: extension functions, scope functions (`let`, `apply`, `with`), sealed classes, data classes, function references, default arguments.

```kotlin
package demo

fun quirkLambda(x: String): Int {
    val coerce: (String) -> Int = { it.toInt() }
    return coerce(x)
}

fun quirkFunctionRef(x: String): Int {
    val coerce: (String) -> Int = String::toInt
    return coerce(x)
}

fun quirkApply(x: String): Int {
    return StringBuilder().apply { append(x) }.toString().toInt()
}

fun quirkLet(x: String?): Int {
    return x?.let { it.toInt() } ?: 0
}

fun quirkWith(x: String): Int {
    return with(x) { toInt() }
}

fun quirkRun(x: String): Int = x.run { toInt() }

fun String.coerceTo(default: Int): Int = this.toIntOrNull() ?: default

fun quirkExtensionFn(x: String): Int = x.coerceTo(0)

fun quirkWhen(mode: String, x: String): Int = when (mode) {
    "int" -> x.toInt()
    "abs" -> Math.abs(x.toInt())
    else -> 0
}

sealed class Coercion {
    abstract fun apply(x: String): Int
    object IntCoerce : Coercion() { override fun apply(x: String): Int = x.toInt() }
    object AbsCoerce : Coercion() { override fun apply(x: String): Int = Math.abs(x.toInt()) }
}

fun quirkSealed(c: Coercion, x: String): Int = c.apply(x)

data class Coercer(val prefix: String) {
    fun coerce(x: String): Int = x.removePrefix(prefix).toInt()
}

fun quirkDataClass(x: String): Int = Coercer("+").coerce(x)

fun quirkDispatchMap(key: String, x: String): Int {
    val dispatch: Map<String, (String) -> Int> = mapOf(
        "int" to String::toInt,
        "abs" to { s -> Math.abs(s.toInt()) }
    )
    return dispatch[key]?.invoke(x) ?: 0
}

fun quirkInfix(x: String): Int = (x to 0).coerceFirst()

infix fun Pair<String, Int>.coerceFirst(): Int = this.first.toIntOrNull() ?: this.second
```

- [ ] **Step 4: `live-test/kotlin/input.jsonl`**

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":18}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":18}}
{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":36,"column":5}}
{"id":"refs-area-from-totalArea", "tool":"ide_find_references",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":31,"column":51}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":24,"column":9}}
{"id":"refs-shape-class",         "tool":"ide_find_references",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18}}
{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":4,"column":9}}
{"id":"impls-coercion",           "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":34,"column":14}}
{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":18}}
{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":18}}
{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":22,"column":7}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":17,"column":12}}
{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":36,"column":5,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Normal.kt"}}
{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Quirks.kt"}}
{"id":"diagnostics-Normal",       "tool":"ide_diagnostics",          "params":{"file":"src/main/kotlin/demo/Normal.kt"}}
{"id":"diagnostics-Quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/main/kotlin/demo/Quirks.kt"}}
```

- [ ] **Step 5: Open in IntelliJ, bless, verify, commit**

```bash
# Manually: open live-test/kotlin in IntelliJ as a Gradle project. Wait for sync + indexing.
live-test/run.sh --language kotlin --bless
live-test/run.sh --language kotlin
git add live-test/kotlin
git commit -m "feat(live-test): kotlin fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 10: JavaScript fixture

**Files:**
- Create: `live-test/javascript/package.json`
- Create: `live-test/javascript/src/normal.js`
- Create: `live-test/javascript/src/quirks.js`
- Create: `live-test/javascript/input.jsonl`
- Create: `live-test/javascript/expected.jsonl` (via `--bless`)

- [ ] **Step 1: Project skeleton**

`live-test/javascript/package.json`:

```json
{
  "name": "live-test-javascript",
  "version": "0.1.0",
  "private": true,
  "type": "commonjs"
}
```

```bash
mkdir -p live-test/javascript/src
```

- [ ] **Step 2: `live-test/javascript/src/normal.js`**

```javascript
'use strict';

class Drawable {
    draw() { throw new Error('not implemented'); }
}

class Shape {
    area() { throw new Error('abstract'); }
    describe() { return `${this.constructor.name} with area ${this.area()}`; }
}

class Circle extends Shape {
    constructor(radius) {
        super();
        this.radius = radius;
    }
    area() { return 3.14159 * this.radius * this.radius; }
    draw() { return `circle r=${this.radius}`; }
}

class Rectangle extends Shape {
    constructor(width, height) {
        super();
        this.width = width;
        this.height = height;
    }
    area() { return this.width * this.height; }
    draw() { return `rect ${this.width}x${this.height}`; }
}

class Square extends Rectangle {
    constructor(side) {
        super(side, side);
    }
}

class ShapeCollection {
    constructor() {
        this.shapes = [];
    }
    add(shape) { this.shapes.push(shape); }
    totalArea() {
        let sum = 0;
        for (const s of this.shapes) sum += s.area();
        return sum;
    }
    largest() {
        let best = null;
        for (const s of this.shapes) {
            if (best === null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

function makeDefaultShapes() {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}

module.exports = { Drawable, Shape, Circle, Rectangle, Square, ShapeCollection, makeDefaultShapes };
```

- [ ] **Step 3: `live-test/javascript/src/quirks.js`**

Ported from `~/dev/sink-enum/testcases/javascript/src/quirks.js`. Drop SINK comments, replace `eval`, `Function`, `cp.exec` etc. with `Number.parseInt`, `String.toUpperCase`.

```javascript
'use strict';

// Name rebinding
function qRebind(x) {
    const fn = Number.parseInt;
    return fn(x, 10);
}

// Computed property access
function qComputed(name, x) {
    return Number[name](x, 10);
}

// Object literal dispatch
function qObjLit(x) {
    return ({ parse: Number.parseInt }).parse(x, 10);
}

// Conditional expression
function qCond(flag, x) {
    return (flag ? Number.parseInt : Number.parseFloat)(x, 10);
}

// IIFE returning sink
function qReturned(x) {
    return (() => Number.parseInt)()(x, 10);
}

// Array-indexed dispatch
function qArrayIdx(x) {
    return [Number.parseInt, Number.parseFloat][0](x, 10);
}

// Destructured rebind
function qDestructured(x) {
    const { parseInt: p } = Number;
    return p(x, 10);
}

// Spread/rest unpacking
function qSpread(x) {
    const [fn] = [Number.parseInt];
    return fn(x, 10);
}

// bind/call/apply
function qBind(x) {
    return Number.parseInt.call(null, x, 10);
}

// Higher-order forEach
function qForEach(x) {
    const out = [];
    [Number.parseInt].forEach((fn) => out.push(fn(x, 10)));
    return out[0];
}

// Promise chain
function qPromise(x) {
    return Promise.resolve(Number.parseInt).then((fn) => fn(x, 10));
}

// async/await wrapping
async function qAwait(x) {
    const fn = await (async () => Number.parseInt)();
    return fn(x, 10);
}

// Optional chaining
function qOpt(x) {
    return Number?.parseInt(x, 10);
}

// Nullish-coalesced sink
function qNullish(x) {
    return (Number.parseInt ?? (() => 0))(x, 10);
}

// Re-export proxy
const proxy = { parse: Number.parseInt };
function qProxy(x) {
    return proxy.parse(x, 10);
}

module.exports = {
    qRebind, qComputed, qObjLit, qCond, qReturned, qArrayIdx,
    qDestructured, qSpread, qBind, qForEach, qPromise, qAwait,
    qOpt, qNullish, qProxy,
};
```

- [ ] **Step 4: `live-test/javascript/input.jsonl`**

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":17,"column":5}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":27,"column":5}}
{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":59,"column":10}}
{"id":"refs-area-from-totalArea", "tool":"ide_find_references",      "params":{"file":"src/normal.js","line":47,"column":42}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/normal.js","line":42,"column":14}}
{"id":"refs-shape-class",         "tool":"ide_find_references",      "params":{"file":"src/normal.js","line":7,"column":7}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":8,"column":5}}
{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":17,"column":5}}
{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":27,"column":5}}
{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":31,"column":7}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":21,"column":7}}
{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":7,"column":7}}
{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":59,"column":10,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":8,"column":5,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Rectangle",     "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-qRebind",      "tool":"ide_find_symbol",          "params":{"query":"qRebind"}}
{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.js"}}
{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.js"}}
{"id":"diagnostics-normal",       "tool":"ide_diagnostics",          "params":{"file":"src/normal.js"}}
{"id":"diagnostics-quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/quirks.js"}}
```

- [ ] **Step 5: Open in WebStorm, bless, verify, commit**

```bash
# Manually: open live-test/javascript in WebStorm. Wait for indexing.
live-test/run.sh --language javascript --bless
live-test/run.sh --language javascript
git add live-test/javascript
git commit -m "feat(live-test): javascript fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 11: TypeScript fixture

**Files:**
- Create: `live-test/typescript/package.json`
- Create: `live-test/typescript/tsconfig.json`
- Create: `live-test/typescript/src/normal.ts`
- Create: `live-test/typescript/src/quirks.ts`
- Create: `live-test/typescript/input.jsonl`
- Create: `live-test/typescript/expected.jsonl` (via `--bless`)

- [ ] **Step 1: Project skeleton**

`live-test/typescript/package.json`:

```json
{
  "name": "live-test-typescript",
  "version": "0.1.0",
  "private": true,
  "devDependencies": {
    "typescript": "^5.4.0"
  }
}
```

`live-test/typescript/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "./dist"
  },
  "include": ["src/**/*"]
}
```

```bash
mkdir -p live-test/typescript/src
```

- [ ] **Step 2: `live-test/typescript/src/normal.ts`**

```typescript
export interface Drawable {
    draw(): string;
}

export abstract class Shape {
    abstract area(): number;

    describe(): string {
        return `${this.constructor.name} with area ${this.area()}`;
    }
}

export class Circle extends Shape implements Drawable {
    constructor(public readonly radius: number) {
        super();
    }
    area(): number { return 3.14159 * this.radius * this.radius; }
    draw(): string { return `circle r=${this.radius}`; }
}

export class Rectangle extends Shape implements Drawable {
    constructor(public readonly width: number, public readonly height: number) {
        super();
    }
    area(): number { return this.width * this.height; }
    draw(): string { return `rect ${this.width}x${this.height}`; }
}

export class Square extends Rectangle {
    constructor(side: number) {
        super(side, side);
    }
}

export class ShapeCollection {
    readonly shapes: Shape[] = [];

    add(shape: Shape): void { this.shapes.push(shape); }

    totalArea(): number {
        let sum = 0;
        for (const s of this.shapes) sum += s.area();
        return sum;
    }

    largest(): Shape | null {
        let best: Shape | null = null;
        for (const s of this.shapes) {
            if (best === null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

export function makeDefaultShapes(): Shape[] {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}
```

- [ ] **Step 3: `live-test/typescript/src/quirks.ts`**

Ported from `~/dev/sink-enum/testcases/typescript/src/ts_quirks.ts`. Drop SINK comments, swap security target functions for neutral ones.

```typescript
export function qLambda(x: string): number {
    const fn: (s: string) => number = (s) => Number.parseInt(s, 10);
    return fn(x);
}

export function qFunctionRef(x: string): number {
    const fn = Number.parseInt;
    return fn(x, 10);
}

export function qGenericLambda<T extends string>(x: T): number {
    const fn = (s: T): number => Number.parseInt(s, 10);
    return fn(x);
}

export function qConditionalType<T extends "int" | "float">(mode: T, x: string): number {
    type Fn = T extends "int" ? typeof Number.parseInt : typeof Number.parseFloat;
    const fn = (mode === "int" ? Number.parseInt : Number.parseFloat) as Fn;
    return fn(x, 10);
}

export function qDispatchMap(key: string, x: string): number {
    const dispatch: Record<string, (s: string) => number> = {
        int: (s) => Number.parseInt(s, 10),
        abs: (s) => Math.abs(Number.parseInt(s, 10)),
    };
    return dispatch[key](x);
}

export function qOptional(x?: string): number {
    return x?.length ? Number.parseInt(x, 10) : 0;
}

export function qNonNullAssertion(x: string | undefined): number {
    return Number.parseInt(x!, 10);
}

export function qAsCast(x: unknown): number {
    return Number.parseInt(x as string, 10);
}

export interface Coercer { coerce(x: string): number; }

export const intCoercer: Coercer = {
    coerce(x: string) { return Number.parseInt(x, 10); }
};

export function qInterfaceDispatch(c: Coercer, x: string): number {
    return c.coerce(x);
}

export class TypedCoercer<T extends string> {
    coerce(x: T): number { return Number.parseInt(x, 10); }
}

export function qGenericClass(x: string): number {
    return new TypedCoercer<string>().coerce(x);
}

export type Coerce = (s: string) => number;

export const aliasedCoerce: Coerce = (s) => Number.parseInt(s, 10);

export function qTypeAlias(x: string): number {
    return aliasedCoerce(x);
}
```

- [ ] **Step 4: `live-test/typescript/input.jsonl`**

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":15,"column":5}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":23,"column":5}}
{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":51,"column":17}}
{"id":"refs-area-from-totalArea", "tool":"ide_find_references",      "params":{"file":"src/normal.ts","line":40,"column":34}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/normal.ts","line":35,"column":14}}
{"id":"refs-shape-class",         "tool":"ide_find_references",      "params":{"file":"src/normal.ts","line":5,"column":23}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":6,"column":14}}
{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":2,"column":5}}
{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.ts","line":31,"column":18}}
{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":15,"column":5}}
{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":23,"column":5}}
{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":27,"column":14}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":19,"column":14}}
{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":5,"column":23}}
{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":51,"column":17,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":6,"column":14,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-qLambda",      "tool":"ide_find_symbol",          "params":{"query":"qLambda"}}
{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.ts"}}
{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.ts"}}
{"id":"diagnostics-normal",       "tool":"ide_diagnostics",          "params":{"file":"src/normal.ts"}}
{"id":"diagnostics-quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/quirks.ts"}}
```

- [ ] **Step 5: Open in WebStorm, bless, verify, commit**

```bash
# Manually: open live-test/typescript in WebStorm. Wait for indexing.
live-test/run.sh --language typescript --bless
live-test/run.sh --language typescript
git add live-test/typescript
git commit -m "feat(live-test): typescript fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 12: Go fixture

**Files:**
- Create: `live-test/go/go.mod`
- Create: `live-test/go/normal.go`
- Create: `live-test/go/quirks.go`
- Create: `live-test/go/input.jsonl`
- Create: `live-test/go/expected.jsonl` (via `--bless`)

Note: Go's plugin handler does **not** support `find_implementations`, `find_super_methods`, or `file_structure` (per CLAUDE.md). Skip those tools in `input.jsonl`.

- [ ] **Step 1: Project skeleton**

`live-test/go/go.mod`:

```
module live-test/go

go 1.21
```

- [ ] **Step 2: `live-test/go/normal.go`**

Go has no inheritance; we use interface embedding and struct composition.

```go
package main

import "fmt"

type Drawable interface {
	Draw() string
}

type Shape interface {
	Area() float64
	Describe() string
}

type baseShape struct{}

func (b baseShape) Describe() string { return "shape with unknown area" }

type Circle struct {
	baseShape
	Radius float64
}

func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
func (c Circle) Describe() string {
	return fmt.Sprintf("Circle with area %f", c.Area())
}
func (c Circle) Draw() string { return fmt.Sprintf("circle r=%f", c.Radius) }

type Rectangle struct {
	baseShape
	Width, Height float64
}

func (r Rectangle) Area() float64 { return r.Width * r.Height }
func (r Rectangle) Describe() string {
	return fmt.Sprintf("Rectangle with area %f", r.Area())
}
func (r Rectangle) Draw() string { return fmt.Sprintf("rect %fx%f", r.Width, r.Height) }

type Square struct{ Rectangle }

func NewSquare(side float64) Square {
	return Square{Rectangle: Rectangle{Width: side, Height: side}}
}

type ShapeCollection struct {
	Shapes []Shape
}

func (sc *ShapeCollection) Add(s Shape) {
	sc.Shapes = append(sc.Shapes, s)
}

func (sc *ShapeCollection) TotalArea() float64 {
	sum := 0.0
	for _, s := range sc.Shapes {
		sum += s.Area()
	}
	return sum
}

func (sc *ShapeCollection) Largest() Shape {
	var best Shape
	for _, s := range sc.Shapes {
		if best == nil || s.Area() > best.Area() {
			best = s
		}
	}
	return best
}

func MakeDefaultShapes() []Shape {
	return []Shape{
		Circle{Radius: 1.0},
		Rectangle{Width: 2.0, Height: 3.0},
		NewSquare(4.0),
	}
}
```

- [ ] **Step 3: `live-test/go/quirks.go`**

Ported from `~/dev/sink-enum/testcases/go/quirks.go`. Drop SINK comments, swap security target functions (exec.Command, os.Open, etc.) for neutral ones (strconv.Atoi, strings.ToUpper).

```go
package main

import (
	"fmt"
	"strconv"
	"strings"
)

// Function variable
func qFnVar(x string) int {
	fn := strconv.Atoi
	v, _ := fn(x)
	return v
}

// Closure
func qClosure(x string) int {
	coerce := func(s string) int {
		v, _ := strconv.Atoi(s)
		return v
	}
	return coerce(x)
}

// Map of functions
func qMapDispatch(key, x string) int {
	dispatch := map[string]func(string) int{
		"int": func(s string) int {
			v, _ := strconv.Atoi(s)
			return v
		},
		"len": func(s string) int { return len(s) },
	}
	return dispatch[key](x)
}

// Slice of functions
func qSliceIdx(x string) int {
	fns := []func(string) int{
		func(s string) int {
			v, _ := strconv.Atoi(s)
			return v
		},
		func(s string) int { return len(s) },
	}
	return fns[0](x)
}

// Interface dispatch
type Coercer interface {
	Coerce(s string) int
}

type IntCoercer struct{}

func (IntCoercer) Coerce(s string) int {
	v, _ := strconv.Atoi(s)
	return v
}

type LenCoercer struct{}

func (LenCoercer) Coerce(s string) int { return len(s) }

func qInterfaceDispatch(c Coercer, x string) int {
	return c.Coerce(x)
}

// Goroutine + channel
func qGoroutine(x string) int {
	ch := make(chan int, 1)
	go func() {
		v, _ := strconv.Atoi(x)
		ch <- v
	}()
	return <-ch
}

// Defer
func qDefer(x string) (out int) {
	defer func() {
		v, _ := strconv.Atoi(x)
		out = v
	}()
	return 0
}

// Method value
func qMethodValue(x string) int {
	c := IntCoercer{}
	fn := c.Coerce
	return fn(x)
}

// Method expression
func qMethodExpression(x string) int {
	fn := IntCoercer.Coerce
	return fn(IntCoercer{}, x)
}

// Variadic
func qVariadic(xs ...string) int {
	if len(xs) == 0 {
		return 0
	}
	v, _ := strconv.Atoi(xs[0])
	return v
}

// Type assertion
func qTypeAssertion(x interface{}) int {
	s := x.(string)
	v, _ := strconv.Atoi(s)
	return v
}

// Type switch
func qTypeSwitch(x interface{}) int {
	switch s := x.(type) {
	case string:
		v, _ := strconv.Atoi(s)
		return v
	default:
		return 0
	}
}

// Naked print to keep package "used"
func qPrintToUpper(x string) {
	fmt.Println(strings.ToUpper(x))
}
```

- [ ] **Step 4: `live-test/go/input.jsonl`**

(Tools omitted: `ide_find_implementations`, `ide_find_super_methods`, `ide_file_structure` — Go is unsupported.)

```jsonl
{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":24,"column":18}}
{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":35,"column":18}}
{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":68,"column":6}}
{"id":"refs-area-from-totalArea","tool":"ide_find_references", "params":{"file":"normal.go","line":51,"column":11}}
{"id":"refs-shapes-field",       "tool":"ide_find_references", "params":{"file":"normal.go","line":47,"column":2}}
{"id":"refs-shape-iface",        "tool":"ide_find_references", "params":{"file":"normal.go","line":9,"column":6}}
{"id":"type-hier-Circle",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":17,"column":6}}
{"id":"type-hier-Rectangle",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":29,"column":6}}
{"id":"type-hier-Square",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":40,"column":6}}
{"id":"call-hier-MakeDefault",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":68,"column":6,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":24,"column":18,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",        "tool":"ide_find_class",      "params":{"query":"Shape"}}
{"id":"find-class-Rectangle",    "tool":"ide_find_class",      "params":{"query":"Rectangle"}}
{"id":"find-symbol-Area",        "tool":"ide_find_symbol",     "params":{"query":"Area"}}
{"id":"find-symbol-qFnVar",      "tool":"ide_find_symbol",     "params":{"query":"qFnVar"}}
{"id":"diagnostics-normal",      "tool":"ide_diagnostics",     "params":{"file":"normal.go"}}
{"id":"diagnostics-quirks",      "tool":"ide_diagnostics",     "params":{"file":"quirks.go"}}
```

- [ ] **Step 5: Open in GoLand, bless, verify, commit**

```bash
# Manually: open live-test/go in GoLand. Wait for indexing (Go SDK detection).
live-test/run.sh --language go --bless
live-test/run.sh --language go
git add live-test/go
git commit -m "feat(live-test): go fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 13: PHP fixture

**Files:**
- Create: `live-test/php/composer.json`
- Create: `live-test/php/src/Normal.php`
- Create: `live-test/php/src/Quirks.php`
- Create: `live-test/php/input.jsonl`
- Create: `live-test/php/expected.jsonl` (via `--bless`)

Note: PHP supports `find_implementations` and `find_super_methods` but **not** `file_structure`. Skip `file_structure` for PHP.

- [ ] **Step 1: Project skeleton**

`live-test/php/composer.json`:

```json
{
    "name": "live-test/php",
    "type": "library",
    "autoload": {
        "psr-4": {
            "Demo\\": "src/"
        }
    }
}
```

```bash
mkdir -p live-test/php/src
```

- [ ] **Step 2: `live-test/php/src/Normal.php`**

```php
<?php
namespace Demo;

interface Drawable {
    public function draw(): string;
}

abstract class Shape {
    abstract public function area(): float;

    public function describe(): string {
        return get_class($this) . " with area " . $this->area();
    }
}

class Circle extends Shape implements Drawable {
    public function __construct(public readonly float $radius) {}

    public function area(): float {
        return 3.14159 * $this->radius * $this->radius;
    }

    public function draw(): string {
        return "circle r={$this->radius}";
    }
}

class Rectangle extends Shape implements Drawable {
    public function __construct(public readonly float $width, public readonly float $height) {}

    public function area(): float {
        return $this->width * $this->height;
    }

    public function draw(): string {
        return "rect {$this->width}x{$this->height}";
    }
}

class Square extends Rectangle {
    public function __construct(float $side) {
        parent::__construct($side, $side);
    }
}

class ShapeCollection {
    /** @var Shape[] */
    public array $shapes = [];

    public function add(Shape $shape): void {
        $this->shapes[] = $shape;
    }

    public function totalArea(): float {
        $sum = 0.0;
        foreach ($this->shapes as $s) {
            $sum += $s->area();
        }
        return $sum;
    }

    public function largest(): ?Shape {
        $best = null;
        foreach ($this->shapes as $s) {
            if ($best === null || $s->area() > $best->area()) {
                $best = $s;
            }
        }
        return $best;
    }
}

function makeDefaultShapes(): array {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}
```

- [ ] **Step 3: `live-test/php/src/Quirks.php`**

Ported from `~/dev/sink-enum/testcases/php/quirks.php`. Drop SINK comments, swap security calls (eval, exec, file_get_contents, etc.) for neutral ones (intval, strtoupper, strlen).

```php
<?php
namespace Demo;

class Quirks {

    public static function qNameRebind(string $x): int {
        $fn = 'intval';
        return $fn($x);
    }

    public static function qVariableFunction(string $x): int {
        $fname = 'intval';
        return $fname($x);
    }

    public static function qClosure(string $x): int {
        $coerce = function (string $s): int { return intval($s); };
        return $coerce($x);
    }

    public static function qArrowFn(string $x): int {
        $coerce = fn(string $s): int => intval($s);
        return $coerce($x);
    }

    public static function qArrayDispatch(string $key, string $x): int {
        $dispatch = ['int' => 'intval', 'len' => 'strlen'];
        $fn = $dispatch[$key];
        return $fn($x);
    }

    public static function qCallableArray(string $x): int {
        $callable = [self::class, 'qNameRebind'];
        return call_user_func($callable, $x);
    }

    public static function qCallUserFunc(string $x): int {
        return call_user_func('intval', $x);
    }

    public static function qStaticMethodVariable(string $x): int {
        $cls = self::class;
        return $cls::qNameRebind($x);
    }

    public static function qFromCallable(string $x): int {
        $coerce = \Closure::fromCallable('intval');
        return $coerce($x);
    }

    public static function qTernary(bool $flag, string $x): int {
        $fn = $flag ? 'intval' : 'strlen';
        return $fn($x);
    }

    public static function qNullCoalesce(string $x): int {
        $fn = null ?? 'intval';
        return $fn($x);
    }

    public static function qMatch(string $mode, string $x): int {
        $fn = match ($mode) {
            'int' => fn($s) => intval($s),
            'len' => 'strlen',
            default => fn($s) => 0,
        };
        return $fn($x);
    }
}

interface Coercer {
    public function coerce(string $x): int;
}

class IntCoercer implements Coercer {
    public function coerce(string $x): int { return intval($x); }
}

class LenCoercer implements Coercer {
    public function coerce(string $x): int { return strlen($x); }
}
```

- [ ] **Step 4: `live-test/php/input.jsonl`**

(Tool omitted: `ide_file_structure` — PHP unsupported.)

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":21,"column":21}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":33,"column":21}}
{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":75,"column":10}}
{"id":"refs-area-from-totalArea", "tool":"ide_find_references",      "params":{"file":"src/Normal.php","line":62,"column":24}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/Normal.php","line":54,"column":19}}
{"id":"refs-shape-class",         "tool":"ide_find_references",      "params":{"file":"src/Normal.php","line":9,"column":16}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":10,"column":29}}
{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":5,"column":21}}
{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":80,"column":11}}
{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":21,"column":21}}
{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":33,"column":21}}
{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":42,"column":7}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":29,"column":7}}
{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":9,"column":16}}
{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":75,"column":10,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":10,"column":29,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-qNameRebind",  "tool":"ide_find_symbol",          "params":{"query":"qNameRebind"}}
{"id":"diagnostics-normal",       "tool":"ide_diagnostics",          "params":{"file":"src/Normal.php"}}
{"id":"diagnostics-quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/Quirks.php"}}
```

- [ ] **Step 5: Open in PhpStorm, bless, verify, commit**

```bash
# Manually: open live-test/php in PhpStorm. Wait for indexing.
live-test/run.sh --language php --bless
live-test/run.sh --language php
git add live-test/php
git commit -m "feat(live-test): php fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 14: Rust fixture

**Files:**
- Create: `live-test/rust/Cargo.toml`
- Create: `live-test/rust/src/lib.rs`
- Create: `live-test/rust/src/normal.rs`
- Create: `live-test/rust/src/quirks.rs`
- Create: `live-test/rust/input.jsonl`
- Create: `live-test/rust/expected.jsonl` (via `--bless`)

Note: Rust supports `find_implementations` but **not** `find_super_methods` or `file_structure`. Skip those.

- [ ] **Step 1: Project skeleton**

`live-test/rust/Cargo.toml`:

```toml
[package]
name = "live-test-rust"
version = "0.1.0"
edition = "2021"

[lib]
path = "src/lib.rs"
```

```bash
mkdir -p live-test/rust/src
```

`live-test/rust/src/lib.rs`:

```rust
pub mod normal;
pub mod quirks;
```

- [ ] **Step 2: `live-test/rust/src/normal.rs`**

Rust uses traits instead of inheritance.

```rust
pub trait Drawable {
    fn draw(&self) -> String;
}

pub trait Shape {
    fn area(&self) -> f64;

    fn describe(&self) -> String {
        format!("Shape with area {}", self.area())
    }
}

pub struct Circle {
    pub radius: f64,
}

impl Shape for Circle {
    fn area(&self) -> f64 {
        3.14159 * self.radius * self.radius
    }

    fn describe(&self) -> String {
        format!("Circle with area {}", self.area())
    }
}

impl Drawable for Circle {
    fn draw(&self) -> String {
        format!("circle r={}", self.radius)
    }
}

pub struct Rectangle {
    pub width: f64,
    pub height: f64,
}

impl Shape for Rectangle {
    fn area(&self) -> f64 {
        self.width * self.height
    }

    fn describe(&self) -> String {
        format!("Rectangle with area {}", self.area())
    }
}

impl Drawable for Rectangle {
    fn draw(&self) -> String {
        format!("rect {}x{}", self.width, self.height)
    }
}

pub struct Square {
    inner: Rectangle,
}

impl Square {
    pub fn new(side: f64) -> Self {
        Square { inner: Rectangle { width: side, height: side } }
    }
}

impl Shape for Square {
    fn area(&self) -> f64 {
        self.inner.area()
    }
}

pub struct ShapeCollection {
    pub shapes: Vec<Box<dyn Shape>>,
}

impl ShapeCollection {
    pub fn new() -> Self {
        ShapeCollection { shapes: Vec::new() }
    }

    pub fn add(&mut self, shape: Box<dyn Shape>) {
        self.shapes.push(shape);
    }

    pub fn total_area(&self) -> f64 {
        self.shapes.iter().map(|s| s.area()).sum()
    }

    pub fn largest(&self) -> Option<&Box<dyn Shape>> {
        self.shapes.iter().max_by(|a, b| {
            a.area().partial_cmp(&b.area()).unwrap()
        })
    }
}

pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
    vec![
        Box::new(Circle { radius: 1.0 }),
        Box::new(Rectangle { width: 2.0, height: 3.0 }),
        Box::new(Square::new(4.0)),
    ]
}
```

- [ ] **Step 3: `live-test/rust/src/quirks.rs`**

Authored fresh (no Rust quirks file in sink-enum). Covers Rust-specific patterns: closures, trait objects, enum dispatch, `match`, iterator chains, `impl Trait`, generics.

```rust
pub fn q_closure(x: &str) -> i32 {
    let coerce = |s: &str| s.parse::<i32>().unwrap_or(0);
    coerce(x)
}

pub fn q_fn_pointer(x: &str) -> i32 {
    let coerce: fn(&str) -> i32 = parse_or_zero;
    coerce(x)
}

fn parse_or_zero(s: &str) -> i32 {
    s.parse().unwrap_or(0)
}

pub fn q_box_dyn_fn(x: &str) -> i32 {
    let coerce: Box<dyn Fn(&str) -> i32> = Box::new(|s| s.parse().unwrap_or(0));
    coerce(x)
}

pub fn q_match_dispatch(mode: &str, x: &str) -> i32 {
    match mode {
        "int" => x.parse().unwrap_or(0),
        "len" => x.len() as i32,
        _ => 0,
    }
}

pub trait Coercer {
    fn coerce(&self, x: &str) -> i32;
}

pub struct IntCoercer;
impl Coercer for IntCoercer {
    fn coerce(&self, x: &str) -> i32 {
        x.parse().unwrap_or(0)
    }
}

pub struct LenCoercer;
impl Coercer for LenCoercer {
    fn coerce(&self, x: &str) -> i32 {
        x.len() as i32
    }
}

pub fn q_trait_object(c: &dyn Coercer, x: &str) -> i32 {
    c.coerce(x)
}

pub fn q_generic_bound<C: Coercer>(c: &C, x: &str) -> i32 {
    c.coerce(x)
}

pub fn q_impl_trait_arg(c: impl Coercer, x: &str) -> i32 {
    c.coerce(x)
}

pub fn q_impl_trait_return(use_int: bool) -> Box<dyn Coercer> {
    if use_int {
        Box::new(IntCoercer)
    } else {
        Box::new(LenCoercer)
    }
}

pub enum CoerceMode {
    Int,
    Len,
}

impl CoerceMode {
    pub fn apply(&self, x: &str) -> i32 {
        match self {
            CoerceMode::Int => x.parse().unwrap_or(0),
            CoerceMode::Len => x.len() as i32,
        }
    }
}

pub fn q_enum_dispatch(mode: CoerceMode, x: &str) -> i32 {
    mode.apply(x)
}

pub fn q_iter_map(xs: &[&str]) -> Vec<i32> {
    xs.iter().map(|s| s.parse().unwrap_or(0)).collect()
}

pub fn q_iter_filter_map(xs: &[&str]) -> Vec<i32> {
    xs.iter().filter_map(|s| s.parse().ok()).collect()
}

pub fn q_iter_fold(xs: &[&str]) -> i32 {
    xs.iter().fold(0, |acc, s| acc + s.parse::<i32>().unwrap_or(0))
}

pub fn q_question_mark(x: &str) -> Result<i32, std::num::ParseIntError> {
    let v: i32 = x.parse()?;
    Ok(v)
}

pub fn q_if_let(x: Option<&str>) -> i32 {
    if let Some(s) = x {
        s.parse().unwrap_or(0)
    } else {
        0
    }
}
```

- [ ] **Step 4: `live-test/rust/input.jsonl`**

(Tools omitted: `ide_find_super_methods`, `ide_file_structure` — Rust unsupported.)

```jsonl
{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":11,"column":8}}
{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":31,"column":8}}
{"id":"def-make-default-shapes",  "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":86,"column":8}}
{"id":"refs-area-from-total",     "tool":"ide_find_references",      "params":{"file":"src/normal.rs","line":76,"column":40}}
{"id":"refs-shapes-field",        "tool":"ide_find_references",      "params":{"file":"src/normal.rs","line":68,"column":9}}
{"id":"refs-shape-trait",         "tool":"ide_find_references",      "params":{"file":"src/normal.rs","line":5,"column":11}}
{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
{"id":"type-hier-circle",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":9,"column":12}}
{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":29,"column":12}}
{"id":"type-hier-shape-trait",    "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":5,"column":11}}
{"id":"call-hier-make-default",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":86,"column":8,"direction":"callees","maxDepth":2}}
{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":6,"column":8,"direction":"callers","maxDepth":2}}
{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
{"id":"find-symbol-q_closure",    "tool":"ide_find_symbol",          "params":{"query":"q_closure"}}
{"id":"diagnostics-normal",       "tool":"ide_diagnostics",          "params":{"file":"src/normal.rs"}}
{"id":"diagnostics-quirks",       "tool":"ide_diagnostics",          "params":{"file":"src/quirks.rs"}}
```

- [ ] **Step 5: Open in RustRover, bless, verify, commit**

```bash
# Manually: open live-test/rust in RustRover. Run `cargo check` to populate
# rust-analyzer cache, wait for indexing.
live-test/run.sh --language rust --bless
live-test/run.sh --language rust
git add live-test/rust
git commit -m "feat(live-test): rust fixture with normal + quirks sources, blessed snapshots"
```

---

## Task 15: Final docs polish

**Files:**
- Modify: `live-test/README.md`
- Modify: `CHANGELOG.md`
- Modify: `gradle.properties`

- [ ] **Step 1: Expand `live-test/README.md` with the workflow**

Replace the existing README with a longer version that documents:
- The exact commands for first-time setup (build plugin, install in IDEs).
- The version-bump regression workflow (build → install → run → bless if intentional).
- A troubleshooting section (precheck failed, port conflict, dumb mode).
- A note on why no CI: requires running IDEs, deferred.

```markdown
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
```

- [ ] **Step 2: Add CHANGELOG entry**

In `CHANGELOG.md`, under `## [Unreleased]`, add a new section:

```markdown
### Added
- **Live MCP test harness** in `live-test/`. Snapshot-based regression
  suite covering 8 languages and 10 navigation/intelligence tools via a
  single bash + curl + jq runner. Run after every version bump:
  `./live-test/run.sh` (or `--bless` to update snapshots). See
  `live-test/README.md` for setup.
```

(Keep this in `Added` regardless of whether other sections exist; merge
gracefully if the section already has entries.)

- [ ] **Step 3: Bump plugin version**

Per `CLAUDE.md` PR checklist: this PR adds a new feature, so it's a
**minor** version bump. Edit `gradle.properties`:

```diff
-pluginVersion = 5.0.0
+pluginVersion = 5.1.0
```

- [ ] **Step 4: Commit**

```bash
git add live-test/README.md CHANGELOG.md gradle.properties
git commit -m "docs: document live MCP test harness workflow + 5.1.0 version bump"
```

- [ ] **Step 5: Smoke test the full suite**

```bash
# With every IDE/fixture set up:
./live-test/run.sh
# Expected: ALL: N passed, 0 failed
```

If any language was not open in its IDE during this final run, the precheck
will skip it with a SKIPPED marker. That's acceptable for a final check —
the developer's bless step on each language is what locked in the snapshot.

---

## Plan self-review

- **Spec coverage:** Every spec section maps to at least one task —
  repo layout (Tasks 1, 7–14), tool matrix (Tasks 7–14, with per-language
  exclusions), source-file strategy (Tasks 7–14, normal + quirks per
  language), input/expected format (Task 5 implements diff and bless),
  normalization (Task 4 implements jq filter + sed), runner CLI
  (Tasks 2–6), workflow + prerequisites + version bump (Task 15).
- **Volume sanity:** ~20–30 input.jsonl entries per language × 8 languages
  ≈ 160–240 expected lines. The spec's "~500" was an upper bound; the
  authored entry counts in this plan are tighter and easier to review.
- **No placeholders:** Every code block contains the actual content the
  engineer needs. The exception is line/column anchors in `input.jsonl`,
  which depend on whitespace and may need ±1 adjustment after authoring;
  the workflow ("bless then inspect, fix anchors that resolved nothing,
  re-bless") covers this.
- **Type consistency:** Method names match across files —
  `makeDefaultShapes` / `make_default_shapes` / `MakeDefaultShapes` per
  language idiom; `area()` everywhere; `total_area` / `totalArea` per
  idiom. The `ShapeCollection.shapes` field is the same name in every
  language. The trait/interface name `Drawable` is consistent.
- **External dependencies:** None of the fixtures pull third-party
  packages (no `pip install`, no `npm install`, no `composer install`)
  to keep first-time setup short. The TypeScript fixture lists
  `typescript` as a devDependency for IDE schema awareness, but the
  harness does not require it to be installed.
