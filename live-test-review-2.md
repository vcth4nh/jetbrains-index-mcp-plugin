Reading additional input from stdin...
OpenAI Codex v0.131.0
--------
workdir: /home/ubuntu/dev/jetbrains-index-mcp-plugin
model: gpt-5.5
provider: openai
approval: never
sandbox: danger-full-access
reasoning effort: xhigh
reasoning summaries: none
session id: 019e4d75-6dde-70d0-886f-47278ec34323
--------
user
Adversarial review #2 of the live-test snapshot harness in /home/ubuntu/dev/jetbrains-index-mcp-plugin. Work from that dir.

Recent changes since your last review (live-test-review.md, file present in cwd) — please VERIFY these landed correctly first, then proceed to forward-looking analysis:
- run.py now matches by id, not row index. Output AND expected store `{"id": ..., "result": ...}` per line. Bless preserves untouched ids when --tool is set. Duplicate input ids fail. Orphan expected ids (no matching input) are reported and counted as failures (non-filtered runs only).
- input.jsonl files deduped: 5 rows removed (python def-Circle-constructor-call, audit-super-Circle-draw-protocol; go audit-find-class-exact-Circle; java audit-file-structure-Quirks, audit-super-CoerceMode-INT-apply).
- LIBRARY_PATH_SUBS regex set still applies path normalization (${RUST_STDLIB}, ${KOTLIN_STDLIB}, ${JDK}, ${PYCHARM_TYPESHED}, ${PYTHON_STDLIB}, ${PHP_STUBS}, ${WEBSTORM_JS_STUBS}, catch-all ${HOME}).
- Pending: a single bless that re-migrates all expected.jsonl into id-keyed format (currently in old positional format → all rows show MISSING).

Files in scope:
- live-test/run.py
- live-test/<lang>/{input,expected,output}.jsonl for lang in {java, kotlin, python, javascript, typescript, php, go, rust}
- Any test data referenced by the harness

Your task: **list every plausible reason why this test suite + harness is going to break or rot in the future**. Be merciless. Forward-looking failure modes. Be concrete — name the file, the trigger, the symptom, the blast radius.

Categories to mine (not exhaustive):
1. IDE-side changes — JetBrains version updates, plugin updates, indexing-engine behavior shifts, new fields in tool responses, deprecated tools, message text changes embedded in expected output, parameter rename/removal in tool schemas.
2. Machine / environment drift — new SDK install locations, IDE-bundled-library version bumps (kotlin-stdlib, Rust stdlib, JDK, Python typeshed, etc.) changing internal paths in ways the regex set doesn't cover. macOS/Windows hosts. Toolbox replaced by another launcher. Plugin-bundled stub paths changing structure across IDE updates. Anything with a numbered subdir (RustRover2026.1 → 2027.x) shifting under the regex.
3. Fixture drift — someone adds a line to Normal.java/Quirks.py/etc., shifting every line-anchored input.jsonl probe in that file silently. Tree-sitter-like edits (refactor rename of Shape to Form) breaking dozens of probes at once. Adding usages somewhere upstream that affects every find_usages result count downstream.
4. Snapshot semantics — what does the bless step LOCK IN that the IDE will keep changing? Look at expected.jsonl: any fields that look stable-looking but are actually IDE-implementation-dependent (e.g. `enclosingScope` content, hierarchy ordering, qualifiedName format with anonymous types, descriptors of generated members). Sort orders the runner imposes vs the IDE's defaults — does the IDE ever break ties differently?
5. Harness logic edges — what happens if input.jsonl has a row with no `id` (the new check returns 0,1 — does that interact correctly with --tool and bless?). If --bless is run with --tool, what is the orphan-detection state? What if expected.jsonl already has new-format AND old-format mixed (after partial migration)? Concurrent bless from multiple terminals? IDE going to dumb mode mid-run after the precheck passed? Cancellation/SIGINT mid-bless.
6. Test-design rot — tests that are valuable today but become noise tomorrow (e.g. exhaustive find_class queries that flag every new class added to fixtures as a 'MISSING' until re-blessed). The id-keyed format means anyone who renames an id silently MISSING-s their probe forever until re-blessed under the new id — what mitigates that?

For each finding, output: severity (CRITICAL / HIGH / MEDIUM / LOW), trigger (what change causes it), symptom (what the user sees), blast radius (1 row / 1 language / suite-wide), and one concrete mitigation idea. Cite file:line where applicable. No 'maybe consider' — say what you mean.
codex
I’ll verify the stated changes against the current files first, then I’ll review the harness and fixtures for future failure modes with concrete triggers and blast radius.
exec
/usr/bin/zsh -lc 'git status --short' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc 'pwd && rg --files live-test | sort' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
 M live-test/go/input.jsonl
 M live-test/java/input.jsonl
 M live-test/python/input.jsonl
 M live-test/run.py
?? .classpath
?? .project
?? .settings/
?? live-test-review-2.md
?? live-test-review.md
?? live-test/__pycache__/
?? logs/

 succeeded in 0ms:
/home/ubuntu/dev/jetbrains-index-mcp-plugin
live-test/README.md
live-test/__pycache__/run.cpython-314.pyc
live-test/go/embed.go
live-test/go/expected.jsonl
live-test/go/go.mod
live-test/go/input.jsonl
live-test/go/normal.go
live-test/go/quirks.go
live-test/java/expected.jsonl
live-test/java/input.jsonl
live-test/java/pom.xml
live-test/java/src/main/java/demo/Modern.java
live-test/java/src/main/java/demo/Normal.java
live-test/java/src/main/java/demo/Quirks.java
live-test/javascript/expected.jsonl
live-test/javascript/input.jsonl
live-test/javascript/package.json
live-test/javascript/src/accessors.js
live-test/javascript/src/consumer.js
live-test/javascript/src/normal.js
live-test/javascript/src/quirks.js
live-test/kotlin/build.gradle.kts
live-test/kotlin/expected.jsonl
live-test/kotlin/input.jsonl
live-test/kotlin/settings.gradle.kts
live-test/kotlin/src/main/kotlin/demo/Modern.kt
live-test/kotlin/src/main/kotlin/demo/Normal.kt
live-test/kotlin/src/main/kotlin/demo/Quirks.kt
live-test/php/composer.json
live-test/php/expected.jsonl
live-test/php/input.jsonl
live-test/php/src/Modern.php
live-test/php/src/Normal.php
live-test/php/src/Quirks.php
live-test/python/expected.jsonl
live-test/python/input.jsonl
live-test/python/pyproject.toml
live-test/python/src/__init__.py
live-test/python/src/normal.py
live-test/python/src/quirks.py
live-test/run.py
live-test/rust/Cargo.toml
live-test/rust/expected.jsonl
live-test/rust/input.jsonl
live-test/rust/src/extra.rs
live-test/rust/src/lib.rs
live-test/rust/src/normal.rs
live-test/rust/src/quirks.rs
live-test/typescript/expected.jsonl
live-test/typescript/input.jsonl
live-test/typescript/package.json
live-test/typescript/src/normal.ts
live-test/typescript/src/quirks.ts
live-test/typescript/tsconfig.json

exec
/usr/bin/zsh -lc "sed -n '1,260p' live-test/run.py" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
#!/usr/bin/env python3
"""Live MCP test harness runner.

Drives HTTP POST requests against running JetBrains IDEs and either diffs the
responses against committed expected.jsonl files (default) or rewrites them
(--bless).

Usage:
    ./run.py [--language LANG] [--tool TOOL] [--url URL] [--bless]
"""
from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

PORT_BY_LANG = {
    "python": 29172,
    "java": 29170,
    "kotlin": 29170,
    "javascript": 29173,
    "typescript": 29173,
    "go": 29174,
    "php": 29175,
    "rust": 29178,
}

DROP_FIELDS = {
    "preview", "nextCursor", "stale", "hasMore", "truncated",
    "totalCollected", "offset", "pageSize",
}

SORTABLE_ARRAYS = {
    "usages", "references", "implementations", "subtypes", "supertypes",
    "classes", "symbols", "files", "matches",
    "problems", "intentions", "buildErrors", "testResults", "hierarchy", "calls",
    "children",
}

# Replace machine-specific library / SDK / stub paths with stable tokens so
# expected.jsonl stays portable across machines, IDE installs, and runtime
# versions. Order matters — more-specific patterns first; the catch-all home
# substitution must run last.
LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
    # Rust stdlib via RustRover cache (version + 40-char git hash)
    (re.compile(
        r"/home/[^/]+/\.cache/JetBrains/RustRover[^/]+/intellij-rust/"
        r"stdlib-local-copy/[^/]+/library/"),
     "${RUST_STDLIB}/"),
    # Kotlin stdlib JAR via Gradle cache (version + hash dir)
    (re.compile(
        r"/home/[^/]+/\.gradle/caches/modules-2/files-2\.1/"
        r"org\.jetbrains\.kotlin/kotlin-stdlib/[^/]+/[0-9a-f]+/"
        r"kotlin-stdlib-[^/]+\.jar!"),
     "${KOTLIN_STDLIB}.jar!"),
    # JDK installed via Gradle JDK manager
    (re.compile(r"/home/[^/]+/\.gradle/jdks/[^!]+!"), "${JDK}!"),
    # JDK installed via mise
    (re.compile(r"/home/[^/]+/\.local/share/mise/installs/java/[^!]+!"), "${JDK}!"),
    # PyCharm typeshed stubs
    (re.compile(
        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/pycharm/plugins/"
        r"python-ce/helpers/typeshed/"),
     "${PYCHARM_TYPESHED}/"),
    # Python stdlib installed via uv
    (re.compile(
        r"/home/[^/]+/\.local/share/uv/python/cpython-[^/]+/lib/python[^/]+/"),
     "${PYTHON_STDLIB}/"),
    # PhpStorm bundled PHP stubs
    (re.compile(
        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/"
        r"php-impl/lib/php\.jar!"),
     "${PHP_STUBS}.jar!"),
    # WebStorm bundled JS library stubs
    (re.compile(
        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/webstorm/plugins/"
        r"javascript-plugin/jsLanguageServicesImpl/external/"),
     "${WEBSTORM_JS_STUBS}/"),
    # Catch-all: any remaining $HOME prefix
    (re.compile(r"/home/[^/]+/"), "${HOME}/"),
]


def _normalize_library_paths(s: str) -> str:
    for pattern, replacement in LIBRARY_PATH_SUBS:
        s = pattern.sub(replacement, s)
    return s


def _sort_key(item: Any) -> tuple:
    if isinstance(item, dict):
        return (
            item.get("file") or "",
            item.get("line") or 0,
            item.get("column") or 0,
            json.dumps(item, sort_keys=True),
        )
    return ("", 0, 0, json.dumps(item, sort_keys=True) if item is not None else "")


def normalize(obj: Any, project_root: str) -> Any:
    """Drop noisy fields, sort known result arrays, substitute project paths."""
    if isinstance(obj, dict):
        out: dict[str, Any] = {}
        for k, v in obj.items():
            if k in DROP_FIELDS:
                continue
            v = normalize(v, project_root)
            if k in SORTABLE_ARRAYS and isinstance(v, list):
                v = sorted(v, key=_sort_key)
            out[k] = v
        return out
    if isinstance(obj, list):
        return [normalize(item, project_root) for item in obj]
    if isinstance(obj, str):
        return _normalize_library_paths(obj.replace(project_root, "${PROJECT_ROOT}"))
    return obj


def post_jsonrpc(url: str, request: dict, timeout: float = 60.0) -> Any:
    """POST a JSON-RPC request, return the unwrapped tool result.

    Possible return shapes:
    - parsed JSON dict/list — happy path; the tool's payload
    - {"transport_error": "..."} — curl-level failure
    - {"jsonrpc_error": {...}} — JSON-RPC envelope-level error
    - {"tool_error_text": "..."} — text payload that wasn't valid JSON
    """
    data = json.dumps(request).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = json.loads(resp.read())
    except urllib.error.URLError as e:
        return {"transport_error": str(e.reason)}
    except (TimeoutError, ConnectionError, OSError) as e:
        return {"transport_error": f"{type(e).__name__}: {e}"}
    if "error" in raw:
        return {"jsonrpc_error": raw["error"]}
    result = raw.get("result", {}) or {}
    content = result.get("content") or []
    text = content[0].get("text", "") if content else ""
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return {"tool_error_text": text}


def build_request(input_entry: dict, project_path: str) -> dict:
    return {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": input_entry["tool"],
            "arguments": {
                **input_entry.get("params", {}),
                "project_path": project_path,
            },
        },
    }


def check_ready(url: str, project_path: str) -> str | None:
    """Return None if ready; otherwise a diagnostic string."""
    req = build_request(
        {"tool": "ide_index_status", "params": {}}, project_path
    )
    result = post_jsonrpc(url, req, timeout=5.0)
    if not isinstance(result, dict):
        return f"unexpected ready-check shape: {result!r}"
    if "transport_error" in result:
        return f"cannot reach {url}: {result['transport_error']}"
    if "tool_error_text" in result:
        return f"unexpected text response: {result['tool_error_text']!r}"
    if "jsonrpc_error" in result:
        return f"jsonrpc error: {result['jsonrpc_error']}"
    if "error" in result:
        return f"{result['error']} — {result.get('message', '')}"
    if result.get("isDumbMode") is True:
        return "project is in dumb mode (still indexing)"
    return None


def diff_lines(expected: Any, actual: Any) -> str:
    e = json.dumps(expected, indent=2, sort_keys=True).splitlines(keepends=True)
    a = json.dumps(actual, indent=2, sort_keys=True).splitlines(keepends=True)
    return "".join(
        difflib.unified_diff(e, a, fromfile="expected", tofile="actual")
    )


def _load_expected_by_id(expected_path: Path) -> dict[str, Any]:
    """Read expected.jsonl into an id → result dict.

    Each row is expected to be {"id": <str>, "result": <obj>}. Rows missing an
    `id` are skipped (legacy-format remnants will surface as MISSING during diff,
    prompting a bless).
    """
    if not expected_path.is_file():
        return {}
    out: dict[str, Any] = {}
    for line in expected_path.read_text().splitlines():
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(row, dict) and "id" in row and "result" in row:
            out[row["id"]] = row["result"]
    return out


def _serialize_row(eid: str, result: Any) -> str:
    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))


def run_language(
    lang: str,
    project_path: Path,
    url: str,
    tool_filter: str | None,
    bless: bool,
) -> tuple[int, int]:
    """Returns (passed, failed).

    Rows are matched by `id`, not by position. Output and expected both store
    `{"id": ..., "result": ...}` per line so the snapshot survives row
    insertions, reorderings, and `--tool` filtered blesses.
    """
    print(f"[{lang}] {url}")

    err = check_ready(url, str(project_path))
    if err is not None:
        print(f"  PRECHECK: {err}")
        print(f"[{lang}] SKIPPED (precheck failed)")
        return 0, 1

    input_path = project_path / "input.jsonl"
    expected_path = project_path / "expected.jsonl"
    output_path = project_path / "output.jsonl"
    inputs = [
        json.loads(line)
        for line in input_path.read_text().splitlines()
        if line.strip()
    ]

    # Detect duplicate input ids — id-keyed matching requires uniqueness.
    seen: dict[str, int] = {}
    for i, entry in enumerate(inputs):
        eid = entry.get("id")

exec
/usr/bin/zsh -lc "sed -n '1,260p' live-test-review.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
Reading additional input from stdin...
OpenAI Codex v0.131.0
--------
workdir: /home/ubuntu/dev/jetbrains-index-mcp-plugin
model: gpt-5.5
provider: openai
approval: never
sandbox: danger-full-access
reasoning effort: xhigh
reasoning summaries: none
session id: 019e4abd-b387-7ef2-a924-02de690e47af
--------
user
Adversarial review of a live-test snapshot harness for the JetBrains IDE Index MCP plugin. Work from /home/ubuntu/dev/jetbrains-index-mcp-plugin.

The harness POSTs JSON-RPC requests to running JetBrains IDEs and diffs the responses against committed snapshot files.

In scope for review:
- live-test/run.py — the harness (~290 lines). Normalizes responses (drops noisy fields, sorts known result arrays, substitutes paths), POSTs, diffs.
- live-test/<lang>/input.jsonl for lang in {java, kotlin, python, javascript, typescript, php, go, rust} — JSONL probe requests.
- live-test/<lang>/expected.jsonl — blessed snapshot outputs to diff against.
- live-test/<lang>/output.jsonl — most-recent harness output (may differ from expected because a path-normalization change is pending re-bless).

Recent change you should focus on hard: I just added LIBRARY_PATH_SUBS — a regex list in run.py that substitutes machine-specific library / SDK / IDE-stub paths in IDE responses with stable tokens (${RUST_STDLIB}, ${KOTLIN_STDLIB}, ${JDK}, ${PYCHARM_TYPESHED}, ${PYTHON_STDLIB}, ${PHP_STUBS}, ${WEBSTORM_JS_STUBS}, ${HOME}). The catch-all ${HOME}/ rule is last. 55 expected.jsonl rows currently diff against output.jsonl purely because of this rebless that hasn't happened yet — that's a known state, not a bug to find.

Adversarial focus — find bugs, blind spots, brittle assumptions:
1. run.py logic: race conditions, dead branches, incorrect normalization, the new LIBRARY_PATH_SUBS regexes (over-match / under-match, ordering, missed path families on macOS/Windows, escapes), broken sort key, bless step writing the wrong thing.
2. input.jsonl correctness across all 8 languages: are line/column pointing at the intended identifier? Ambiguous probe IDs? Duplicate IDs? Probes that don't really exercise what their ID claims?
3. expected.jsonl: outputs that look like IDE bugs rather than ground truth (empty results where there should be hits, missing supertypes, swapped kinds, etc.). Any paths in expected.jsonl that survived normalization and would still leak machine-specific state?
4. Cross-language inconsistency: same probe pattern, different result shapes between languages — flag the suspicious ones.

Cite concrete file:line. Be specific — not 'maybe consider ...'. Severity tags (BUG / SMELL / NIT) on each finding. Length: thorough but no padding.
codex
I’ll treat this as a review, so I’m going to inspect the harness and snapshots first, then validate the JSONL mechanically where useful. I’ll avoid changing files.
exec
/usr/bin/zsh -lc "nl -ba live-test/run.py | sed -n '1,360p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	#!/usr/bin/env python3
     2	"""Live MCP test harness runner.
     3	
     4	Drives HTTP POST requests against running JetBrains IDEs and either diffs the
     5	responses against committed expected.jsonl files (default) or rewrites them
     6	(--bless).
     7	
     8	Usage:
     9	    ./run.py [--language LANG] [--tool TOOL] [--url URL] [--bless]
    10	"""
    11	from __future__ import annotations
    12	
    13	import argparse
    14	import difflib
    15	import json
    16	import re
    17	import sys
    18	import urllib.error
    19	import urllib.request
    20	from pathlib import Path
    21	from typing import Any
    22	
    23	PORT_BY_LANG = {
    24	    "python": 29172,
    25	    "java": 29170,
    26	    "kotlin": 29170,
    27	    "javascript": 29173,
    28	    "typescript": 29173,
    29	    "go": 29174,
    30	    "php": 29175,
    31	    "rust": 29178,
    32	}
    33	
    34	DROP_FIELDS = {
    35	    "preview", "nextCursor", "stale", "hasMore", "truncated",
    36	    "totalCollected", "offset", "pageSize",
    37	}
    38	
    39	SORTABLE_ARRAYS = {
    40	    "usages", "references", "implementations", "subtypes", "supertypes",
    41	    "classes", "symbols", "files", "matches",
    42	    "problems", "intentions", "buildErrors", "testResults", "hierarchy", "calls",
    43	    "children",
    44	}
    45	
    46	# Replace machine-specific library / SDK / stub paths with stable tokens so
    47	# expected.jsonl stays portable across machines, IDE installs, and runtime
    48	# versions. Order matters — more-specific patterns first; the catch-all home
    49	# substitution must run last.
    50	LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
    51	    # Rust stdlib via RustRover cache (version + 40-char git hash)
    52	    (re.compile(
    53	        r"/home/[^/]+/\.cache/JetBrains/RustRover[^/]+/intellij-rust/"
    54	        r"stdlib-local-copy/[^/]+/library/"),
    55	     "${RUST_STDLIB}/"),
    56	    # Kotlin stdlib JAR via Gradle cache (version + hash dir)
    57	    (re.compile(
    58	        r"/home/[^/]+/\.gradle/caches/modules-2/files-2\.1/"
    59	        r"org\.jetbrains\.kotlin/kotlin-stdlib/[^/]+/[0-9a-f]+/"
    60	        r"kotlin-stdlib-[^/]+\.jar!"),
    61	     "${KOTLIN_STDLIB}.jar!"),
    62	    # JDK installed via Gradle JDK manager
    63	    (re.compile(r"/home/[^/]+/\.gradle/jdks/[^!]+!"), "${JDK}!"),
    64	    # JDK installed via mise
    65	    (re.compile(r"/home/[^/]+/\.local/share/mise/installs/java/[^!]+!"), "${JDK}!"),
    66	    # PyCharm typeshed stubs
    67	    (re.compile(
    68	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/pycharm/plugins/"
    69	        r"python-ce/helpers/typeshed/"),
    70	     "${PYCHARM_TYPESHED}/"),
    71	    # Python stdlib installed via uv
    72	    (re.compile(
    73	        r"/home/[^/]+/\.local/share/uv/python/cpython-[^/]+/lib/python[^/]+/"),
    74	     "${PYTHON_STDLIB}/"),
    75	    # PhpStorm bundled PHP stubs
    76	    (re.compile(
    77	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/"
    78	        r"php-impl/lib/php\.jar!"),
    79	     "${PHP_STUBS}.jar!"),
    80	    # WebStorm bundled JS library stubs
    81	    (re.compile(
    82	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/webstorm/plugins/"
    83	        r"javascript-plugin/jsLanguageServicesImpl/external/"),
    84	     "${WEBSTORM_JS_STUBS}/"),
    85	    # Catch-all: any remaining $HOME prefix
    86	    (re.compile(r"/home/[^/]+/"), "${HOME}/"),
    87	]
    88	
    89	
    90	def _normalize_library_paths(s: str) -> str:
    91	    for pattern, replacement in LIBRARY_PATH_SUBS:
    92	        s = pattern.sub(replacement, s)
    93	    return s
    94	
    95	
    96	def _sort_key(item: Any) -> tuple:
    97	    if isinstance(item, dict):
    98	        return (
    99	            item.get("file") or "",
   100	            item.get("line") or 0,
   101	            item.get("column") or 0,
   102	            json.dumps(item, sort_keys=True),
   103	        )
   104	    return ("", 0, 0, json.dumps(item, sort_keys=True) if item is not None else "")
   105	
   106	
   107	def normalize(obj: Any, project_root: str) -> Any:
   108	    """Drop noisy fields, sort known result arrays, substitute project paths."""
   109	    if isinstance(obj, dict):
   110	        out: dict[str, Any] = {}
   111	        for k, v in obj.items():
   112	            if k in DROP_FIELDS:
   113	                continue
   114	            v = normalize(v, project_root)
   115	            if k in SORTABLE_ARRAYS and isinstance(v, list):
   116	                v = sorted(v, key=_sort_key)
   117	            out[k] = v
   118	        return out
   119	    if isinstance(obj, list):
   120	        return [normalize(item, project_root) for item in obj]
   121	    if isinstance(obj, str):
   122	        return _normalize_library_paths(obj.replace(project_root, "${PROJECT_ROOT}"))
   123	    return obj
   124	
   125	
   126	def post_jsonrpc(url: str, request: dict, timeout: float = 60.0) -> Any:
   127	    """POST a JSON-RPC request, return the unwrapped tool result.
   128	
   129	    Possible return shapes:
   130	    - parsed JSON dict/list — happy path; the tool's payload
   131	    - {"transport_error": "..."} — curl-level failure
   132	    - {"jsonrpc_error": {...}} — JSON-RPC envelope-level error
   133	    - {"tool_error_text": "..."} — text payload that wasn't valid JSON
   134	    """
   135	    data = json.dumps(request).encode("utf-8")
   136	    req = urllib.request.Request(
   137	        url, data=data, headers={"Content-Type": "application/json"}, method="POST"
   138	    )
   139	    try:
   140	        with urllib.request.urlopen(req, timeout=timeout) as resp:
   141	            raw = json.loads(resp.read())
   142	    except urllib.error.URLError as e:
   143	        return {"transport_error": str(e.reason)}
   144	    except (TimeoutError, ConnectionError, OSError) as e:
   145	        return {"transport_error": f"{type(e).__name__}: {e}"}
   146	    if "error" in raw:
   147	        return {"jsonrpc_error": raw["error"]}
   148	    result = raw.get("result", {}) or {}
   149	    content = result.get("content") or []
   150	    text = content[0].get("text", "") if content else ""
   151	    try:
   152	        return json.loads(text)
   153	    except (json.JSONDecodeError, TypeError):
   154	        return {"tool_error_text": text}
   155	
   156	
   157	def build_request(input_entry: dict, project_path: str) -> dict:
   158	    return {
   159	        "jsonrpc": "2.0",
   160	        "id": 1,
   161	        "method": "tools/call",
   162	        "params": {
   163	            "name": input_entry["tool"],
   164	            "arguments": {
   165	                **input_entry.get("params", {}),
   166	                "project_path": project_path,
   167	            },
   168	        },
   169	    }
   170	
   171	
   172	def check_ready(url: str, project_path: str) -> str | None:
   173	    """Return None if ready; otherwise a diagnostic string."""
   174	    req = build_request(
   175	        {"tool": "ide_index_status", "params": {}}, project_path
   176	    )
   177	    result = post_jsonrpc(url, req, timeout=5.0)
   178	    if not isinstance(result, dict):
   179	        return f"unexpected ready-check shape: {result!r}"
   180	    if "transport_error" in result:
   181	        return f"cannot reach {url}: {result['transport_error']}"
   182	    if "tool_error_text" in result:
   183	        return f"unexpected text response: {result['tool_error_text']!r}"
   184	    if "jsonrpc_error" in result:
   185	        return f"jsonrpc error: {result['jsonrpc_error']}"
   186	    if "error" in result:
   187	        return f"{result['error']} — {result.get('message', '')}"
   188	    if result.get("isDumbMode") is True:
   189	        return "project is in dumb mode (still indexing)"
   190	    return None
   191	
   192	
   193	def diff_lines(expected: Any, actual: Any) -> str:
   194	    e = json.dumps(expected, indent=2, sort_keys=True).splitlines(keepends=True)
   195	    a = json.dumps(actual, indent=2, sort_keys=True).splitlines(keepends=True)
   196	    return "".join(
   197	        difflib.unified_diff(e, a, fromfile="expected", tofile="actual")
   198	    )
   199	
   200	
   201	def run_language(
   202	    lang: str,
   203	    project_path: Path,
   204	    url: str,
   205	    tool_filter: str | None,
   206	    bless: bool,
   207	) -> tuple[int, int]:
   208	    """Returns (passed, failed)."""
   209	    print(f"[{lang}] {url}")
   210	
   211	    err = check_ready(url, str(project_path))
   212	    if err is not None:
   213	        print(f"  PRECHECK: {err}")
   214	        print(f"[{lang}] SKIPPED (precheck failed)")
   215	        return 0, 1
   216	
   217	    input_path = project_path / "input.jsonl"
   218	    expected_path = project_path / "expected.jsonl"
   219	    output_path = project_path / "output.jsonl"
   220	    inputs = [
   221	        json.loads(line)
   222	        for line in input_path.read_text().splitlines()
   223	        if line.strip()

codex
The repo has uncommitted edits in the files the user named, so I’m treating the current working tree as the live snapshot under review. The previous review file is present but looks like a captured transcript, so I’ll use it as historical context only after verifying the actual files.
exec
/usr/bin/zsh -lc "nl -ba live-test/run.py | sed -n '200,380p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
   200	
   201	def _load_expected_by_id(expected_path: Path) -> dict[str, Any]:
   202	    """Read expected.jsonl into an id → result dict.
   203	
   204	    Each row is expected to be {"id": <str>, "result": <obj>}. Rows missing an
   205	    `id` are skipped (legacy-format remnants will surface as MISSING during diff,
   206	    prompting a bless).
   207	    """
   208	    if not expected_path.is_file():
   209	        return {}
   210	    out: dict[str, Any] = {}
   211	    for line in expected_path.read_text().splitlines():
   212	        if not line.strip():
   213	            continue
   214	        try:
   215	            row = json.loads(line)
   216	        except json.JSONDecodeError:
   217	            continue
   218	        if isinstance(row, dict) and "id" in row and "result" in row:
   219	            out[row["id"]] = row["result"]
   220	    return out
   221	
   222	
   223	def _serialize_row(eid: str, result: Any) -> str:
   224	    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))
   225	
   226	
   227	def run_language(
   228	    lang: str,
   229	    project_path: Path,
   230	    url: str,
   231	    tool_filter: str | None,
   232	    bless: bool,
   233	) -> tuple[int, int]:
   234	    """Returns (passed, failed).
   235	
   236	    Rows are matched by `id`, not by position. Output and expected both store
   237	    `{"id": ..., "result": ...}` per line so the snapshot survives row
   238	    insertions, reorderings, and `--tool` filtered blesses.
   239	    """
   240	    print(f"[{lang}] {url}")
   241	
   242	    err = check_ready(url, str(project_path))
   243	    if err is not None:
   244	        print(f"  PRECHECK: {err}")
   245	        print(f"[{lang}] SKIPPED (precheck failed)")
   246	        return 0, 1
   247	
   248	    input_path = project_path / "input.jsonl"
   249	    expected_path = project_path / "expected.jsonl"
   250	    output_path = project_path / "output.jsonl"
   251	    inputs = [
   252	        json.loads(line)
   253	        for line in input_path.read_text().splitlines()
   254	        if line.strip()
   255	    ]
   256	
   257	    # Detect duplicate input ids — id-keyed matching requires uniqueness.
   258	    seen: dict[str, int] = {}
   259	    for i, entry in enumerate(inputs):
   260	        eid = entry.get("id")
   261	        if not isinstance(eid, str) or not eid:
   262	            print(f"  ERROR: input row {i + 1} has no id; fix before running.")
   263	            return 0, 1
   264	        if eid in seen:
   265	            print(f"  ERROR: duplicate input id '{eid}' at rows {seen[eid] + 1} and {i + 1}.")
   266	            return 0, 1
   267	        seen[eid] = i
   268	
   269	    filtered_inputs = inputs
   270	    if tool_filter:
   271	        filtered_inputs = [e for e in inputs if e["tool"] == tool_filter]
   272	
   273	    expected_by_id = _load_expected_by_id(expected_path) if not bless else {}
   274	
   275	    # Process filtered inputs
   276	    fresh_results: dict[str, Any] = {}
   277	    passed = failed = 0
   278	    for entry in filtered_inputs:
   279	        eid = entry["id"]
   280	        request = build_request(entry, str(project_path))
   281	        result = normalize(post_jsonrpc(url, request), str(project_path))
   282	        fresh_results[eid] = result
   283	
   284	        if bless:
   285	            print(f"  {eid} BLESS")
   286	            passed += 1
   287	            continue
   288	
   289	        if eid not in expected_by_id:
   290	            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
   291	            failed += 1
   292	            continue
   293	
   294	        if expected_by_id[eid] == result:
   295	            print(f"  {eid} PASS")
   296	            passed += 1
   297	        else:
   298	            print(f"  {eid} FAIL")
   299	            for line in diff_lines(expected_by_id[eid], result).splitlines():
   300	                print(f"    {line}")
   301	            failed += 1
   302	
   303	    # Output reflects only the rows we ran (filtered or full).
   304	    output_lines = [_serialize_row(e["id"], fresh_results[e["id"]]) for e in filtered_inputs]
   305	    output_path.write_text("\n".join(output_lines) + ("\n" if output_lines else ""))
   306	
   307	    # Detect orphan expected rows (ids present in expected but not in input).
   308	    # Only meaningful in full (non-filtered) diff runs.
   309	    if not bless and tool_filter is None:
   310	        input_ids = {e["id"] for e in inputs}
   311	        existing_expected = _load_expected_by_id(expected_path) if expected_path.is_file() else {}
   312	        orphan_ids = sorted(set(existing_expected) - input_ids)
   313	        for eid in orphan_ids:
   314	            print(f"  ORPHAN expected id '{eid}' has no matching input — remove or rename.")
   315	        failed += len(orphan_ids)
   316	
   317	    if bless:
   318	        # Merge fresh results into existing expected, then write in input order.
   319	        merged: dict[str, Any] = _load_expected_by_id(expected_path)
   320	        merged.update(fresh_results)
   321	        # Drop expected ids that no longer have a matching input.
   322	        input_ids = {e["id"] for e in inputs}
   323	        merged = {k: v for k, v in merged.items() if k in input_ids}
   324	        new_lines = [
   325	            _serialize_row(e["id"], merged[e["id"]])
   326	            for e in inputs
   327	            if e["id"] in merged
   328	        ]
   329	        expected_path.write_text("\n".join(new_lines) + ("\n" if new_lines else ""))
   330	        print(f"[{lang}] BLESSED {expected_path}")
   331	    else:
   332	        print(f"[{lang}] {passed} passed, {failed} failed")
   333	    return passed, failed
   334	
   335	
   336	def discover_languages(root: Path, only: str | None) -> list[str]:
   337	    if only is not None:
   338	        if not (root / only / "input.jsonl").is_file():
   339	            print(f"No input.jsonl for language '{only}'", file=sys.stderr)
   340	            sys.exit(1)
   341	        return [only]
   342	    return sorted(
   343	        d.name
   344	        for d in root.iterdir()
   345	        if d.is_dir() and (d / "input.jsonl").is_file()
   346	    )
   347	
   348	
   349	def main() -> int:
   350	    parser = argparse.ArgumentParser(
   351	        description="Live MCP test harness runner.",
   352	        formatter_class=argparse.RawDescriptionHelpFormatter,
   353	        epilog=(
   354	            "Examples:\n"
   355	            "  ./run.py                          # diff every language\n"
   356	            "  ./run.py --bless                  # rewrite expected.jsonl\n"
   357	            "  ./run.py --language java          # one language\n"
   358	            "  ./run.py --tool ide_find_definition\n"
   359	            "  ./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http"
   360	        ),
   361	    )
   362	    parser.add_argument("--language", help="Restrict to one language.")
   363	    parser.add_argument("--tool", help="Restrict to one MCP tool.")
   364	    parser.add_argument("--url", help="Override server URL for the run.")
   365	    parser.add_argument(
   366	        "--bless",
   367	        action="store_true",
   368	        help="Rewrite expected.jsonl from server output instead of diffing.",
   369	    )
   370	    args = parser.parse_args()
   371	
   372	    root = Path(__file__).resolve().parent
   373	    langs = discover_languages(root, args.language)
   374	    if not langs:
   375	        print(f"No fixtures found in {root}", file=sys.stderr)
   376	        return 0
   377	
   378	    total_pass = total_fail = 0
   379	    for lang in langs:
   380	        if args.url:

exec
/usr/bin/zsh -lc "for lang in java kotlin python javascript typescript php go rust; do printf '%s ' \""'$lang"; wc -l live-test/$lang/input.jsonl live-test/$lang/expected.jsonl 2>/dev/null | tail -n 1; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
java   164 total
kotlin   134 total
python   150 total
javascript   142 total
typescript   132 total
php   140 total
go   113 total
rust   130 total

exec
/usr/bin/zsh -lc "rg -n '\"id\"|MISSING|LIBRARY_PATH_SUBS|orphan|duplicate|Duplicate|--tool|bless|row index|positional' live-test-review.md live-test/run.py" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/run.py:6:(--bless).
live-test/run.py:9:    ./run.py [--language LANG] [--tool TOOL] [--url URL] [--bless]
live-test/run.py:50:LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
live-test/run.py:91:    for pattern, replacement in LIBRARY_PATH_SUBS:
live-test/run.py:160:        "id": 1,
live-test/run.py:204:    Each row is expected to be {"id": <str>, "result": <obj>}. Rows missing an
live-test/run.py:205:    `id` are skipped (legacy-format remnants will surface as MISSING during diff,
live-test/run.py:206:    prompting a bless).
live-test/run.py:218:        if isinstance(row, dict) and "id" in row and "result" in row:
live-test/run.py:219:            out[row["id"]] = row["result"]
live-test/run.py:224:    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))
live-test/run.py:232:    bless: bool,
live-test/run.py:237:    `{"id": ..., "result": ...}` per line so the snapshot survives row
live-test/run.py:238:    insertions, reorderings, and `--tool` filtered blesses.
live-test/run.py:257:    # Detect duplicate input ids — id-keyed matching requires uniqueness.
live-test/run.py:260:        eid = entry.get("id")
live-test/run.py:265:            print(f"  ERROR: duplicate input id '{eid}' at rows {seen[eid] + 1} and {i + 1}.")
live-test/run.py:273:    expected_by_id = _load_expected_by_id(expected_path) if not bless else {}
live-test/run.py:279:        eid = entry["id"]
live-test/run.py:284:        if bless:
live-test/run.py:290:            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
live-test/run.py:304:    output_lines = [_serialize_row(e["id"], fresh_results[e["id"]]) for e in filtered_inputs]
live-test/run.py:307:    # Detect orphan expected rows (ids present in expected but not in input).
live-test/run.py:309:    if not bless and tool_filter is None:
live-test/run.py:310:        input_ids = {e["id"] for e in inputs}
live-test/run.py:312:        orphan_ids = sorted(set(existing_expected) - input_ids)
live-test/run.py:313:        for eid in orphan_ids:
live-test/run.py:315:        failed += len(orphan_ids)
live-test/run.py:317:    if bless:
live-test/run.py:322:        input_ids = {e["id"] for e in inputs}
live-test/run.py:325:            _serialize_row(e["id"], merged[e["id"]])
live-test/run.py:327:            if e["id"] in merged
live-test/run.py:356:            "  ./run.py --bless                  # rewrite expected.jsonl\n"
live-test/run.py:358:            "  ./run.py --tool ide_find_definition\n"
live-test/run.py:363:    parser.add_argument("--tool", help="Restrict to one MCP tool.")
live-test/run.py:366:        "--bless",
live-test/run.py:389:            lang, root / lang, url, args.tool, args.bless
live-test-review.md:21:- live-test/<lang>/expected.jsonl — blessed snapshot outputs to diff against.
live-test-review.md:22:- live-test/<lang>/output.jsonl — most-recent harness output (may differ from expected because a path-normalization change is pending re-bless).
live-test-review.md:24:Recent change you should focus on hard: I just added LIBRARY_PATH_SUBS — a regex list in run.py that substitutes machine-specific library / SDK / IDE-stub paths in IDE responses with stable tokens (${RUST_STDLIB}, ${KOTLIN_STDLIB}, ${JDK}, ${PYCHARM_TYPESHED}, ${PYTHON_STDLIB}, ${PHP_STUBS}, ${WEBSTORM_JS_STUBS}, ${HOME}). The catch-all ${HOME}/ rule is last. 55 expected.jsonl rows currently diff against output.jsonl purely because of this rebless that hasn't happened yet — that's a known state, not a bug to find.
live-test-review.md:27:1. run.py logic: race conditions, dead branches, incorrect normalization, the new LIBRARY_PATH_SUBS regexes (over-match / under-match, ordering, missed path families on macOS/Windows, escapes), broken sort key, bless step writing the wrong thing.
live-test-review.md:28:2. input.jsonl correctness across all 8 languages: are line/column pointing at the intended identifier? Ambiguous probe IDs? Duplicate IDs? Probes that don't really exercise what their ID claims?
live-test-review.md:43:     6	(--bless).
live-test-review.md:46:     9	    ./run.py [--language LANG] [--tool TOOL] [--url URL] [--bless]
live-test-review.md:87:    50	LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
live-test-review.md:128:    91	    for pattern, replacement in LIBRARY_PATH_SUBS:
live-test-review.md:197:   160	        "id": 1,
live-test-review.md:243:   206	    bless: bool,
live-test-review.md:266:   229	    if not bless and expected_path.is_file():
live-test-review.md:272:   235	        eid = entry.get("id", f"#{i + 1}")
live-test-review.md:278:   241	        if bless:
live-test-review.md:284:   247	            print(f"  [{i + 1}] {eid} MISSING (no expected.jsonl line {i + 1} — bless?)")
live-test-review.md:307:   270	    if bless:
live-test-review.md:335:   298	            "  ./run.py --bless                  # rewrite expected.jsonl\n"
live-test-review.md:337:   300	            "  ./run.py --tool ide_find_definition\n"
live-test-review.md:342:   305	    parser.add_argument("--tool", help="Restrict to one MCP tool.")
live-test-review.md:345:   308	        "--bless",
live-test-review.md:368:   331	            lang, root / lang, url, args.tool, args.bless
live-test-review.md:448:     1	{"id":"def-circle-area-decl",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
live-test-review.md:449:     2	{"id":"def-rectangle-area-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
live-test-review.md:450:     3	{"id":"def-makeDefaultShapes-decl","tool":"ide_find_definition",     "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31}}
live-test-review.md:451:     4	{"id":"def-parse-string-overload","tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":16}}
live-test-review.md:452:     5	{"id":"def-parse-stringint-overload","tool":"ide_find_definition",   "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":30}}
live-test-review.md:453:     6	{"id":"def-CoerceMode-INT-apply", "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":78,"column":31}}
live-test-review.md:454:     7	{"id":"def-quirkVar-coerce",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":18,"column":16}}
live-test-review.md:455:     8	{"id":"def-Point-record-decl",   "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
live-test-review.md:456:     9	{"id":"def-Animal-sealed-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
live-test-review.md:457:    10	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":72,"column":22}}
live-test-review.md:458:    11	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":63,"column":33}}
live-test-review.md:459:    12	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
live-test-review.md:460:    13	{"id":"refs-Circle-ctor",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":21,"column":5}}
live-test-review.md:461:    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21}}
live-test-review.md:462:    15	{"id":"impls-drawable-draw",       "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":7,"column":12}}
live-test-review.md:463:    16	{"id":"impls-functional-Coerce",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":15}}
live-test-review.md:464:    17	{"id":"impls-Coerce-via-lambda",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":68,"column":18}}
live-test-review.md:465:    18	{"id":"impls-CoerceMode-apply",   "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":74,"column":22}}
live-test-review.md:466:    19	{"id":"impls-coerce-run",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":28}}
live-test-review.md:467:    20	{"id":"impls-Animal-name",        "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Modern.java","line":9,"column":16}}
live-test-review.md:468:    21	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
live-test-review.md:469:    22	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
live-test-review.md:470:    23	{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":31,"column":19}}
live-test-review.md:471:    24	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7}}
live-test-review.md:472:    25	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
live-test-review.md:473:    26	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
live-test-review.md:474:    27	{"id":"type-hier-CoerceMode",     "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Quirks.java","line":71,"column":10}}
live-test-review.md:475:    28	{"id":"type-hier-Point",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
live-test-review.md:476:    29	{"id":"type-hier-Animal",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
live-test-review.md:477:    30	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":2}}
live-test-review.md:478:    31	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"direction":"callers","maxDepth":2}}
live-test-review.md:479:    32	{"id":"call-hier-parse-string-callers","tool":"ide_call_hierarchy",  "params":{"file":"src/main/java/demo/Quirks.java","line":86,"column":23,"direction":"callers","maxDepth":2}}
live-test-review.md:480:    33	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:481:    34	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test-review.md:482:    35	{"id":"find-class-Point",         "tool":"ide_find_class",           "params":{"query":"Point"}}
live-test-review.md:483:    36	{"id":"find-class-Animal",        "tool":"ide_find_class",           "params":{"query":"Animal"}}
live-test-review.md:484:    37	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:485:    38	{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
live-test-review.md:486:    39	{"id":"find-symbol-parse",        "tool":"ide_find_symbol",          "params":{"query":"parse"}}
live-test-review.md:487:    40	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Normal.java"}}
live-test-review.md:488:    41	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Quirks.java"}}
live-test-review.md:489:    42	{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
live-test-review.md:490:    43	{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":90,"column":24}}
live-test-review.md:491:    44	{"id":"def-Point-via-new-record-call", "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Modern.java","line":21,"column":23}}
live-test-review.md:492:    45	{"id":"def-ArrayList-via-new-jdk-call","tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":88,"column":34}}
live-test-review.md:493:    46	{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
live-test-review.md:494:    47	{"id":"index-status",             "tool":"ide_index_status",         "params":{}}
live-test-review.md:495:    48	{"id":"diagnostics-Quirks-errors","tool":"ide_diagnostics",          "params":{"file":"src/main/java/demo/Quirks.java","severity":"errors"}}
live-test-review.md:496:    49	{"id":"refs-Drawable-interface",    "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
live-test-review.md:497:    50	{"id":"refs-Rectangle-class",      "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
live-test-review.md:498:    51	{"id":"super-Rectangle-draw",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":51,"column":19}}
live-test-review.md:499:    52	{"id":"super-Cat-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":13,"column":33}}
live-test-review.md:500:    53	{"id":"super-Dog-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":17,"column":33}}
live-test-review.md:501:    54	{"id":"type-hier-Drawable",        "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
live-test-review.md:502:    55	{"id":"type-hier-Circle",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":18,"column":7}}
live-test-review.md:503:    56	{"id":"call-hier-totalArea-callees","tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":69,"column":12,"direction":"callees","maxDepth":2}}
live-test-review.md:504:    57	{"id":"call-hier-describe-callers","tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":13,"column":12,"direction":"callers","maxDepth":2}}
live-test-review.md:505:    58	{"id":"call-hier-largest-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":77,"column":11,"direction":"callees","maxDepth":2}}
live-test-review.md:506:    59	{"id":"find-class-Circle",         "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:507:    60	{"id":"find-class-Square",         "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:508:    61	{"id":"find-class-ShapeCollection","tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:509:    62	{"id":"find-symbol-describe",      "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:510:    63	{"id":"find-symbol-totalArea",     "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test-review.md:511:    64	{"id":"find-symbol-draw",          "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:512:    65	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"scope":"project_and_libraries"}}
live-test-review.md:513:    66	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:514:    67	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:515:    68	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:516:    69	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:517:    70	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7,"direction":"supertypes"}}
live-test-review.md:518:    71	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16,"direction":"subtypes"}}
live-test-review.md:519:    72	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":3}}
live-test-review.md:520:    73	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test-review.md:521:    74	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:522:    75	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:523:    76	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:524:    77	{"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test-review.md:525:    78	{"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test-review.md:526:    79	{"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
live-test-review.md:527:    80	{"id":"audit-super-CoerceMode-INT-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":72,"column":19}}
live-test-review.md:528:    81	{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
live-test-review.md:529:    82	{"id":"audit-file-structure-Quirks","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Quirks.java"}}
live-test-review.md:530:    83	{"id":"audit-diagnostics-warnings","tool":"ide_diagnostics","params":{"file":"src/main/java/demo/Normal.java","severity":"warnings"}}
live-test-review.md:533:     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
live-test-review.md:534:     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
live-test-review.md:535:     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5}}
live-test-review.md:536:     4	{"id":"def-extension-coerceTo",   "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":29,"column":42}}
live-test-review.md:537:     5	{"id":"def-infix-coerceFirst",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":59,"column":43}}
live-test-review.md:538:     6	{"id":"def-companion-create-call",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":23,"column":21}}
live-test-review.md:539:     7	{"id":"def-companion-DEFAULT-LIMIT",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":25,"column":20}}
live-test-review.md:540:     8	{"id":"def-suspend-fetchValue-call",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":17,"column":13}}
live-test-review.md:541:     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":32,"column":49}}
live-test-review.md:542:    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":26,"column":9}}
live-test-review.md:543:    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test-review.md:544:    12	{"id":"refs-Circle-radius",       "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":18}}
live-test-review.md:545:    13	{"id":"refs-coerceFirst-callsite","tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":61,"column":29}}
live-test-review.md:546:    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18}}
live-test-review.md:547:    15	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":4,"column":9}}
live-test-review.md:548:    16	{"id":"impls-coercion",           "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
live-test-review.md:549:    17	{"id":"impls-Coercion-apply",     "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":38,"column":18}}
live-test-review.md:550:    18	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
live-test-review.md:551:    19	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
live-test-review.md:552:    20	{"id":"super-Circle-draw-from-Drawable","tool":"ide_find_super_methods","params":{"file":"src/main/kotlin/demo/Normal.kt","line":15,"column":18}}
live-test-review.md:553:    21	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7}}
live-test-review.md:554:    22	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test-review.md:555:    23	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test-review.md:556:    24	{"id":"type-hier-Coercion-sealed","tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
live-test-review.md:557:    25	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:558:    26	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18,"direction":"callers","maxDepth":2}}
live-test-review.md:559:    27	{"id":"call-hier-quirkApply-callees","tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":13,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:560:    28	{"id":"call-hier-quirkLet-callees",  "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":17,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:561:    29	{"id":"call-hier-quirkWith-callees", "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":21,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:562:    30	{"id":"call-hier-computeTotal-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Modern.kt","line":16,"column":13,"direction":"callees","maxDepth":2}}
live-test-review.md:563:    31	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:564:    32	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test-review.md:565:    33	{"id":"find-class-Counter",       "tool":"ide_find_class",           "params":{"query":"Counter"}}
live-test-review.md:566:    34	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:567:    35	{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
live-test-review.md:568:    36	{"id":"find-symbol-fetchValue",   "tool":"ide_find_symbol",          "params":{"query":"fetchValue"}}
live-test-review.md:569:    37	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Normal.kt"}}
live-test-review.md:570:    38	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Quirks.kt"}}
live-test-review.md:571:    39	{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
live-test-review.md:572:    40	{"id":"refs-Rectangle-class",       "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test-review.md:573:    41	{"id":"impls-Shape-class",          "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test-review.md:574:    42	{"id":"impls-Rectangle-class",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test-review.md:575:    43	{"id":"super-Rectangle-draw",       "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":20,"column":18}}
live-test-review.md:576:    44	{"id":"super-IntCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":39,"column":50}}
live-test-review.md:577:    45	{"id":"super-AbsCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":40,"column":50}}
live-test-review.md:578:    46	{"id":"type-hier-Drawable",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
live-test-review.md:579:    47	{"id":"type-hier-Circle",           "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":7}}
live-test-review.md:580:    48	{"id":"type-hier-ShapeCollection",  "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":25,"column":7}}
live-test-review.md:581:    49	{"id":"find-class-Circle",          "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:582:    50	{"id":"find-class-Square",          "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:583:    51	{"id":"find-class-ShapeCollection", "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:584:    52	{"id":"find-class-Drawable",        "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test-review.md:585:    53	{"id":"find-symbol-describe",       "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:586:    54	{"id":"find-symbol-totalArea",      "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test-review.md:587:    55	{"id":"find-symbol-draw",           "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:588:    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18,"scope":"project_and_libraries"}}
live-test-review.md:589:    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:590:    58	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:591:    59	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:592:    60	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:593:    61	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7,"direction":"supertypes"}}
live-test-review.md:594:    62	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16,"direction":"subtypes"}}
live-test-review.md:595:    63	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":3}}
live-test-review.md:596:    64	{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test-review.md:597:    65	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:598:    66	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:599:    67	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:602:     1	{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
live-test-review.md:603:     2	{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
live-test-review.md:604:     3	{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
live-test-review.md:605:     4	{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
live-test-review.md:606:     5	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
live-test-review.md:607:     6	{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
live-test-review.md:608:     7	{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
live-test-review.md:609:     8	{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
live-test-review.md:610:     9	{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
live-test-review.md:611:    10	{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
live-test-review.md:612:    11	{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
live-test-review.md:613:    12	{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
live-test-review.md:614:    13	{"id":"def-walrus-result",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":70,"column":16}}
live-test-review.md:615:    14	{"id":"def-multiple-assign-b",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":100,"column":19}}
live-test-review.md:616:    15	{"id":"def-nested-return-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":82,"column":12}}
live-test-review.md:617:    16	{"id":"refs-area-from-describe",    "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":12,"column":9}}
live-test-review.md:618:    17	{"id":"refs-shapes-field",          "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":49,"column":14}}
live-test-review.md:619:    18	{"id":"refs-shape-class",           "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":10,"column":7}}
live-test-review.md:620:    19	{"id":"refs-int-builtin-in-quirks", "tool":"ide_find_usages",      "params":{"file":"src/quirks.py","line":64,"column":20}}
live-test-review.md:621:    20	{"id":"refs-circle-radius",         "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":21,"column":14}}
live-test-review.md:622:    21	{"id":"impls-shape-area",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":12,"column":9}}
live-test-review.md:623:    22	{"id":"impls-drawable-draw",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":7,"column":9}}
live-test-review.md:624:    23	{"id":"super-Square-init",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":43,"column":9}}
live-test-review.md:625:    24	{"id":"super-circle-area",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":23,"column":9}}
live-test-review.md:626:    25	{"id":"super-rectangle-area",       "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":35,"column":9}}
live-test-review.md:627:    26	{"id":"type-hier-square",           "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":42,"column":7}}
live-test-review.md:628:    27	{"id":"type-hier-rectangle",        "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":30,"column":7}}
live-test-review.md:629:    28	{"id":"type-hier-shape",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":10,"column":7}}
live-test-review.md:630:    29	{"id":"call-hier-make-default",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:631:    30	{"id":"call-hier-area-callers",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":12,"column":9,"direction":"callers","maxDepth":2}}
live-test-review.md:632:    31	{"id":"call-hier-total-area",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":54,"column":9,"direction":"callees","maxDepth":2}}
live-test-review.md:633:    32	{"id":"find-class-Shape",           "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:634:    33	{"id":"find-class-Rectangle",       "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test-review.md:635:    34	{"id":"find-symbol-area",           "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:636:    35	{"id":"find-symbol-quirk-rebind",   "tool":"ide_find_symbol",          "params":{"query":"quirk_name_rebinding"}}
live-test-review.md:637:    36	{"id":"file-structure-normal",      "tool":"ide_file_structure",       "params":{"file":"src/normal.py"}}
live-test-review.md:638:    37	{"id":"file-structure-quirks",      "tool":"ide_file_structure",       "params":{"file":"src/quirks.py"}}
live-test-review.md:639:    38	{"id":"refs-Drawable-protocol",      "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":6,"column":7}}
live-test-review.md:640:    39	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":30,"column":7}}
live-test-review.md:641:    40	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":10,"column":7}}
live-test-review.md:642:    41	{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":30,"column":7}}
live-test-review.md:643:    42	{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":19,"column":7}}
live-test-review.md:644:    43	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":15,"column":9}}
live-test-review.md:645:    44	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":26,"column":9}}
live-test-review.md:646:    45	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":38,"column":9}}
live-test-review.md:647:    46	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":6,"column":7}}
live-test-review.md:648:    47	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":19,"column":7}}
live-test-review.md:649:    48	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":47,"column":7}}
live-test-review.md:650:    49	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":15,"column":9,"direction":"callers","maxDepth":2}}
live-test-review.md:651:    50	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":57,"column":9,"direction":"callees","maxDepth":2}}
live-test-review.md:652:    51	{"id":"call-hier-add-callers",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":51,"column":9,"direction":"callers","maxDepth":2}}
live-test-review.md:653:    52	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:654:    53	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:655:    54	{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test-review.md:656:    55	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:657:    56	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:658:    57	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test-review.md:659:    58	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:660:    59	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test-review.md:661:    60	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
live-test-review.md:662:    61	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:663:    62	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:664:    63	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:665:    64	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:666:    65	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":42,"column":7,"direction":"supertypes"}}
live-test-review.md:667:    66	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":10,"column":7,"direction":"subtypes"}}
live-test-review.md:668:    67	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":3}}
live-test-review.md:669:    68	{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test-review.md:670:    69	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:671:    70	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:672:    71	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:673:    72	{"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
live-test-review.md:674:    73	{"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
live-test-review.md:675:    74	{"id":"audit-super-Circle-draw-protocol","tool":"ide_find_super_methods","params":{"file":"src/normal.py","line":26,"column":9}}
live-test-review.md:676:    75	{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
live-test-review.md:677:    76	{"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}
live-test-review.md:680:     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":17,"column":5}}
live-test-review.md:681:     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":27,"column":5}}
live-test-review.md:682:     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":56,"column":10}}
live-test-review.md:683:     4	{"id":"def-quirks-fn-localbind",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":6,"column":12}}
live-test-review.md:684:     5	{"id":"def-quirks-parseInt-property-on-Number","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23}}
live-test-review.md:685:     6	{"id":"def-quirks-parseint-destructured","tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":37,"column":12}}
live-test-review.md:686:     7	{"id":"def-quirks-destructured-Number-receiver","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":36,"column":29}}
live-test-review.md:687:     8	{"id":"def-quirks-parseint-optional",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":71,"column":20}}
live-test-review.md:688:     9	{"id":"def-quirks-proxy-call",           "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":82,"column":18}}
live-test-review.md:689:    10	{"id":"def-quirks-proxy-call-source",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":80,"column":31}}
live-test-review.md:690:    11	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":8,"column":5}}
live-test-review.md:691:    12	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":39,"column":14}}
live-test-review.md:692:    13	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":7,"column":7}}
live-test-review.md:693:    14	{"id":"impls-shape-area-direct-overrides-only","tool":"ide_find_implementations","params":{"file":"src/normal.js","line":8,"column":5}}
live-test-review.md:694:    15	{"id":"impls-Drawable-draw",             "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":4,"column":5}}
live-test-review.md:695:    16	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":17,"column":5}}
live-test-review.md:696:    17	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":27,"column":5}}
live-test-review.md:697:    18	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":31,"column":7}}
live-test-review.md:698:    19	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":21,"column":7}}
live-test-review.md:699:    20	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":7,"column":7}}
live-test-review.md:700:    21	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":2}}
live-test-review.md:701:    22	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":8,"column":5,"direction":"callers","maxDepth":2}}
live-test-review.md:702:    23	{"id":"call-hier-qAwait-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/quirks.js","line":64,"column":16,"direction":"callees","maxDepth":2}}
live-test-review.md:703:    24	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:704:    25	{"id":"find-class-Rectangle",     "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test-review.md:705:    26	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:706:    27	{"id":"find-symbol-qRebind",      "tool":"ide_find_symbol",          "params":{"query":"qRebind"}}
live-test-review.md:707:    28	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.js"}}
live-test-review.md:708:    29	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.js"}}
live-test-review.md:709:    30	{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":17}}
live-test-review.md:710:    31	{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":34}}
live-test-review.md:711:    32	{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":57,"column":17}}
live-test-review.md:712:    33	{"id":"refs-Drawable-class",         "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":3,"column":7}}
live-test-review.md:713:    34	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":21,"column":7}}
live-test-review.md:714:    35	{"id":"refs-describe-method",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":9,"column":5}}
live-test-review.md:715:    36	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":7,"column":7}}
live-test-review.md:716:    37	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":9,"column":5}}
live-test-review.md:717:    38	{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":21,"column":7}}
live-test-review.md:718:    39	{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":12,"column":7}}
live-test-review.md:719:    40	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":18,"column":5}}
live-test-review.md:720:    41	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":28,"column":5}}
live-test-review.md:721:    42	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":3,"column":7}}
live-test-review.md:722:    43	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":12,"column":7}}
live-test-review.md:723:    44	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":37,"column":7}}
live-test-review.md:724:    45	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":42,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:725:    46	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":9,"column":5,"direction":"callers","maxDepth":2}}
live-test-review.md:726:    47	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":47,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:727:    48	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:728:    49	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:729:    50	{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test-review.md:730:    51	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:731:    52	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:732:    53	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test-review.md:733:    54	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:734:    55	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test-review.md:735:    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.js","line":17,"column":5,"scope":"project_and_libraries"}}
live-test-review.md:736:    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:737:    58	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:738:    59	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:739:    60	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:740:    61	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":31,"column":7,"direction":"supertypes"}}
live-test-review.md:741:    62	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":7,"column":7,"direction":"subtypes"}}
live-test-review.md:742:    63	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":3}}
live-test-review.md:743:    64	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test-review.md:744:    65	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:745:    66	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:746:    67	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:747:    68	{"id":"audit-refs-Circle-cross-file","tool":"ide_find_usages","params":{"file":"src/normal.js","line":12,"column":7}}
live-test-review.md:748:    69	{"id":"audit-struct-accessors","tool":"ide_file_structure","params":{"file":"src/accessors.js"}}
live-test-review.md:749:    70	{"id":"audit-super-Box-area","tool":"ide_find_super_methods","params":{"file":"src/accessors.js","line":9,"column":9}}
live-test-review.md:750:    71	{"id":"audit-def-Number-parseInt-library","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23,"scope":"project_and_libraries"}}
live-test-review.md:753:     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":17,"column":5}}
live-test-review.md:754:     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":25,"column":5}}
live-test-review.md:755:     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":55,"column":17}}
live-test-review.md:756:     4	{"id":"def-quirks-parseint-from-typed-lambda","tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":3,"column":12}}
live-test-review.md:757:     5	{"id":"def-quirks-parseint-arg-cast",         "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":39,"column":19}}
live-test-review.md:758:     6	{"id":"def-T-typeparam-use",                  "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":53,"column":15}}
live-test-review.md:759:     7	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":6,"column":14}}
live-test-review.md:760:     8	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":36,"column":14}}
live-test-review.md:761:     9	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test-review.md:762:    10	{"id":"refs-Coercer-interface",               "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":42,"column":18}}
live-test-review.md:763:    11	{"id":"refs-aliasedCoerce",                   "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":62,"column":14}}
live-test-review.md:764:    12	{"id":"refs-Drawable-class",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test-review.md:765:    13	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":6,"column":14}}
live-test-review.md:766:    14	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":2,"column":5}}
live-test-review.md:767:    15	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.ts","line":42,"column":18}}
live-test-review.md:768:    16	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":17,"column":5}}
live-test-review.md:769:    17	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":25,"column":5}}
live-test-review.md:770:    18	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":29,"column":14}}
live-test-review.md:771:    19	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":21,"column":14}}
live-test-review.md:772:    20	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test-review.md:773:    21	{"id":"type-hier-TypedCoercer",               "tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":52,"column":14}}
live-test-review.md:774:    22	{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test-review.md:775:    23	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":2}}
live-test-review.md:776:    24	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":6,"column":14,"direction":"callers","maxDepth":2}}
live-test-review.md:777:    25	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:778:    26	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test-review.md:779:    27	{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test-review.md:780:    28	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:781:    29	{"id":"find-symbol-qLambda",      "tool":"ide_find_symbol",          "params":{"query":"qLambda"}}
live-test-review.md:782:    30	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.ts"}}
live-test-review.md:783:    31	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.ts"}}
live-test-review.md:784:    32	{"id":"def-Circle-via-new-call",            "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":17}}
live-test-review.md:785:    33	{"id":"def-Rectangle-via-new-call",         "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":34}}
live-test-review.md:786:    34	{"id":"def-TypedCoercer-via-new-generic",   "tool":"ide_find_definition",  "params":{"file":"src/quirks.ts","line":57,"column":16}}
live-test-review.md:787:    35	{"id":"refs-Circle-via-new-call-site",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":56,"column":17}}
live-test-review.md:788:    36	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test-review.md:789:    37	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":8,"column":5}}
live-test-review.md:790:    38	{"id":"impls-Drawable-interface",    "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test-review.md:791:    39	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":18,"column":5}}
live-test-review.md:792:    40	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":26,"column":5}}
live-test-review.md:793:    41	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":13,"column":14}}
live-test-review.md:794:    42	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":35,"column":14}}
live-test-review.md:795:    43	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":40,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:796:    44	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":8,"column":5,"direction":"callers","maxDepth":2}}
live-test-review.md:797:    45	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":46,"column":5,"direction":"callees","maxDepth":2}}
live-test-review.md:798:    46	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":2,"column":5,"direction":"callers","maxDepth":2}}
live-test-review.md:799:    47	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:800:    48	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:801:    49	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:802:    50	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:803:    51	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test-review.md:804:    52	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:805:    53	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test-review.md:806:    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.ts","line":6,"column":14,"scope":"project_and_libraries"}}
live-test-review.md:807:    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:808:    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:809:    57	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:810:    58	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:811:    59	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":29,"column":14,"direction":"supertypes"}}
live-test-review.md:812:    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":5,"column":23,"direction":"subtypes"}}
live-test-review.md:813:    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":3}}
live-test-review.md:814:    62	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test-review.md:815:    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:816:    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:817:    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:818:    66	{"id":"audit-type-hier-Coerce-alias","tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":60,"column":13}}
live-test-review.md:821:     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":19,"column":21}}
live-test-review.md:822:     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":31,"column":21}}
live-test-review.md:823:     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":73,"column":10}}
live-test-review.md:824:     4	{"id":"def-match-coerce",         "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":63,"column":32}}
live-test-review.md:825:     5	{"id":"def-self-class",           "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":33,"column":22}}
live-test-review.md:826:     6	{"id":"def-promoted-property",    "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":75,"column":20}}
live-test-review.md:827:     7	{"id":"def-Status-enum-decl",     "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test-review.md:828:     8	{"id":"def-Status-Active-usage",  "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":22,"column":20}}
live-test-review.md:829:     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":9,"column":30}}
live-test-review.md:830:    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":48,"column":18}}
live-test-review.md:831:    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test-review.md:832:    12	{"id":"refs-Quirks-class",        "tool":"ide_find_usages",      "params":{"file":"src/Quirks.php","line":4,"column":7}}
live-test-review.md:833:    13	{"id":"refs-Status-enum",         "tool":"ide_find_usages",      "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test-review.md:834:    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":9,"column":30}}
live-test-review.md:835:    15	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":5,"column":21}}
live-test-review.md:836:    16	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":79,"column":11}}
live-test-review.md:837:    17	{"id":"impls-coerce-at-usage",    "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":71,"column":20}}
live-test-review.md:838:    18	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":19,"column":21}}
live-test-review.md:839:    19	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":31,"column":21}}
live-test-review.md:840:    20	{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":23,"column":21}}
live-test-review.md:841:    21	{"id":"super-IntCoercer-coerce",  "tool":"ide_find_super_methods",   "params":{"file":"src/Quirks.php","line":84,"column":21}}
live-test-review.md:842:    22	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":40,"column":7}}
live-test-review.md:843:    23	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":28,"column":7}}
live-test-review.md:844:    24	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test-review.md:845:    25	{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":4,"column":11}}
live-test-review.md:846:    26	{"id":"type-hier-Coercer",        "tool":"ide_type_hierarchy",       "params":{"file":"src/Quirks.php","line":79,"column":11}}
live-test-review.md:847:    27	{"id":"type-hier-Status",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test-review.md:848:    28	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":2}}
live-test-review.md:849:    29	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":9,"column":30,"direction":"callers","maxDepth":2}}
live-test-review.md:850:    30	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:851:    31	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test-review.md:852:    32	{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test-review.md:853:    33	{"id":"find-class-Status",        "tool":"ide_find_class",           "params":{"query":"Status"}}
live-test-review.md:854:    34	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:855:    35	{"id":"find-symbol-qNameRebind",  "tool":"ide_find_symbol",          "params":{"query":"qNameRebind"}}
live-test-review.md:856:    36	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/Normal.php"}}
live-test-review.md:857:    37	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/Quirks.php"}}
live-test-review.md:858:    38	{"id":"file-structure-Modern",    "tool":"ide_file_structure",       "params":{"file":"src/Modern.php"}}
live-test-review.md:859:    39	{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":4,"column":11}}
live-test-review.md:860:    40	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":28,"column":7}}
live-test-review.md:861:    41	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test-review.md:862:    42	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":11,"column":21}}
live-test-review.md:863:    43	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":35,"column":21}}
live-test-review.md:864:    44	{"id":"super-Square-construct",      "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":41,"column":21}}
live-test-review.md:865:    45	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":16,"column":7}}
live-test-review.md:866:    46	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":46,"column":7}}
live-test-review.md:867:    47	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":54,"column":21,"direction":"callees","maxDepth":2}}
live-test-review.md:868:    48	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":11,"column":21,"direction":"callers","maxDepth":2}}
live-test-review.md:869:    49	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":62,"column":21,"direction":"callees","maxDepth":2}}
live-test-review.md:870:    50	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":5,"column":21,"direction":"callers","maxDepth":2}}
live-test-review.md:871:    51	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:872:    52	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:873:    53	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test-review.md:874:    54	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:875:    55	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test-review.md:876:    56	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:877:    57	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test-review.md:878:    58	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/Normal.php","line":9,"column":30,"scope":"project_and_libraries"}}
live-test-review.md:879:    59	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:880:    60	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:881:    61	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:882:    62	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:883:    63	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":40,"column":7,"direction":"supertypes"}}
live-test-review.md:884:    64	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":8,"column":16,"direction":"subtypes"}}
live-test-review.md:885:    65	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":3}}
live-test-review.md:886:    66	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}
live-test-review.md:887:    67	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:888:    68	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:889:    69	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:890:    70	{"id":"audit-refs-qNameRebind-callable-array","tool":"ide_find_usages","params":{"file":"src/Quirks.php","line":6,"column":28}}
live-test-review.md:893:     1	{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":23,"column":18}}
live-test-review.md:894:     2	{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":34,"column":20}}
live-test-review.md:895:     3	{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":72,"column":6}}
live-test-review.md:896:     4	{"id":"def-quirks-fnvar-resolve",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":12,"column":10}}
live-test-review.md:897:     5	{"id":"def-quirks-method-value",   "tool":"ide_find_definition", "params":{"file":"quirks.go","line":91,"column":10}}
live-test-review.md:898:     6	{"id":"def-quirks-method-expr",    "tool":"ide_find_definition", "params":{"file":"quirks.go","line":97,"column":19}}
live-test-review.md:899:     7	{"id":"def-quirks-type-switch-s",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":121,"column":24}}
live-test-review.md:900:     8	{"id":"def-quirks-iface-dispatch", "tool":"ide_find_definition", "params":{"file":"quirks.go","line":66,"column":11}}
live-test-review.md:901:     9	{"id":"def-drawable-draw",        "tool":"ide_find_definition", "params":{"file":"normal.go","line":6,"column":2}}
live-test-review.md:902:    10	{"id":"refs-area-from-totalArea","tool":"ide_find_usages", "params":{"file":"normal.go","line":57,"column":12}}
live-test-review.md:903:    11	{"id":"refs-shapes-field",       "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":2}}
live-test-review.md:904:    12	{"id":"refs-shape-iface",        "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":11}}
live-test-review.md:905:    13	{"id":"refs-drawable-draw",      "tool":"ide_find_usages", "params":{"file":"normal.go","line":6,"column":2}}
live-test-review.md:906:    14	{"id":"refs-shape-collection-add","tool":"ide_find_usages","params":{"file":"normal.go","line":50,"column":28}}
live-test-review.md:907:    15	{"id":"type-hier-Circle",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":18,"column":6}}
live-test-review.md:908:    16	{"id":"type-hier-Rectangle",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":29,"column":6}}
live-test-review.md:909:    17	{"id":"type-hier-Square",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":40,"column":6}}
live-test-review.md:910:    18	{"id":"type-hier-baseShape",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":14,"column":6}}
live-test-review.md:911:    19	{"id":"call-hier-MakeDefault",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":2}}
live-test-review.md:912:    20	{"id":"call-hier-area-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":23,"column":18,"direction":"callers","maxDepth":2}}
live-test-review.md:913:    21	{"id":"find-class-Shape",        "tool":"ide_find_class",      "params":{"query":"Shape"}}
live-test-review.md:914:    22	{"id":"find-class-Rectangle",    "tool":"ide_find_class",      "params":{"query":"Rectangle"}}
live-test-review.md:915:    23	{"id":"find-class-Drawable",     "tool":"ide_find_class",      "params":{"query":"Drawable"}}
live-test-review.md:916:    24	{"id":"find-symbol-Area",        "tool":"ide_find_symbol",     "params":{"query":"Area"}}
live-test-review.md:917:    25	{"id":"find-symbol-qFnVar",      "tool":"ide_find_symbol",     "params":{"query":"qFnVar"}}
live-test-review.md:918:    26	{"id":"file-structure-normal",   "tool":"ide_file_structure",  "params":{"file":"normal.go"}}
live-test-review.md:919:    27	{"id":"file-structure-quirks",   "tool":"ide_file_structure",  "params":{"file":"quirks.go"}}
live-test-review.md:920:    28	{"id":"refs-Drawable-iface",         "tool":"ide_find_usages",     "params":{"file":"normal.go","line":5,"column":6}}
live-test-review.md:921:    29	{"id":"refs-Circle-struct",          "tool":"ide_find_usages",     "params":{"file":"normal.go","line":18,"column":6}}
live-test-review.md:922:    30	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":5,"column":6}}
live-test-review.md:923:    31	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":46,"column":6}}
live-test-review.md:924:    32	{"id":"call-hier-TotalArea-callees", "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":54,"column":28,"direction":"callees","maxDepth":2}}
live-test-review.md:925:    33	{"id":"call-hier-Describe-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":11,"column":2,"direction":"callers","maxDepth":2}}
live-test-review.md:926:    34	{"id":"call-hier-Draw-callers",      "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":6,"column":2,"direction":"callers","maxDepth":2}}
live-test-review.md:927:    35	{"id":"call-hier-Largest-callees",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":62,"column":28,"direction":"callees","maxDepth":2}}
live-test-review.md:928:    36	{"id":"find-class-Circle",           "tool":"ide_find_class",      "params":{"query":"Circle"}}
live-test-review.md:929:    37	{"id":"find-class-Square",           "tool":"ide_find_class",      "params":{"query":"Square"}}
live-test-review.md:930:    38	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",      "params":{"query":"ShapeCollection"}}
live-test-review.md:931:    39	{"id":"find-symbol-Describe",        "tool":"ide_find_symbol",     "params":{"query":"Describe"}}
live-test-review.md:932:    40	{"id":"find-symbol-TotalArea",       "tool":"ide_find_symbol",     "params":{"query":"TotalArea"}}
live-test-review.md:933:    41	{"id":"find-symbol-Draw",            "tool":"ide_find_symbol",     "params":{"query":"Draw"}}
live-test-review.md:934:    42	{"id":"find-symbol-Largest",         "tool":"ide_find_symbol",     "params":{"query":"Largest"}}
live-test-review.md:935:    43	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"normal.go","line":23,"column":18,"scope":"project_and_libraries"}}
live-test-review.md:936:    44	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"Area","pageSize":2}}
live-test-review.md:937:    45	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:938:    46	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:939:    47	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:940:    48	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":40,"column":7,"direction":"supertypes"}}
live-test-review.md:941:    49	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":9,"column":6,"direction":"subtypes"}}
live-test-review.md:942:    50	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":3}}
live-test-review.md:943:    51	{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer.Coerce"}}
live-test-review.md:944:    52	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:945:    53	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:946:    54	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:947:    55	{"id":"audit-refs-baseShape-Describe-promoted","tool":"ide_find_usages","params":{"file":"normal.go","line":16,"column":20}}
live-test-review.md:948:    56	{"id":"audit-find-class-prefix-Shape","tool":"ide_find_class","params":{"query":"Shape","matchMode":"prefix"}}
live-test-review.md:949:    57	{"id":"audit-find-class-exact-Circle","tool":"ide_find_class","params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:952:     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":23,"column":48}}
live-test-review.md:953:     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":44,"column":48}}
live-test-review.md:954:     3	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":94,"column":8}}
live-test-review.md:955:     4	{"id":"def-trait-object-coerce",     "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":47,"column":11}}
live-test-review.md:956:     5	{"id":"def-enum-variant-coerce-int", "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":74,"column":25}}
live-test-review.md:957:     6	{"id":"def-q-stdlib-parse",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":97,"column":21}}
live-test-review.md:958:     7	{"id":"def-q-fn-pointer",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":7,"column":35}}
live-test-review.md:959:     8	{"id":"def-q-box-dyn-fn",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":16,"column":25}}
live-test-review.md:960:     9	{"id":"def-CoerceMode-apply",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":81,"column":10}}
live-test-review.md:961:    10	{"id":"def-q-iter-parse",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":85,"column":25}}
live-test-review.md:962:    11	{"id":"def-drawable-draw-decl",      "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":2,"column":8}}
live-test-review.md:963:    12	{"id":"def-cross-mod-helper",       "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":7,"column":12}}
live-test-review.md:964:    13	{"id":"def-cross-file-Circle",      "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":11,"column":20}}
live-test-review.md:965:    14	{"id":"refs-area-from-total",     "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":84,"column":38}}
live-test-review.md:966:    15	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":71,"column":9}}
live-test-review.md:967:    16	{"id":"refs-shape-trait",         "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test-review.md:968:    17	{"id":"refs-circle-radius",          "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":14,"column":9}}
live-test-review.md:969:    18	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
live-test-review.md:970:    19	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
live-test-review.md:971:    20	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
live-test-review.md:972:    21	{"id":"impls-generic-bound-coercer", "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":50,"column":27}}
live-test-review.md:973:    22	{"id":"type-hier-circle",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":13,"column":12}}
live-test-review.md:974:    23	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":33,"column":12}}
live-test-review.md:975:    24	{"id":"type-hier-shape-trait",    "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test-review.md:976:    25	{"id":"call-hier-make-default",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":2}}
live-test-review.md:977:    26	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":6,"column":8,"direction":"callers","maxDepth":2}}
live-test-review.md:978:    27	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test-review.md:979:    28	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test-review.md:980:    29	{"id":"find-class-Marker",          "tool":"ide_find_class",           "params":{"query":"Marker"}}
live-test-review.md:981:    30	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test-review.md:982:    31	{"id":"find-symbol-q_closure",    "tool":"ide_find_symbol",          "params":{"query":"q_closure"}}
live-test-review.md:983:    32	{"id":"find-symbol-extra-function", "tool":"ide_find_symbol",          "params":{"query":"extra_function"}}
live-test-review.md:984:    33	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.rs"}}
live-test-review.md:985:    34	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.rs"}}
live-test-review.md:986:    35	{"id":"file-structure-extra",     "tool":"ide_file_structure",       "params":{"file":"src/extra.rs"}}
live-test-review.md:987:    36	{"id":"refs-Drawable-trait",         "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":1,"column":11}}
live-test-review.md:988:    37	{"id":"refs-Rectangle-struct",       "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":33,"column":12}}
live-test-review.md:989:    38	{"id":"refs-Coercer-trait",          "tool":"ide_find_usages",          "params":{"file":"src/quirks.rs","line":28,"column":11}}
live-test-review.md:990:    39	{"id":"impls-Shape-trait",           "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test-review.md:991:    40	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":8,"column":8}}
live-test-review.md:992:    41	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":1,"column":11}}
live-test-review.md:993:    42	{"id":"type-hier-Square",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":54,"column":12}}
live-test-review.md:994:    43	{"id":"type-hier-CoerceMode",        "tool":"ide_type_hierarchy",       "params":{"file":"src/quirks.rs","line":66,"column":10}}
live-test-review.md:995:    44	{"id":"call-hier-total-area-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":83,"column":12,"direction":"callees","maxDepth":2}}
live-test-review.md:996:    45	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":87,"column":12,"direction":"callees","maxDepth":2}}
live-test-review.md:997:    46	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":8,"column":8,"direction":"callers","maxDepth":2}}
live-test-review.md:998:    47	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":2,"column":8,"direction":"callers","maxDepth":2}}
live-test-review.md:999:    48	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test-review.md:1000:    49	{"id":"find-class-Rectangle",        "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test-review.md:1001:    50	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:1002:    51	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:1003:    52	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test-review.md:1004:    53	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:1005:    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
live-test-review.md:1006:    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:1007:    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:1008:    57	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:1009:    58	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:1010:    59	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":54,"column":12,"direction":"supertypes"}}
live-test-review.md:1011:    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":5,"column":11,"direction":"subtypes"}}
live-test-review.md:1012:    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":3}}
live-test-review.md:1013:    62	{"id":"audit-find-symbol-qualified-IntCoercer-coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer::coerce"}}
live-test-review.md:1014:    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:1015:    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:1016:    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:3709:So far I see the expected/output drift is exactly the pre/post normalization shape in many rows, but I’m not assuming all drift is harmless. Next I’m going to run a few mechanical checks: duplicate probe IDs, row-count alignment, remaining absolute paths, and what token each line/column actually lands on.
live-test-review.md:3800:    print(f'{lang}: inputs={len(inputs)} expected={sum(1 for l in exp.read_text().splitlines() if l.strip()) if exp.exists() else \"missing\"} output={sum(1 for l in out.read_text().splitlines() if l.strip()) if out.exists() else \"missing\"} duplicate_ids={dups or \"none\"}')
live-test-review.md:3823:java: inputs=83 expected=83 output=83 duplicate_ids=none
live-test-review.md:3824:kotlin: inputs=67 expected=67 output=67 duplicate_ids=none
live-test-review.md:3825:python: inputs=76 expected=76 output=76 duplicate_ids=none
live-test-review.md:3826:javascript: inputs=71 expected=71 output=71 duplicate_ids=none
live-test-review.md:3827:typescript: inputs=66 expected=66 output=66 duplicate_ids=none
live-test-review.md:3828:php: inputs=70 expected=70 output=70 duplicate_ids=none
live-test-review.md:3829:go: inputs=57 expected=57 output=57 duplicate_ids=none
live-test-review.md:3830:rust: inputs=65 expected=65 output=65 duplicate_ids=none
live-test-review.md:4407:     1	{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
live-test-review.md:4408:     2	{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
live-test-review.md:4409:     3	{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
live-test-review.md:4410:     4	{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
live-test-review.md:4411:     5	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
live-test-review.md:4412:     6	{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
live-test-review.md:4413:     7	{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
live-test-review.md:4414:     8	{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
live-test-review.md:4415:     9	{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
live-test-review.md:4416:    10	{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
live-test-review.md:4417:    11	{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
live-test-review.md:4418:    12	{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
live-test-review.md:4435:    50	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test-review.md:4436:    51	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test-review.md:4437:    52	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test-review.md:4438:    53	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:4439:    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
live-test-review.md:4440:    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:4441:    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:4442:    57	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:4443:    58	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:4444:    59	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":54,"column":12,"direction":"supertypes"}}
live-test-review.md:4445:    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":5,"column":11,"direction":"subtypes"}}
live-test-review.md:4446:    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":3}}
live-test-review.md:4447:    62	{"id":"audit-find-symbol-qualified-IntCoercer-coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer::coerce"}}
live-test-review.md:4448:    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:4449:    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:4450:    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:4467:    45	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:4468:    46	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:4469:    47	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test-review.md:4470:    48	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":40,"column":7,"direction":"supertypes"}}
live-test-review.md:4471:    49	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":9,"column":6,"direction":"subtypes"}}
live-test-review.md:4472:    50	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":3}}
live-test-review.md:4473:    51	{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer.Coerce"}}
live-test-review.md:4474:    52	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:4475:    53	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:4476:    54	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:4477:    55	{"id":"audit-refs-baseShape-Describe-promoted","tool":"ide_find_usages","params":{"file":"normal.go","line":16,"column":20}}
live-test-review.md:4478:    56	{"id":"audit-find-class-prefix-Shape","tool":"ide_find_class","params":{"query":"Shape","matchMode":"prefix"}}
live-test-review.md:4479:    57	{"id":"audit-find-class-exact-Circle","tool":"ide_find_class","params":{"query":"Circle","matchMode":"exact"}}
live-test-review.md:4525:    77	{"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test-review.md:4526:    78	{"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test-review.md:4527:    79	{"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
live-test-review.md:4528:    80	{"id":"audit-super-CoerceMode-INT-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":72,"column":19}}
live-test-review.md:4529:    81	{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
live-test-review.md:4544:    63	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":40,"column":7,"direction":"supertypes"}}
live-test-review.md:4545:    64	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":8,"column":16,"direction":"subtypes"}}
live-test-review.md:4546:    65	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":3}}
live-test-review.md:4547:    66	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}
live-test-review.md:4548:    67	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:4549:    68	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:4550:    69	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:4551:    70	{"id":"audit-refs-qNameRebind-callable-array","tool":"ide_find_usages","params":{"file":"src/Quirks.php","line":6,"column":28}}
live-test-review.md:4564:    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":5,"column":23,"direction":"subtypes"}}
live-test-review.md:4565:    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":3}}
live-test-review.md:4566:    62	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test-review.md:4567:    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test-review.md:4568:    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test-review.md:4569:    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test-review.md:4570:    66	{"id":"audit-type-hier-Coerce-alias","tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":60,"column":13}}
live-test-review.md:4609:    14	{"id":"refs-area-from-total",     "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":84,"column":38}}
live-test-review.md:4610:    15	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":71,"column":9}}
live-test-review.md:4611:    16	{"id":"refs-shape-trait",         "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test-review.md:4612:    17	{"id":"refs-circle-radius",          "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":14,"column":9}}
live-test-review.md:4613:    18	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
live-test-review.md:4614:    19	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
live-test-review.md:4615:    20	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
live-test-review.md:4616:    52	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test-review.md:4617:    53	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test-review.md:4618:    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
live-test-review.md:4619:    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:4620:    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test-review.md:4637:    60	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
live-test-review.md:4638:    61	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:4645:    65	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"scope":"project_and_libraries"}}
live-test-review.md:4646:    66	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:4649:    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18,"scope":"project_and_libraries"}}
live-test-review.md:4650:    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test-review.md:4749:    33	./run.py --bless                  # rewrite expected.jsonl from server output
live-test-review.md:4751:    35	./run.py --tool ide_find_definition   # one tool across all languages
live-test-review.md:4771:    55	   - Intentional: `./live-test/run.py --bless` and commit alongside the
live-test-review.md:4777:    61	responses without re-blessing or scraping diff output.
live-test-review.md:4788:    72	- **`MISSING (no expected.jsonl line N)`** — `expected.jsonl` is shorter
live-test-review.md:4790:    74	  haven't blessed yet. Run `--bless` to regenerate.
live-test-review.md:4792:    76	  toolchain change shifted JDK source line numbers. Re-bless once
live-test-review.md:4798:    82	*captured intentionally* — re-blessing is not the right response when
live-test-review.md:4840:   124	  changes when the toolchain changes; re-bless is the right response.
live-test-review.md:4881:     7	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":6,"column":14}}
live-test-review.md:4882:     8	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":36,"column":14}}
live-test-review.md:4883:     9	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test-review.md:4891:    10	{"id":"def-quirks-proxy-call-source",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":80,"column":31}}
live-test-review.md:4892:    11	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":8,"column":5}}
live-test-review.md:4893:    12	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":39,"column":14}}
live-test-review.md:4894:    13	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":7,"column":7}}
live-test-review.md:4895:    14	{"id":"impls-shape-area-direct-overrides-only","tool":"ide_find_implementations","params":{"file":"src/normal.js","line":8,"column":5}}
live-test-review.md:4919:     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":9,"column":30}}
live-test-review.md:4920:    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":48,"column":18}}
live-test-review.md:4921:    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test-review.md:4922:    12	{"id":"refs-Quirks-class",        "tool":"ide_find_usages",      "params":{"file":"src/Quirks.php","line":4,"column":7}}
live-test-review.md:5100:     1	{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":23,"column":18}}
live-test-review.md:5101:     2	{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":34,"column":20}}
live-test-review.md:5102:     3	{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":72,"column":6}}
live-test-review.md:5103:     4	{"id":"def-quirks-fnvar-resolve",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":12,"column":10}}
live-test-review.md:5104:     5	{"id":"def-quirks-method-value",   "tool":"ide_find_definition", "params":{"file":"quirks.go","line":91,"column":10}}
live-test-review.md:5105:     6	{"id":"def-quirks-method-expr",    "tool":"ide_find_definition", "params":{"file":"quirks.go","line":97,"column":19}}
live-test-review.md:5106:     7	{"id":"def-quirks-type-switch-s",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":121,"column":24}}
live-test-review.md:5107:     8	{"id":"def-quirks-iface-dispatch", "tool":"ide_find_definition", "params":{"file":"quirks.go","line":66,"column":11}}
live-test-review.md:5108:     9	{"id":"def-drawable-draw",        "tool":"ide_find_definition", "params":{"file":"normal.go","line":6,"column":2}}
live-test-review.md:5124:- **BUG** `--tool` mode compares and blesses the wrong rows. `inputs` are filtered in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225), but `expected_lines` stays unfiltered and is indexed by the filtered loop index in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246). Example: `--tool ide_find_class --language java` starts with [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), but compares it to [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:1). Worse, `--bless --tool ...` writes only the filtered rows back to the full snapshot at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:270).
live-test-review.md:5126:- **BUG** The current diff state is not purely path rebless. After applying the current `normalize()` to committed expected rows, Kotlin still has semantic failures: [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:22) is `{"tool_error_text":"Failed to build supertype hierarchy"}` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:22), and [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:24) is `Browser refused element Coercion for SUBTYPES` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:24). Those are not path substitutions.
live-test-review.md:5128:- **BUG** `LIBRARY_PATH_SUBS` is not portable outside this Linux `/home` + Toolbox layout. Every specific path regex and the catch-all are anchored to `/home/[^/]+` or Linux Toolbox paths in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50). macOS `/Users/...`, Windows `C:\Users\...`, `/Applications/...`, `/opt/...`, `/usr/lib/jvm/...`, SDKMAN JDKs, rustup sources, and non-Toolbox IDE installs will leak or partially normalize into unstable `${HOME}/...` paths instead of stable SDK tokens.
live-test-review.md:5130:- **BUG** Extra committed snapshot rows are never detected. The harness only checks `expected.jsonl` being too short in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246); after the loop there is no check for `len(expected_lines) > len(inputs)` before writing output at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:267). Removing an input row can leave stale blessed output behind while the run still passes.
live-test-review.md:5132:- **BUG** Qualified symbol snapshots bless obvious false positives. `Shape.area` / `Shape::area` queries include `ShapeCollection.totalArea`, which is not the qualified target: [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:73) vs [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:73), [javascript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/expected.jsonl:64), [typescript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/typescript/expected.jsonl:62), and [php/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/expected.jsonl:66).
live-test-review.md:5134:- **BUG** Java `audit-super-CoerceMode-INT-apply` does not test what its ID claims. The probe points at `apply` in the enum constant body in [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:80), but the blessed result is a `CONSTRUCTOR` for `CoerceMode` with empty hierarchy in [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:80). That is not a super-method result for `apply`.
live-test-review.md:5136:- **SMELL** Several exact duplicate probes add no coverage and can hide row-maintenance mistakes: [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1) and line 2 are identical; [go/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:46) and line 57 are identical; [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:41) and line 82 are identical; [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:44) and line 74 are identical.
live-test-review.md:5142:I did not count the known pre-rebless absolute paths in `expected.jsonl` as findings. Current `output.jsonl` has no raw `/home/...` paths from the checked patterns.
live-test-review.md:5146:- **BUG** `--tool` mode compares and blesses the wrong rows. `inputs` are filtered in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225), but `expected_lines` stays unfiltered and is indexed by the filtered loop index in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246). Example: `--tool ide_find_class --language java` starts with [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), but compares it to [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:1). Worse, `--bless --tool ...` writes only the filtered rows back to the full snapshot at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:270).
live-test-review.md:5148:- **BUG** The current diff state is not purely path rebless. After applying the current `normalize()` to committed expected rows, Kotlin still has semantic failures: [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:22) is `{"tool_error_text":"Failed to build supertype hierarchy"}` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:22), and [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:24) is `Browser refused element Coercion for SUBTYPES` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:24). Those are not path substitutions.
live-test-review.md:5150:- **BUG** `LIBRARY_PATH_SUBS` is not portable outside this Linux `/home` + Toolbox layout. Every specific path regex and the catch-all are anchored to `/home/[^/]+` or Linux Toolbox paths in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50). macOS `/Users/...`, Windows `C:\Users\...`, `/Applications/...`, `/opt/...`, `/usr/lib/jvm/...`, SDKMAN JDKs, rustup sources, and non-Toolbox IDE installs will leak or partially normalize into unstable `${HOME}/...` paths instead of stable SDK tokens.
live-test-review.md:5152:- **BUG** Extra committed snapshot rows are never detected. The harness only checks `expected.jsonl` being too short in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246); after the loop there is no check for `len(expected_lines) > len(inputs)` before writing output at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:267). Removing an input row can leave stale blessed output behind while the run still passes.
live-test-review.md:5154:- **BUG** Qualified symbol snapshots bless obvious false positives. `Shape.area` / `Shape::area` queries include `ShapeCollection.totalArea`, which is not the qualified target: [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:73) vs [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:73), [javascript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/expected.jsonl:64), [typescript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/typescript/expected.jsonl:62), and [php/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/expected.jsonl:66).
live-test-review.md:5156:- **BUG** Java `audit-super-CoerceMode-INT-apply` does not test what its ID claims. The probe points at `apply` in the enum constant body in [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:80), but the blessed result is a `CONSTRUCTOR` for `CoerceMode` with empty hierarchy in [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:80). That is not a super-method result for `apply`.
live-test-review.md:5158:- **SMELL** Several exact duplicate probes add no coverage and can hide row-maintenance mistakes: [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1) and line 2 are identical; [go/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:46) and line 57 are identical; [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:41) and line 82 are identical; [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:44) and line 74 are identical.
live-test-review.md:5164:I did not count the known pre-rebless absolute paths in `expected.jsonl` as findings. Current `output.jsonl` has no raw `/home/...` paths from the checked patterns.

exec
/usr/bin/zsh -lc 'for f in live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/{input,expected,output}.jsonl; do [ -f "$f" ] && printf '"'%s\\n' \""'$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/java/input.jsonl
live-test/java/expected.jsonl
live-test/java/output.jsonl
live-test/kotlin/input.jsonl
live-test/kotlin/expected.jsonl
live-test/kotlin/output.jsonl
live-test/python/input.jsonl
live-test/python/expected.jsonl
live-test/python/output.jsonl
live-test/javascript/input.jsonl
live-test/javascript/expected.jsonl
live-test/javascript/output.jsonl
live-test/typescript/input.jsonl
live-test/typescript/expected.jsonl
live-test/typescript/output.jsonl
live-test/php/input.jsonl
live-test/php/expected.jsonl
live-test/php/output.jsonl
live-test/go/input.jsonl
live-test/go/expected.jsonl
live-test/go/output.jsonl
live-test/rust/input.jsonl
live-test/rust/expected.jsonl
live-test/rust/output.jsonl

exec
/usr/bin/zsh -lc "nl -ba live-test/run.py | sed -n '378,430p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
   378	    total_pass = total_fail = 0
   379	    for lang in langs:
   380	        if args.url:
   381	            url = args.url
   382	        else:
   383	            port = PORT_BY_LANG.get(lang)
   384	            if port is None:
   385	                print(f"No port mapped for language '{lang}'", file=sys.stderr)
   386	                return 1
   387	            url = f"http://127.0.0.1:{port}/index-mcp/streamable-http"
   388	        passed, failed = run_language(
   389	            lang, root / lang, url, args.tool, args.bless
   390	        )
   391	        total_pass += passed
   392	        total_fail += failed
   393	
   394	    print(f"ALL: {total_pass} passed, {total_fail} failed")
   395	    return 0 if total_fail == 0 else 1
   396	
   397	
   398	if __name__ == "__main__":
   399	    sys.exit(main())

codex
The harness changes for id-keying are present in `run.py`; I’m now checking the JSONL shape and the current snapshot state mechanically so the review doesn’t rely on eyeballing.
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
root=Path('live-test')
langs=['java','kotlin','python','javascript','typescript','php','go','rust']
for lang in langs:
    for kind in ['input','expected','output']:
        p=root/lang/f'{kind}.jsonl'
        if not p.exists():
            continue
        total=blank=bad=with_id_result=with_id=old=0
        ids=[]
        rows=[]
        for n,line in enumerate(p.read_text().splitlines(),1):
            if not line.strip():
                blank+=1; continue
            total+=1
            try: row=json.loads(line)
            except Exception as e:
                bad+=1; continue
            rows.append((n,row))
            if isinstance(row,dict) and 'id' in row:
                with_id+=1; ids.append(row.get('id'))
            if isinstance(row,dict) and set(['id','result']).issubset(row):
                with_id_result+=1
            elif kind in ('expected','output'):
                old+=1
        dup_ids=sorted({x for x in ids if ids.count(x)>1})
        print(f'{lang:10} {kind:8} total={total:3} id={with_id:3} id_result={with_id_result:3} old/other={old:3} bad={bad:2} dup_ids={dup_ids[:5]}')
    print()
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json, collections
from pathlib import Path
langs=['java','kotlin','python','javascript','typescript','php','go','rust']
for lang in langs:
    p=Path('live-test')/lang/'input.jsonl'
    rows=[]
    for n,line in enumerate(p.read_text().splitlines(),1):
        if line.strip():
            row=json.loads(line); rows.append((n,row))
    by_payload=collections.defaultdict(list)
    by_id=collections.defaultdict(list)
    missing=[]
    for n,row in rows:
        key=json.dumps({'tool':row.get('tool'),'params':row.get('params')}, sort_keys=True, separators=(',',':'))
        by_payload[key].append((n,row.get('id')))
        by_id[row.get('id')].append(n)
        if not isinstance(row.get('id'), str) or not row.get('id'):
            missing.append(n)
    dups={k:v for k,v in by_payload.items() if len(v)>1}
    dupids={k:v for k,v in by_id.items() if len(v)>1}
    if missing or dups or dupids:
        print(lang)
        if missing: print('  missing ids',missing)
        if dupids: print('  duplicate ids',dupids)
        for _,v in dups.items(): print('  duplicate payload',v)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "rg -n '(/home/|/Users/|C:\\\\|RustRover|Toolbox|pycharm|phpstorm|webstorm|\\.gradle|\\.cache|\\.local/share|\\.rustup|jdk|typeshed|stdlib|php\\.jar|kotlin-stdlib)' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/{expected,output}.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/rust/output.jsonl:6:{"id":"def-q-stdlib-parse","result":{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}}
live-test/rust/expected.jsonl:6:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:8:{"column":17,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
live-test/rust/expected.jsonl:10:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:25:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/rust/expected.jsonl:44:{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
live-test/rust/expected.jsonl:45:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
live-test/rust/expected.jsonl:61:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/kotlin/expected.jsonl:21:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:22:{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:23:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:24:{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:27:{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
live-test/kotlin/expected.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:61:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:62:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/java/output.jsonl:30:{"id":"call-hier-makeDefault","result":{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}}
live-test/java/output.jsonl:45:{"id":"def-ArrayList-via-new-jdk-call","result":{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}}
live-test/java/output.jsonl:72:{"id":"audit-call-hier-makeDefault-d3","result":{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}}
live-test/typescript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/java/expected.jsonl:24:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/expected.jsonl:25:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:26:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/expected.jsonl:27:{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
live-test/java/expected.jsonl:28:{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:30:{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/expected.jsonl:45:{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21/lib/src.zip!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
live-test/java/expected.jsonl:55:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:70:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/expected.jsonl:71:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/expected.jsonl:72:{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/expected.jsonl:79:{"hierarchy":[{"column":7,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
live-test/javascript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:7:{"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
live-test/javascript/expected.jsonl:8:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:10:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:71:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/php/expected.jsonl:4:{"column":10,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
live-test/php/expected.jsonl:27:{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
live-test/python/expected.jsonl:6:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:7:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
live-test/python/expected.jsonl:8:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/uv/python/cpython-3.11.14-linux-x86_64-gnu/lib/python3.11/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
live-test/python/expected.jsonl:9:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:10:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:11:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:12:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:14:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:26:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/expected.jsonl:27:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/expected.jsonl:28:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/expected.jsonl:29:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
live-test/python/expected.jsonl:31:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}
live-test/python/expected.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/expected.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}
live-test/python/expected.jsonl:50:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}
live-test/python/expected.jsonl:65:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/expected.jsonl:66:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/expected.jsonl:67:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
live-test/python/output.jsonl:5:{"id":"def-int-builtin-via-rebinding-rhs","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:6:{"id":"def-super-init","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}}
live-test/python/output.jsonl:8:{"id":"def-dict-dispatch-int","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:9:{"id":"def-lambda-wrap-int","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:10:{"id":"def-list-indexing-int","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:11:{"id":"def-quirk-rebound-fn","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:13:{"id":"def-multiple-assign-b","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:25:{"id":"type-hier-square","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}}
live-test/python/output.jsonl:26:{"id":"type-hier-rectangle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}}
live-test/python/output.jsonl:27:{"id":"type-hier-shape","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}}
live-test/python/output.jsonl:28:{"id":"call-hier-make-default","result":{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}}
live-test/python/output.jsonl:30:{"id":"call-hier-total-area","result":{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}}
live-test/python/output.jsonl:46:{"id":"type-hier-Circle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}}
live-test/python/output.jsonl:47:{"id":"type-hier-ShapeCollection","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}}
live-test/python/output.jsonl:49:{"id":"call-hier-largest-callees","result":{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}}
live-test/python/output.jsonl:64:{"id":"audit-type-hier-Square-supertypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}}
live-test/python/output.jsonl:65:{"id":"audit-type-hier-Shape-subtypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}}
live-test/python/output.jsonl:66:{"id":"audit-call-hier-makeDefault-d3","result":{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}}

exec
/usr/bin/zsh -lc "head -n 3 live-test/java/expected.jsonl; printf '\\n--- output ---\\n'; head -n 3 live-test/java/output.jsonl; printf '\\n--- input ---\\n'; head -n 3 live-test/java/input.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}
{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}
{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"makeDefaultShapes","qualifiedName":"demo.Normal#makeDefaultShapes"}

--- output ---
{"id":"def-circle-area-decl","result":{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}}
{"id":"def-rectangle-area-decl","result":{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}}
{"id":"def-makeDefaultShapes-decl","result":{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"makeDefaultShapes","qualifiedName":"demo.Normal#makeDefaultShapes"}}

--- input ---
{"id":"def-circle-area-decl",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
{"id":"def-rectangle-area-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
{"id":"def-makeDefaultShapes-decl","tool":"ide_find_definition",     "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31}}

 succeeded in 147ms:
python
  duplicate payload [(67, 'audit-find-symbol-qualified-Coercer-coerce'), (73, 'audit-find-symbol-Coercer-coerce')]

 succeeded in 166ms:
java       input    total= 81 id= 81 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
java       expected total= 83 id=  0 id_result=  0 old/other= 83 bad= 0 dup_ids=[]
java       output   total= 81 id= 81 id_result= 81 old/other=  0 bad= 0 dup_ids=[]

kotlin     input    total= 67 id= 67 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
kotlin     expected total= 67 id=  0 id_result=  0 old/other= 67 bad= 0 dup_ids=[]
kotlin     output   total= 67 id= 67 id_result= 67 old/other=  0 bad= 0 dup_ids=[]

python     input    total= 74 id= 74 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
python     expected total= 76 id=  0 id_result=  0 old/other= 76 bad= 0 dup_ids=[]
python     output   total= 74 id= 74 id_result= 74 old/other=  0 bad= 0 dup_ids=[]

javascript input    total= 71 id= 71 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
javascript expected total= 71 id=  0 id_result=  0 old/other= 71 bad= 0 dup_ids=[]
javascript output   total= 71 id= 71 id_result= 71 old/other=  0 bad= 0 dup_ids=[]

typescript input    total= 66 id= 66 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
typescript expected total= 66 id=  0 id_result=  0 old/other= 66 bad= 0 dup_ids=[]
typescript output   total= 66 id= 66 id_result= 66 old/other=  0 bad= 0 dup_ids=[]

php        input    total= 70 id= 70 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
php        expected total= 70 id=  0 id_result=  0 old/other= 70 bad= 0 dup_ids=[]
php        output   total= 70 id= 70 id_result= 70 old/other=  0 bad= 0 dup_ids=[]

go         input    total= 56 id= 56 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
go         expected total= 57 id=  0 id_result=  0 old/other= 57 bad= 0 dup_ids=[]
go         output   total= 56 id= 56 id_result= 56 old/other=  0 bad= 0 dup_ids=[]

rust       input    total= 65 id= 65 id_result=  0 old/other=  0 bad= 0 dup_ids=[]
rust       expected total= 65 id=  0 id_result=  0 old/other= 65 bad= 0 dup_ids=[]
rust       output   total= 65 id= 65 id_result= 65 old/other=  0 bad= 0 dup_ids=[]


exec
/usr/bin/zsh -lc "rg -n 'tool_error_text|jsonrpc_error|transport_error|Browser refused|Failed to build|No symbol|Cannot|error|message' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/{expected,output}.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/java/output.jsonl:48:{"id":"diagnostics-Quirks-errors","result":{"analysisFresh":true,"analysisMessage":"Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":0,"problems":[],"testResults":null,"testResultsTruncated":null,"testSummary":null}}
live-test/java/output.jsonl:81:{"id":"audit-diagnostics-warnings","result":{"analysisFresh":true,"analysisMessage":"Closed-file diagnostics use public batch analysis; weak warnings are only available when the file is open in an editor. Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":10,"problems":[{"column":12,"endColumn":15,"endLine":7,"file":"src/main/java/demo/Normal.java","line":7,"message":"Method 'draw()' is never used","severity":"WARNING"},{"column":12,"endColumn":19,"endLine":13,"file":"src/main/java/demo/Normal.java","line":13,"message":"Method 'describe()' is never used","severity":"WARNING"},{"column":7,"endColumn":21,"endLine":62,"file":"src/main/java/demo/Normal.java","line":62,"message":"Class 'ShapeCollection' is never used","severity":"WARNING"},{"column":10,"endColumn":12,"endLine":65,"file":"src/main/java/demo/Normal.java","line":65,"message":"Method 'add(demo.Shape)' is never used","severity":"WARNING"},{"column":12,"endColumn":20,"endLine":69,"file":"src/main/java/demo/Normal.java","line":69,"message":"Method 'totalArea()' is never used","severity":"WARNING"},{"column":11,"endColumn":17,"endLine":77,"file":"src/main/java/demo/Normal.java","line":77,"message":"Method 'largest()' is never used","severity":"WARNING"},{"column":14,"endColumn":19,"endLine":86,"file":"src/main/java/demo/Normal.java","line":86,"message":"Class 'Normal' is never used","severity":"WARNING"},{"column":24,"endColumn":28,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Class 'Shape' is exposed outside its defined visibility scope","severity":"WARNING"},{"column":31,"endColumn":47,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Method 'makeDefaultShapes()' is never used","severity":"WARNING"},{"column":16,"endColumn":28,"endLine":96,"file":"src/main/java/demo/Normal.java","line":96,"message":"Method 'classifyShape(demo.Shape)' is never used","severity":"WARNING"}],"testResults":null,"testResultsTruncated":null,"testSummary":null}}
live-test/java/expected.jsonl:83:{"analysisFresh":true,"analysisMessage":"Closed-file diagnostics use public batch analysis; weak warnings are only available when the file is open in an editor. Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":10,"problems":[{"column":12,"endColumn":15,"endLine":7,"file":"src/main/java/demo/Normal.java","line":7,"message":"Method 'draw()' is never used","severity":"WARNING"},{"column":12,"endColumn":19,"endLine":13,"file":"src/main/java/demo/Normal.java","line":13,"message":"Method 'describe()' is never used","severity":"WARNING"},{"column":7,"endColumn":21,"endLine":62,"file":"src/main/java/demo/Normal.java","line":62,"message":"Class 'ShapeCollection' is never used","severity":"WARNING"},{"column":10,"endColumn":12,"endLine":65,"file":"src/main/java/demo/Normal.java","line":65,"message":"Method 'add(demo.Shape)' is never used","severity":"WARNING"},{"column":12,"endColumn":20,"endLine":69,"file":"src/main/java/demo/Normal.java","line":69,"message":"Method 'totalArea()' is never used","severity":"WARNING"},{"column":11,"endColumn":17,"endLine":77,"file":"src/main/java/demo/Normal.java","line":77,"message":"Method 'largest()' is never used","severity":"WARNING"},{"column":14,"endColumn":19,"endLine":86,"file":"src/main/java/demo/Normal.java","line":86,"message":"Class 'Normal' is never used","severity":"WARNING"},{"column":24,"endColumn":28,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Class 'Shape' is exposed outside its defined visibility scope","severity":"WARNING"},{"column":31,"endColumn":47,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Method 'makeDefaultShapes()' is never used","severity":"WARNING"},{"column":16,"endColumn":28,"endLine":96,"file":"src/main/java/demo/Normal.java","line":96,"message":"Method 'classifyShape(demo.Shape)' is never used","severity":"WARNING"}],"testResults":null,"testResultsTruncated":null,"testSummary":null}

exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    for kind in ['expected','output']:
        p=Path('live-test')/lang/f'{kind}.jsonl'
        if not p.exists(): continue
        hits=[]
        for n,line in enumerate(p.read_text().splitlines(),1):
            if not line.strip(): continue
            row=json.loads(line)
            result=row.get('result') if isinstance(row,dict) and 'result' in row else row
            if isinstance(result,dict) and any(k in result for k in ['tool_error_text','jsonrpc_error','transport_error','error']):
                hits.append((n,result))
        if hits:
            print(lang, kind)
            for n,r in hits[:20]: print(' ', n, r)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json, collections
from pathlib import Path
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    p=Path('live-test')/lang/'input.jsonl'
    counts=collections.Counter()
    for line in p.read_text().splitlines():
        if line.strip(): counts[json.loads(line)['tool']]+=1
    print(lang, dict(sorted(counts.items())))
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    p=Path('live-test')/lang/'output.jsonl'
    if not p.exists(): continue
    print('\\n'+lang)
    for n,line in enumerate(p.read_text().splitlines(),1):
        row=json.loads(line); rid=row['id']; res=row['result']
        if isinstance(res,dict):
            keys=sorted(res.keys())
            if 'tool_error_text' in res or 'jsonrpc_error' in res or 'transport_error' in res:
                print(n, rid, res)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 145ms:
java {'ide_call_hierarchy': 7, 'ide_diagnostics': 2, 'ide_file_structure': 3, 'ide_find_class': 12, 'ide_find_definition': 14, 'ide_find_implementations': 7, 'ide_find_super_methods': 7, 'ide_find_symbol': 9, 'ide_find_usages': 9, 'ide_index_status': 1, 'ide_type_hierarchy': 10}
kotlin {'ide_call_hierarchy': 7, 'ide_file_structure': 2, 'ide_find_class': 12, 'ide_find_definition': 8, 'ide_find_implementations': 6, 'ide_find_super_methods': 6, 'ide_find_symbol': 9, 'ide_find_usages': 8, 'ide_type_hierarchy': 9}
python {'ide_call_hierarchy': 7, 'ide_file_structure': 2, 'ide_find_class': 11, 'ide_find_definition': 15, 'ide_find_implementations': 6, 'ide_find_super_methods': 5, 'ide_find_symbol': 10, 'ide_find_usages': 10, 'ide_type_hierarchy': 8}
javascript {'ide_call_hierarchy': 7, 'ide_file_structure': 3, 'ide_find_class': 11, 'ide_find_definition': 13, 'ide_find_implementations': 6, 'ide_find_super_methods': 5, 'ide_find_symbol': 9, 'ide_find_usages': 9, 'ide_type_hierarchy': 8}
typescript {'ide_call_hierarchy': 7, 'ide_file_structure': 2, 'ide_find_class': 11, 'ide_find_definition': 9, 'ide_find_implementations': 6, 'ide_find_super_methods': 4, 'ide_find_symbol': 9, 'ide_find_usages': 8, 'ide_type_hierarchy': 10}
php {'ide_call_hierarchy': 7, 'ide_file_structure': 3, 'ide_find_class': 12, 'ide_find_definition': 8, 'ide_find_implementations': 6, 'ide_find_super_methods': 6, 'ide_find_symbol': 9, 'ide_find_usages': 9, 'ide_type_hierarchy': 10}
go {'ide_call_hierarchy': 7, 'ide_file_structure': 2, 'ide_find_class': 12, 'ide_find_definition': 9, 'ide_find_symbol': 9, 'ide_find_usages': 9, 'ide_type_hierarchy': 8}
rust {'ide_call_hierarchy': 7, 'ide_file_structure': 3, 'ide_find_class': 11, 'ide_find_definition': 13, 'ide_find_implementations': 6, 'ide_find_symbol': 9, 'ide_find_usages': 8, 'ide_type_hierarchy': 8}

 succeeded in 143ms:

java

kotlin

python

javascript

typescript

php

go

rust

 succeeded in 154ms:
exec
/usr/bin/zsh -lc "nl -ba live-test/java/input.jsonl | sed -n '1,90p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area-decl",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
     2	{"id":"def-rectangle-area-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
     3	{"id":"def-makeDefaultShapes-decl","tool":"ide_find_definition",     "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31}}
     4	{"id":"def-parse-string-overload","tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":16}}
     5	{"id":"def-parse-stringint-overload","tool":"ide_find_definition",   "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":30}}
     6	{"id":"def-CoerceMode-INT-apply", "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":78,"column":31}}
     7	{"id":"def-quirkVar-coerce",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":18,"column":16}}
     8	{"id":"def-Point-record-decl",   "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
     9	{"id":"def-Animal-sealed-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
    10	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":72,"column":22}}
    11	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":63,"column":33}}
    12	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
    13	{"id":"refs-Circle-ctor",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":21,"column":5}}
    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21}}
    15	{"id":"impls-drawable-draw",       "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":7,"column":12}}
    16	{"id":"impls-functional-Coerce",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":15}}
    17	{"id":"impls-Coerce-via-lambda",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":68,"column":18}}
    18	{"id":"impls-CoerceMode-apply",   "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":74,"column":22}}
    19	{"id":"impls-coerce-run",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":28}}
    20	{"id":"impls-Animal-name",        "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Modern.java","line":9,"column":16}}
    21	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
    22	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
    23	{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":31,"column":19}}
    24	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7}}
    25	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
    26	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
    27	{"id":"type-hier-CoerceMode",     "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Quirks.java","line":71,"column":10}}
    28	{"id":"type-hier-Point",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
    29	{"id":"type-hier-Animal",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
    30	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":2}}
    31	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"direction":"callers","maxDepth":2}}
    32	{"id":"call-hier-parse-string-callers","tool":"ide_call_hierarchy",  "params":{"file":"src/main/java/demo/Quirks.java","line":86,"column":23,"direction":"callers","maxDepth":2}}
    33	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    34	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
    35	{"id":"find-class-Point",         "tool":"ide_find_class",           "params":{"query":"Point"}}
    36	{"id":"find-class-Animal",        "tool":"ide_find_class",           "params":{"query":"Animal"}}
    37	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    38	{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
    39	{"id":"find-symbol-parse",        "tool":"ide_find_symbol",          "params":{"query":"parse"}}
    40	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Normal.java"}}
    41	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Quirks.java"}}
    42	{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
    43	{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":90,"column":24}}
    44	{"id":"def-Point-via-new-record-call", "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Modern.java","line":21,"column":23}}
    45	{"id":"def-ArrayList-via-new-jdk-call","tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":88,"column":34}}
    46	{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
    47	{"id":"index-status",             "tool":"ide_index_status",         "params":{}}
    48	{"id":"diagnostics-Quirks-errors","tool":"ide_diagnostics",          "params":{"file":"src/main/java/demo/Quirks.java","severity":"errors"}}
    49	{"id":"refs-Drawable-interface",    "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
    50	{"id":"refs-Rectangle-class",      "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
    51	{"id":"super-Rectangle-draw",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":51,"column":19}}
    52	{"id":"super-Cat-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":13,"column":33}}
    53	{"id":"super-Dog-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":17,"column":33}}
    54	{"id":"type-hier-Drawable",        "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
    55	{"id":"type-hier-Circle",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":18,"column":7}}
    56	{"id":"call-hier-totalArea-callees","tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":69,"column":12,"direction":"callees","maxDepth":2}}
    57	{"id":"call-hier-describe-callers","tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":13,"column":12,"direction":"callers","maxDepth":2}}
    58	{"id":"call-hier-largest-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":77,"column":11,"direction":"callees","maxDepth":2}}
    59	{"id":"find-class-Circle",         "tool":"ide_find_class",           "params":{"query":"Circle"}}
    60	{"id":"find-class-Square",         "tool":"ide_find_class",           "params":{"query":"Square"}}
    61	{"id":"find-class-ShapeCollection","tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    62	{"id":"find-symbol-describe",      "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    63	{"id":"find-symbol-totalArea",     "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
    64	{"id":"find-symbol-draw",          "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    65	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"scope":"project_and_libraries"}}
    66	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    67	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    68	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    69	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    70	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7,"direction":"supertypes"}}
    71	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16,"direction":"subtypes"}}
    72	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":3}}
    73	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
    74	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    75	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    76	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    77	{"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
    78	{"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
    79	{"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
    80	{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
    81	{"id":"audit-diagnostics-warnings","tool":"ide_diagnostics","params":{"file":"src/main/java/demo/Normal.java","severity":"warnings"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/python/input.jsonl | sed -n '1,85p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 2ms:
     1	{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
     2	{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
     3	{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
     4	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
     5	{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
     6	{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
     7	{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
     8	{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
     9	{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
    10	{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
    11	{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
    12	{"id":"def-walrus-result",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":70,"column":16}}
    13	{"id":"def-multiple-assign-b",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":100,"column":19}}
    14	{"id":"def-nested-return-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":82,"column":12}}
    15	{"id":"refs-area-from-describe",    "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":12,"column":9}}
    16	{"id":"refs-shapes-field",          "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":49,"column":14}}
    17	{"id":"refs-shape-class",           "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":10,"column":7}}
    18	{"id":"refs-int-builtin-in-quirks", "tool":"ide_find_usages",      "params":{"file":"src/quirks.py","line":64,"column":20}}
    19	{"id":"refs-circle-radius",         "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":21,"column":14}}
    20	{"id":"impls-shape-area",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":12,"column":9}}
    21	{"id":"impls-drawable-draw",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":7,"column":9}}
    22	{"id":"super-Square-init",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":43,"column":9}}
    23	{"id":"super-circle-area",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":23,"column":9}}
    24	{"id":"super-rectangle-area",       "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":35,"column":9}}
    25	{"id":"type-hier-square",           "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":42,"column":7}}
    26	{"id":"type-hier-rectangle",        "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":30,"column":7}}
    27	{"id":"type-hier-shape",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":10,"column":7}}
    28	{"id":"call-hier-make-default",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":2}}
    29	{"id":"call-hier-area-callers",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":12,"column":9,"direction":"callers","maxDepth":2}}
    30	{"id":"call-hier-total-area",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":54,"column":9,"direction":"callees","maxDepth":2}}
    31	{"id":"find-class-Shape",           "tool":"ide_find_class",           "params":{"query":"Shape"}}
    32	{"id":"find-class-Rectangle",       "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
    33	{"id":"find-symbol-area",           "tool":"ide_find_symbol",          "params":{"query":"area"}}
    34	{"id":"find-symbol-quirk-rebind",   "tool":"ide_find_symbol",          "params":{"query":"quirk_name_rebinding"}}
    35	{"id":"file-structure-normal",      "tool":"ide_file_structure",       "params":{"file":"src/normal.py"}}
    36	{"id":"file-structure-quirks",      "tool":"ide_file_structure",       "params":{"file":"src/quirks.py"}}
    37	{"id":"refs-Drawable-protocol",      "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":6,"column":7}}
    38	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":30,"column":7}}
    39	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":10,"column":7}}
    40	{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":30,"column":7}}
    41	{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":19,"column":7}}
    42	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":15,"column":9}}
    43	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":26,"column":9}}
    44	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":38,"column":9}}
    45	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":6,"column":7}}
    46	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":19,"column":7}}
    47	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":47,"column":7}}
    48	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":15,"column":9,"direction":"callers","maxDepth":2}}
    49	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":57,"column":9,"direction":"callees","maxDepth":2}}
    50	{"id":"call-hier-add-callers",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":51,"column":9,"direction":"callers","maxDepth":2}}
    51	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    52	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    53	{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    54	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    55	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    56	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
    57	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    58	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
    59	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
    60	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    61	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    62	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    63	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    64	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":42,"column":7,"direction":"supertypes"}}
    65	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":10,"column":7,"direction":"subtypes"}}
    66	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":3}}
    67	{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
    68	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    69	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    70	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    71	{"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
    72	{"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
    73	{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
    74	{"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}

exec
/usr/bin/zsh -lc "nl -ba live-test/rust/input.jsonl | sed -n '1,75p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":23,"column":48}}
     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":44,"column":48}}
     3	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":94,"column":8}}
     4	{"id":"def-trait-object-coerce",     "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":47,"column":11}}
     5	{"id":"def-enum-variant-coerce-int", "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":74,"column":25}}
     6	{"id":"def-q-stdlib-parse",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":97,"column":21}}
     7	{"id":"def-q-fn-pointer",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":7,"column":35}}
     8	{"id":"def-q-box-dyn-fn",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":16,"column":25}}
     9	{"id":"def-CoerceMode-apply",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":81,"column":10}}
    10	{"id":"def-q-iter-parse",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":85,"column":25}}
    11	{"id":"def-drawable-draw-decl",      "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":2,"column":8}}
    12	{"id":"def-cross-mod-helper",       "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":7,"column":12}}
    13	{"id":"def-cross-file-Circle",      "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":11,"column":20}}
    14	{"id":"refs-area-from-total",     "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":84,"column":38}}
    15	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":71,"column":9}}
    16	{"id":"refs-shape-trait",         "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":5,"column":11}}
    17	{"id":"refs-circle-radius",          "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":14,"column":9}}
    18	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
    19	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
    20	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
    21	{"id":"impls-generic-bound-coercer", "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":50,"column":27}}
    22	{"id":"type-hier-circle",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":13,"column":12}}
    23	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":33,"column":12}}
    24	{"id":"type-hier-shape-trait",    "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":5,"column":11}}
    25	{"id":"call-hier-make-default",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":2}}
    26	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":6,"column":8,"direction":"callers","maxDepth":2}}
    27	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    28	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
    29	{"id":"find-class-Marker",          "tool":"ide_find_class",           "params":{"query":"Marker"}}
    30	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    31	{"id":"find-symbol-q_closure",    "tool":"ide_find_symbol",          "params":{"query":"q_closure"}}
    32	{"id":"find-symbol-extra-function", "tool":"ide_find_symbol",          "params":{"query":"extra_function"}}
    33	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.rs"}}
    34	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.rs"}}
    35	{"id":"file-structure-extra",     "tool":"ide_file_structure",       "params":{"file":"src/extra.rs"}}
    36	{"id":"refs-Drawable-trait",         "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":1,"column":11}}
    37	{"id":"refs-Rectangle-struct",       "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":33,"column":12}}
    38	{"id":"refs-Coercer-trait",          "tool":"ide_find_usages",          "params":{"file":"src/quirks.rs","line":28,"column":11}}
    39	{"id":"impls-Shape-trait",           "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":5,"column":11}}
    40	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":8,"column":8}}
    41	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":1,"column":11}}
    42	{"id":"type-hier-Square",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":54,"column":12}}
    43	{"id":"type-hier-CoerceMode",        "tool":"ide_type_hierarchy",       "params":{"file":"src/quirks.rs","line":66,"column":10}}
    44	{"id":"call-hier-total-area-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":83,"column":12,"direction":"callees","maxDepth":2}}
    45	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":87,"column":12,"direction":"callees","maxDepth":2}}
    46	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":8,"column":8,"direction":"callers","maxDepth":2}}
    47	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":2,"column":8,"direction":"callers","maxDepth":2}}
    48	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    49	{"id":"find-class-Rectangle",        "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
    50	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    51	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    52	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
    53	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    57	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    58	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    59	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":54,"column":12,"direction":"supertypes"}}
    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":5,"column":11,"direction":"subtypes"}}
    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":3}}
    62	{"id":"audit-find-symbol-qualified-IntCoercer-coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer::coerce"}}
    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/go/input.jsonl | sed -n '1,70p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":23,"column":18}}
     2	{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":34,"column":20}}
     3	{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":72,"column":6}}
     4	{"id":"def-quirks-fnvar-resolve",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":12,"column":10}}
     5	{"id":"def-quirks-method-value",   "tool":"ide_find_definition", "params":{"file":"quirks.go","line":91,"column":10}}
     6	{"id":"def-quirks-method-expr",    "tool":"ide_find_definition", "params":{"file":"quirks.go","line":97,"column":19}}
     7	{"id":"def-quirks-type-switch-s",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":121,"column":24}}
     8	{"id":"def-quirks-iface-dispatch", "tool":"ide_find_definition", "params":{"file":"quirks.go","line":66,"column":11}}
     9	{"id":"def-drawable-draw",        "tool":"ide_find_definition", "params":{"file":"normal.go","line":6,"column":2}}
    10	{"id":"refs-area-from-totalArea","tool":"ide_find_usages", "params":{"file":"normal.go","line":57,"column":12}}
    11	{"id":"refs-shapes-field",       "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":2}}
    12	{"id":"refs-shape-iface",        "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":11}}
    13	{"id":"refs-drawable-draw",      "tool":"ide_find_usages", "params":{"file":"normal.go","line":6,"column":2}}
    14	{"id":"refs-shape-collection-add","tool":"ide_find_usages","params":{"file":"normal.go","line":50,"column":28}}
    15	{"id":"type-hier-Circle",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":18,"column":6}}
    16	{"id":"type-hier-Rectangle",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":29,"column":6}}
    17	{"id":"type-hier-Square",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":40,"column":6}}
    18	{"id":"type-hier-baseShape",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":14,"column":6}}
    19	{"id":"call-hier-MakeDefault",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":2}}
    20	{"id":"call-hier-area-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":23,"column":18,"direction":"callers","maxDepth":2}}
    21	{"id":"find-class-Shape",        "tool":"ide_find_class",      "params":{"query":"Shape"}}
    22	{"id":"find-class-Rectangle",    "tool":"ide_find_class",      "params":{"query":"Rectangle"}}
    23	{"id":"find-class-Drawable",     "tool":"ide_find_class",      "params":{"query":"Drawable"}}
    24	{"id":"find-symbol-Area",        "tool":"ide_find_symbol",     "params":{"query":"Area"}}
    25	{"id":"find-symbol-qFnVar",      "tool":"ide_find_symbol",     "params":{"query":"qFnVar"}}
    26	{"id":"file-structure-normal",   "tool":"ide_file_structure",  "params":{"file":"normal.go"}}
    27	{"id":"file-structure-quirks",   "tool":"ide_file_structure",  "params":{"file":"quirks.go"}}
    28	{"id":"refs-Drawable-iface",         "tool":"ide_find_usages",     "params":{"file":"normal.go","line":5,"column":6}}
    29	{"id":"refs-Circle-struct",          "tool":"ide_find_usages",     "params":{"file":"normal.go","line":18,"column":6}}
    30	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":5,"column":6}}
    31	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":46,"column":6}}
    32	{"id":"call-hier-TotalArea-callees", "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":54,"column":28,"direction":"callees","maxDepth":2}}
    33	{"id":"call-hier-Describe-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":11,"column":2,"direction":"callers","maxDepth":2}}
    34	{"id":"call-hier-Draw-callers",      "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":6,"column":2,"direction":"callers","maxDepth":2}}
    35	{"id":"call-hier-Largest-callees",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":62,"column":28,"direction":"callees","maxDepth":2}}
    36	{"id":"find-class-Circle",           "tool":"ide_find_class",      "params":{"query":"Circle"}}
    37	{"id":"find-class-Square",           "tool":"ide_find_class",      "params":{"query":"Square"}}
    38	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",      "params":{"query":"ShapeCollection"}}
    39	{"id":"find-symbol-Describe",        "tool":"ide_find_symbol",     "params":{"query":"Describe"}}
    40	{"id":"find-symbol-TotalArea",       "tool":"ide_find_symbol",     "params":{"query":"TotalArea"}}
    41	{"id":"find-symbol-Draw",            "tool":"ide_find_symbol",     "params":{"query":"Draw"}}
    42	{"id":"find-symbol-Largest",         "tool":"ide_find_symbol",     "params":{"query":"Largest"}}
    43	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"normal.go","line":23,"column":18,"scope":"project_and_libraries"}}
    44	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"Area","pageSize":2}}
    45	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    46	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    47	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    48	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":40,"column":7,"direction":"supertypes"}}
    49	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":9,"column":6,"direction":"subtypes"}}
    50	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":3}}
    51	{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer.Coerce"}}
    52	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    53	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    54	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    55	{"id":"audit-refs-baseShape-Describe-promoted","tool":"ide_find_usages","params":{"file":"normal.go","line":16,"column":20}}
    56	{"id":"audit-find-class-prefix-Shape","tool":"ide_find_class","params":{"query":"Shape","matchMode":"prefix"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/kotlin/input.jsonl | sed -n '1,75p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5}}
     4	{"id":"def-extension-coerceTo",   "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":29,"column":42}}
     5	{"id":"def-infix-coerceFirst",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":59,"column":43}}
     6	{"id":"def-companion-create-call",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":23,"column":21}}
     7	{"id":"def-companion-DEFAULT-LIMIT",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":25,"column":20}}
     8	{"id":"def-suspend-fetchValue-call",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":17,"column":13}}
     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":32,"column":49}}
    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":26,"column":9}}
    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
    12	{"id":"refs-Circle-radius",       "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":18}}
    13	{"id":"refs-coerceFirst-callsite","tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":61,"column":29}}
    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18}}
    15	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":4,"column":9}}
    16	{"id":"impls-coercion",           "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
    17	{"id":"impls-Coercion-apply",     "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":38,"column":18}}
    18	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
    19	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
    20	{"id":"super-Circle-draw-from-Drawable","tool":"ide_find_super_methods","params":{"file":"src/main/kotlin/demo/Normal.kt","line":15,"column":18}}
    21	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7}}
    22	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
    23	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
    24	{"id":"type-hier-Coercion-sealed","tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
    25	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":2}}
    26	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18,"direction":"callers","maxDepth":2}}
    27	{"id":"call-hier-quirkApply-callees","tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":13,"column":5,"direction":"callees","maxDepth":2}}
    28	{"id":"call-hier-quirkLet-callees",  "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":17,"column":5,"direction":"callees","maxDepth":2}}
    29	{"id":"call-hier-quirkWith-callees", "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":21,"column":5,"direction":"callees","maxDepth":2}}
    30	{"id":"call-hier-computeTotal-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Modern.kt","line":16,"column":13,"direction":"callees","maxDepth":2}}
    31	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    32	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
    33	{"id":"find-class-Counter",       "tool":"ide_find_class",           "params":{"query":"Counter"}}
    34	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    35	{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
    36	{"id":"find-symbol-fetchValue",   "tool":"ide_find_symbol",          "params":{"query":"fetchValue"}}
    37	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Normal.kt"}}
    38	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Quirks.kt"}}
    39	{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
    40	{"id":"refs-Rectangle-class",       "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
    41	{"id":"impls-Shape-class",          "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
    42	{"id":"impls-Rectangle-class",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
    43	{"id":"super-Rectangle-draw",       "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":20,"column":18}}
    44	{"id":"super-IntCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":39,"column":50}}
    45	{"id":"super-AbsCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":40,"column":50}}
    46	{"id":"type-hier-Drawable",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
    47	{"id":"type-hier-Circle",           "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":7}}
    48	{"id":"type-hier-ShapeCollection",  "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":25,"column":7}}
    49	{"id":"find-class-Circle",          "tool":"ide_find_class",           "params":{"query":"Circle"}}
    50	{"id":"find-class-Square",          "tool":"ide_find_class",           "params":{"query":"Square"}}
    51	{"id":"find-class-ShapeCollection", "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    52	{"id":"find-class-Drawable",        "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    53	{"id":"find-symbol-describe",       "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    54	{"id":"find-symbol-totalArea",      "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
    55	{"id":"find-symbol-draw",           "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18,"scope":"project_and_libraries"}}
    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    58	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    59	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    60	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    61	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7,"direction":"supertypes"}}
    62	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16,"direction":"subtypes"}}
    63	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":3}}
    64	{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
    65	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    66	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    67	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/javascript/input.jsonl | sed -n '1,80p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":17,"column":5}}
     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":27,"column":5}}
     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":56,"column":10}}
     4	{"id":"def-quirks-fn-localbind",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":6,"column":12}}
     5	{"id":"def-quirks-parseInt-property-on-Number","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23}}
     6	{"id":"def-quirks-parseint-destructured","tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":37,"column":12}}
     7	{"id":"def-quirks-destructured-Number-receiver","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":36,"column":29}}
     8	{"id":"def-quirks-parseint-optional",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":71,"column":20}}
     9	{"id":"def-quirks-proxy-call",           "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":82,"column":18}}
    10	{"id":"def-quirks-proxy-call-source",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":80,"column":31}}
    11	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":8,"column":5}}
    12	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":39,"column":14}}
    13	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":7,"column":7}}
    14	{"id":"impls-shape-area-direct-overrides-only","tool":"ide_find_implementations","params":{"file":"src/normal.js","line":8,"column":5}}
    15	{"id":"impls-Drawable-draw",             "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":4,"column":5}}
    16	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":17,"column":5}}
    17	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":27,"column":5}}
    18	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":31,"column":7}}
    19	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":21,"column":7}}
    20	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":7,"column":7}}
    21	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":2}}
    22	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":8,"column":5,"direction":"callers","maxDepth":2}}
    23	{"id":"call-hier-qAwait-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/quirks.js","line":64,"column":16,"direction":"callees","maxDepth":2}}
    24	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    25	{"id":"find-class-Rectangle",     "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
    26	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    27	{"id":"find-symbol-qRebind",      "tool":"ide_find_symbol",          "params":{"query":"qRebind"}}
    28	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.js"}}
    29	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.js"}}
    30	{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":17}}
    31	{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":34}}
    32	{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":57,"column":17}}
    33	{"id":"refs-Drawable-class",         "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":3,"column":7}}
    34	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":21,"column":7}}
    35	{"id":"refs-describe-method",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":9,"column":5}}
    36	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":7,"column":7}}
    37	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":9,"column":5}}
    38	{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":21,"column":7}}
    39	{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":12,"column":7}}
    40	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":18,"column":5}}
    41	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":28,"column":5}}
    42	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":3,"column":7}}
    43	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":12,"column":7}}
    44	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":37,"column":7}}
    45	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":42,"column":5,"direction":"callees","maxDepth":2}}
    46	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":9,"column":5,"direction":"callers","maxDepth":2}}
    47	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":47,"column":5,"direction":"callees","maxDepth":2}}
    48	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    49	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    50	{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    51	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    52	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    53	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
    54	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    55	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.js","line":17,"column":5,"scope":"project_and_libraries"}}
    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    58	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    59	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    60	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    61	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":31,"column":7,"direction":"supertypes"}}
    62	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":7,"column":7,"direction":"subtypes"}}
    63	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":3}}
    64	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
    65	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    66	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    67	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    68	{"id":"audit-refs-Circle-cross-file","tool":"ide_find_usages","params":{"file":"src/normal.js","line":12,"column":7}}
    69	{"id":"audit-struct-accessors","tool":"ide_file_structure","params":{"file":"src/accessors.js"}}
    70	{"id":"audit-super-Box-area","tool":"ide_find_super_methods","params":{"file":"src/accessors.js","line":9,"column":9}}
    71	{"id":"audit-def-Number-parseInt-library","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23,"scope":"project_and_libraries"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/typescript/input.jsonl | sed -n '1,75p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":17,"column":5}}
     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":25,"column":5}}
     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":55,"column":17}}
     4	{"id":"def-quirks-parseint-from-typed-lambda","tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":3,"column":12}}
     5	{"id":"def-quirks-parseint-arg-cast",         "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":39,"column":19}}
     6	{"id":"def-T-typeparam-use",                  "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":53,"column":15}}
     7	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":6,"column":14}}
     8	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":36,"column":14}}
     9	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":5,"column":23}}
    10	{"id":"refs-Coercer-interface",               "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":42,"column":18}}
    11	{"id":"refs-aliasedCoerce",                   "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":62,"column":14}}
    12	{"id":"refs-Drawable-class",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":1,"column":18}}
    13	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":6,"column":14}}
    14	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":2,"column":5}}
    15	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.ts","line":42,"column":18}}
    16	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":17,"column":5}}
    17	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":25,"column":5}}
    18	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":29,"column":14}}
    19	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":21,"column":14}}
    20	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":5,"column":23}}
    21	{"id":"type-hier-TypedCoercer",               "tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":52,"column":14}}
    22	{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":1,"column":18}}
    23	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":2}}
    24	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":6,"column":14,"direction":"callers","maxDepth":2}}
    25	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    26	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
    27	{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    28	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    29	{"id":"find-symbol-qLambda",      "tool":"ide_find_symbol",          "params":{"query":"qLambda"}}
    30	{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.ts"}}
    31	{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.ts"}}
    32	{"id":"def-Circle-via-new-call",            "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":17}}
    33	{"id":"def-Rectangle-via-new-call",         "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":34}}
    34	{"id":"def-TypedCoercer-via-new-generic",   "tool":"ide_find_definition",  "params":{"file":"src/quirks.ts","line":57,"column":16}}
    35	{"id":"refs-Circle-via-new-call-site",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":56,"column":17}}
    36	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":5,"column":23}}
    37	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":8,"column":5}}
    38	{"id":"impls-Drawable-interface",    "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":1,"column":18}}
    39	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":18,"column":5}}
    40	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":26,"column":5}}
    41	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":13,"column":14}}
    42	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":35,"column":14}}
    43	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":40,"column":5,"direction":"callees","maxDepth":2}}
    44	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":8,"column":5,"direction":"callers","maxDepth":2}}
    45	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":46,"column":5,"direction":"callees","maxDepth":2}}
    46	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":2,"column":5,"direction":"callers","maxDepth":2}}
    47	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    48	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    49	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    50	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    51	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
    52	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    53	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.ts","line":6,"column":14,"scope":"project_and_libraries"}}
    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    57	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    58	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    59	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":29,"column":14,"direction":"supertypes"}}
    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":5,"column":23,"direction":"subtypes"}}
    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":3}}
    62	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    66	{"id":"audit-type-hier-Coerce-alias","tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":60,"column":13}}

exec
/usr/bin/zsh -lc "nl -ba live-test/php/input.jsonl | sed -n '1,80p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":19,"column":21}}
     2	{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":31,"column":21}}
     3	{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":73,"column":10}}
     4	{"id":"def-match-coerce",         "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":63,"column":32}}
     5	{"id":"def-self-class",           "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":33,"column":22}}
     6	{"id":"def-promoted-property",    "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":75,"column":20}}
     7	{"id":"def-Status-enum-decl",     "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":4,"column":6}}
     8	{"id":"def-Status-Active-usage",  "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":22,"column":20}}
     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":9,"column":30}}
    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":48,"column":18}}
    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":8,"column":16}}
    12	{"id":"refs-Quirks-class",        "tool":"ide_find_usages",      "params":{"file":"src/Quirks.php","line":4,"column":7}}
    13	{"id":"refs-Status-enum",         "tool":"ide_find_usages",      "params":{"file":"src/Modern.php","line":4,"column":6}}
    14	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":9,"column":30}}
    15	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":5,"column":21}}
    16	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":79,"column":11}}
    17	{"id":"impls-coerce-at-usage",    "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":71,"column":20}}
    18	{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":19,"column":21}}
    19	{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":31,"column":21}}
    20	{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":23,"column":21}}
    21	{"id":"super-IntCoercer-coerce",  "tool":"ide_find_super_methods",   "params":{"file":"src/Quirks.php","line":84,"column":21}}
    22	{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":40,"column":7}}
    23	{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":28,"column":7}}
    24	{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":8,"column":16}}
    25	{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":4,"column":11}}
    26	{"id":"type-hier-Coercer",        "tool":"ide_type_hierarchy",       "params":{"file":"src/Quirks.php","line":79,"column":11}}
    27	{"id":"type-hier-Status",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Modern.php","line":4,"column":6}}
    28	{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":2}}
    29	{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":9,"column":30,"direction":"callers","maxDepth":2}}
    30	{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
    31	{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
    32	{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    33	{"id":"find-class-Status",        "tool":"ide_find_class",           "params":{"query":"Status"}}
    34	{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
    35	{"id":"find-symbol-qNameRebind",  "tool":"ide_find_symbol",          "params":{"query":"qNameRebind"}}
    36	{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/Normal.php"}}
    37	{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/Quirks.php"}}
    38	{"id":"file-structure-Modern",    "tool":"ide_file_structure",       "params":{"file":"src/Modern.php"}}
    39	{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":4,"column":11}}
    40	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":28,"column":7}}
    41	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":8,"column":16}}
    42	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":11,"column":21}}
    43	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":35,"column":21}}
    44	{"id":"super-Square-construct",      "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":41,"column":21}}
    45	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":16,"column":7}}
    46	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":46,"column":7}}
    47	{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":54,"column":21,"direction":"callees","maxDepth":2}}
    48	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":11,"column":21,"direction":"callers","maxDepth":2}}
    49	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":62,"column":21,"direction":"callees","maxDepth":2}}
    50	{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":5,"column":21,"direction":"callers","maxDepth":2}}
    51	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    52	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    53	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    54	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    55	{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
    56	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    57	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
    58	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/Normal.php","line":9,"column":30,"scope":"project_and_libraries"}}
    59	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    60	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    61	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    62	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    63	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":40,"column":7,"direction":"supertypes"}}
    64	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":8,"column":16,"direction":"subtypes"}}
    65	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":3}}
    66	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}
    67	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    68	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    69	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    70	{"id":"audit-refs-qNameRebind-callable-array","tool":"ide_find_usages","params":{"file":"src/Quirks.php","line":6,"column":28}}

exec
/usr/bin/zsh -lc "sed -n '1,240p' live-test/README.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
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

exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    print('\\n'+lang)
    p=Path('live-test')/lang/'output.jsonl'
    for n,line in enumerate(p.read_text().splitlines(),1):
        row=json.loads(line); res=row['result']; rid=row['id']
        if row['id'].startswith('find-class') or row['id'].startswith('audit-find-class') or row['id'].startswith('find-symbol') or row['id'].startswith('audit-find-symbol'):
            if isinstance(res,dict):
                for key in ['classes','symbols','matches']:
                    if key in res and isinstance(res[key],list):
                        print(f'{n:2} {rid:45} {key}={len(res[key])}')
                        break
                else:
                    print(f'{n:2} {rid:45} keys={list(res)[:5]}')
            elif isinstance(res,list): print(f'{n:2} {rid:45} list={len(res)}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
fields=set(); keypaths={}
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    p=Path('live-test')/lang/'output.jsonl'
    for n,line in enumerate(p.read_text().splitlines(),1):
        row=json.loads(line); res=row['result']
        def walk(o,path=''):
            if isinstance(o,dict):
                for k,v in o.items():
                    fields.add(k); keypaths.setdefault(k, (lang,n,row['id'],path+'/'+k)); walk(v,path+'/'+k)
            elif isinstance(o,list):
                for x in o: walk(x,path+'[]')
        walk(res)
for k in sorted(fields):
    lang,n,rid,path=keypaths[k]
    print(f'{k:25} e.g. {lang}:{n}:{rid} {path}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
needles=['enclosingScope','qualifiedName','kind','name','line','column','message','analysisMessage','supertypes','subtypes','calls','children','problems','classes','symbols','matches']
counts={k:0 for k in needles}
for lang in ['java','kotlin','python','javascript','typescript','php','go','rust']:
    for line in (Path('live-test')/lang/'output.jsonl').read_text().splitlines():
        text=line
        for k in needles:
            if '\"'+k+'\"' in text: counts[k]+=1
print(counts)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 143ms:

java
33 find-class-Shape                              classes=2
34 find-class-Coercer                            classes=1
35 find-class-Point                              classes=1
36 find-class-Animal                             classes=1
37 find-symbol-area                              symbols=2
38 find-symbol-quirkLambda                       symbols=1
39 find-symbol-parse                             symbols=3
59 find-class-Circle                             classes=1
60 find-class-Square                             classes=1
61 find-class-ShapeCollection                    classes=1
62 find-symbol-describe                          symbols=1
63 find-symbol-totalArea                         symbols=1
64 find-symbol-draw                              symbols=2
66 audit-find-symbol-area-paged                  symbols=2
67 audit-find-class-Sh-prefix                    classes=2
68 audit-find-class-Circle-exact                 classes=1
69 audit-find-class-SC-camelCase                 classes=1
73 audit-find-symbol-qualified-Shape-area        symbols=2
74 audit-find-class-no-match                     classes=0
75 audit-find-symbol-no-match                    symbols=0
76 audit-find-class-Shape-wildcard               classes=2

kotlin
31 find-class-Shape                              classes=2
32 find-class-Coercer                            classes=1
33 find-class-Counter                            classes=1
34 find-symbol-area                              symbols=4
35 find-symbol-quirkLambda                       symbols=1
36 find-symbol-fetchValue                        symbols=1
49 find-class-Circle                             classes=1
50 find-class-Square                             classes=1
51 find-class-ShapeCollection                    classes=1
52 find-class-Drawable                           classes=1
53 find-symbol-describe                          symbols=1
54 find-symbol-totalArea                         symbols=1
55 find-symbol-draw                              symbols=4
57 audit-find-symbol-area-paged                  symbols=2
58 audit-find-class-Sh-prefix                    classes=2
59 audit-find-class-Circle-exact                 classes=1
60 audit-find-class-SC-camelCase                 classes=2
64 audit-find-symbol-qualified-Coercer-coerce    symbols=1
65 audit-find-class-no-match                     classes=0
66 audit-find-symbol-no-match                    symbols=0
67 audit-find-class-Shape-wildcard               classes=2

python
31 find-class-Shape                              classes=2
32 find-class-Rectangle                          classes=1
33 find-symbol-area                              symbols=4
34 find-symbol-quirk-rebind                      symbols=1
51 find-class-Circle                             classes=1
52 find-class-Square                             classes=1
53 find-class-Drawable                           classes=1
54 find-class-ShapeCollection                    classes=1
55 find-symbol-describe                          symbols=1
56 find-symbol-total_area                        symbols=1
57 find-symbol-draw                              symbols=4
58 find-symbol-largest                           symbols=1
60 audit-find-symbol-area-paged                  symbols=2
61 audit-find-class-Sh-prefix                    classes=2
62 audit-find-class-Circle-exact                 classes=1
63 audit-find-class-SC-camelCase                 classes=1
67 audit-find-symbol-qualified-Coercer-coerce    symbols=1
68 audit-find-class-no-match                     classes=0
69 audit-find-symbol-no-match                    symbols=0
70 audit-find-class-Shape-wildcard               classes=2
73 audit-find-symbol-Coercer-coerce              symbols=1

javascript
24 find-class-Shape                              classes=2
25 find-class-Rectangle                          classes=1
26 find-symbol-area                              symbols=5
27 find-symbol-qRebind                           symbols=2
48 find-class-Circle                             classes=1
49 find-class-Square                             classes=1
50 find-class-Drawable                           classes=1
51 find-class-ShapeCollection                    classes=1
52 find-symbol-describe                          symbols=1
53 find-symbol-totalArea                         symbols=1
54 find-symbol-draw                              symbols=5
55 find-symbol-largest                           symbols=1
57 audit-find-symbol-area-paged                  symbols=2
58 audit-find-class-Sh-prefix                    classes=2
59 audit-find-class-Circle-exact                 classes=1
60 audit-find-class-SC-camelCase                 classes=1
64 audit-find-symbol-qualified-Shape-area        symbols=2
65 audit-find-class-no-match                     classes=0
66 audit-find-symbol-no-match                    symbols=0
67 audit-find-class-Shape-wildcard               classes=2

typescript
25 find-class-Shape                              classes=2
26 find-class-Coercer                            classes=2
27 find-class-Drawable                           classes=1
28 find-symbol-area                              symbols=4
29 find-symbol-qLambda                           symbols=2
47 find-class-Circle                             classes=1
48 find-class-Square                             classes=1
49 find-class-ShapeCollection                    classes=1
50 find-symbol-describe                          symbols=1
51 find-symbol-totalArea                         symbols=1
52 find-symbol-draw                              symbols=4
53 find-symbol-largest                           symbols=1
55 audit-find-symbol-area-paged                  symbols=2
56 audit-find-class-Sh-prefix                    classes=2
57 audit-find-class-Circle-exact                 classes=1
58 audit-find-class-SC-camelCase                 classes=1
62 audit-find-symbol-qualified-Shape-area        symbols=2
63 audit-find-class-no-match                     classes=0
64 audit-find-symbol-no-match                    symbols=0
65 audit-find-class-Shape-wildcard               classes=2

php
30 find-class-Shape                              classes=2
31 find-class-Coercer                            classes=3
32 find-class-Drawable                           classes=1
33 find-class-Status                             classes=1
34 find-symbol-area                              symbols=4
35 find-symbol-qNameRebind                       symbols=1
51 find-class-Circle                             classes=1
52 find-class-Square                             classes=1
53 find-class-ShapeCollection                    classes=1
54 find-symbol-describe                          symbols=1
55 find-symbol-totalArea                         symbols=1
56 find-symbol-draw                              symbols=4
57 find-symbol-largest                           symbols=1
59 audit-find-symbol-area-paged                  symbols=2
60 audit-find-class-Sh-prefix                    classes=2
61 audit-find-class-Circle-exact                 classes=1
62 audit-find-class-SC-camelCase                 classes=1
66 audit-find-symbol-qualified-Shape-area        symbols=2
67 audit-find-class-no-match                     classes=0
68 audit-find-symbol-no-match                    symbols=0
69 audit-find-class-Shape-wildcard               classes=2

go
21 find-class-Shape                              classes=3
22 find-class-Rectangle                          classes=1
23 find-class-Drawable                           classes=1
24 find-symbol-Area                              symbols=4
25 find-symbol-qFnVar                            symbols=1
36 find-class-Circle                             classes=1
37 find-class-Square                             classes=1
38 find-class-ShapeCollection                    classes=1
39 find-symbol-Describe                          symbols=4
40 find-symbol-TotalArea                         symbols=1
41 find-symbol-Draw                              symbols=4
42 find-symbol-Largest                           symbols=1
44 audit-find-symbol-area-paged                  symbols=2
45 audit-find-class-Sh-prefix                    classes=2
46 audit-find-class-Circle-exact                 classes=1
47 audit-find-class-SC-camelCase                 classes=1
51 audit-find-symbol-qualified-IntCoercer-Coerce symbols=1
52 audit-find-class-no-match                     classes=0
53 audit-find-symbol-no-match                    symbols=0
54 audit-find-class-Shape-wildcard               classes=3
56 audit-find-class-prefix-Shape                 classes=2

rust
27 find-class-Shape                              classes=2
28 find-class-Coercer                            classes=3
29 find-class-Marker                             classes=1
30 find-symbol-area                              symbols=5
31 find-symbol-q_closure                         symbols=1
32 find-symbol-extra-function                    symbols=1
48 find-class-Circle                             classes=1
49 find-class-Rectangle                          classes=1
50 find-class-Square                             classes=1
51 find-symbol-describe                          symbols=3
52 find-symbol-total_area                        symbols=1
53 find-symbol-draw                              symbols=4
55 audit-find-symbol-area-paged                  symbols=2
56 audit-find-class-Sh-prefix                    classes=2
57 audit-find-class-Circle-exact                 classes=1
58 audit-find-class-SC-camelCase                 classes=1
62 audit-find-symbol-qualified-IntCoercer-coerce symbols=0
63 audit-find-class-no-match                     classes=0
64 audit-find-symbol-no-match                    symbols=0
65 audit-find-class-Shape-wildcard               classes=2

 succeeded in 152ms:
analysisFresh             e.g. java:48:diagnostics-Quirks-errors /analysisFresh
analysisMessage           e.g. java:48:diagnostics-Quirks-errors /analysisMessage
analysisTimedOut          e.g. java:48:diagnostics-Quirks-errors /analysisTimedOut
buildErrorCount           e.g. java:48:diagnostics-Quirks-errors /buildErrorCount
buildErrors               e.g. java:48:diagnostics-Quirks-errors /buildErrors
buildErrorsTruncated      e.g. java:48:diagnostics-Quirks-errors /buildErrorsTruncated
buildTimestamp            e.g. java:48:diagnostics-Quirks-errors /buildTimestamp
buildWarningCount         e.g. java:48:diagnostics-Quirks-errors /buildWarningCount
calls                     e.g. java:30:call-hier-makeDefault /calls
children                  e.g. java:30:call-hier-makeDefault /calls[]/children
classes                   e.g. java:33:find-class-Shape /classes
column                    e.g. java:1:def-circle-area-decl /column
element                   e.g. java:24:type-hier-square /element
enclosingScope            e.g. java:1:def-circle-area-decl /enclosingScope
endColumn                 e.g. java:81:audit-diagnostics-warnings /problems[]/endColumn
endLine                   e.g. java:81:audit-diagnostics-warnings /problems[]/endLine
file                      e.g. java:1:def-circle-area-decl /file
hierarchy                 e.g. java:21:super-circle-area /hierarchy
implementations           e.g. java:14:impls-shape-area /implementations
indexingProgress          e.g. java:47:index-status /indexingProgress
intentionCount            e.g. java:48:diagnostics-Quirks-errors /intentionCount
intentions                e.g. java:48:diagnostics-Quirks-errors /intentions
isDumbMode                e.g. java:47:index-status /isDumbMode
isIndexing                e.g. java:47:index-status /isIndexing
kind                      e.g. java:1:def-circle-area-decl /kind
language                  e.g. java:40:file-structure-Normal /language
line                      e.g. java:1:def-circle-area-decl /line
message                   e.g. java:81:audit-diagnostics-warnings /problems[]/message
method                    e.g. java:21:super-circle-area /method
name                      e.g. java:1:def-circle-area-decl /name
problemCount              e.g. java:48:diagnostics-Quirks-errors /problemCount
problems                  e.g. java:48:diagnostics-Quirks-errors /problems
qualifiedName             e.g. java:1:def-circle-area-decl /qualifiedName
query                     e.g. java:33:find-class-Shape /query
severity                  e.g. java:81:audit-diagnostics-warnings /problems[]/severity
structure                 e.g. java:40:file-structure-Normal /structure
subtypes                  e.g. java:24:type-hier-square /subtypes
supertypes                e.g. java:24:type-hier-square /element/supertypes
symbols                   e.g. java:37:find-symbol-area /symbols
testResults               e.g. java:48:diagnostics-Quirks-errors /testResults
testResultsTruncated      e.g. java:48:diagnostics-Quirks-errors /testResultsTruncated
testSummary               e.g. java:48:diagnostics-Quirks-errors /testSummary
totalCount                e.g. java:10:refs-area-from-totalArea /totalCount
usageType                 e.g. java:10:refs-area-from-totalArea /usages[]/usageType
usages                    e.g. java:10:refs-area-from-totalArea /usages

 succeeded in 154ms:
{'enclosingScope': 281, 'qualifiedName': 431, 'kind': 431, 'name': 431, 'line': 497, 'column': 497, 'message': 1, 'analysisMessage': 2, 'supertypes': 71, 'subtypes': 71, 'calls': 56, 'children': 56, 'problems': 2, 'classes': 92, 'symbols': 73, 'matches': 0}

exec
/usr/bin/zsh -lc "git diff -- live-test/run.py | sed -n '1,260p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
diff --git a/live-test/run.py b/live-test/run.py
index 44efb59..3e39e4a 100755
--- a/live-test/run.py
+++ b/live-test/run.py
@@ -13,6 +13,7 @@ from __future__ import annotations
 import argparse
 import difflib
 import json
+import re
 import sys
 import urllib.error
 import urllib.request
@@ -42,6 +43,55 @@ SORTABLE_ARRAYS = {
     "children",
 }
 
+# Replace machine-specific library / SDK / stub paths with stable tokens so
+# expected.jsonl stays portable across machines, IDE installs, and runtime
+# versions. Order matters — more-specific patterns first; the catch-all home
+# substitution must run last.
+LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
+    # Rust stdlib via RustRover cache (version + 40-char git hash)
+    (re.compile(
+        r"/home/[^/]+/\.cache/JetBrains/RustRover[^/]+/intellij-rust/"
+        r"stdlib-local-copy/[^/]+/library/"),
+     "${RUST_STDLIB}/"),
+    # Kotlin stdlib JAR via Gradle cache (version + hash dir)
+    (re.compile(
+        r"/home/[^/]+/\.gradle/caches/modules-2/files-2\.1/"
+        r"org\.jetbrains\.kotlin/kotlin-stdlib/[^/]+/[0-9a-f]+/"
+        r"kotlin-stdlib-[^/]+\.jar!"),
+     "${KOTLIN_STDLIB}.jar!"),
+    # JDK installed via Gradle JDK manager
+    (re.compile(r"/home/[^/]+/\.gradle/jdks/[^!]+!"), "${JDK}!"),
+    # JDK installed via mise
+    (re.compile(r"/home/[^/]+/\.local/share/mise/installs/java/[^!]+!"), "${JDK}!"),
+    # PyCharm typeshed stubs
+    (re.compile(
+        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/pycharm/plugins/"
+        r"python-ce/helpers/typeshed/"),
+     "${PYCHARM_TYPESHED}/"),
+    # Python stdlib installed via uv
+    (re.compile(
+        r"/home/[^/]+/\.local/share/uv/python/cpython-[^/]+/lib/python[^/]+/"),
+     "${PYTHON_STDLIB}/"),
+    # PhpStorm bundled PHP stubs
+    (re.compile(
+        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/"
+        r"php-impl/lib/php\.jar!"),
+     "${PHP_STUBS}.jar!"),
+    # WebStorm bundled JS library stubs
+    (re.compile(
+        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/webstorm/plugins/"
+        r"javascript-plugin/jsLanguageServicesImpl/external/"),
+     "${WEBSTORM_JS_STUBS}/"),
+    # Catch-all: any remaining $HOME prefix
+    (re.compile(r"/home/[^/]+/"), "${HOME}/"),
+]
+
+
+def _normalize_library_paths(s: str) -> str:
+    for pattern, replacement in LIBRARY_PATH_SUBS:
+        s = pattern.sub(replacement, s)
+    return s
+
 
 def _sort_key(item: Any) -> tuple:
     if isinstance(item, dict):
@@ -69,7 +119,7 @@ def normalize(obj: Any, project_root: str) -> Any:
     if isinstance(obj, list):
         return [normalize(item, project_root) for item in obj]
     if isinstance(obj, str):
-        return obj.replace(project_root, "${PROJECT_ROOT}")
+        return _normalize_library_paths(obj.replace(project_root, "${PROJECT_ROOT}"))
     return obj
 
 
@@ -148,6 +198,32 @@ def diff_lines(expected: Any, actual: Any) -> str:
     )
 
 
+def _load_expected_by_id(expected_path: Path) -> dict[str, Any]:
+    """Read expected.jsonl into an id → result dict.
+
+    Each row is expected to be {"id": <str>, "result": <obj>}. Rows missing an
+    `id` are skipped (legacy-format remnants will surface as MISSING during diff,
+    prompting a bless).
+    """
+    if not expected_path.is_file():
+        return {}
+    out: dict[str, Any] = {}
+    for line in expected_path.read_text().splitlines():
+        if not line.strip():
+            continue
+        try:
+            row = json.loads(line)
+        except json.JSONDecodeError:
+            continue
+        if isinstance(row, dict) and "id" in row and "result" in row:
+            out[row["id"]] = row["result"]
+    return out
+
+
+def _serialize_row(eid: str, result: Any) -> str:
+    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))
+
+
 def run_language(
     lang: str,
     project_path: Path,
@@ -155,7 +231,12 @@ def run_language(
     tool_filter: str | None,
     bless: bool,
 ) -> tuple[int, int]:
-    """Returns (passed, failed)."""
+    """Returns (passed, failed).
+
+    Rows are matched by `id`, not by position. Output and expected both store
+    `{"id": ..., "result": ...}` per line so the snapshot survives row
+    insertions, reorderings, and `--tool` filtered blesses.
+    """
     print(f"[{lang}] {url}")
 
     err = check_ready(url, str(project_path))
@@ -172,53 +253,80 @@ def run_language(
         for line in input_path.read_text().splitlines()
         if line.strip()
     ]
+
+    # Detect duplicate input ids — id-keyed matching requires uniqueness.
+    seen: dict[str, int] = {}
+    for i, entry in enumerate(inputs):
+        eid = entry.get("id")
+        if not isinstance(eid, str) or not eid:
+            print(f"  ERROR: input row {i + 1} has no id; fix before running.")
+            return 0, 1
+        if eid in seen:
+            print(f"  ERROR: duplicate input id '{eid}' at rows {seen[eid] + 1} and {i + 1}.")
+            return 0, 1
+        seen[eid] = i
+
+    filtered_inputs = inputs
     if tool_filter:
-        inputs = [e for e in inputs if e["tool"] == tool_filter]
+        filtered_inputs = [e for e in inputs if e["tool"] == tool_filter]
 
-    expected_lines: list[str] = []
-    if not bless and expected_path.is_file():
-        expected_lines = expected_path.read_text().splitlines()
+    expected_by_id = _load_expected_by_id(expected_path) if not bless else {}
 
-    output_lines: list[str] = []
+    # Process filtered inputs
+    fresh_results: dict[str, Any] = {}
     passed = failed = 0
-    for i, entry in enumerate(inputs):
-        eid = entry.get("id", f"#{i + 1}")
+    for entry in filtered_inputs:
+        eid = entry["id"]
         request = build_request(entry, str(project_path))
         result = normalize(post_jsonrpc(url, request), str(project_path))
-        serialized = json.dumps(result, sort_keys=True, separators=(",", ":"))
-        output_lines.append(serialized)
+        fresh_results[eid] = result
 
         if bless:
-            print(f"  [{i + 1}] {eid} BLESS")
+            print(f"  {eid} BLESS")
             passed += 1
             continue
 
-        if i >= len(expected_lines) or not expected_lines[i].strip():
-            print(f"  [{i + 1}] {eid} MISSING (no expected.jsonl line {i + 1} — bless?)")
+        if eid not in expected_by_id:
+            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
             failed += 1
             continue
 
-        try:
-            expected_obj = json.loads(expected_lines[i])
-        except json.JSONDecodeError as e:
-            print(f"  [{i + 1}] {eid} ERROR (expected.jsonl line {i + 1} not JSON: {e})")
-            failed += 1
-            continue
-
-        if expected_obj == result:
-            print(f"  [{i + 1}] {eid} PASS")
+        if expected_by_id[eid] == result:
+            print(f"  {eid} PASS")
             passed += 1
         else:
-            print(f"  [{i + 1}] {eid} FAIL")
-            for line in diff_lines(expected_obj, result).splitlines():
+            print(f"  {eid} FAIL")
+            for line in diff_lines(expected_by_id[eid], result).splitlines():
                 print(f"    {line}")
             failed += 1
 
-    body = "\n".join(output_lines) + ("\n" if output_lines else "")
-    output_path.write_text(body)
+    # Output reflects only the rows we ran (filtered or full).
+    output_lines = [_serialize_row(e["id"], fresh_results[e["id"]]) for e in filtered_inputs]
+    output_path.write_text("\n".join(output_lines) + ("\n" if output_lines else ""))
+
+    # Detect orphan expected rows (ids present in expected but not in input).
+    # Only meaningful in full (non-filtered) diff runs.
+    if not bless and tool_filter is None:
+        input_ids = {e["id"] for e in inputs}
+        existing_expected = _load_expected_by_id(expected_path) if expected_path.is_file() else {}
+        orphan_ids = sorted(set(existing_expected) - input_ids)
+        for eid in orphan_ids:
+            print(f"  ORPHAN expected id '{eid}' has no matching input — remove or rename.")
+        failed += len(orphan_ids)
 
     if bless:
-        expected_path.write_text(body)
+        # Merge fresh results into existing expected, then write in input order.
+        merged: dict[str, Any] = _load_expected_by_id(expected_path)
+        merged.update(fresh_results)
+        # Drop expected ids that no longer have a matching input.
+        input_ids = {e["id"] for e in inputs}
+        merged = {k: v for k, v in merged.items() if k in input_ids}
+        new_lines = [
+            _serialize_row(e["id"], merged[e["id"]])
+            for e in inputs
+            if e["id"] in merged
+        ]
+        expected_path.write_text("\n".join(new_lines) + ("\n" if new_lines else ""))
         print(f"[{lang}] BLESSED {expected_path}")
     else:
         print(f"[{lang}] {passed} passed, {failed} failed")

exec
/usr/bin/zsh -lc "git diff -- live-test/python/input.jsonl live-test/java/input.jsonl live-test/go/input.jsonl | sed -n '1,220p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
diff --git a/live-test/go/input.jsonl b/live-test/go/input.jsonl
index 4df5529..6e39ae1 100644
--- a/live-test/go/input.jsonl
+++ b/live-test/go/input.jsonl
@@ -54,4 +54,3 @@
 {"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
 {"id":"audit-refs-baseShape-Describe-promoted","tool":"ide_find_usages","params":{"file":"normal.go","line":16,"column":20}}
 {"id":"audit-find-class-prefix-Shape","tool":"ide_find_class","params":{"query":"Shape","matchMode":"prefix"}}
-{"id":"audit-find-class-exact-Circle","tool":"ide_find_class","params":{"query":"Circle","matchMode":"exact"}}
diff --git a/live-test/java/input.jsonl b/live-test/java/input.jsonl
index 5dc891c..0ebf746 100644
--- a/live-test/java/input.jsonl
+++ b/live-test/java/input.jsonl
@@ -77,7 +77,5 @@
 {"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
 {"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
 {"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
-{"id":"audit-super-CoerceMode-INT-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":72,"column":19}}
 {"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
-{"id":"audit-file-structure-Quirks","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Quirks.java"}}
 {"id":"audit-diagnostics-warnings","tool":"ide_diagnostics","params":{"file":"src/main/java/demo/Normal.java","severity":"warnings"}}
diff --git a/live-test/python/input.jsonl b/live-test/python/input.jsonl
index 95c7332..0e1a21c 100644
--- a/live-test/python/input.jsonl
+++ b/live-test/python/input.jsonl
@@ -1,5 +1,4 @@
 {"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
-{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
 {"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
 {"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
 {"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
@@ -71,6 +70,5 @@
 {"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
 {"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
 {"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
-{"id":"audit-super-Circle-draw-protocol","tool":"ide_find_super_methods","params":{"file":"src/normal.py","line":26,"column":9}}
 {"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
 {"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}

exec
/usr/bin/zsh -lc 'git diff --stat -- live-test/run.py live-test/*/input.jsonl live-test/*/expected.jsonl' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
 live-test/go/input.jsonl     |   1 -
 live-test/java/input.jsonl   |   2 -
 live-test/python/input.jsonl |   2 -
 live-test/run.py             | 164 +++++++++++++++++++++++++++++++++++--------
 4 files changed, 136 insertions(+), 33 deletions(-)

exec
/usr/bin/zsh -lc 'git ls-files -i --exclude-standard live-test/*/output.jsonl live-test/__pycache__/* 2>/dev/null; git check-ignore -v live-test/java/output.jsonl live-test/__pycache__/run.cpython-314.pyc 2>/dev/null' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/.gitignore:17:output.jsonl	live-test/java/output.jsonl

exec
/usr/bin/zsh -lc "rg -n '\\"'$'"\\{(RUST_STDLIB|KOTLIN_STDLIB|JDK|PYCHARM_TYPESHED|PYTHON_STDLIB|PHP_STUBS|WEBSTORM_JS_STUBS|HOME|PROJECT_ROOT)\\}' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "rg -n '(/home/|/Users/|C:\\\\|RustRover|Toolbox|\\.gradle|\\.cache|\\.local/share|\\.rustup|/usr/lib/jvm|/opt/|/Applications)' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/output.jsonl || true" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
 succeeded in 0ms:
live-test/rust/output.jsonl:6:{"id":"def-q-stdlib-parse","result":{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}}
live-test/rust/output.jsonl:8:{"id":"def-q-box-dyn-fn","result":{"column":17,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}}
live-test/rust/output.jsonl:10:{"id":"def-q-iter-parse","result":{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}}
live-test/rust/output.jsonl:25:{"id":"call-hier-make-default","result":{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}}
live-test/rust/output.jsonl:44:{"id":"call-hier-total-area-callees","result":{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}}
live-test/rust/output.jsonl:45:{"id":"call-hier-largest-callees","result":{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}}
live-test/rust/output.jsonl:61:{"id":"audit-call-hier-makeDefault-d3","result":{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}}
live-test/php/output.jsonl:4:{"id":"def-match-coerce","result":{"column":10,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}}
live-test/php/output.jsonl:27:{"id":"type-hier-Status","result":{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}}
live-test/typescript/output.jsonl:5:{"id":"def-quirks-parseint-arg-cast","result":{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}}
live-test/javascript/output.jsonl:5:{"id":"def-quirks-parseInt-property-on-Number","result":{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}}
live-test/javascript/output.jsonl:7:{"id":"def-quirks-destructured-Number-receiver","result":{"column":13,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}}
live-test/javascript/output.jsonl:8:{"id":"def-quirks-parseint-optional","result":{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}}
live-test/javascript/output.jsonl:10:{"id":"def-quirks-proxy-call-source","result":{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}}
live-test/javascript/output.jsonl:71:{"id":"audit-def-Number-parseInt-library","result":{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}}
live-test/python/output.jsonl:5:{"id":"def-int-builtin-via-rebinding-rhs","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:6:{"id":"def-super-init","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}}
live-test/python/output.jsonl:7:{"id":"def-abstractmethod-decorator","result":{"column":5,"enclosingScope":null,"file":"${PYTHON_STDLIB}/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}}
live-test/python/output.jsonl:8:{"id":"def-dict-dispatch-int","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:9:{"id":"def-lambda-wrap-int","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:10:{"id":"def-list-indexing-int","result":{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}}
live-test/python/output.jsonl:11:{"id":"def-quirk-rebound-fn","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:13:{"id":"def-multiple-assign-b","result":{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}}
live-test/python/output.jsonl:25:{"id":"type-hier-square","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}}
live-test/python/output.jsonl:26:{"id":"type-hier-rectangle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}}
live-test/python/output.jsonl:27:{"id":"type-hier-shape","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}}
live-test/python/output.jsonl:28:{"id":"call-hier-make-default","result":{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}}
live-test/python/output.jsonl:30:{"id":"call-hier-total-area","result":{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}}
live-test/python/output.jsonl:46:{"id":"type-hier-Circle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}}
live-test/python/output.jsonl:47:{"id":"type-hier-ShapeCollection","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}}
live-test/python/output.jsonl:49:{"id":"call-hier-largest-callees","result":{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}}
live-test/python/output.jsonl:64:{"id":"audit-type-hier-Square-supertypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}}
live-test/python/output.jsonl:65:{"id":"audit-type-hier-Shape-subtypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}}
live-test/python/output.jsonl:66:{"id":"audit-call-hier-makeDefault-d3","result":{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}}
live-test/java/output.jsonl:24:{"id":"type-hier-square","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}}
live-test/java/output.jsonl:25:{"id":"type-hier-rectangle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}}
live-test/java/output.jsonl:26:{"id":"type-hier-shape","result":{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}}
live-test/java/output.jsonl:27:{"id":"type-hier-CoerceMode","result":{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}}
live-test/java/output.jsonl:28:{"id":"type-hier-Point","result":{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}}
live-test/java/output.jsonl:30:{"id":"call-hier-makeDefault","result":{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}}
live-test/java/output.jsonl:45:{"id":"def-ArrayList-via-new-jdk-call","result":{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}}
live-test/java/output.jsonl:55:{"id":"type-hier-Circle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}}
live-test/java/output.jsonl:70:{"id":"audit-type-hier-Square-supertypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}}
live-test/java/output.jsonl:71:{"id":"audit-type-hier-Shape-subtypes","result":{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}}
live-test/java/output.jsonl:72:{"id":"audit-call-hier-makeDefault-d3","result":{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}}
live-test/java/output.jsonl:79:{"id":"audit-super-anon-apply","result":{"hierarchy":[{"column":7,"file":"${JDK}!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}}
live-test/kotlin/output.jsonl:21:{"id":"type-hier-square","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}}
live-test/kotlin/output.jsonl:22:{"id":"type-hier-rectangle","result":{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}}
live-test/kotlin/output.jsonl:23:{"id":"type-hier-shape","result":{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}}
live-test/kotlin/output.jsonl:24:{"id":"type-hier-Coercion-sealed","result":{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}}
live-test/kotlin/output.jsonl:27:{"id":"call-hier-quirkApply-callees","result":{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}}
live-test/kotlin/output.jsonl:47:{"id":"type-hier-Circle","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}}
live-test/kotlin/output.jsonl:48:{"id":"type-hier-ShapeCollection","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}}
live-test/kotlin/output.jsonl:61:{"id":"audit-type-hier-Square-supertypes","result":{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}}
live-test/kotlin/output.jsonl:62:{"id":"audit-type-hier-Shape-subtypes","result":{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}}

exec
/usr/bin/zsh -lc "rg -n '/home/|RustRover|Toolbox|\\.gradle|\\.cache|\\.local/share|\\.rustup|/usr/lib/jvm|/opt/|/Applications' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/expected.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "rg -n '\\"'$'"\\{HOME\\}' live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/output.jsonl || true" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/rust/expected.jsonl:6:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:8:{"column":17,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
live-test/rust/expected.jsonl:10:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:25:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/rust/expected.jsonl:44:{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
live-test/rust/expected.jsonl:45:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
live-test/rust/expected.jsonl:61:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/php/expected.jsonl:4:{"column":10,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
live-test/php/expected.jsonl:27:{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
live-test/javascript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:7:{"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
live-test/javascript/expected.jsonl:8:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:10:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:71:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/typescript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/kotlin/expected.jsonl:21:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:22:{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:23:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:24:{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:27:{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
live-test/kotlin/expected.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:61:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:62:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/java/expected.jsonl:24:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/expected.jsonl:25:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:26:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/expected.jsonl:27:{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
live-test/java/expected.jsonl:28:{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:30:{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/expected.jsonl:45:{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21/lib/src.zip!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
live-test/java/expected.jsonl:55:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/expected.jsonl:70:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/expected.jsonl:71:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/expected.jsonl:72:{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/expected.jsonl:79:{"hierarchy":[{"column":7,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
live-test/python/expected.jsonl:6:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:7:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
live-test/python/expected.jsonl:8:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/uv/python/cpython-3.11.14-linux-x86_64-gnu/lib/python3.11/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
live-test/python/expected.jsonl:9:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:10:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:11:{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/expected.jsonl:12:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:14:{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/expected.jsonl:26:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/expected.jsonl:27:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/expected.jsonl:28:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/expected.jsonl:29:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
live-test/python/expected.jsonl:31:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}
live-test/python/expected.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/expected.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}
live-test/python/expected.jsonl:50:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}
live-test/python/expected.jsonl:65:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/expected.jsonl:66:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/expected.jsonl:67:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}

 succeeded in 0ms:
exec
/usr/bin/zsh -lc "nl -ba live-test/java/src/main/java/demo/Normal.java | sed -n '1,110p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	package demo;
     2	
     3	import java.util.ArrayList;
     4	import java.util.List;
     5	
     6	interface Drawable {
     7	    String draw();
     8	}
     9	
    10	abstract class Shape {
    11	    abstract double area();
    12	
    13	    String describe() {
    14	        return getClass().getSimpleName() + " with area " + area();
    15	    }
    16	}
    17	
    18	class Circle extends Shape implements Drawable {
    19	    private final double radius;
    20	
    21	    Circle(double radius) {
    22	        this.radius = radius;
    23	    }
    24	
    25	    @Override
    26	    double area() {
    27	        return 3.14159 * radius * radius;
    28	    }
    29	
    30	    @Override
    31	    public String draw() {
    32	        return "circle r=" + radius;
    33	    }
    34	}
    35	
    36	class Rectangle extends Shape implements Drawable {
    37	    protected final double width;
    38	    protected final double height;
    39	
    40	    Rectangle(double width, double height) {
    41	        this.width = width;
    42	        this.height = height;
    43	    }
    44	
    45	    @Override
    46	    double area() {
    47	        return width * height;
    48	    }
    49	
    50	    @Override
    51	    public String draw() {
    52	        return "rect " + width + "x" + height;
    53	    }
    54	}
    55	
    56	class Square extends Rectangle {
    57	    Square(double side) {
    58	        super(side, side);
    59	    }
    60	}
    61	
    62	class ShapeCollection {
    63	    private final List<Shape> shapes = new ArrayList<>();
    64	
    65	    void add(Shape shape) {
    66	        shapes.add(shape);
    67	    }
    68	
    69	    double totalArea() {
    70	        double sum = 0;
    71	        for (Shape s : shapes) {
    72	            sum += s.area();
    73	        }
    74	        return sum;
    75	    }
    76	
    77	    Shape largest() {
    78	        Shape best = null;
    79	        for (Shape s : shapes) {
    80	            if (best == null || s.area() > best.area()) best = s;
    81	        }
    82	        return best;
    83	    }
    84	}
    85	
    86	public class Normal {
    87	    public static List<Shape> makeDefaultShapes() {
    88	        List<Shape> shapes = new ArrayList<>();
    89	        shapes.add(new Circle(1.0));
    90	        shapes.add(new Rectangle(2.0, 3.0));
    91	        shapes.add(new Square(4.0));
    92	        return shapes;
    93	    }
    94	
    95	    /** Issue #11: variable-assign in if/else for find_usages coverage. */
    96	    static int classifyShape(Shape s) {
    97	        int kind;
    98	        if (s instanceof Circle) {
    99	            kind = 1;
   100	        } else if (s instanceof Rectangle) {
   101	            kind = 2;
   102	        } else {
   103	            kind = 0;
   104	        }
   105	        return kind;
   106	    }
   107	}

exec
/usr/bin/zsh -lc "nl -ba live-test/javascript/src/normal.js | sed -n '1,75p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	'use strict';
     2	
     3	class Drawable {
     4	    draw() { throw new Error('not implemented'); }
     5	}
     6	
     7	class Shape {
     8	    area() { throw new Error('abstract'); }
     9	    describe() { return `${this.constructor.name} with area ${this.area()}`; }
    10	}
    11	
    12	class Circle extends Shape {
    13	    constructor(radius) {
    14	        super();
    15	        this.radius = radius;
    16	    }
    17	    area() { return 3.14159 * this.radius * this.radius; }
    18	    draw() { return `circle r=${this.radius}`; }
    19	}
    20	
    21	class Rectangle extends Shape {
    22	    constructor(width, height) {
    23	        super();
    24	        this.width = width;
    25	        this.height = height;
    26	    }
    27	    area() { return this.width * this.height; }
    28	    draw() { return `rect ${this.width}x${this.height}`; }
    29	}
    30	
    31	class Square extends Rectangle {
    32	    constructor(side) {
    33	        super(side, side);
    34	    }
    35	}
    36	
    37	class ShapeCollection {
    38	    constructor() {
    39	        this.shapes = [];
    40	    }
    41	    add(shape) { this.shapes.push(shape); }
    42	    totalArea() {
    43	        let sum = 0;
    44	        for (const s of this.shapes) sum += s.area();
    45	        return sum;
    46	    }
    47	    largest() {
    48	        let best = null;
    49	        for (const s of this.shapes) {
    50	            if (best === null || s.area() > best.area()) best = s;
    51	        }
    52	        return best;
    53	    }
    54	}
    55	
    56	function makeDefaultShapes() {
    57	    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
    58	}
    59	
    60	module.exports = { Drawable, Shape, Circle, Rectangle, Square, ShapeCollection, makeDefaultShapes };

exec
/usr/bin/zsh -lc "nl -ba live-test/python/src/normal.py | sed -n '1,90p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	"""Vanilla OOP patterns for the live MCP test harness."""
     2	from abc import ABC, abstractmethod
     3	from typing import Protocol
     4	
     5	
     6	class Drawable(Protocol):
     7	    def draw(self) -> str: ...
     8	
     9	
    10	class Shape(ABC):
    11	    @abstractmethod
    12	    def area(self) -> float:
    13	        ...
    14	
    15	    def describe(self) -> str:
    16	        return f"{type(self).__name__} with area {self.area()}"
    17	
    18	
    19	class Circle(Shape):
    20	    def __init__(self, radius: float) -> None:
    21	        self.radius = radius
    22	
    23	    def area(self) -> float:
    24	        return 3.14159 * self.radius * self.radius
    25	
    26	    def draw(self) -> str:
    27	        return f"circle r={self.radius}"
    28	
    29	
    30	class Rectangle(Shape):
    31	    def __init__(self, width: float, height: float) -> None:
    32	        self.width = width
    33	        self.height = height
    34	
    35	    def area(self) -> float:
    36	        return self.width * self.height
    37	
    38	    def draw(self) -> str:
    39	        return f"rect {self.width}x{self.height}"
    40	
    41	
    42	class Square(Rectangle):
    43	    def __init__(self, side: float) -> None:
    44	        super().__init__(side, side)
    45	
    46	
    47	class ShapeCollection:
    48	    def __init__(self) -> None:
    49	        self.shapes: list[Shape] = []
    50	
    51	    def add(self, shape: Shape) -> None:
    52	        self.shapes.append(shape)
    53	
    54	    def total_area(self) -> float:
    55	        return sum(s.area() for s in self.shapes)
    56	
    57	    def largest(self) -> Shape | None:
    58	        if not self.shapes:
    59	            return None
    60	        return max(self.shapes, key=lambda s: s.area())
    61	
    62	
    63	def make_default_shapes() -> list[Shape]:
    64	    return [Circle(1.0), Rectangle(2.0, 3.0), Square(4.0)]

exec
/usr/bin/zsh -lc "nl -ba live-test/rust/src/normal.rs | sed -n '1,110p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	pub trait Drawable {
     2	    fn draw(&self) -> String;
     3	}
     4	
     5	pub trait Shape {
     6	    fn area(&self) -> f64;
     7	
     8	    fn describe(&self) -> String {
     9	        format!("Shape with area {}", self.area())
    10	    }
    11	}
    12	
    13	pub struct Circle {
    14	    pub radius: f64,
    15	}
    16	
    17	impl Shape for Circle {
    18	    fn area(&self) -> f64 {
    19	        3.14159 * self.radius * self.radius
    20	    }
    21	
    22	    fn describe(&self) -> String {
    23	        format!("Circle with area {}", self.area())
    24	    }
    25	}
    26	
    27	impl Drawable for Circle {
    28	    fn draw(&self) -> String {
    29	        format!("circle r={}", self.radius)
    30	    }
    31	}
    32	
    33	pub struct Rectangle {
    34	    pub width: f64,
    35	    pub height: f64,
    36	}
    37	
    38	impl Shape for Rectangle {
    39	    fn area(&self) -> f64 {
    40	        self.width * self.height
    41	    }
    42	
    43	    fn describe(&self) -> String {
    44	        format!("Rectangle with area {}", self.area())
    45	    }
    46	}
    47	
    48	impl Drawable for Rectangle {
    49	    fn draw(&self) -> String {
    50	        format!("rect {}x{}", self.width, self.height)
    51	    }
    52	}
    53	
    54	pub struct Square {
    55	    inner: Rectangle,
    56	}
    57	
    58	impl Square {
    59	    pub fn new(side: f64) -> Self {
    60	        Square { inner: Rectangle { width: side, height: side } }
    61	    }
    62	}
    63	
    64	impl Shape for Square {
    65	    fn area(&self) -> f64 {
    66	        self.inner.area()
    67	    }
    68	}
    69	
    70	pub struct ShapeCollection {
    71	    pub shapes: Vec<Box<dyn Shape>>,
    72	}
    73	
    74	impl ShapeCollection {
    75	    pub fn new() -> Self {
    76	        ShapeCollection { shapes: Vec::new() }
    77	    }
    78	
    79	    pub fn add(&mut self, shape: Box<dyn Shape>) {
    80	        self.shapes.push(shape);
    81	    }
    82	
    83	    pub fn total_area(&self) -> f64 {
    84	        self.shapes.iter().map(|s| s.area()).sum()
    85	    }
    86	
    87	    pub fn largest(&self) -> Option<&Box<dyn Shape>> {
    88	        self.shapes.iter().max_by(|a, b| {
    89	            a.area().partial_cmp(&b.area()).unwrap()
    90	        })
    91	    }
    92	}
    93	
    94	pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
    95	    vec![
    96	        Box::new(Circle { radius: 1.0 }),
    97	        Box::new(Rectangle { width: 2.0, height: 3.0 }),
    98	        Box::new(Square::new(4.0)),
    99	    ]
   100	}

exec
/usr/bin/zsh -lc "nl -ba live-test/README.md | sed -n '1,145p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	# Live MCP Test Harness
     2	
     3	Snapshot-based regression suite for the IDE Index MCP plugin. Drives real
     4	HTTP POST requests against running JetBrains IDEs and diffs the responses
     5	against committed `expected.jsonl` files.
     6	
     7	Run after every plugin version bump.
     8	
     9	## Requirements
    10	
    11	- Python 3.10+ (stdlib only — no third-party deps)
    12	- The dev plugin installed in each IDE you intend to test against (see below).
    13	- The corresponding fixture project open in that IDE, fully indexed.
    14	
    15	## Per-IDE fixture setup
    16	
    17	| Language fixture | Open in | Default port |
    18	|---|---|---|
    19	| `python/` | PyCharm | 29172 |
    20	| `java/`, `kotlin/` | IntelliJ IDEA | 29170 |
    21	| `javascript/`, `typescript/` | WebStorm | 29173 |
    22	| `go/` | GoLand | 29174 |
    23	| `php/` | PhpStorm | 29175 |
    24	| `rust/` | RustRover | 29178 |
    25	
    26	Each fixture is a real IDE-openable project. Open it, wait for indexing,
    27	then run the harness.
    28	
    29	## Quick start
    30	
    31	```bash
    32	./run.py                          # runs every language, fails on diff
    33	./run.py --bless                  # rewrite expected.jsonl from server output
    34	./run.py --language python        # one language only
    35	./run.py --tool ide_find_definition   # one tool across all languages
    36	./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http   # override URL
    37	```
    38	
    39	## Version-bump workflow
    40	
    41	1. Bump `pluginVersion` in `gradle.properties`.
    42	2. `./gradlew buildPlugin` and install the resulting ZIP into each IDE
    43	   (Settings → Plugins → ⚙ → Install Plugin from Disk…).
    44	3. Restart each IDE (or use the in-IDE plugin reload).
    45	4. Re-open every fixture; wait for indexing to finish.
    46	5. Run the harness:
    47	
    48	   ```bash
    49	   ./live-test/run.py
    50	   ```
    51	
    52	6. If failures appear:
    53	   - Read each diff carefully. Is the change intentional (matches the
    54	     CHANGELOG entry for the new version) or a regression?
    55	   - Intentional: `./live-test/run.py --bless` and commit alongside the
    56	     version bump.
    57	   - Regression: file an issue or revert the change.
    58	
    59	Each run also writes `live-test/<lang>/output.jsonl` with the raw
    60	normalized response per entry. Useful for inspecting current
    61	responses without re-blessing or scraping diff output.
    62	`output.jsonl` is gitignored.
    63	
    64	## Troubleshooting
    65	
    66	- **`PRECHECK: cannot reach …`** — the IDE's MCP server isn't running on
    67	  the expected port. Check that the dev plugin is installed and enabled,
    68	  and that the IDE is open. Override the port with `--url` if you've
    69	  configured a non-default value in Settings → Tools → Index MCP Server.
    70	- **`PRECHECK: project is in dumb mode`** — wait for indexing to finish
    71	  in the IDE, then retry.
    72	- **`MISSING (no expected.jsonl line N)`** — `expected.jsonl` is shorter
    73	  than `input.jsonl`. Likely you added a new entry to `input.jsonl` and
    74	  haven't blessed yet. Run `--bless` to regenerate.
    75	- **All entries `FAIL` after a JDK / language toolchain update** — the
    76	  toolchain change shifted JDK source line numbers. Re-bless once
    77	  intentionally.
    78	
    79	## Known limitations captured as ground truth
    80	
    81	Some fixtures encode IntelliJ / language-plugin behaviors that are
    82	*captured intentionally* — re-blessing is not the right response when
    83	they show up. They document quirks of the platform, not bugs to fix:
    84	
    85	- **Java `call-hier-makeDefault` callees empty**: constructor invocations
    86	  inside a method body don't surface as callees. Same in Kotlin / TS / PHP
    87	  fixtures' analogous entries.
    88	- **Java `find_symbol` for overridden methods**: collapses to the
    89	  topmost super; concrete overrides on subclasses are not separately
    90	  surfaced.
    91	- **PHP `call-hier-makeDefault` callees empty**: `new` expressions
    92	  aren't tracked as callees (same root cause as Java's).
    93	- **Python `find_implementations` on a `Protocol`**: returns empty —
    94	  Protocols are structural, so PyCharm has no nominal implementer set.
    95	- **Python `find_definition` on builtins inside lambda / dict bodies**:
    96	  some positions return `tool_error_text: No named element at position`.
    97	  Captured as the documented limitation.
    98	- **TypeScript `find_implementations` via object literal**: classes/objects
    99	  satisfying an interface structurally (no `implements` clause) are not
   100	  surfaced.
   101	- **Go `type_hierarchy`**: returns empty `supertypes` / `subtypes` —
   102	  Go uses implicit (structural) interfaces, so the `Drawable` ↔
   103	  `Circle` relationship doesn't appear here. Use `ide_find_class` for
   104	  Go interface implementations.
   105	- **Go `qualifiedName` is universally `null`**: GoLand does not register
   106	  a `QualifiedNameProvider` for Go elements, and the plugin's
   107	  `QualifiedNameUtil` has no Go-specific fallback. Tracked separately as
   108	  a plugin enhancement.
   109	- **Rust `qualifiedName` partially `null`**: when the Rust provider can't
   110	  compute an FQN. The `name` field is unaffected.
   111	- **Rust `find_implementations` on a generic trait bound** (e.g.
   112	  `<C: Coercer>`): returns "No method or class found at position".
   113	  Bound positions don't expose the trait through this API; anchor on
   114	  the trait declaration directly instead.
   115	- **Kotlin `call-hier-makeDefault` callees empty**: same constructor
   116	  filtering as Java's — `Circle(...)`, `Rectangle(...)`, `Square(...)`
   117	  invocations don't appear.
   118	- **Kotlin `qualifiedName` uses `#` for methods** (e.g.
   119	  `demo.Shape#area`): correct — matches IntelliJ's "Copy Reference"
   120	  format and is consistent with Java.
   121	- **JDK / toolchain paths in supertype results**: `type_hierarchy` for
   122	  classes that extend `java.lang.Object` records an absolute path to a
   123	  JDK `.class` file (Adoptium, mise-installed openjdk, etc.). Path
   124	  changes when the toolchain changes; re-bless is the right response.
   125	- **PyCharm / WebStorm stdlib paths**: similar to above for
   126	  Python/JavaScript/TypeScript builtins (Number.parseInt → JetBrains
   127	  helpers stdlib path; Python `int` → PyCharm typeshed path).
   128	
   129	## Why not in CI?
   130	
   131	The harness POSTs to live IDE-hosted servers, so it requires running
   132	IDEs. CI runners don't carry a desktop IDE. Headless IDE execution
   133	(`./gradlew runIde`) plus a fixture-loading script could enable this in
   134	the future; deferred for v1.

exec
/usr/bin/zsh -lc 'for lang in java javascript typescript php; do echo $lang; rg -n '"'audit-find-symbol-qualified-Shape-area' live-test/"'$lang/input.jsonl live-test/$lang/output.jsonl; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
java
live-test/java/output.jsonl:73:{"id":"audit-find-symbol-qualified-Shape-area","result":{"query":"Shape.area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}}
live-test/java/input.jsonl:73:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
javascript
live-test/javascript/output.jsonl:64:{"id":"audit-find-symbol-qualified-Shape-area","result":{"query":"Shape.area","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}}
live-test/javascript/input.jsonl:64:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
typescript
live-test/typescript/output.jsonl:62:{"id":"audit-find-symbol-qualified-Shape-area","result":{"query":"Shape.area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}}
live-test/typescript/input.jsonl:62:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
php
live-test/php/output.jsonl:66:{"id":"audit-find-symbol-qualified-Shape-area","result":{"query":"Shape::area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":2}}
live-test/php/input.jsonl:66:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}

exec
/usr/bin/zsh -lc 'for lang in kotlin python go rust; do echo $lang; rg -n '"'audit-find-symbol-qualified|audit-find-symbol-Coercer' live-test/"'$lang/input.jsonl live-test/$lang/output.jsonl; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
kotlin
live-test/kotlin/output.jsonl:64:{"id":"audit-find-symbol-qualified-Coercer-coerce","result":{"query":"Coercer.coerce","symbols":[{"column":9,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":46,"name":"coerce","qualifiedName":"demo.Coercer#coerce"}],"totalCount":1}}
live-test/kotlin/input.jsonl:64:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
python
live-test/python/output.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","result":{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}}
live-test/python/output.jsonl:73:{"id":"audit-find-symbol-Coercer-coerce","result":{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}}
live-test/python/input.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test/python/input.jsonl:73:{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
go
live-test/go/output.jsonl:51:{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","result":{"query":"IntCoercer.Coerce","symbols":[{"column":19,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}],"totalCount":1}}
live-test/go/input.jsonl:51:{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer.Coerce"}}
rust
live-test/rust/output.jsonl:62:{"id":"audit-find-symbol-qualified-IntCoercer-coerce","result":{"query":"IntCoercer::coerce","symbols":[],"totalCount":0}}
live-test/rust/input.jsonl:62:{"id":"audit-find-symbol-qualified-IntCoercer-coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer::coerce"}}

exec
/usr/bin/zsh -lc "rg -n 'audit-find-symbol-Coercer-coerce|audit-find-symbol-qualified-Coercer-coerce' live-test/python/input.jsonl live-test/python/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/python/input.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test/python/input.jsonl:73:{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
live-test/python/output.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","result":{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}}
live-test/python/output.jsonl:73:{"id":"audit-find-symbol-Coercer-coerce","result":{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}}

exec
/usr/bin/zsh -lc "rg -n 'analysisMessage|problemCount|message' live-test/java/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
48:{"id":"diagnostics-Quirks-errors","result":{"analysisFresh":true,"analysisMessage":"Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":0,"problems":[],"testResults":null,"testResultsTruncated":null,"testSummary":null}}
81:{"id":"audit-diagnostics-warnings","result":{"analysisFresh":true,"analysisMessage":"Closed-file diagnostics use public batch analysis; weak warnings are only available when the file is open in an editor. Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":10,"problems":[{"column":12,"endColumn":15,"endLine":7,"file":"src/main/java/demo/Normal.java","line":7,"message":"Method 'draw()' is never used","severity":"WARNING"},{"column":12,"endColumn":19,"endLine":13,"file":"src/main/java/demo/Normal.java","line":13,"message":"Method 'describe()' is never used","severity":"WARNING"},{"column":7,"endColumn":21,"endLine":62,"file":"src/main/java/demo/Normal.java","line":62,"message":"Class 'ShapeCollection' is never used","severity":"WARNING"},{"column":10,"endColumn":12,"endLine":65,"file":"src/main/java/demo/Normal.java","line":65,"message":"Method 'add(demo.Shape)' is never used","severity":"WARNING"},{"column":12,"endColumn":20,"endLine":69,"file":"src/main/java/demo/Normal.java","line":69,"message":"Method 'totalArea()' is never used","severity":"WARNING"},{"column":11,"endColumn":17,"endLine":77,"file":"src/main/java/demo/Normal.java","line":77,"message":"Method 'largest()' is never used","severity":"WARNING"},{"column":14,"endColumn":19,"endLine":86,"file":"src/main/java/demo/Normal.java","line":86,"message":"Class 'Normal' is never used","severity":"WARNING"},{"column":24,"endColumn":28,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Class 'Shape' is exposed outside its defined visibility scope","severity":"WARNING"},{"column":31,"endColumn":47,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Method 'makeDefaultShapes()' is never used","severity":"WARNING"},{"column":16,"endColumn":28,"endLine":96,"file":"src/main/java/demo/Normal.java","line":96,"message":"Method 'classifyShape(demo.Shape)' is never used","severity":"WARNING"}],"testResults":null,"testResultsTruncated":null,"testSummary":null}}

codex
Verified current state first:

- `run.py` now matches expected rows by `id`, writes `{"id","result"}`, rejects missing/duplicate input IDs, and reports orphans in full non-bless runs: [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:201), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:289), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307).
- The five named duplicate input rows are gone. Current input IDs have no duplicates.
- Current `output.jsonl` files are already id-keyed and have no raw `/home/...` paths.
- Current `expected.jsonl` files are still legacy positional format: zero `{"id","result"}` rows. A normal diff run will report every input as `MISSING` until migration/bless. Java/Python/Go expected row counts still include the removed positional rows.

**Findings**

| Severity | File | Trigger | Symptom | Blast Radius | Mitigation |
|---|---|---|---|---|---|
| CRITICAL | [run.py:273](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:273), [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless` to “migrate” legacy expected files | It does not migrate old snapshots by id; it rebaselines from the live IDE. Any current IDE/plugin regression becomes blessed truth. | Suite-wide | Add a one-time offline migration pairing current `input.jsonl` order to legacy `expected.jsonl`, then require a separate reviewed live rebless. |
| HIGH | [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless --tool` before expected files are fully id-keyed | `_load_expected_by_id()` loads nothing from legacy rows, so the filtered bless writes only that tool’s rows and drops the rest. | 1 language or suite-wide | Refuse `--bless --tool` when any expected row lacks `id`/`result`. |
| HIGH | [run.py:214](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:214) | Partial migration, bad merge, malformed JSON, duplicate expected IDs | Malformed/legacy rows are silently skipped; duplicate expected IDs silently last-write-win. User sees `MISSING`, not “corrupt snapshot.” | 1 row to 1 language | Make expected loading strict: fail on malformed rows, legacy rows after migration, and duplicate expected IDs. |
| HIGH | [run.py:281](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:281), [run.py:284](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:284) | IDE returns `transport_error`, `jsonrpc_error`, or `tool_error_text` during bless | Harness blesses the error payload as expected output. | 1 row to suite-wide | During bless, fail unless result matches a known success schema or `--bless-errors` is explicit. |
| HIGH | [run.py:242](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:242), [run.py:278](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:278) | IDE enters dumb mode after the precheck | Mid-run rows fail or get blessed as errors even though precheck passed. | 1 language | Recheck index status before each row or retry rows that return indexing/dumb-mode errors. |
| HIGH | [run.py:303](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:303), [run.py:329](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:329) | SIGINT, crash, or two terminals blessing at once | `write_text()` can truncate or race `output.jsonl`/`expected.jsonl`. | 1 language | Write to a temp file, `fsync`, atomic `os.replace`, and take a per-language lock. |
| HIGH | [run.py:321](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:321) | Accidental input row deletion followed by bless | Expected IDs without inputs are silently pruned during bless. | 1 row to 1 language | Print pruned IDs and fail unless `--prune` is passed. |
| MEDIUM | [run.py:307](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307) | Running only `--tool` in daily workflow | Orphans are detected only in full non-filtered runs. Renames/deletions outside that tool rot unnoticed. | 1 language | Add `--check-fixtures` that validates IDs/orphans without calling IDEs, and run it before filtered runs. |
| MEDIUM | [run.py:257](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257) | A row for another tool lacks `id` | Even `--tool some_other_tool` fails before filtering. | 1 language | Keep strict validation, but expose `--check-fixtures` so fixture errors are diagnosed separately from live runs. |
| HIGH | [run.py:50](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50) | macOS, Windows, non-Toolbox installs, SDKMAN/asdf/system JDKs, rustup sources | Absolute SDK/stub paths leak or normalize only to `${HOME}/...`, causing noisy diffs. | Suite-wide on new host | Normalize via `Path.home()` plus platform-specific path families; add tests for `/Users`, Windows, `/usr/lib/jvm`, SDKMAN, rustup, JetBrains non-Toolbox layouts. |
| MEDIUM | [run.py:85](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:85) | New under-home toolchain layout appears | Catch-all `${HOME}` hides the missing specific normalizer but leaves versioned subdirs to drift. | 1 language | Report any `${HOME}/.gradle`, `${HOME}/.cache/JetBrains`, `${HOME}/.local/share/JetBrains`, `${HOME}/.rustup` in output as normalization failures. |
| HIGH | [rust/output.jsonl:6](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/output.jsonl:6), [python/output.jsonl:7](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/output.jsonl:7), [javascript/output.jsonl:5](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/output.jsonl:5) | Rust/Python/JS/PHP/JDK library version bump | Paths are tokenized, but library `line`/`column`, `name`, and overload lists still change. | Many rows in affected languages | For library files, compare `file` token + `qualifiedName` + `kind`; drop or separately bucket library line/column. |
| MEDIUM | [run.py:34](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) | Tool adds meaningful `offset`, `pageSize`, `truncated`, `hasMore`, etc. | Normalizer drops fields globally, hiding pagination regressions. | Any paged tool | Drop noisy fields only by response type/path, not by key name everywhere. |
| MEDIUM | [run.py:39](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:39), [run.py:115](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:115) | IDE changes order in hierarchy/call trees where order is semantic | Harness sorts `children`, `calls`, `hierarchy`, masking ordering bugs. | Call/type/super hierarchy rows | Sort only sets where order is contractually irrelevant; keep ordered snapshots for tree traversal. |
| MEDIUM | [run.py:96](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:96) | Schema changes `line`/`column` to strings or nullable mixed types | Python sort can throw `TypeError`, aborting the language run. | 1 language | Coerce sort fields to typed tuples and validate schema before sorting. |
| HIGH | [run.py:126](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:126) | MCP response moves from first text content to `structuredContent`, multiple content items, or `isError` | Harness ignores structured data or treats tool errors as JSON text. | Suite-wide after MCP/plugin change | Parse MCP result according to current schema: handle `isError`, all content items, and `structuredContent`. |
| MEDIUM | [run.py:139](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:139) | IDE returns HTML/empty/non-JSON response | `json.loads(resp.read())` is uncaught; whole run crashes instead of row failure. | 1 language | Catch envelope `JSONDecodeError` and return structured transport failure. |
| MEDIUM | [run.py:157](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:157) | Tool schemas rename `project_path`, `file`, `line`, `column`, `matchMode`, or `scope` | Every affected request fails or returns schema errors. | Tool-wide or suite-wide | Fetch/list tool schemas before running and validate input params against them. |
| MEDIUM | [run.py:23](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:23), [run.py:380](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:380) | Java/Kotlin share one port, JS/TS share one port, or `--url` is used across all languages | Requests go to the wrong IDE/project or only one fixture is open. | 1-2 languages or suite-wide | Support per-language URLs/env vars and verify the IDE has the requested `project_path` open. |
| MEDIUM | [java/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:1), [python/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1), [rust/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:1) | Inserting lines in fixtures before probed symbols | Line/column anchors hit the wrong token or no token. | Dozens of rows per edited file | Add a preflight that checks each anchor still points at an expected identifier string. |
| HIGH | [java/input.jsonl:10](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:10), [javascript/input.jsonl:11](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/input.jsonl:11) | Adding a legitimate usage upstream/downstream | `find_usages` counts and ordering change across many snapshots. | 1 language | Keep usage fixtures frozen or split “search universe” files from editable fixture examples. |
| HIGH | [java/input.jsonl:33](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), [go/input.jsonl:21](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:21) | Adding `ShapeFactory`, `ShapeError`, etc. | Broad `find_class` queries return extra hits. | Many find-class rows | Use `matchMode:"exact"` for baseline invariants; keep fuzzy matching in dedicated small fixtures. |
| HIGH | [java/input.jsonl:37](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:37), [rust/input.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:30) | Adding methods named `area`, `draw`, `Describe`, etc. | Broad `find_symbol` rows become noisy. | Many find-symbol rows | Assert exact expected subset plus count only for specific fuzzy-search tests. |
| MEDIUM | [java/output.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:73), [php/output.jsonl:66](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/output.jsonl:66) | Qualified search behavior improves | Current snapshots bless false positives like `ShapeCollection.totalArea` for `Shape.area`. A real fix will look like a regression. | 4 languages | Mark these as known-bad assertions or change expected semantics to require only the qualified target. |
| LOW | [python/input.jsonl:67](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:67), [python/input.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:73) | Either row changes/fails | Two IDs still run identical `Coercer.coerce` queries and duplicate noise. | 2 rows | Delete one or change one query to exercise a distinct path. |
| HIGH | [java/input.jsonl:40](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:40), [python/input.jsonl:35](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:35) | Any fixture refactor or line move | `ide_file_structure` snapshots churn on line/column/name ordering, not just structural regressions. | Whole file-structure row | Compare normalized structure names/kinds separately from locations. |
| HIGH | [java/output.jsonl:81](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:81) | JetBrains inspection text changes, file-open state changes, weak-warning policy changes | Diagnostics row fails on English message text and warning counts. | Java diagnostics rows | Snapshot stable fields; put message text behind targeted golden tests only. |
| HIGH | [java/output.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:30) | JDK adds/removes implementations or changes class stubs | Call hierarchy rows explode with JDK/library callees. | Java/Kotlin and Rust/Python library-heavy rows | Default hierarchy/call tests to project scope; add one explicit library-scope smoke test. |
| LOW | [README.md:72](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:72) | Someone follows troubleshooting after id-keying | README still says “no expected.jsonl line N”; current harness reports missing IDs. | Human workflow | Update README to id-keyed migration and orphan behavior. |

The biggest immediate risk is the migration step: a full `--bless` now records live IDE behavior, not the old positional expectations. Do the id-key migration offline first, then run the live diff so semantic changes stay visible.
tokens used
203,613
Verified current state first:

- `run.py` now matches expected rows by `id`, writes `{"id","result"}`, rejects missing/duplicate input IDs, and reports orphans in full non-bless runs: [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:201), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:289), [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307).
- The five named duplicate input rows are gone. Current input IDs have no duplicates.
- Current `output.jsonl` files are already id-keyed and have no raw `/home/...` paths.
- Current `expected.jsonl` files are still legacy positional format: zero `{"id","result"}` rows. A normal diff run will report every input as `MISSING` until migration/bless. Java/Python/Go expected row counts still include the removed positional rows.

**Findings**

| Severity | File | Trigger | Symptom | Blast Radius | Mitigation |
|---|---|---|---|---|---|
| CRITICAL | [run.py:273](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:273), [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless` to “migrate” legacy expected files | It does not migrate old snapshots by id; it rebaselines from the live IDE. Any current IDE/plugin regression becomes blessed truth. | Suite-wide | Add a one-time offline migration pairing current `input.jsonl` order to legacy `expected.jsonl`, then require a separate reviewed live rebless. |
| HIGH | [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless --tool` before expected files are fully id-keyed | `_load_expected_by_id()` loads nothing from legacy rows, so the filtered bless writes only that tool’s rows and drops the rest. | 1 language or suite-wide | Refuse `--bless --tool` when any expected row lacks `id`/`result`. |
| HIGH | [run.py:214](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:214) | Partial migration, bad merge, malformed JSON, duplicate expected IDs | Malformed/legacy rows are silently skipped; duplicate expected IDs silently last-write-win. User sees `MISSING`, not “corrupt snapshot.” | 1 row to 1 language | Make expected loading strict: fail on malformed rows, legacy rows after migration, and duplicate expected IDs. |
| HIGH | [run.py:281](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:281), [run.py:284](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:284) | IDE returns `transport_error`, `jsonrpc_error`, or `tool_error_text` during bless | Harness blesses the error payload as expected output. | 1 row to suite-wide | During bless, fail unless result matches a known success schema or `--bless-errors` is explicit. |
| HIGH | [run.py:242](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:242), [run.py:278](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:278) | IDE enters dumb mode after the precheck | Mid-run rows fail or get blessed as errors even though precheck passed. | 1 language | Recheck index status before each row or retry rows that return indexing/dumb-mode errors. |
| HIGH | [run.py:303](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:303), [run.py:329](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:329) | SIGINT, crash, or two terminals blessing at once | `write_text()` can truncate or race `output.jsonl`/`expected.jsonl`. | 1 language | Write to a temp file, `fsync`, atomic `os.replace`, and take a per-language lock. |
| HIGH | [run.py:321](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:321) | Accidental input row deletion followed by bless | Expected IDs without inputs are silently pruned during bless. | 1 row to 1 language | Print pruned IDs and fail unless `--prune` is passed. |
| MEDIUM | [run.py:307](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307) | Running only `--tool` in daily workflow | Orphans are detected only in full non-filtered runs. Renames/deletions outside that tool rot unnoticed. | 1 language | Add `--check-fixtures` that validates IDs/orphans without calling IDEs, and run it before filtered runs. |
| MEDIUM | [run.py:257](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257) | A row for another tool lacks `id` | Even `--tool some_other_tool` fails before filtering. | 1 language | Keep strict validation, but expose `--check-fixtures` so fixture errors are diagnosed separately from live runs. |
| HIGH | [run.py:50](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50) | macOS, Windows, non-Toolbox installs, SDKMAN/asdf/system JDKs, rustup sources | Absolute SDK/stub paths leak or normalize only to `${HOME}/...`, causing noisy diffs. | Suite-wide on new host | Normalize via `Path.home()` plus platform-specific path families; add tests for `/Users`, Windows, `/usr/lib/jvm`, SDKMAN, rustup, JetBrains non-Toolbox layouts. |
| MEDIUM | [run.py:85](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:85) | New under-home toolchain layout appears | Catch-all `${HOME}` hides the missing specific normalizer but leaves versioned subdirs to drift. | 1 language | Report any `${HOME}/.gradle`, `${HOME}/.cache/JetBrains`, `${HOME}/.local/share/JetBrains`, `${HOME}/.rustup` in output as normalization failures. |
| HIGH | [rust/output.jsonl:6](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/output.jsonl:6), [python/output.jsonl:7](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/output.jsonl:7), [javascript/output.jsonl:5](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/output.jsonl:5) | Rust/Python/JS/PHP/JDK library version bump | Paths are tokenized, but library `line`/`column`, `name`, and overload lists still change. | Many rows in affected languages | For library files, compare `file` token + `qualifiedName` + `kind`; drop or separately bucket library line/column. |
| MEDIUM | [run.py:34](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) | Tool adds meaningful `offset`, `pageSize`, `truncated`, `hasMore`, etc. | Normalizer drops fields globally, hiding pagination regressions. | Any paged tool | Drop noisy fields only by response type/path, not by key name everywhere. |
| MEDIUM | [run.py:39](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:39), [run.py:115](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:115) | IDE changes order in hierarchy/call trees where order is semantic | Harness sorts `children`, `calls`, `hierarchy`, masking ordering bugs. | Call/type/super hierarchy rows | Sort only sets where order is contractually irrelevant; keep ordered snapshots for tree traversal. |
| MEDIUM | [run.py:96](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:96) | Schema changes `line`/`column` to strings or nullable mixed types | Python sort can throw `TypeError`, aborting the language run. | 1 language | Coerce sort fields to typed tuples and validate schema before sorting. |
| HIGH | [run.py:126](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:126) | MCP response moves from first text content to `structuredContent`, multiple content items, or `isError` | Harness ignores structured data or treats tool errors as JSON text. | Suite-wide after MCP/plugin change | Parse MCP result according to current schema: handle `isError`, all content items, and `structuredContent`. |
| MEDIUM | [run.py:139](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:139) | IDE returns HTML/empty/non-JSON response | `json.loads(resp.read())` is uncaught; whole run crashes instead of row failure. | 1 language | Catch envelope `JSONDecodeError` and return structured transport failure. |
| MEDIUM | [run.py:157](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:157) | Tool schemas rename `project_path`, `file`, `line`, `column`, `matchMode`, or `scope` | Every affected request fails or returns schema errors. | Tool-wide or suite-wide | Fetch/list tool schemas before running and validate input params against them. |
| MEDIUM | [run.py:23](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:23), [run.py:380](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:380) | Java/Kotlin share one port, JS/TS share one port, or `--url` is used across all languages | Requests go to the wrong IDE/project or only one fixture is open. | 1-2 languages or suite-wide | Support per-language URLs/env vars and verify the IDE has the requested `project_path` open. |
| MEDIUM | [java/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:1), [python/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1), [rust/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:1) | Inserting lines in fixtures before probed symbols | Line/column anchors hit the wrong token or no token. | Dozens of rows per edited file | Add a preflight that checks each anchor still points at an expected identifier string. |
| HIGH | [java/input.jsonl:10](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:10), [javascript/input.jsonl:11](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/input.jsonl:11) | Adding a legitimate usage upstream/downstream | `find_usages` counts and ordering change across many snapshots. | 1 language | Keep usage fixtures frozen or split “search universe” files from editable fixture examples. |
| HIGH | [java/input.jsonl:33](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), [go/input.jsonl:21](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:21) | Adding `ShapeFactory`, `ShapeError`, etc. | Broad `find_class` queries return extra hits. | Many find-class rows | Use `matchMode:"exact"` for baseline invariants; keep fuzzy matching in dedicated small fixtures. |
| HIGH | [java/input.jsonl:37](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:37), [rust/input.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:30) | Adding methods named `area`, `draw`, `Describe`, etc. | Broad `find_symbol` rows become noisy. | Many find-symbol rows | Assert exact expected subset plus count only for specific fuzzy-search tests. |
| MEDIUM | [java/output.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:73), [php/output.jsonl:66](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/output.jsonl:66) | Qualified search behavior improves | Current snapshots bless false positives like `ShapeCollection.totalArea` for `Shape.area`. A real fix will look like a regression. | 4 languages | Mark these as known-bad assertions or change expected semantics to require only the qualified target. |
| LOW | [python/input.jsonl:67](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:67), [python/input.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:73) | Either row changes/fails | Two IDs still run identical `Coercer.coerce` queries and duplicate noise. | 2 rows | Delete one or change one query to exercise a distinct path. |
| HIGH | [java/input.jsonl:40](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:40), [python/input.jsonl:35](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:35) | Any fixture refactor or line move | `ide_file_structure` snapshots churn on line/column/name ordering, not just structural regressions. | Whole file-structure row | Compare normalized structure names/kinds separately from locations. |
| HIGH | [java/output.jsonl:81](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:81) | JetBrains inspection text changes, file-open state changes, weak-warning policy changes | Diagnostics row fails on English message text and warning counts. | Java diagnostics rows | Snapshot stable fields; put message text behind targeted golden tests only. |
| HIGH | [java/output.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:30) | JDK adds/removes implementations or changes class stubs | Call hierarchy rows explode with JDK/library callees. | Java/Kotlin and Rust/Python library-heavy rows | Default hierarchy/call tests to project scope; add one explicit library-scope smoke test. |
| LOW | [README.md:72](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:72) | Someone follows troubleshooting after id-keying | README still says “no expected.jsonl line N”; current harness reports missing IDs. | Human workflow | Update README to id-keyed migration and orphan behavior. |

The biggest immediate risk is the migration step: a full `--bless` now records live IDE behavior, not the old positional expectations. Do the id-key migration offline first, then run the live diff so semantic changes stay visible.
