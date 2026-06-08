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
   224	    ]
   225	    if tool_filter:
   226	        inputs = [e for e in inputs if e["tool"] == tool_filter]
   227	
   228	    expected_lines: list[str] = []
   229	    if not bless and expected_path.is_file():
   230	        expected_lines = expected_path.read_text().splitlines()
   231	
   232	    output_lines: list[str] = []
   233	    passed = failed = 0
   234	    for i, entry in enumerate(inputs):
   235	        eid = entry.get("id", f"#{i + 1}")
   236	        request = build_request(entry, str(project_path))
   237	        result = normalize(post_jsonrpc(url, request), str(project_path))
   238	        serialized = json.dumps(result, sort_keys=True, separators=(",", ":"))
   239	        output_lines.append(serialized)
   240	
   241	        if bless:
   242	            print(f"  [{i + 1}] {eid} BLESS")
   243	            passed += 1
   244	            continue
   245	
   246	        if i >= len(expected_lines) or not expected_lines[i].strip():
   247	            print(f"  [{i + 1}] {eid} MISSING (no expected.jsonl line {i + 1} — bless?)")
   248	            failed += 1
   249	            continue
   250	
   251	        try:
   252	            expected_obj = json.loads(expected_lines[i])
   253	        except json.JSONDecodeError as e:
   254	            print(f"  [{i + 1}] {eid} ERROR (expected.jsonl line {i + 1} not JSON: {e})")
   255	            failed += 1
   256	            continue
   257	
   258	        if expected_obj == result:
   259	            print(f"  [{i + 1}] {eid} PASS")
   260	            passed += 1
   261	        else:
   262	            print(f"  [{i + 1}] {eid} FAIL")
   263	            for line in diff_lines(expected_obj, result).splitlines():
   264	                print(f"    {line}")
   265	            failed += 1
   266	
   267	    body = "\n".join(output_lines) + ("\n" if output_lines else "")
   268	    output_path.write_text(body)
   269	
   270	    if bless:
   271	        expected_path.write_text(body)
   272	        print(f"[{lang}] BLESSED {expected_path}")
   273	    else:
   274	        print(f"[{lang}] {passed} passed, {failed} failed")
   275	    return passed, failed
   276	
   277	
   278	def discover_languages(root: Path, only: str | None) -> list[str]:
   279	    if only is not None:
   280	        if not (root / only / "input.jsonl").is_file():
   281	            print(f"No input.jsonl for language '{only}'", file=sys.stderr)
   282	            sys.exit(1)
   283	        return [only]
   284	    return sorted(
   285	        d.name
   286	        for d in root.iterdir()
   287	        if d.is_dir() and (d / "input.jsonl").is_file()
   288	    )
   289	
   290	
   291	def main() -> int:
   292	    parser = argparse.ArgumentParser(
   293	        description="Live MCP test harness runner.",
   294	        formatter_class=argparse.RawDescriptionHelpFormatter,
   295	        epilog=(
   296	            "Examples:\n"
   297	            "  ./run.py                          # diff every language\n"
   298	            "  ./run.py --bless                  # rewrite expected.jsonl\n"
   299	            "  ./run.py --language java          # one language\n"
   300	            "  ./run.py --tool ide_find_definition\n"
   301	            "  ./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http"
   302	        ),
   303	    )
   304	    parser.add_argument("--language", help="Restrict to one language.")
   305	    parser.add_argument("--tool", help="Restrict to one MCP tool.")
   306	    parser.add_argument("--url", help="Override server URL for the run.")
   307	    parser.add_argument(
   308	        "--bless",
   309	        action="store_true",
   310	        help="Rewrite expected.jsonl from server output instead of diffing.",
   311	    )
   312	    args = parser.parse_args()
   313	
   314	    root = Path(__file__).resolve().parent
   315	    langs = discover_languages(root, args.language)
   316	    if not langs:
   317	        print(f"No fixtures found in {root}", file=sys.stderr)
   318	        return 0
   319	
   320	    total_pass = total_fail = 0
   321	    for lang in langs:
   322	        if args.url:
   323	            url = args.url
   324	        else:
   325	            port = PORT_BY_LANG.get(lang)
   326	            if port is None:
   327	                print(f"No port mapped for language '{lang}'", file=sys.stderr)
   328	                return 1
   329	            url = f"http://127.0.0.1:{port}/index-mcp/streamable-http"
   330	        passed, failed = run_language(
   331	            lang, root / lang, url, args.tool, args.bless
   332	        )
   333	        total_pass += passed
   334	        total_fail += failed
   335	
   336	    print(f"ALL: {total_pass} passed, {total_fail} failed")
   337	    return 0 if total_fail == 0 else 1
   338	
   339	
   340	if __name__ == "__main__":
   341	    sys.exit(main())

exec
/usr/bin/zsh -lc 'pwd && rg --files live-test | sort' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
/home/ubuntu/dev/jetbrains-index-mcp-plugin
live-test/README.md
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
/usr/bin/zsh -lc 'find . -maxdepth 2 -name AGENTS.md -print' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
codex
The harness is small enough to review directly. I’m going to cross-check the probes against their fixture source files now, because line/column and snapshot drift bugs are usually where this kind of harness gets quietly weak.
exec
/usr/bin/zsh -lc "for f in live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/input.jsonl; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/java/input.jsonl
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
    80	{"id":"audit-super-CoerceMode-INT-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":72,"column":19}}
    81	{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
    82	{"id":"audit-file-structure-Quirks","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Quirks.java"}}
    83	{"id":"audit-diagnostics-warnings","tool":"ide_diagnostics","params":{"file":"src/main/java/demo/Normal.java","severity":"warnings"}}

### live-test/kotlin/input.jsonl
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

### live-test/python/input.jsonl
     1	{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
     2	{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
     3	{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
     4	{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
     5	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
     6	{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
     7	{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
     8	{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
     9	{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
    10	{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
    11	{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
    12	{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
    13	{"id":"def-walrus-result",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":70,"column":16}}
    14	{"id":"def-multiple-assign-b",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":100,"column":19}}
    15	{"id":"def-nested-return-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":82,"column":12}}
    16	{"id":"refs-area-from-describe",    "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":12,"column":9}}
    17	{"id":"refs-shapes-field",          "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":49,"column":14}}
    18	{"id":"refs-shape-class",           "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":10,"column":7}}
    19	{"id":"refs-int-builtin-in-quirks", "tool":"ide_find_usages",      "params":{"file":"src/quirks.py","line":64,"column":20}}
    20	{"id":"refs-circle-radius",         "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":21,"column":14}}
    21	{"id":"impls-shape-area",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":12,"column":9}}
    22	{"id":"impls-drawable-draw",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":7,"column":9}}
    23	{"id":"super-Square-init",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":43,"column":9}}
    24	{"id":"super-circle-area",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":23,"column":9}}
    25	{"id":"super-rectangle-area",       "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":35,"column":9}}
    26	{"id":"type-hier-square",           "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":42,"column":7}}
    27	{"id":"type-hier-rectangle",        "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":30,"column":7}}
    28	{"id":"type-hier-shape",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":10,"column":7}}
    29	{"id":"call-hier-make-default",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":2}}
    30	{"id":"call-hier-area-callers",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":12,"column":9,"direction":"callers","maxDepth":2}}
    31	{"id":"call-hier-total-area",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":54,"column":9,"direction":"callees","maxDepth":2}}
    32	{"id":"find-class-Shape",           "tool":"ide_find_class",           "params":{"query":"Shape"}}
    33	{"id":"find-class-Rectangle",       "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
    34	{"id":"find-symbol-area",           "tool":"ide_find_symbol",          "params":{"query":"area"}}
    35	{"id":"find-symbol-quirk-rebind",   "tool":"ide_find_symbol",          "params":{"query":"quirk_name_rebinding"}}
    36	{"id":"file-structure-normal",      "tool":"ide_file_structure",       "params":{"file":"src/normal.py"}}
    37	{"id":"file-structure-quirks",      "tool":"ide_file_structure",       "params":{"file":"src/quirks.py"}}
    38	{"id":"refs-Drawable-protocol",      "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":6,"column":7}}
    39	{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":30,"column":7}}
    40	{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":10,"column":7}}
    41	{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":30,"column":7}}
    42	{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":19,"column":7}}
    43	{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":15,"column":9}}
    44	{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":26,"column":9}}
    45	{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":38,"column":9}}
    46	{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":6,"column":7}}
    47	{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":19,"column":7}}
    48	{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":47,"column":7}}
    49	{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":15,"column":9,"direction":"callers","maxDepth":2}}
    50	{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":57,"column":9,"direction":"callees","maxDepth":2}}
    51	{"id":"call-hier-add-callers",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":51,"column":9,"direction":"callers","maxDepth":2}}
    52	{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
    53	{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
    54	{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
    55	{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
    56	{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
    57	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
    58	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    59	{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
    60	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
    61	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    62	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    63	{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
    64	{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
    65	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":42,"column":7,"direction":"supertypes"}}
    66	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":10,"column":7,"direction":"subtypes"}}
    67	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":3}}
    68	{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
    69	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    70	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    71	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    72	{"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
    73	{"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
    74	{"id":"audit-super-Circle-draw-protocol","tool":"ide_find_super_methods","params":{"file":"src/normal.py","line":26,"column":9}}
    75	{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
    76	{"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}

### live-test/javascript/input.jsonl
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

### live-test/typescript/input.jsonl
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

### live-test/php/input.jsonl
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

### live-test/go/input.jsonl
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
    57	{"id":"audit-find-class-exact-Circle","tool":"ide_find_class","params":{"query":"Circle","matchMode":"exact"}}

### live-test/rust/input.jsonl
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
/usr/bin/zsh -lc "for f in live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/expected.jsonl; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f" | sed -n '"'1,220p'; done" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/java/expected.jsonl
     1	{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}
     2	{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}
     3	{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"makeDefaultShapes","qualifiedName":"demo.Normal#makeDefaultShapes"}
     4	{"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String)"}
     5	{"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":90,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String, int)"}
     6	{"column":22,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":74,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"}
     7	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"LOCAL_VARIABLE","line":17,"name":"coerce","qualifiedName":"demo.Quirks#quirkVar"}
     8	{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}
     9	{"column":29,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal","qualifiedName":"demo.Modern.Animal"}
    10	{"totalCount":4,"usages":[{"column":61,"enclosingScope":["Shape","describe"],"file":"src/main/java/demo/Normal.java","line":14,"usageType":"METHOD_CALL"},{"column":22,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":72,"usageType":"METHOD_CALL"},{"column":35,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"},{"column":49,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"}]}
    11	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/main/java/demo/Normal.java","line":66,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":71,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":79,"usageType":"REFERENCE"}]}
    12	{"totalCount":11,"usages":[{"column":22,"enclosingScope":["Circle"],"file":"src/main/java/demo/Normal.java","line":18,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Rectangle"],"file":"src/main/java/demo/Normal.java","line":36,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","shapes"],"file":"src/main/java/demo/Normal.java","line":63,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/main/java/demo/Normal.java","line":65,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","totalArea","s"],"file":"src/main/java/demo/Normal.java","line":71,"usageType":"REFERENCE"},{"column":5,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":77,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["ShapeCollection","largest","best"],"file":"src/main/java/demo/Normal.java","line":78,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","largest","s"],"file":"src/main/java/demo/Normal.java","line":79,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":87,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["Normal","makeDefaultShapes","shapes"],"file":"src/main/java/demo/Normal.java","line":88,"usageType":"REFERENCE"},{"column":30,"enclosingScope":["Normal","classifyShape","s"],"file":"src/main/java/demo/Normal.java","line":96,"usageType":"REFERENCE"}]}
    13	{"totalCount":1,"usages":[{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":89,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":2}
    15	{"implementations":[{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":31,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":51,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":2}
    16	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    17	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    18	{"implementations":[{"column":19,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":72,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"},{"column":19,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":73,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"}],"totalCount":2}
    19	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    20	{"implementations":[{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":13,"name":"name","qualifiedName":"demo.Modern.Cat#name"},{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":17,"name":"name","qualifiedName":"demo.Modern.Dog#name"}],"totalCount":2}
    21	{"hierarchy":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}}
    22	{"hierarchy":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}}
    23	{"hierarchy":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":31,"name":"draw","qualifiedName":"demo.Circle#draw"}}
    24	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
    25	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    26	{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
    27	{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
    28	{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    29	{"element":{"column":29,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal in Modern","qualifiedName":"demo.Modern.Animal","supertypes":null},"subtypes":[{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"CLASS","line":12,"name":"Cat in Modern","qualifiedName":"demo.Modern.Cat","supertypes":null},{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"CLASS","line":16,"name":"Dog in Modern","qualifiedName":"demo.Modern.Dog","supertypes":null}],"supertypes":[]}
    30	{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
    31	{"calls":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"},{"children":null,"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":77,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}}
    32	{"calls":[{"children":null,"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":94,"name":"Quirks.parseUsage()","qualifiedName":"demo.Quirks#parseUsage"}],"element":{"children":null,"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"Quirks.parse(String)","qualifiedName":"demo.Quirks#parse(java.lang.String)"}}
    33	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Shape","totalCount":2}
    34	{"classes":[{"column":18,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":57,"name":"Coercer","qualifiedName":"demo.Quirks.Coercer"}],"query":"Coercer","totalCount":1}
    35	{"classes":[{"column":19,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}],"query":"Point","totalCount":1}
    36	{"classes":[{"column":29,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal","qualifiedName":"demo.Modern.Animal"}],"query":"Animal","totalCount":1}
    37	{"query":"area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    38	{"query":"quirkLambda","symbols":[{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":11,"name":"quirkLambda","qualifiedName":"demo.Quirks#quirkLambda"}],"totalCount":1}
    39	{"query":"parse","symbols":[{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String)"},{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":90,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String, int)"},{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":94,"name":"parseUsage","qualifiedName":"demo.Quirks#parseUsage"}],"totalCount":3}
    40	{"file":"src/main/java/demo/Normal.java","language":"Java","structure":"Normal.java\n\nDrawable (line 6)\n  draw(): String (line 7)\nabstract Shape (line 10)\n  abstract area(): double (line 11)\n  describe(): String (line 13)\nCircle (line 18)\n  private final radius: double (line 19)\n  Circle(double) (line 21)\n  area(): double \u2191Shape (line 26)\n  public draw(): String (line 31)\nRectangle (line 36)\n  protected final width: double (line 37)\n  protected final height: double (line 38)\n  Rectangle(double, double) (line 40)\n  area(): double \u2191Shape (line 46)\n  public draw(): String (line 51)\nSquare (line 56)\n  Square(double) (line 57)\nShapeCollection (line 62)\n  private final shapes: List<Shape> = new ArrayList<>() (line 63)\n  add(Shape): void (line 65)\n  totalArea(): double (line 69)\n  largest(): Shape (line 77)\npublic Normal (line 86)\n  public static makeDefaultShapes(): List<Shape> (line 87)\n  static classifyShape(Shape): int (line 96)"}
    41	{"file":"src/main/java/demo/Quirks.java","language":"Java","structure":"Quirks.java\n\npublic Quirks (line 9)\n  public static quirkLambda(String): int (line 11)\n    lambda$quirkLambda$0 Function<String, Integer> (line 12)\n  public static quirkVar(String): int (line 16)\n  public static quirkAnonClass(String): int (line 21)\n  public static quirkOptional(String): Optional<Integer> (line 31)\n  public static quirkTernary(String, boolean): int (line 35)\n    lambda$quirkTernary$1 Function<String, Integer> (line 37)\n  public static quirkCompletableFuture(String): CompletableFuture<Integer> (line 42)\n    lambda$quirkCompletableFuture$2 Supplier<Integer> (line 43)\n  public static quirkStreamMap(List<String>): List<Integer> (line 46)\n  public static quirkMapDispatch(String, String): int (line 50)\n    lambda$quirkMapDispatch$3 Function<String, Integer> (line 53)\n  static Coercer (line 57)\n    private final prefix: String (line 58)\n    Coercer(String) (line 59)\n    coerce(String): int (line 60)\n  Coerce (line 64)\n    run(String): int (line 64)\n  public static quirkFunctionalIface(String): int (line 66)\n  CoerceMode (line 71)\n    INT: CoerceMode (line 72)\n      apply(String): int \u2191CoerceMode (line 72)\n    ABS: CoerceMode (line 73)\n      apply(String): int \u2191CoerceMode (line 73)\n    abstract apply(String): int (line 74)\n  public static quirkEnumDispatch(String): int (line 77)\n  public static quirkSupplier(String): int (line 81)\n    lambda$quirkSupplier$4 Supplier<Integer> (line 82)\n  public static parse(String): int (line 86)\n  public static parse(String, int): int (line 90)\n  public static parseUsage(): int (line 94)"}
    42	{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}
    43	{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle"}
    44	{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}
    45	{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21/lib/src.zip!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
    46	{"totalCount":2,"usages":[{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":89,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":98,"usageType":"REFERENCE"}]}
    47	{"indexingProgress":null,"isDumbMode":false,"isIndexing":false}
    48	{"analysisFresh":true,"analysisMessage":"Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":0,"problems":[],"testResults":null,"testResultsTruncated":null,"testSummary":null}
    49	{"totalCount":2,"usages":[{"column":39,"enclosingScope":["Circle"],"file":"src/main/java/demo/Normal.java","line":18,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["Rectangle"],"file":"src/main/java/demo/Normal.java","line":36,"usageType":"REFERENCE"}]}
    50	{"totalCount":3,"usages":[{"column":22,"enclosingScope":["Square"],"file":"src/main/java/demo/Normal.java","line":56,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":90,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":100,"usageType":"REFERENCE"}]}
    51	{"hierarchy":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":51,"name":"draw","qualifiedName":"demo.Rectangle#draw"}}
    52	{"hierarchy":[{"column":16,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":9,"name":"name","qualifiedName":"demo.Modern.Animal#name"}],"method":{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":13,"name":"name","qualifiedName":"demo.Modern.Cat#name"}}
    53	{"hierarchy":[{"column":16,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":9,"name":"name","qualifiedName":"demo.Modern.Animal#name"}],"method":{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":17,"name":"name","qualifiedName":"demo.Modern.Dog#name"}}
    54	{"element":{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[]}
    55	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    56	{"calls":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"Circle.area()","qualifiedName":"demo.Circle#area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"Rectangle.area()","qualifiedName":"demo.Rectangle#area"}],"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"}}
    57	{"calls":[],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"}}
    58	{"calls":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"Circle.area()","qualifiedName":"demo.Circle#area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"Rectangle.area()","qualifiedName":"demo.Rectangle#area"}],"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}],"element":{"children":null,"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":77,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}}
    59	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square"}],"query":"Square","totalCount":1}
    61	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    62	{"query":"describe","symbols":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"describe","qualifiedName":"demo.Shape#describe"}],"totalCount":1}
    63	{"query":"totalArea","symbols":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":1}
    64	{"query":"draw","symbols":[{"column":11,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"totalCount":2}
    65	{"totalCount":4,"usages":[{"column":61,"enclosingScope":["Shape","describe"],"file":"src/main/java/demo/Normal.java","line":14,"usageType":"METHOD_CALL"},{"column":22,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":72,"usageType":"METHOD_CALL"},{"column":35,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"},{"column":49,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"}]}
    66	{"query":"area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    67	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh","totalCount":2}
    68	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    69	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"SC","totalCount":1}
    70	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
    71	{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
    72	{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
    73	{"query":"Shape.area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    74	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    75	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    76	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh*","totalCount":2}
    77	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"LOCAL_VARIABLE","line":97,"name":"kind","qualifiedName":"demo.Normal#classifyShape"}
    78	{"totalCount":4,"usages":[{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":99,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":101,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":103,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":105,"usageType":"REFERENCE"}]}
    79	{"hierarchy":[{"column":7,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
    80	{"hierarchy":[],"method":{"column":10,"file":"src/main/java/demo/Quirks.java","kind":"CONSTRUCTOR","line":71,"name":"CoerceMode","qualifiedName":"demo.Quirks.CoerceMode#CoerceMode"}}
    81	{"file":"src/main/java/demo/Modern.java","language":"Java","structure":"Modern.java\n\npublic Modern (line 3)\n  public Point (line 4)\n    public sum(): int (line 5)\n    x: int (line 4)\n    y: int (line 4)\n  public sealed Animal (line 8)\n    name(): String (line 9)\n  public static final Cat (line 12)\n    public name(): String (line 13)\n  public static final Dog (line 16)\n    public name(): String (line 17)\n  public static probe(): int (line 20)"}
    82	{"file":"src/main/java/demo/Quirks.java","language":"Java","structure":"Quirks.java\n\npublic Quirks (line 9)\n  public static quirkLambda(String): int (line 11)\n    lambda$quirkLambda$0 Function<String, Integer> (line 12)\n  public static quirkVar(String): int (line 16)\n  public static quirkAnonClass(String): int (line 21)\n  public static quirkOptional(String): Optional<Integer> (line 31)\n  public static quirkTernary(String, boolean): int (line 35)\n    lambda$quirkTernary$1 Function<String, Integer> (line 37)\n  public static quirkCompletableFuture(String): CompletableFuture<Integer> (line 42)\n    lambda$quirkCompletableFuture$2 Supplier<Integer> (line 43)\n  public static quirkStreamMap(List<String>): List<Integer> (line 46)\n  public static quirkMapDispatch(String, String): int (line 50)\n    lambda$quirkMapDispatch$3 Function<String, Integer> (line 53)\n  static Coercer (line 57)\n    private final prefix: String (line 58)\n    Coercer(String) (line 59)\n    coerce(String): int (line 60)\n  Coerce (line 64)\n    run(String): int (line 64)\n  public static quirkFunctionalIface(String): int (line 66)\n  CoerceMode (line 71)\n    INT: CoerceMode (line 72)\n      apply(String): int \u2191CoerceMode (line 72)\n    ABS: CoerceMode (line 73)\n      apply(String): int \u2191CoerceMode (line 73)\n    abstract apply(String): int (line 74)\n  public static quirkEnumDispatch(String): int (line 77)\n  public static quirkSupplier(String): int (line 81)\n    lambda$quirkSupplier$4 Supplier<Integer> (line 82)\n  public static parse(String): int (line 86)\n  public static parse(String, int): int (line 90)\n  public static parseUsage(): int (line 94)"}
    83	{"analysisFresh":true,"analysisMessage":"Closed-file diagnostics use public batch analysis; weak warnings are only available when the file is open in an editor. Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":10,"problems":[{"column":12,"endColumn":15,"endLine":7,"file":"src/main/java/demo/Normal.java","line":7,"message":"Method 'draw()' is never used","severity":"WARNING"},{"column":12,"endColumn":19,"endLine":13,"file":"src/main/java/demo/Normal.java","line":13,"message":"Method 'describe()' is never used","severity":"WARNING"},{"column":7,"endColumn":21,"endLine":62,"file":"src/main/java/demo/Normal.java","line":62,"message":"Class 'ShapeCollection' is never used","severity":"WARNING"},{"column":10,"endColumn":12,"endLine":65,"file":"src/main/java/demo/Normal.java","line":65,"message":"Method 'add(demo.Shape)' is never used","severity":"WARNING"},{"column":12,"endColumn":20,"endLine":69,"file":"src/main/java/demo/Normal.java","line":69,"message":"Method 'totalArea()' is never used","severity":"WARNING"},{"column":11,"endColumn":17,"endLine":77,"file":"src/main/java/demo/Normal.java","line":77,"message":"Method 'largest()' is never used","severity":"WARNING"},{"column":14,"endColumn":19,"endLine":86,"file":"src/main/java/demo/Normal.java","line":86,"message":"Class 'Normal' is never used","severity":"WARNING"},{"column":24,"endColumn":28,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Class 'Shape' is exposed outside its defined visibility scope","severity":"WARNING"},{"column":31,"endColumn":47,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Method 'makeDefaultShapes()' is never used","severity":"WARNING"},{"column":16,"endColumn":28,"endLine":96,"file":"src/main/java/demo/Normal.java","line":96,"message":"Method 'classifyShape(demo.Shape)' is never used","severity":"WARNING"}],"testResults":null,"testResultsTruncated":null,"testSummary":null}

### live-test/kotlin/expected.jsonl
     1	{"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"}
     2	{"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}
     3	{"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes","qualifiedName":"demo.NormalKt#makeDefaultShapes"}
     4	{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":27,"name":"coerceTo","qualifiedName":"demo.QuirksKt#coerceTo"}
     5	{"column":29,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":61,"name":"coerceFirst","qualifiedName":"demo.QuirksKt#coerceFirst"}
     6	{"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":10,"name":"create","qualifiedName":"demo.Counter.Companion#create"}
     7	{"column":19,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"PROPERTY","line":9,"name":"DEFAULT_LIMIT","qualifiedName":"demo.Counter#DEFAULT_LIMIT"}
     8	{"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue","qualifiedName":"demo.ModernKt#fetchValue"}
     9	{"totalCount":3,"usages":[{"column":74,"enclosingScope":["Shape","describe"],"file":"src/main/kotlin/demo/Normal.kt","line":10,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["ShapeCollection","totalArea","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":53,"enclosingScope":["ShapeCollection","largest","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    10	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/main/kotlin/demo/Normal.kt","line":29,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    11	{"totalCount":6,"usages":[{"column":36,"enclosingScope":["Circle"],"file":"src/main/kotlin/demo/Normal.kt","line":13,"usageType":"REFERENCE"},{"column":63,"enclosingScope":["Rectangle"],"file":"src/main/kotlin/demo/Normal.kt","line":18,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","shapes"],"file":"src/main/kotlin/demo/Normal.kt","line":26,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/main/kotlin/demo/Normal.kt","line":28,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["makeDefaultShapes"],"file":"src/main/kotlin/demo/Normal.kt","line":37,"usageType":"REFERENCE"}]}
    12	{"totalCount":3,"usages":[{"column":45,"enclosingScope":["Circle","area"],"file":"src/main/kotlin/demo/Normal.kt","line":14,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["Circle","area"],"file":"src/main/kotlin/demo/Normal.kt","line":14,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["Circle","draw"],"file":"src/main/kotlin/demo/Normal.kt","line":15,"usageType":"REFERENCE"}]}
    13	{"totalCount":1,"usages":[{"column":43,"enclosingScope":["quirkInfix"],"file":"src/main/kotlin/demo/Quirks.kt","line":59,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":2}
    15	{"implementations":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":2}
    16	{"implementations":[{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce"},{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce"}],"totalCount":2}
    17	{"implementations":[{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":39,"name":"apply","qualifiedName":"demo.Coercion.IntCoerce#apply"},{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":40,"name":"apply","qualifiedName":"demo.Coercion.AbsCoerce#apply"}],"totalCount":2}
    18	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":8,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":14,"name":"area","qualifiedName":"demo.Circle#area"}}
    19	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":8,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}}
    20	{"hierarchy":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"}}
    21	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    22	{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
    23	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    24	{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    25	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes()","qualifiedName":"demo.NormalKt#makeDefaultShapes"}}
    26	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":10,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"},{"children":null,"column":9,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"},{"children":null,"column":9,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":34,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}],"element":{"children":null,"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":8,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}}
    27	{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
    28	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":17,"name":"quirkLet(String?)","qualifiedName":"demo.QuirksKt#quirkLet"}}
    29	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":21,"name":"quirkWith(String)","qualifiedName":"demo.QuirksKt#quirkWith"}}
    30	{"calls":[{"children":null,"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue()","qualifiedName":"demo.ModernKt#fetchValue"}],"element":{"children":null,"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":16,"name":"computeTotal()","qualifiedName":"demo.ModernKt#computeTotal"}}
    31	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Shape","totalCount":2}
    32	{"classes":[{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"DATA_CLASS","line":45,"name":"Coercer","qualifiedName":"demo.Coercer"}],"query":"Coercer","totalCount":1}
    33	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Modern.kt","kind":"CLASS","line":3,"name":"Counter","qualifiedName":"demo.Counter"}],"query":"Counter","totalCount":1}
    34	{"query":"area","symbols":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":8,"name":"area","qualifiedName":"demo.Shape#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"},{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":4}
    35	{"query":"quirkLambda","symbols":[{"column":5,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":3,"name":"quirkLambda","qualifiedName":"demo.QuirksKt#quirkLambda"}],"totalCount":1}
    36	{"query":"fetchValue","symbols":[{"column":13,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue","qualifiedName":"demo.ModernKt#fetchValue"}],"totalCount":1}
    37	{"file":"src/main/kotlin/demo/Normal.kt","language":"Kotlin","structure":"Normal.kt\n\nDrawable (line 3)\n  draw(): String (line 4)\nabstract Shape (line 7)\n  abstract area(): Double (line 8)\n  open describe(): String (line 10)\nCircle (line 13)\n  constructor Circle(Double) (line 13)\n  radius: Double (line 13)\n  override area(): Double (line 14)\n  override draw(): String (line 15)\nopen Rectangle (line 18)\n  constructor Rectangle(Double, Double) (line 18)\n  width: Double (line 18)\n  height: Double (line 18)\n  override area(): Double (line 19)\n  override draw(): String (line 20)\nSquare (line 23)\n  constructor Square(Double) (line 23)\nShapeCollection (line 25)\n  shapes: MutableList<Shape> (line 26)\n  add(Shape): Unit (line 28)\n  totalArea(): Double (line 32)\n  largest(): Shape? (line 34)\nmakeDefaultShapes(): List<Shape> (line 37)"}
    38	{"file":"src/main/kotlin/demo/Quirks.kt","language":"Kotlin","structure":"Quirks.kt\n\nquirkLambda(String): Int (line 3)\nquirkFunctionRef(String): Int (line 8)\nquirkApply(String): Int (line 13)\nquirkLet(String?): Int (line 17)\nquirkWith(String): Int (line 21)\nquirkRun(String): Int (line 25)\nString.coerceTo(Int): Int (line 27)\nquirkExtensionFn(String): Int (line 29)\nquirkWhen(String, String): Int (line 31)\nsealed Coercion (line 37)\n  abstract apply(String): Int (line 38)\n  object IntCoerce (line 39)\n    override apply(String): Int (line 39)\n  object AbsCoerce (line 40)\n    override apply(String): Int (line 40)\nquirkSealed(Coercion, String): Int (line 43)\ndata Coercer (line 45)\n  constructor Coercer(String) (line 45)\n  prefix: String (line 45)\n  coerce(String): Int (line 46)\nquirkDataClass(String): Int (line 49)\nquirkDispatchMap(String, String): Int (line 51)\nquirkInfix(String): Int (line 59)\ninfix Pair<String, Int>.coerceFirst(): Int (line 61)"}
    39	{"totalCount":2,"usages":[{"column":45,"enclosingScope":["Circle"],"file":"src/main/kotlin/demo/Normal.kt","line":13,"usageType":"REFERENCE"},{"column":72,"enclosingScope":["Rectangle"],"file":"src/main/kotlin/demo/Normal.kt","line":18,"usageType":"REFERENCE"}]}
    40	{"totalCount":2,"usages":[{"column":30,"enclosingScope":["Square"],"file":"src/main/kotlin/demo/Normal.kt","line":23,"usageType":"REFERENCE"},{"column":60,"enclosingScope":["makeDefaultShapes"],"file":"src/main/kotlin/demo/Normal.kt","line":37,"usageType":"REFERENCE"}]}
    41	{"implementations":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"},{"column":12,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"totalCount":3}
    42	{"implementations":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"totalCount":1}
    43	{"hierarchy":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}}
    44	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":38,"name":"apply","qualifiedName":"demo.Coercion#apply"}],"method":{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":39,"name":"apply","qualifiedName":"demo.Coercion.IntCoerce#apply"}}
    45	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":38,"name":"apply","qualifiedName":"demo.Coercion#apply"}],"method":{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":40,"name":"apply","qualifiedName":"demo.Coercion.AbsCoerce#apply"}}
    46	{"element":{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[]}
    47	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
    48	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    49	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    50	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"query":"Square","totalCount":1}
    51	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    52	{"classes":[{"column":11,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable"}],"query":"Drawable","totalCount":1}
    53	{"query":"describe","symbols":[{"column":14,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":10,"name":"describe","qualifiedName":"demo.Shape#describe"}],"totalCount":1}
    54	{"query":"totalArea","symbols":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":1}
    55	{"query":"draw","symbols":[{"column":11,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable"},{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":4}
    56	{"totalCount":3,"usages":[{"column":74,"enclosingScope":["Shape","describe"],"file":"src/main/kotlin/demo/Normal.kt","line":10,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["ShapeCollection","totalArea","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":53,"enclosingScope":["ShapeCollection","largest","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    57	{"query":"area","symbols":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":4}
    58	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh","totalCount":2}
    59	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"},{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce"}],"query":"SC","totalCount":2}
    61	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    62	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    63	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes()","qualifiedName":"demo.NormalKt#makeDefaultShapes"}}
    64	{"query":"Coercer.coerce","symbols":[{"column":9,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":46,"name":"coerce","qualifiedName":"demo.Coercer#coerce"}],"totalCount":1}
    65	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    66	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    67	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh*","totalCount":2}

### live-test/python/expected.jsonl
     1	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     2	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     3	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}
     4	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}
     5	{"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes","qualifiedName":"normal.make_default_shapes"}
     6	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
     7	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
     8	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/uv/python/cpython-3.11.14-linux-x86_64-gnu/lib/python3.11/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
     9	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    10	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    11	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    12	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    13	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":69,"name":"result","qualifiedName":"quirks.quirk_walrus"}
    14	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    15	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"FUNCTION","line":80,"name":"get_coercer","qualifiedName":"quirks.quirk_nested_return"}
    16	{"totalCount":3,"usages":[{"column":51,"enclosingScope":["Shape","describe"],"file":"src/normal.py","line":16,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":47,"enclosingScope":["ShapeCollection","largest","key"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    17	{"totalCount":5,"usages":[{"column":14,"enclosingScope":["ShapeCollection","__init__"],"file":"src/normal.py","line":49,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.py","line":52,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":58,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    18	{"totalCount":6,"usages":[{"column":14,"enclosingScope":["Circle"],"file":"src/normal.py","line":19,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["Rectangle"],"file":"src/normal.py","line":30,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["ShapeCollection","__init__"],"file":"src/normal.py","line":49,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/normal.py","line":51,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["make_default_shapes"],"file":"src/normal.py","line":63,"usageType":"REFERENCE"}]}
    19	{"totalCount":36,"usages":[{"column":37,"enclosingScope":["quirk_name_rebinding"],"file":"src/quirks.py","line":11,"usageType":"REFERENCE"},{"column":10,"enclosingScope":["quirk_name_rebinding"],"file":"src/quirks.py","line":12,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_getattr_module"],"file":"src/quirks.py","line":16,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_getattr_module"],"file":"src/quirks.py","line":18,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_functools_partial"],"file":"src/quirks.py","line":21,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["quirk_functools_partial"],"file":"src/quirks.py","line":22,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":26,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":27,"usageType":"REFERENCE"},{"column":50,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":27,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["quirk_lambda_wrap"],"file":"src/quirks.py","line":31,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["quirk_lambda_wrap"],"file":"src/quirks.py","line":32,"usageType":"REFERENCE"},{"column":36,"enclosingScope":["quirk_list_indexing"],"file":"src/quirks.py","line":36,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["quirk_list_indexing"],"file":"src/quirks.py","line":37,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["quirk_conditional_expr"],"file":"src/quirks.py","line":41,"usageType":"REFERENCE"},{"column":10,"enclosingScope":["quirk_conditional_expr"],"file":"src/quirks.py","line":42,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":46,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["quirk_decorator_wrap"],"file":"src/quirks.py","line":51,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["quirk_decorator_wrap"],"file":"src/quirks.py","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["quirk_class_method"],"file":"src/quirks.py","line":61,"usageType":"REFERENCE"},{"column":39,"enclosingScope":["quirk_class_method","Coercer","coerce"],"file":"src/quirks.py","line":63,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["quirk_class_method","Coercer","coerce"],"file":"src/quirks.py","line":64,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["quirk_walrus"],"file":"src/quirks.py","line":68,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["quirk_walrus"],"file":"src/quirks.py","line":69,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["quirk_unpacking"],"file":"src/quirks.py","line":74,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_unpacking"],"file":"src/quirks.py","line":75,"usageType":"REFERENCE"},{"column":36,"enclosingScope":["quirk_nested_return"],"file":"src/quirks.py","line":79,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["quirk_nested_return","get_coercer"],"file":"src/quirks.py","line":81,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["quirk_map_filter"],"file":"src/quirks.py","line":85,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["quirk_map_filter"],"file":"src/quirks.py","line":86,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_reduce"],"file":"src/quirks.py","line":89,"usageType":"REFERENCE"},{"column":50,"enclosingScope":["quirk_reduce"],"file":"src/quirks.py","line":90,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["quirk_chained_getattr"],"file":"src/quirks.py","line":93,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_chained_getattr"],"file":"src/quirks.py","line":95,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["quirk_multiple_assignment"],"file":"src/quirks.py","line":98,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["quirk_multiple_assignment"],"file":"src/quirks.py","line":99,"usageType":"REFERENCE"}]}
    20	{"totalCount":4,"usages":[{"column":14,"enclosingScope":["Circle","__init__"],"file":"src/normal.py","line":21,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["Circle","area"],"file":"src/normal.py","line":24,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["Circle","area"],"file":"src/normal.py","line":24,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["Circle","draw"],"file":"src/normal.py","line":27,"usageType":"REFERENCE"}]}
    21	{"implementations":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}],"totalCount":2}
    22	{"implementations":[],"totalCount":0}
    23	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":31,"name":"__init__","qualifiedName":"normal.Rectangle.__init__"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":43,"name":"__init__","qualifiedName":"normal.Square.__init__"}}
    24	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"}}
    25	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}}
    26	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
    27	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
    28	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
    29	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
    30	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"Shape.describe(self)","qualifiedName":"normal.Shape.describe"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}}
    31	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}
    32	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Shape","totalCount":2}
    33	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle","qualifiedName":"normal.Rectangle"}],"query":"Rectangle","totalCount":1}
    34	{"query":"area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":54,"name":"total_area","qualifiedName":"normal.ShapeCollection.total_area"}],"totalCount":4}
    35	{"query":"quirk_name_rebinding","symbols":[{"column":5,"file":"src/quirks.py","kind":"FUNCTION","line":11,"name":"quirk_name_rebinding","qualifiedName":"quirks.quirk_name_rebinding"}],"totalCount":1}
    36	{"file":"src/normal.py","language":"Python","structure":"normal.py\n\nDrawable(Protocol) (line 6)\n  draw(self) (line 7)\nShape(ABC) (line 10)\n  area(self) (line 12)\n  describe(self) (line 15)\nCircle(Shape) (line 19)\n  __init__(self, radius) (line 20)\n  area(self) (line 23)\n  draw(self) (line 26)\n  radius (line 21)\nRectangle(Shape) (line 30)\n  __init__(self, width, height) (line 31)\n  area(self) (line 35)\n  draw(self) (line 38)\n  height (line 33)\n  width (line 32)\nSquare(Rectangle) (line 42)\n  __init__(self, side) (line 43)\nShapeCollection (line 47)\n  __init__(self) (line 48)\n  add(self, shape) (line 51)\n  total_area(self) (line 54)\n  largest(self) (line 57)\n  shapes (line 49)\nmake_default_shapes() (line 63)"}
    37	{"file":"src/quirks.py","language":"Python","structure":"quirks.py\n\nquirk_name_rebinding(x) (line 11)\nquirk_getattr_module(name) (line 16)\nquirk_functools_partial(x) (line 21)\nquirk_dict_dispatch(key, x) (line 26)\nquirk_lambda_wrap(x) (line 31)\nquirk_list_indexing(x) (line 36)\nquirk_conditional_expr(x, use_int) (line 41)\nquirk_star_import_simulation(x) (line 46)\nquirk_decorator_wrap(x) (line 51)\n  with_logging(fn) (line 52)\n    wrapper(*args, **kwargs) (line 54)\nquirk_class_method(x) (line 61)\n  Coercer (line 62)\n    coerce(self, raw) (line 63)\nquirk_walrus(x) (line 68)\nquirk_unpacking(x) (line 74)\nquirk_nested_return(x) (line 79)\n  get_coercer() (line 80)\nquirk_map_filter(items) (line 85)\nquirk_reduce(values) (line 89)\nquirk_chained_getattr(x) (line 93)\nquirk_multiple_assignment(x) (line 98)\nclassify_shape(s) (line 103)"}
    38	{"totalCount":0,"usages":[]}
    39	{"totalCount":4,"usages":[{"column":14,"enclosingScope":["Square"],"file":"src/normal.py","line":42,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["make_default_shapes"],"file":"src/normal.py","line":64,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":105,"usageType":"IMPORT"},{"column":24,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":108,"usageType":"REFERENCE"}]}
    40	{"implementations":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle","qualifiedName":"normal.Rectangle"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"totalCount":3}
    41	{"implementations":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"totalCount":1}
    42	{"implementations":[],"totalCount":0}
    43	{"implementations":[],"totalCount":0}
    44	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"}}
    45	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":38,"name":"draw","qualifiedName":"normal.Rectangle.draw"}}
    46	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable(Protocol)","qualifiedName":"normal.Drawable","supertypes":null},"subtypes":[],"supertypes":[]}
    47	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
    48	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}
    49	{"calls":[],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"Shape.describe(self)","qualifiedName":"normal.Shape.describe"}}
    50	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}
    51	{"calls":[],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":51,"name":"ShapeCollection.add(self, shape)","qualifiedName":"normal.ShapeCollection.add"}}
    52	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"}],"query":"Circle","totalCount":1}
    53	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"query":"Square","totalCount":1}
    54	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable","qualifiedName":"normal.Drawable"}],"query":"Drawable","totalCount":1}
    55	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    56	{"query":"describe","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}],"totalCount":1}
    57	{"query":"total_area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":54,"name":"total_area","qualifiedName":"normal.ShapeCollection.total_area"}],"totalCount":1}
    58	{"query":"draw","symbols":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable","qualifiedName":"normal.Drawable"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":7,"name":"draw","qualifiedName":"normal.Drawable.draw"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":38,"name":"draw","qualifiedName":"normal.Rectangle.draw"}],"totalCount":4}
    59	{"query":"largest","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":57,"name":"largest","qualifiedName":"normal.ShapeCollection.largest"}],"totalCount":1}
    60	{"totalCount":3,"usages":[{"column":51,"enclosingScope":["Shape","describe"],"file":"src/normal.py","line":16,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":47,"enclosingScope":["ShapeCollection","largest","key"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    61	{"query":"area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}],"totalCount":4}
    62	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Sh","totalCount":2}
    63	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"}],"query":"Circle","totalCount":1}
    64	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"SC","totalCount":1}
    65	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
    66	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
    67	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
    68	{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}
    69	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    70	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    71	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Sh*","totalCount":2}
    72	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":107,"name":"kind","qualifiedName":"quirks.classify_shape"}
    73	{"totalCount":4,"usages":[{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":107,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":109,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":111,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":112,"usageType":"REFERENCE"}]}
    74	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"}}
    75	{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}
    76	{"totalCount":2,"usages":[{"column":33,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":47,"usageType":"IMPORT"},{"column":12,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"}]}

### live-test/javascript/expected.jsonl
     1	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}
     2	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}
     3	{"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes","qualifiedName":"makeDefaultShapes"}
     4	{"column":11,"enclosingScope":null,"file":"src/quirks.js","kind":"CONSTANT","line":5,"name":"fn","qualifiedName":"fn"}
     5	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     6	{"column":23,"enclosingScope":null,"file":"src/quirks.js","kind":"CONSTANT","line":36,"name":"p","qualifiedName":"p"}
     7	{"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
     8	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     9	{"column":17,"enclosingScope":null,"file":"src/quirks.js","kind":"PROPERTY","line":80,"name":"parse","qualifiedName":"proxy.parse"}
    10	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
    11	{"totalCount":5,"usages":[{"column":12,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":63,"enclosingScope":["Shape","describe"],"file":"src/normal.js","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"}]}
    12	{"totalCount":3,"usages":[{"column":18,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.js","line":41,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":49,"usageType":"REFERENCE"}]}
    13	{"totalCount":5,"usages":[{"column":9,"enclosingScope":[],"file":"src/accessors.js","line":2,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["Box"],"file":"src/accessors.js","line":4,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Circle"],"file":"src/normal.js","line":12,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Rectangle"],"file":"src/normal.js","line":21,"usageType":"REFERENCE"},{"column":30,"enclosingScope":["Shape"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":3}
    15	{"implementations":[],"totalCount":0}
    16	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}}
    17	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}}
    18	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    19	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    20	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    21	{"calls":[{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    22	{"calls":[{"children":[{"children":null,"column":1,"enclosingScope":["/","home","ubuntu","dev","jetbrains-index-mcp-plugin","live-test","javascript","src"],"file":"src/consumer.js","kind":"FILE","line":1,"name":"consumer.js","qualifiedName":null}],"column":10,"enclosingScope":null,"file":"src/consumer.js","kind":"FUNCTION","line":4,"name":"use()","qualifiedName":"use"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":9,"name":"Shape.describe()","qualifiedName":"Shape.describe"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":42,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":47,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":8,"name":"Shape.area()","qualifiedName":"Shape.area"}}
    23	{"calls":[],"element":{"children":null,"column":16,"enclosingScope":null,"file":"src/quirks.js","kind":"FUNCTION","line":64,"name":"qAwait(x)","qualifiedName":"qAwait"}}
    24	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Shape","totalCount":2}
    25	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"}],"query":"Rectangle","totalCount":1}
    26	{"query":"area","symbols":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":5}
    27	{"query":"qRebind","symbols":[{"column":10,"file":"src/quirks.js","kind":"FUNCTION","line":4,"name":"qRebind","qualifiedName":"qRebind"},{"column":5,"file":"src/quirks.js","kind":"PROPERTY","line":86,"name":"qRebind","qualifiedName":"exports.qRebind"}],"totalCount":2}
    28	{"file":"src/normal.js","language":"JavaScript","structure":"normal.js\n\nexports (line 1)\n  Drawable (line 60)\n  Shape (line 60)\n  Circle (line 60)\n  Rectangle (line 60)\n  Square (line 60)\n  ShapeCollection (line 60)\n  makeDefaultShapes (line 60)\nDrawable (line 3)\n  draw(): never (line 4)\nShape (line 7)\n  area(): never (line 8)\n  describe(): string (line 9)\nCircle (line 12)\n  constructor(radius) (line 13)\n  radius (line 15)\n  area() (line 17)\n  draw(): string (line 18)\nRectangle (line 21)\n  constructor(width, height) (line 22)\n  width (line 24)\n  height (line 25)\n  area() (line 27)\n  draw(): string (line 28)\nSquare (line 31)\n  constructor(side) (line 32)\nShapeCollection (line 37)\n  constructor() (line 38)\n  shapes (line 39)\n  add(shape): void (line 41)\n  totalArea() (line 42)\n  largest() (line 47)\nmakeDefaultShapes() (line 56)"}
    29	{"file":"src/quirks.js","language":"JavaScript","structure":"quirks.js\n\nexports (line 1)\n  qRebind (line 86)\n  qComputed (line 86)\n  qObjLit (line 86)\n  qCond (line 86)\n  qReturned (line 86)\n  qArrayIdx (line 86)\n  qDestructured (line 87)\n  qSpread (line 87)\n  qBind (line 87)\n  qForEach (line 87)\n  qPromise (line 87)\n  qAwait (line 87)\n  qOpt (line 88)\n  qNullish (line 88)\n  qProxy (line 88)\nqRebind(x) (line 4)\nqComputed(name, x) (line 10)\nqObjLit(x) (line 15)\n  parse (line 16)\nqCond(flag, x) (line 20)\nqReturned(x) (line 25)\nqArrayIdx(x) (line 30)\nqDestructured(x) (line 35)\nqSpread(x) (line 41)\nqBind(x) (line 47)\nqForEach(x) (line 52)\nqPromise(x) (line 59)\nasync qAwait(x) (line 64)\nqOpt(x) (line 70)\nqNullish(x) (line 75)\nproxy (line 80)\n  parse (line 80)\nqProxy(x) (line 81)"}
    30	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"CONSTRUCTOR","line":13,"name":"constructor","qualifiedName":"Circle"}
    31	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"CONSTRUCTOR","line":22,"name":"constructor","qualifiedName":"Rectangle"}
    32	{"totalCount":4,"usages":[{"column":9,"enclosingScope":[],"file":"src/consumer.js","line":2,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Circle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    33	{"totalCount":1,"usages":[{"column":20,"enclosingScope":["Drawable"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    34	{"totalCount":3,"usages":[{"column":22,"enclosingScope":["Square"],"file":"src/normal.js","line":31,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["Rectangle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    35	{"totalCount":0,"usages":[]}
    36	{"implementations":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    37	{"implementations":[],"totalCount":0}
    38	{"implementations":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"totalCount":1}
    39	{"implementations":[],"totalCount":0}
    40	{"hierarchy":[],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"}}
    41	{"hierarchy":[],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":28,"name":"draw","qualifiedName":"Rectangle.draw"}}
    42	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},"subtypes":[],"supertypes":[]}
    43	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    44	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    45	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":42,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"}}
    46	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":9,"name":"Shape.describe()","qualifiedName":"Shape.describe"}}
    47	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":47,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}}
    48	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    49	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"query":"Square","totalCount":1}
    50	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable"}],"query":"Drawable","totalCount":1}
    51	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    52	{"query":"describe","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":9,"name":"describe","qualifiedName":"Shape.describe"}],"totalCount":1}
    53	{"query":"totalArea","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":1}
    54	{"query":"draw","symbols":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":4,"name":"draw","qualifiedName":"Drawable.draw"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":28,"name":"draw","qualifiedName":"Rectangle.draw"},{"column":20,"file":"src/normal.js","kind":"PROPERTY","line":60,"name":"Drawable","qualifiedName":"exports.Drawable"}],"totalCount":5}
    55	{"query":"largest","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":47,"name":"largest","qualifiedName":"ShapeCollection.largest"}],"totalCount":1}
    56	{"totalCount":4,"usages":[{"column":12,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"}]}
    57	{"query":"area","symbols":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}],"totalCount":5}
    58	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh","totalCount":2}
    59	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"SC","totalCount":1}
    61	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    62	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    63	{"calls":[{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    64	{"query":"Shape.area","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}
    65	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    66	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    67	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh*","totalCount":2}
    68	{"totalCount":4,"usages":[{"column":9,"enclosingScope":[],"file":"src/consumer.js","line":2,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Circle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    69	{"file":"src/accessors.js","language":"JavaScript","structure":"accessors.js\n\nexports (line 1)\n  Box (line 14)\nShape (line 2)\nBox (line 4)\n  constructor(s) (line 5)\n  s (line 7)\n  get area (line 9)"}
    70	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"}}
    71	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}

### live-test/typescript/expected.jsonl
     1	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}
     2	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}
     3	{"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes","qualifiedName":"makeDefaultShapes"}
     4	{"column":11,"enclosingScope":null,"file":"src/quirks.ts","kind":"FUNCTION","line":2,"name":"fn","qualifiedName":"fn"}
     5	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     6	{"column":27,"enclosingScope":null,"file":"src/quirks.ts","kind":"TYPE_PARAMETER","line":52,"name":"T","qualifiedName":"TypedCoercer"}
     7	{"totalCount":4,"usages":[{"column":54,"enclosingScope":["Shape","describe"],"file":"src/normal.ts","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"}]}
     8	{"totalCount":3,"usages":[{"column":31,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":48,"usageType":"REFERENCE"}]}
     9	{"totalCount":7,"usages":[{"column":29,"enclosingScope":["Circle"],"file":"src/normal.ts","line":13,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["Rectangle"],"file":"src/normal.ts","line":21,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.ts","line":36,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":46,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["ShapeCollection","largest","best"],"file":"src/normal.ts","line":47,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.ts","line":55,"usageType":"REFERENCE"}]}
    10	{"totalCount":2,"usages":[{"column":26,"enclosingScope":["intCoercer"],"file":"src/quirks.ts","line":44,"usageType":"REFERENCE"},{"column":39,"enclosingScope":["qInterfaceDispatch","c"],"file":"src/quirks.ts","line":48,"usageType":"REFERENCE"}]}
    11	{"totalCount":1,"usages":[{"column":12,"enclosingScope":["qTypeAlias"],"file":"src/quirks.ts","line":65,"usageType":"REFERENCE"}]}
    12	{"totalCount":2,"usages":[{"column":46,"enclosingScope":["Circle"],"file":"src/normal.ts","line":13,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["Rectangle"],"file":"src/normal.ts","line":21,"usageType":"REFERENCE"}]}
    13	{"implementations":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":2}
    14	{"implementations":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}],"totalCount":2}
    15	{"implementations":[],"totalCount":0}
    16	{"hierarchy":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}}
    17	{"hierarchy":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}}
    18	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    19	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    20	{"element":{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    21	{"element":{"column":14,"enclosingScope":null,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer","supertypes":null},"subtypes":[],"supertypes":[]}
    22	{"element":{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    23	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    24	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"Shape.describe()","qualifiedName":"Shape.describe"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}],"element":{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}}
    25	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Shape","totalCount":2}
    26	{"classes":[{"column":18,"file":"src/quirks.ts","kind":"INTERFACE","line":42,"name":"Coercer","qualifiedName":"Coercer"},{"column":14,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer"}],"query":"Coercer","totalCount":2}
    27	{"classes":[{"column":18,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable"}],"query":"Drawable","totalCount":1}
    28	{"query":"area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":4}
    29	{"query":"qLambda","symbols":[{"column":17,"file":"src/quirks.ts","kind":"FUNCTION","line":1,"name":"qLambda","qualifiedName":"qLambda"},{"column":17,"file":"src/quirks.ts","kind":"FUNCTION","line":11,"name":"qGenericLambda","qualifiedName":"qGenericLambda"}],"totalCount":2}
    30	{"file":"src/normal.ts","language":"TypeScript","structure":"normal.ts\n\nexport Drawable (line 1)\n  draw(): string (line 2)\nexport abstract Shape (line 5)\n  abstract area(): number (line 6)\n  describe(): string (line 8)\nexport Circle (line 13)\n  constructor(radius: number) (line 14)\n  public readonly radius: number (line 14)\n  area(): number (line 17)\n  draw(): string (line 18)\nexport Rectangle (line 21)\n  constructor(width: number, height: number) (line 22)\n  public readonly width: number (line 22)\n  public readonly height: number (line 22)\n  area(): number (line 25)\n  draw(): string (line 26)\nexport Square (line 29)\n  constructor(side: number) (line 30)\nexport ShapeCollection (line 35)\n  readonly shapes: Shape[] (line 36)\n  add(shape: Shape): void (line 38)\n  totalArea(): number (line 40)\n  largest(): Shape | null (line 46)\nexport makeDefaultShapes(): Shape[] (line 55)"}
    31	{"file":"src/quirks.ts","language":"TypeScript","structure":"quirks.ts\n\nexport qLambda(x: string): number (line 1)\n  fn(s: any) (line 2)\nexport qFunctionRef(x: string): number (line 6)\nexport qGenericLambda(x: T): number (line 11)\n  fn(s: T): number (line 12)\nexport qConditionalType(mode: T, x: string): number (line 16)\n  Fn (line 17)\nexport qDispatchMap(key: string, x: string): number (line 22)\n  dispatch: Record<string, (s: string) => number> (line 23)\n    int(s: any) (line 24)\n    abs(s: any) (line 25)\nexport qOptional(x?: string): number (line 30)\nexport qNonNullAssertion(x: string | undefined): number (line 34)\nexport qAsCast(x: unknown): number (line 38)\nexport Coercer (line 42)\n  coerce(x: string): number (line 42)\nexport intCoercer: Coercer (line 44)\n  coerce(x: string) (line 45)\nexport qInterfaceDispatch(c: Coercer, x: string): number (line 48)\nexport TypedCoercer<T> (line 52)\n  coerce(x: T): number (line 53)\nexport qGenericClass(x: string): number (line 56)\nexport Coerce (line 60)\naliasedCoerce(s: any) (line 62)\nexport qTypeAlias(x: string): number (line 64)"}
    32	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"CONSTRUCTOR","line":14,"name":"constructor","qualifiedName":"Circle"}
    33	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"CONSTRUCTOR","line":22,"name":"constructor","qualifiedName":"Rectangle"}
    34	{"column":14,"enclosingScope":null,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer"}
    35	{"totalCount":1,"usages":[{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.ts","line":56,"usageType":"REFERENCE"}]}
    36	{"implementations":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    37	{"implementations":[],"totalCount":0}
    38	{"implementations":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    39	{"hierarchy":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"}}
    40	{"hierarchy":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}}
    41	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    42	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    43	{"calls":[{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"Circle.area()","qualifiedName":"Circle.area"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"Rectangle.area()","qualifiedName":"Rectangle.area"}],"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"}}
    44	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"Shape.describe()","qualifiedName":"Shape.describe"}}
    45	{"calls":[{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"Circle.area()","qualifiedName":"Circle.area"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"Rectangle.area()","qualifiedName":"Rectangle.area"}],"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}}
    46	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"Drawable.draw()","qualifiedName":"Drawable.draw"}}
    47	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    48	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"query":"Square","totalCount":1}
    49	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    50	{"query":"describe","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"describe","qualifiedName":"Shape.describe"}],"totalCount":1}
    51	{"query":"totalArea","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":1}
    52	{"query":"draw","symbols":[{"column":18,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}],"totalCount":4}
    53	{"query":"largest","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"largest","qualifiedName":"ShapeCollection.largest"}],"totalCount":1}
    54	{"totalCount":4,"usages":[{"column":54,"enclosingScope":["Shape","describe"],"file":"src/normal.ts","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"}]}
    55	{"query":"area","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":4}
    56	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh","totalCount":2}
    57	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    58	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"SC","totalCount":1}
    59	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    60	{"element":{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    62	{"query":"Shape.area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh*","totalCount":2}
    66	{"element":{"column":13,"enclosingScope":null,"file":"src/quirks.ts","kind":"TYPE_ALIAS","line":60,"name":"Coerce","qualifiedName":"Coerce","supertypes":null},"subtypes":[],"supertypes":[]}

### live-test/php/expected.jsonl
     1	{"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"}
     2	{"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}
     3	{"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}
     4	{"column":10,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
     5	{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":4,"name":"Quirks","qualifiedName":"\\Demo\\Quirks"}
     6	{"column":55,"enclosingScope":null,"file":"src/Normal.php","kind":"PARAMETER","line":17,"name":"radius","qualifiedName":"\\Demo\\Circle::__construct(radius)"}
     7	{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status"}
     8	{"column":10,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM_CASE","line":5,"name":"Active","qualifiedName":"\\Demo\\Status::Active"}
     9	{"totalCount":4,"usages":[{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"}]}
    10	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["Demo","ShapeCollection","add"],"file":"src/Normal.php","line":51,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":56,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":64,"usageType":"REFERENCE"}]}
    11	{"totalCount":7,"usages":[{"column":26,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"PARAMETER"},{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Demo","Circle"],"file":"src/Normal.php","line":16,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","Rectangle"],"file":"src/Normal.php","line":28,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["Demo","ShapeCollection"],"file":"src/Normal.php","line":47,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","ShapeCollection","add","shape"],"file":"src/Normal.php","line":50,"usageType":"PARAMETER"},{"column":33,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":62,"usageType":"REFERENCE"}]}
    12	{"totalCount":2,"usages":[{"column":22,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":42,"usageType":"REFERENCE"}]}
    13	{"totalCount":5,"usages":[{"column":22,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":9,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":10,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":11,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["Demo","defaultStatus"],"file":"src/Modern.php","line":21,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["Demo","defaultStatus"],"file":"src/Modern.php","line":22,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}],"totalCount":2}
    15	{"implementations":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}],"totalCount":2}
    16	{"implementations":[{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer"}],"totalCount":2}
    17	{"implementations":[{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":84,"name":"coerce","qualifiedName":"\\Demo\\IntCoercer::coerce"},{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":88,"name":"coerce","qualifiedName":"\\Demo\\LenCoercer::coerce"}],"totalCount":2}
    18	{"hierarchy":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"}}
    19	{"hierarchy":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}}
    20	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"}}
    21	{"hierarchy":[{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":80,"name":"coerce","qualifiedName":"\\Demo\\Coercer::coerce"}],"method":{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":84,"name":"coerce","qualifiedName":"\\Demo\\IntCoercer::coerce"}}
    22	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}]}
    23	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}
    24	{"element":{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    25	{"element":{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    26	{"element":{"column":11,"enclosingScope":null,"file":"src/Quirks.php","kind":"INTERFACE","line":79,"name":"Coercer","qualifiedName":"\\Demo\\Coercer","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer","supertypes":null}],"supertypes":[]}
    27	{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
    28	{"calls":[],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}}
    29	{"calls":[{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"Shape.describe","qualifiedName":"\\Demo\\Shape::describe"},{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"ShapeCollection.totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"},{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"ShapeCollection.largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}],"element":{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}}
    30	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Shape","totalCount":2}
    31	{"classes":[{"column":11,"file":"src/Quirks.php","kind":"INTERFACE","line":79,"name":"Coercer","qualifiedName":"\\Demo\\Coercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer"}],"query":"Coercer","totalCount":3}
    32	{"classes":[{"column":11,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable"}],"query":"Drawable","totalCount":1}
    33	{"classes":[{"column":6,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status"}],"query":"Status","totalCount":1}
    34	{"query":"area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":4}
    35	{"query":"qNameRebind","symbols":[{"column":28,"file":"src/Quirks.php","kind":"METHOD","line":6,"name":"qNameRebind","qualifiedName":"\\Demo\\Quirks::qNameRebind"}],"totalCount":1}
    36	{"file":"src/Normal.php","language":"PHP","structure":"Normal.php\n\nabstract public Drawable (line 4)\n  public draw(): string (line 5)\nabstract public Shape (line 8)\n  abstract public area(): float (line 9)\n  public describe(): string (line 11)\npublic Circle (line 16)\n  public radius: float (line 17)\n  public __construct(radius: float) (line 17)\n  public area(): float \u2191Shape (line 19)\n  public draw(): string \u2191Drawable (line 23)\npublic Rectangle (line 28)\n  public width: float (line 29)\n  public height: float (line 29)\n  public __construct(width: float, height: float) (line 29)\n  public area(): float \u2191Shape (line 31)\n  public draw(): string \u2191Drawable (line 35)\npublic Square (line 40)\n  public __construct(side: float) \u2191Rectangle (line 41)\npublic ShapeCollection (line 46)\n  public shapes: Shape[] = [...] (line 48)\n  public add(shape: Shape): void (line 50)\n  public totalArea(): float (line 54)\n  public largest(): Shape|null (line 62)\nmakeDefaultShapes(): array (line 73)"}
    37	{"file":"src/Quirks.php","language":"PHP","structure":"Quirks.php\n\npublic Quirks (line 4)\n  public static qNameRebind(x: string): int (line 6)\n  public static qVariableFunction(x: string): int (line 11)\n  public static qClosure(x: string): int (line 16)\n    Lambda(s: string): int (line 17)\n  public static qArrowFn(x: string): int (line 21)\n    Lambda(s: string): int (line 22)\n  public static qArrayDispatch(key: string, x: string): int (line 26)\n  public static qCallableArray(x: string): int (line 32)\n  public static qCallUserFunc(x: string): int (line 37)\n  public static qStaticMethodVariable(x: string): int (line 41)\n  public static qFromCallable(x: string): int (line 46)\n  public static qTernary(flag: bool, x: string): int (line 51)\n  public static qNullCoalesce(x: string): int (line 56)\n  public static qMatch(mode: string, x: string): int (line 61)\n    Lambda(s): int (line 63)\n    Lambda(s): int (line 65)\n  public static qCoerceUsage(c: Coercer, x: string): int (line 70)\n  public static qPromotedRead(c: Circle): float (line 74)\nabstract public Coercer (line 79)\n  public coerce(x: string): int (line 80)\npublic IntCoercer (line 83)\n  public coerce(x: string): int \u2191Coercer (line 84)\npublic LenCoercer (line 87)\n  public coerce(x: string): int \u2191Coercer (line 88)"}
    38	{"file":"src/Modern.php","language":"PHP","structure":"Modern.php\n\npublic final Status (line 4)\n  public static final Active: Status = 'A' (line 5)\n  public static final Inactive: Status = 'I' (line 6)\n  public label(): string (line 8)\npublic final Color (line 16)\n  public static final Red: Color (line 17)\n  public static final Green: Color (line 18)\ndefaultStatus(): Status (line 21)"}
    39	{"totalCount":2,"usages":[{"column":39,"enclosingScope":["Demo","Circle"],"file":"src/Normal.php","line":16,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["Demo","Rectangle"],"file":"src/Normal.php","line":28,"usageType":"REFERENCE"}]}
    40	{"totalCount":7,"usages":[{"column":16,"enclosingScope":["Demo","Rectangle","area"],"file":"src/Normal.php","line":32,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["Demo","Rectangle","area"],"file":"src/Normal.php","line":32,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Demo","Rectangle","draw"],"file":"src/Normal.php","line":36,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["Demo","Rectangle","draw"],"file":"src/Normal.php","line":36,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Demo","Square"],"file":"src/Normal.php","line":40,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Demo","Square","__construct"],"file":"src/Normal.php","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["Demo","makeDefaultShapes"],"file":"src/Normal.php","line":74,"usageType":"REFERENCE"}]}
    41	{"implementations":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square"}],"totalCount":3}
    42	{"implementations":[],"totalCount":0}
    43	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}}
    44	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":29,"name":"__construct","qualifiedName":"\\Demo\\Rectangle::__construct"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":41,"name":"__construct","qualifiedName":"\\Demo\\Square::__construct"}}
    45	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}
    46	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    47	{"calls":[{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"ShapeCollection.totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}}
    48	{"calls":[],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"Shape.describe","qualifiedName":"\\Demo\\Shape::describe"}}
    49	{"calls":[{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"ShapeCollection.largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}}
    50	{"calls":[],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"Drawable.draw","qualifiedName":"\\Demo\\Drawable::draw"}}
    51	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"}],"query":"Circle","totalCount":1}
    52	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square"}],"query":"Square","totalCount":1}
    53	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    54	{"query":"describe","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"describe","qualifiedName":"\\Demo\\Shape::describe"}],"totalCount":1}
    55	{"query":"totalArea","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":1}
    56	{"query":"draw","symbols":[{"column":11,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}],"totalCount":4}
    57	{"query":"largest","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}],"totalCount":1}
    58	{"totalCount":4,"usages":[{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"}]}
    59	{"query":"area","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}],"totalCount":4}
    60	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Sh","totalCount":2}
    61	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"}],"query":"Circle","totalCount":1}
    62	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"SC","totalCount":1}
    63	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}]}
    64	{"element":{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    65	{"calls":[],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}}
    66	{"query":"Shape::area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":2}
    67	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    68	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    69	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Sh*","totalCount":2}
    70	{"totalCount":2,"usages":[{"column":35,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":43,"usageType":"REFERENCE"}]}

### live-test/go/expected.jsonl
     1	{"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"}
     2	{"column":20,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"}
     3	{"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}
     4	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":11,"name":"fn","qualifiedName":"main.fn"}
     5	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     6	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     7	{"column":9,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":119,"name":"s","qualifiedName":"main.s"}
     8	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":51,"name":"Coerce","qualifiedName":"main.Coerce"}
     9	{"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":6,"name":"Draw","qualifiedName":"main.Draw"}
    10	{"totalCount":3,"usages":[{"column":12,"enclosingScope":["TotalArea"],"file":"normal.go","line":57,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Largest"],"file":"normal.go","line":65,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Largest"],"file":"normal.go","line":65,"usageType":"REFERENCE"}]}
    11	{"totalCount":4,"usages":[{"column":5,"enclosingScope":["Add"],"file":"normal.go","line":51,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Add"],"file":"normal.go","line":51,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["TotalArea"],"file":"normal.go","line":56,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Largest"],"file":"normal.go","line":64,"usageType":"REFERENCE"}]}
    12	{"totalCount":6,"usages":[{"column":11,"enclosingScope":["ShapeCollection"],"file":"normal.go","line":47,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["Add"],"file":"normal.go","line":50,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["Largest"],"file":"normal.go","line":62,"usageType":"REFERENCE"},{"column":11,"enclosingScope":["Largest"],"file":"normal.go","line":63,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":72,"usageType":"REFERENCE"},{"column":11,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":73,"usageType":"REFERENCE"}]}
    13	{"totalCount":0,"usages":[]}
    14	{"totalCount":0,"usages":[]}
    15	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    16	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    17	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    18	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape","supertypes":null},"subtypes":[],"supertypes":[]}
    19	{"calls":[{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":42,"name":"NewSquare","qualifiedName":"main.NewSquare"}],"element":{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}}
    20	{"calls":[{"children":null,"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":24,"name":"Circle.Describe","qualifiedName":"main.Circle.Describe"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":23,"name":"Circle.Area","qualifiedName":"main.Circle.Area"}}
    21	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Shape","totalCount":3}
    22	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle"}],"query":"Rectangle","totalCount":1}
    23	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable"}],"query":"Drawable","totalCount":1}
    24	{"query":"Area","symbols":[{"column":2,"file":"normal.go","kind":"METHOD","line":10,"name":"Area","qualifiedName":"main.Area"},{"column":17,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"},{"column":20,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"},{"column":28,"file":"normal.go","kind":"METHOD","line":54,"name":"TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}],"totalCount":4}
    25	{"query":"qFnVar","symbols":[{"column":6,"file":"quirks.go","kind":"FUNCTION","line":10,"name":"qFnVar","qualifiedName":"main.qFnVar"}],"totalCount":1}
    26	{"file":"normal.go","language":"Go","structure":"normal.go\n\nDrawable (line 5)\n  Draw() string (line 6)\nShape (line 9)\n  Area() float64 (line 10)\n  Describe() string (line 11)\nbaseShape (line 14)\n  Describe() string (line 16)\nCircle (line 18)\n  baseShape (line 19)\n  Radius: float64 (line 20)\n  Area() float64 (line 23)\n  Describe() string (line 24)\n  Draw() string (line 27)\nRectangle (line 29)\n  baseShape (line 30)\n  Width: float64 (line 31)\n  Height: float64 (line 31)\n  Area() float64 (line 34)\n  Describe() string (line 35)\n  Draw() string (line 38)\nSquare (line 40)\n  Rectangle (line 40)\nShapeCollection (line 46)\n  Shapes: []Shape (line 47)\n  Add(s Shape) (line 50)\n  TotalArea() float64 (line 54)\n  Largest() Shape (line 62)\nLabeled embed.go (line 5)\n  baseShape embed.go (line 6)\n  label: string embed.go (line 7)\n  Note() string embed.go (line 10)\nCoercer quirks.go (line 50)\n  Coerce(s string) int quirks.go (line 51)\nIntCoercer quirks.go (line 54)\n  Coerce(s string) int quirks.go (line 56)\nLenCoercer quirks.go (line 61)\n  Coerce(s string) int quirks.go (line 63)\nNewSquare(side float64) Square (line 42)\nMakeDefaultShapes() []Shape (line 72)\nqFnVar(x string) int quirks.go (line 10)\nqClosure(x string) int quirks.go (line 17)\nqMapDispatch(key string, x string) int quirks.go (line 26)\nqSliceIdx(x string) int quirks.go (line 38)\nqInterfaceDispatch(c Coercer, x string) int quirks.go (line 65)\nqGoroutine(x string) int quirks.go (line 70)\nqDefer(x string) (out int) quirks.go (line 80)\nqMethodValue(x string) int quirks.go (line 89)\nqMethodExpression(x string) int quirks.go (line 96)\nqVariadic(xs ...string) int quirks.go (line 102)\nqTypeAssertion(x interface{}) int quirks.go (line 111)\nqTypeSwitch(x interface{}) int quirks.go (line 118)\nqPrintToUpper(x string) quirks.go (line 129)"}
    27	{"file":"quirks.go","language":"Go","structure":"quirks.go\n\nCoercer (line 50)\n  Coerce(s string) int (line 51)\nIntCoercer (line 54)\n  Coerce(s string) int (line 56)\nLenCoercer (line 61)\n  Coerce(s string) int (line 63)\nLabeled embed.go (line 5)\n  baseShape embed.go (line 6)\n  label: string embed.go (line 7)\n  Note() string embed.go (line 10)\nDrawable normal.go (line 5)\n  Draw() string normal.go (line 6)\nShape normal.go (line 9)\n  Area() float64 normal.go (line 10)\n  Describe() string normal.go (line 11)\nbaseShape normal.go (line 14)\n  Describe() string normal.go (line 16)\nCircle normal.go (line 18)\n  baseShape normal.go (line 19)\n  Radius: float64 normal.go (line 20)\n  Area() float64 normal.go (line 23)\n  Describe() string normal.go (line 24)\n  Draw() string normal.go (line 27)\nRectangle normal.go (line 29)\n  baseShape normal.go (line 30)\n  Width: float64 normal.go (line 31)\n  Height: float64 normal.go (line 31)\n  Area() float64 normal.go (line 34)\n  Describe() string normal.go (line 35)\n  Draw() string normal.go (line 38)\nSquare normal.go (line 40)\n  Rectangle normal.go (line 40)\nShapeCollection normal.go (line 46)\n  Shapes: []Shape normal.go (line 47)\n  Add(s Shape) normal.go (line 50)\n  TotalArea() float64 normal.go (line 54)\n  Largest() Shape normal.go (line 62)\nqFnVar(x string) int (line 10)\nqClosure(x string) int (line 17)\nqMapDispatch(key string, x string) int (line 26)\nqSliceIdx(x string) int (line 38)\nqInterfaceDispatch(c Coercer, x string) int (line 65)\nqGoroutine(x string) int (line 70)\nqDefer(x string) (out int) (line 80)\nqMethodValue(x string) int (line 89)\nqMethodExpression(x string) int (line 96)\nqVariadic(xs ...string) int (line 102)\nqTypeAssertion(x interface{}) int (line 111)\nqTypeSwitch(x interface{}) int (line 118)\nqPrintToUpper(x string) (line 129)\nNewSquare(side float64) Square normal.go (line 42)\nMakeDefaultShapes() []Shape normal.go (line 72)"}
    28	{"totalCount":0,"usages":[]}
    29	{"totalCount":4,"usages":[{"column":9,"enclosingScope":["Area","c"],"file":"normal.go","line":23,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Describe","c"],"file":"normal.go","line":24,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Draw","c"],"file":"normal.go","line":27,"usageType":"REFERENCE"},{"column":3,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":74,"usageType":"REFERENCE"}]}
    30	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},"subtypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null}],"supertypes":[]}
    31	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    32	{"calls":[{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":10,"name":"Shape.Area","qualifiedName":"main.Area"}],"element":{"children":null,"column":28,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":54,"name":"*ShapeCollection.TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}}
    33	{"calls":[],"element":{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":11,"name":"Shape.Describe","qualifiedName":"main.Describe"}}
    34	{"calls":[],"element":{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":6,"name":"Drawable.Draw","qualifiedName":"main.Draw"}}
    35	{"calls":[{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":10,"name":"Shape.Area","qualifiedName":"main.Area"}],"element":{"children":null,"column":28,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":62,"name":"*ShapeCollection.Largest","qualifiedName":"main.ShapeCollection.Largest"}}
    36	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}
    37	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square"}],"query":"Square","totalCount":1}
    38	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    39	{"query":"Describe","symbols":[{"column":2,"file":"normal.go","kind":"METHOD","line":11,"name":"Describe","qualifiedName":"main.Describe"},{"column":20,"file":"normal.go","kind":"METHOD","line":16,"name":"Describe","qualifiedName":"main.baseShape.Describe"},{"column":17,"file":"normal.go","kind":"METHOD","line":24,"name":"Describe","qualifiedName":"main.Circle.Describe"},{"column":20,"file":"normal.go","kind":"METHOD","line":35,"name":"Describe","qualifiedName":"main.Rectangle.Describe"}],"totalCount":4}
    40	{"query":"TotalArea","symbols":[{"column":28,"file":"normal.go","kind":"METHOD","line":54,"name":"TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}],"totalCount":1}
    41	{"query":"Draw","symbols":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable"},{"column":2,"file":"normal.go","kind":"METHOD","line":6,"name":"Draw","qualifiedName":"main.Draw"},{"column":17,"file":"normal.go","kind":"METHOD","line":27,"name":"Draw","qualifiedName":"main.Circle.Draw"},{"column":20,"file":"normal.go","kind":"METHOD","line":38,"name":"Draw","qualifiedName":"main.Rectangle.Draw"}],"totalCount":4}
    42	{"query":"Largest","symbols":[{"column":28,"file":"normal.go","kind":"METHOD","line":62,"name":"Largest","qualifiedName":"main.ShapeCollection.Largest"}],"totalCount":1}
    43	{"totalCount":1,"usages":[{"column":46,"enclosingScope":["Describe"],"file":"normal.go","line":25,"usageType":"REFERENCE"}]}
    44	{"query":"Area","symbols":[{"column":17,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"},{"column":20,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"}],"totalCount":4}
    45	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh","totalCount":2}
    46	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}
    47	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"SC","totalCount":1}
    48	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    49	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null},"subtypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null}],"supertypes":[]}
    50	{"calls":[{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":42,"name":"NewSquare","qualifiedName":"main.NewSquare"}],"element":{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}}
    51	{"query":"IntCoercer.Coerce","symbols":[{"column":19,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}],"totalCount":1}
    52	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    53	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    54	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh*","totalCount":3}
    55	{"totalCount":1,"usages":[{"column":42,"enclosingScope":["Note"],"file":"embed.go","line":11,"usageType":"REFERENCE"}]}
    56	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Shape","totalCount":2}
    57	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}

### live-test/rust/expected.jsonl
     1	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}
     2	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"area","qualifiedName":"crate::area"}
     3	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes","qualifiedName":"crate::make_default_shapes"}
     4	{"column":8,"enclosingScope":null,"file":"src/quirks.rs","kind":"METHOD","line":29,"name":"coerce","qualifiedName":"crate::Coercer::coerce"}
     5	{"column":5,"enclosingScope":null,"file":"src/quirks.rs","kind":"ENUM_VARIANT","line":67,"name":"Int","qualifiedName":"crate::CoerceMode::Int"}
     6	{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
     7	{"column":4,"enclosingScope":null,"file":"src/quirks.rs","kind":"FUNCTION","line":11,"name":"parse_or_zero","qualifiedName":"crate::parse_or_zero"}
     8	{"column":17,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
     9	{"column":12,"enclosingScope":null,"file":"src/quirks.rs","kind":"METHOD","line":72,"name":"apply","qualifiedName":"crate::apply"}
    10	{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
    11	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"draw","qualifiedName":"crate::Drawable::draw"}
    12	{"column":12,"enclosingScope":null,"file":"src/extra.rs","kind":"FUNCTION","line":2,"name":"nested_helper","qualifiedName":"crate::inner::nested_helper"}
    13	{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}
    14	{"totalCount":7,"usages":[{"column":44,"enclosingScope":["Shape","describe","self.area()"],"file":"src/normal.rs","line":9,"usageType":"METHOD_CALL"},{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"},{"column":48,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":44,"usageType":"METHOD_CALL"},{"column":20,"enclosingScope":["area"],"file":"src/normal.rs","line":66,"usageType":"METHOD_CALL"},{"column":38,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"METHOD_CALL"},{"column":15,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"},{"column":37,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"}]}
    15	{"totalCount":4,"usages":[{"column":27,"enclosingScope":["new"],"file":"src/normal.rs","line":76,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["add"],"file":"src/normal.rs","line":80,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["largest"],"file":"src/normal.rs","line":88,"usageType":"FIELD_ACCESS"}]}
    16	{"totalCount":7,"usages":[{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":17,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":38,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":64,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.rs","line":71,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["add"],"file":"src/normal.rs","line":79,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["largest"],"file":"src/normal.rs","line":87,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":94,"usageType":"REFERENCE"}]}
    17	{"totalCount":6,"usages":[{"column":29,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"REFERENCE"},{"column":43,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"FIELD_ACCESS"},{"column":24,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":38,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":37,"enclosingScope":["draw","self.radius"],"file":"src/normal.rs","line":29,"usageType":"FIELD_ACCESS"},{"column":27,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":96,"usageType":"REFERENCE"}]}
    18	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"Circle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"Rectangle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":65,"name":"Square::area","qualifiedName":"crate::area"}],"totalCount":3}
    19	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"Circle::draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"Rectangle::draw","qualifiedName":"crate::draw"}],"totalCount":2}
    20	{"implementations":[{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":34,"name":"IntCoercer::coerce","qualifiedName":"crate::coerce"},{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":41,"name":"LenCoercer::coerce","qualifiedName":"crate::coerce"}],"totalCount":2}
    21	{"implementations":[{"column":18,"file":"src/quirks.rs","kind":"IMPL","line":33,"name":"impl Coercer for IntCoercer","qualifiedName":null},{"column":18,"file":"src/quirks.rs","kind":"IMPL","line":40,"name":"impl Coercer for LenCoercer","qualifiedName":null}],"totalCount":2}
    22	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    23	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    24	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null}],"supertypes":[]}
    25	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
    26	{"calls":[{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"Shape::describe(...)","qualifiedName":"crate::Shape::describe"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"Shape::area(...)","qualifiedName":"crate::Shape::area"}}
    27	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Shape","totalCount":2}
    28	{"classes":[{"column":11,"file":"src/quirks.rs","kind":"TRAIT","line":28,"name":"Coercer","qualifiedName":"crate::Coercer"},{"column":12,"file":"src/quirks.rs","kind":"STRUCT","line":32,"name":"IntCoercer","qualifiedName":"crate::IntCoercer"},{"column":12,"file":"src/quirks.rs","kind":"STRUCT","line":39,"name":"LenCoercer","qualifiedName":"crate::LenCoercer"}],"query":"Coercer","totalCount":3}
    29	{"classes":[{"column":16,"file":"src/extra.rs","kind":"STRUCT","line":3,"name":"Marker","qualifiedName":"crate::inner::Marker"}],"query":"Marker","totalCount":1}
    30	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":65,"name":"area","qualifiedName":"crate::area"},{"column":12,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"total_area","qualifiedName":"crate::total_area"}],"totalCount":5}
    31	{"query":"q_closure","symbols":[{"column":8,"file":"src/quirks.rs","kind":"FUNCTION","line":1,"name":"q_closure","qualifiedName":"crate::q_closure"}],"totalCount":1}
    32	{"query":"extra_function","symbols":[{"column":8,"file":"src/extra.rs","kind":"FUNCTION","line":6,"name":"extra_function","qualifiedName":"crate::extra_function"}],"totalCount":1}
    33	{"file":"src/normal.rs","language":"Rust","structure":"normal.rs\n\npub Drawable (line 1)\n  draw() -> String (line 2)\npub Shape (line 5)\n  area() -> f64 (line 6)\n  describe() -> String (line 8)\npub Circle (line 13)\n  pub radius: f64 (line 14)\nShape for Circle (line 17)\n  area() -> f64 (line 18)\n  describe() -> String (line 22)\nDrawable for Circle (line 27)\n  draw() -> String (line 28)\npub Rectangle (line 33)\n  pub width: f64 (line 34)\n  pub height: f64 (line 35)\nShape for Rectangle (line 38)\n  area() -> f64 (line 39)\n  describe() -> String (line 43)\nDrawable for Rectangle (line 48)\n  draw() -> String (line 49)\npub Square (line 54)\n  inner: Rectangle (line 55)\nSquare (line 58)\n  pub new(f64) -> Self (line 59)\nShape for Square (line 64)\n  area() -> f64 (line 65)\npub ShapeCollection (line 70)\n  pub shapes: Vec<Box<dyn Shape>> (line 71)\nShapeCollection (line 74)\n  pub new() -> Self (line 75)\n  pub add(Box<dyn Shape>) (line 79)\n  pub total_area() -> f64 (line 83)\n  pub largest() -> Option<&Box<dyn Shape>> (line 87)\npub make_default_shapes() -> Vec<Box<dyn Shape>> (line 94)"}
    34	{"file":"src/quirks.rs","language":"Rust","structure":"quirks.rs\n\npub q_closure(&str) -> i32 (line 1)\npub q_fn_pointer(&str) -> i32 (line 6)\nparse_or_zero(&str) -> i32 (line 11)\npub q_box_dyn_fn(&str) -> i32 (line 15)\npub q_match_dispatch(&str, &str) -> i32 (line 20)\npub Coercer (line 28)\n  coerce(&str) -> i32 (line 29)\npub IntCoercer (line 32)\nCoercer for IntCoercer (line 33)\n  coerce(&str) -> i32 (line 34)\npub LenCoercer (line 39)\nCoercer for LenCoercer (line 40)\n  coerce(&str) -> i32 (line 41)\npub q_trait_object(&dyn Coercer, &str) -> i32 (line 46)\npub q_generic_bound(&C, &str) -> i32 (line 50)\npub q_impl_trait_arg(impl Coercer, &str) -> i32 (line 54)\npub q_impl_trait_return(bool) -> Box<dyn Coercer> (line 58)\npub CoerceMode (line 66)\n  Int (line 67)\n  Len (line 68)\nCoerceMode (line 71)\n  pub apply(&str) -> i32 (line 72)\npub q_enum_dispatch(CoerceMode, &str) -> i32 (line 80)\npub q_iter_map(&[&str]) -> Vec<i32> (line 84)\npub q_iter_filter_map(&[&str]) -> Vec<i32> (line 88)\npub q_iter_fold(&[&str]) -> i32 (line 92)\npub q_question_mark(&str) -> Result<i32, ::ParseIntError> (line 96)\npub q_if_let(Option<&str>) -> i32 (line 101)"}
    35	{"file":"src/extra.rs","language":"Rust","structure":"extra.rs\n\npub inner (line 1)\n  pub nested_helper() -> i32 (line 2)\n  pub Marker (line 3)\npub extra_function(&str) -> i32 (line 6)\npub use_quirks_circle() -> f64 (line 10)"}
    36	{"totalCount":2,"usages":[{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":27,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":48,"usageType":"REFERENCE"}]}
    37	{"totalCount":5,"usages":[{"column":16,"enclosingScope":[],"file":"src/normal.rs","line":38,"usageType":"REFERENCE"},{"column":19,"enclosingScope":[],"file":"src/normal.rs","line":48,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["Square","inner"],"file":"src/normal.rs","line":55,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["new"],"file":"src/normal.rs","line":60,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":97,"usageType":"REFERENCE"}]}
    38	{"totalCount":6,"usages":[{"column":6,"enclosingScope":[],"file":"src/quirks.rs","line":33,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/quirks.rs","line":40,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["q_trait_object"],"file":"src/quirks.rs","line":46,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["q_generic_bound","C"],"file":"src/quirks.rs","line":50,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["q_impl_trait_arg"],"file":"src/quirks.rs","line":54,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["q_impl_trait_return"],"file":"src/quirks.rs","line":58,"usageType":"REFERENCE"}]}
    39	{"implementations":[{"column":16,"file":"src/normal.rs","kind":"IMPL","line":17,"name":"impl Shape for Circle","qualifiedName":null},{"column":16,"file":"src/normal.rs","kind":"IMPL","line":38,"name":"impl Shape for Rectangle","qualifiedName":null},{"column":16,"file":"src/normal.rs","kind":"IMPL","line":64,"name":"impl Shape for Square","qualifiedName":null}],"totalCount":3}
    40	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":22,"name":"Circle::describe","qualifiedName":"crate::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":43,"name":"Rectangle::describe","qualifiedName":"crate::describe"}],"totalCount":2}
    41	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null}],"supertypes":[]}
    42	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    43	{"element":{"column":10,"enclosingScope":null,"file":"src/quirks.rs","kind":"ENUM","line":66,"name":"CoerceMode","qualifiedName":"crate::CoerceMode","supertypes":null},"subtypes":[],"supertypes":[]}
    44	{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
    45	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
    46	{"calls":[],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"Shape::describe(...)","qualifiedName":"crate::Shape::describe"}}
    47	{"calls":[],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"Drawable::draw(...)","qualifiedName":"crate::Drawable::draw"}}
    48	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}],"query":"Circle","totalCount":1}
    49	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle"}],"query":"Rectangle","totalCount":1}
    50	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square"}],"query":"Square","totalCount":1}
    51	{"query":"describe","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"describe","qualifiedName":"crate::Shape::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":22,"name":"describe","qualifiedName":"crate::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":43,"name":"describe","qualifiedName":"crate::describe"}],"totalCount":3}
    52	{"query":"total_area","symbols":[{"column":12,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"total_area","qualifiedName":"crate::total_area"}],"totalCount":1}
    53	{"query":"draw","symbols":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"draw","qualifiedName":"crate::Drawable::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"draw","qualifiedName":"crate::draw"}],"totalCount":4}
    54	{"totalCount":1,"usages":[{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"}]}
    55	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}],"totalCount":5}
    56	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh","totalCount":2}
    57	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}],"query":"Circle","totalCount":1}
    58	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"SC","totalCount":1}
    59	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    60	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
    62	{"query":"IntCoercer::coerce","symbols":[],"totalCount":0}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh*","totalCount":2}

exec
/usr/bin/zsh -lc 'for f in live-test/{java,kotlin,python,javascript,typescript,php,go,rust}/output.jsonl; do if [ -f "$f" ]; then printf '"'\\n### %s\\n' \""'$f"; nl -ba "$f" | sed -n '"'1,220p'; fi; done" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/java/output.jsonl
     1	{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}
     2	{"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}
     3	{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"makeDefaultShapes","qualifiedName":"demo.Normal#makeDefaultShapes"}
     4	{"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String)"}
     5	{"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":90,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String, int)"}
     6	{"column":22,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":74,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"}
     7	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"LOCAL_VARIABLE","line":17,"name":"coerce","qualifiedName":"demo.Quirks#quirkVar"}
     8	{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}
     9	{"column":29,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal","qualifiedName":"demo.Modern.Animal"}
    10	{"totalCount":4,"usages":[{"column":61,"enclosingScope":["Shape","describe"],"file":"src/main/java/demo/Normal.java","line":14,"usageType":"METHOD_CALL"},{"column":22,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":72,"usageType":"METHOD_CALL"},{"column":35,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"},{"column":49,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"}]}
    11	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/main/java/demo/Normal.java","line":66,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":71,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":79,"usageType":"REFERENCE"}]}
    12	{"totalCount":11,"usages":[{"column":22,"enclosingScope":["Circle"],"file":"src/main/java/demo/Normal.java","line":18,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Rectangle"],"file":"src/main/java/demo/Normal.java","line":36,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["ShapeCollection","shapes"],"file":"src/main/java/demo/Normal.java","line":63,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/main/java/demo/Normal.java","line":65,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","totalArea","s"],"file":"src/main/java/demo/Normal.java","line":71,"usageType":"REFERENCE"},{"column":5,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":77,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["ShapeCollection","largest","best"],"file":"src/main/java/demo/Normal.java","line":78,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["ShapeCollection","largest","s"],"file":"src/main/java/demo/Normal.java","line":79,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":87,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["Normal","makeDefaultShapes","shapes"],"file":"src/main/java/demo/Normal.java","line":88,"usageType":"REFERENCE"},{"column":30,"enclosingScope":["Normal","classifyShape","s"],"file":"src/main/java/demo/Normal.java","line":96,"usageType":"REFERENCE"}]}
    13	{"totalCount":1,"usages":[{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":89,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":2}
    15	{"implementations":[{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":31,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":51,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":2}
    16	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    17	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    18	{"implementations":[{"column":19,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":72,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"},{"column":19,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":73,"name":"apply","qualifiedName":"demo.Quirks.CoerceMode#apply"}],"totalCount":2}
    19	{"implementations":[{"column":20,"file":"src/main/java/demo/Quirks.java","kind":"METHOD_REFERENCE","line":67,"name":"Integer::parseInt in quirkFunctionalIface() in demo.Quirks","qualifiedName":"java.lang.Integer#parseInt(java.lang.String)"}],"totalCount":1}
    20	{"implementations":[{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":13,"name":"name","qualifiedName":"demo.Modern.Cat#name"},{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":17,"name":"name","qualifiedName":"demo.Modern.Dog#name"}],"totalCount":2}
    21	{"hierarchy":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"area","qualifiedName":"demo.Circle#area"}}
    22	{"hierarchy":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"area","qualifiedName":"demo.Rectangle#area"}}
    23	{"hierarchy":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":31,"name":"draw","qualifiedName":"demo.Circle#draw"}}
    24	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
    25	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    26	{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
    27	{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
    28	{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    29	{"element":{"column":29,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal in Modern","qualifiedName":"demo.Modern.Animal","supertypes":null},"subtypes":[{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"CLASS","line":12,"name":"Cat in Modern","qualifiedName":"demo.Modern.Cat","supertypes":null},{"column":31,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"CLASS","line":16,"name":"Dog in Modern","qualifiedName":"demo.Modern.Dog","supertypes":null}],"supertypes":[]}
    30	{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
    31	{"calls":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"},{"children":null,"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":77,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}}
    32	{"calls":[{"children":null,"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":94,"name":"Quirks.parseUsage()","qualifiedName":"demo.Quirks#parseUsage"}],"element":{"children":null,"column":23,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"Quirks.parse(String)","qualifiedName":"demo.Quirks#parse(java.lang.String)"}}
    33	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Shape","totalCount":2}
    34	{"classes":[{"column":18,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":57,"name":"Coercer","qualifiedName":"demo.Quirks.Coercer"}],"query":"Coercer","totalCount":1}
    35	{"classes":[{"column":19,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}],"query":"Point","totalCount":1}
    36	{"classes":[{"column":29,"file":"src/main/java/demo/Modern.java","kind":"INTERFACE","line":8,"name":"Animal","qualifiedName":"demo.Modern.Animal"}],"query":"Animal","totalCount":1}
    37	{"query":"area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    38	{"query":"quirkLambda","symbols":[{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":11,"name":"quirkLambda","qualifiedName":"demo.Quirks#quirkLambda"}],"totalCount":1}
    39	{"query":"parse","symbols":[{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":86,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String)"},{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":90,"name":"parse","qualifiedName":"demo.Quirks#parse(java.lang.String, int)"},{"column":23,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":94,"name":"parseUsage","qualifiedName":"demo.Quirks#parseUsage"}],"totalCount":3}
    40	{"file":"src/main/java/demo/Normal.java","language":"Java","structure":"Normal.java\n\nDrawable (line 6)\n  draw(): String (line 7)\nabstract Shape (line 10)\n  abstract area(): double (line 11)\n  describe(): String (line 13)\nCircle (line 18)\n  private final radius: double (line 19)\n  Circle(double) (line 21)\n  area(): double \u2191Shape (line 26)\n  public draw(): String (line 31)\nRectangle (line 36)\n  protected final width: double (line 37)\n  protected final height: double (line 38)\n  Rectangle(double, double) (line 40)\n  area(): double \u2191Shape (line 46)\n  public draw(): String (line 51)\nSquare (line 56)\n  Square(double) (line 57)\nShapeCollection (line 62)\n  private final shapes: List<Shape> = new ArrayList<>() (line 63)\n  add(Shape): void (line 65)\n  totalArea(): double (line 69)\n  largest(): Shape (line 77)\npublic Normal (line 86)\n  public static makeDefaultShapes(): List<Shape> (line 87)\n  static classifyShape(Shape): int (line 96)"}
    41	{"file":"src/main/java/demo/Quirks.java","language":"Java","structure":"Quirks.java\n\npublic Quirks (line 9)\n  public static quirkLambda(String): int (line 11)\n    lambda$quirkLambda$0 Function<String, Integer> (line 12)\n  public static quirkVar(String): int (line 16)\n  public static quirkAnonClass(String): int (line 21)\n  public static quirkOptional(String): Optional<Integer> (line 31)\n  public static quirkTernary(String, boolean): int (line 35)\n    lambda$quirkTernary$1 Function<String, Integer> (line 37)\n  public static quirkCompletableFuture(String): CompletableFuture<Integer> (line 42)\n    lambda$quirkCompletableFuture$2 Supplier<Integer> (line 43)\n  public static quirkStreamMap(List<String>): List<Integer> (line 46)\n  public static quirkMapDispatch(String, String): int (line 50)\n    lambda$quirkMapDispatch$3 Function<String, Integer> (line 53)\n  static Coercer (line 57)\n    private final prefix: String (line 58)\n    Coercer(String) (line 59)\n    coerce(String): int (line 60)\n  Coerce (line 64)\n    run(String): int (line 64)\n  public static quirkFunctionalIface(String): int (line 66)\n  CoerceMode (line 71)\n    INT: CoerceMode (line 72)\n      apply(String): int \u2191CoerceMode (line 72)\n    ABS: CoerceMode (line 73)\n      apply(String): int \u2191CoerceMode (line 73)\n    abstract apply(String): int (line 74)\n  public static quirkEnumDispatch(String): int (line 77)\n  public static quirkSupplier(String): int (line 81)\n    lambda$quirkSupplier$4 Supplier<Integer> (line 82)\n  public static parse(String): int (line 86)\n  public static parse(String, int): int (line 90)\n  public static parseUsage(): int (line 94)"}
    42	{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}
    43	{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle"}
    44	{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point","qualifiedName":"demo.Modern.Point"}
    45	{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
    46	{"totalCount":2,"usages":[{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":89,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":98,"usageType":"REFERENCE"}]}
    47	{"indexingProgress":null,"isDumbMode":false,"isIndexing":false}
    48	{"analysisFresh":true,"analysisMessage":"Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":0,"problems":[],"testResults":null,"testResultsTruncated":null,"testSummary":null}
    49	{"totalCount":2,"usages":[{"column":39,"enclosingScope":["Circle"],"file":"src/main/java/demo/Normal.java","line":18,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["Rectangle"],"file":"src/main/java/demo/Normal.java","line":36,"usageType":"REFERENCE"}]}
    50	{"totalCount":3,"usages":[{"column":22,"enclosingScope":["Square"],"file":"src/main/java/demo/Normal.java","line":56,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Normal","makeDefaultShapes"],"file":"src/main/java/demo/Normal.java","line":90,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":100,"usageType":"REFERENCE"}]}
    51	{"hierarchy":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":19,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":51,"name":"draw","qualifiedName":"demo.Rectangle#draw"}}
    52	{"hierarchy":[{"column":16,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":9,"name":"name","qualifiedName":"demo.Modern.Animal#name"}],"method":{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":13,"name":"name","qualifiedName":"demo.Modern.Cat#name"}}
    53	{"hierarchy":[{"column":16,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":9,"name":"name","qualifiedName":"demo.Modern.Animal#name"}],"method":{"column":33,"file":"src/main/java/demo/Modern.java","kind":"METHOD","line":17,"name":"name","qualifiedName":"demo.Modern.Dog#name"}}
    54	{"element":{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[]}
    55	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
    56	{"calls":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"Circle.area()","qualifiedName":"demo.Circle#area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"Rectangle.area()","qualifiedName":"demo.Rectangle#area"}],"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"}}
    57	{"calls":[],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"}}
    58	{"calls":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":26,"name":"Circle.area()","qualifiedName":"demo.Circle#area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":46,"name":"Rectangle.area()","qualifiedName":"demo.Rectangle#area"}],"column":21,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}],"element":{"children":null,"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":77,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}}
    59	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square"}],"query":"Square","totalCount":1}
    61	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    62	{"query":"describe","symbols":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":13,"name":"describe","qualifiedName":"demo.Shape#describe"}],"totalCount":1}
    63	{"query":"totalArea","symbols":[{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":1}
    64	{"query":"draw","symbols":[{"column":11,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":7,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"totalCount":2}
    65	{"totalCount":4,"usages":[{"column":61,"enclosingScope":["Shape","describe"],"file":"src/main/java/demo/Normal.java","line":14,"usageType":"METHOD_CALL"},{"column":22,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":72,"usageType":"METHOD_CALL"},{"column":35,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"},{"column":49,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"}]}
    66	{"query":"area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    67	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh","totalCount":2}
    68	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    69	{"classes":[{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"SC","totalCount":1}
    70	{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
    71	{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
    72	{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
    73	{"query":"Shape.area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    74	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    75	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    76	{"classes":[{"column":16,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":62,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh*","totalCount":2}
    77	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"LOCAL_VARIABLE","line":97,"name":"kind","qualifiedName":"demo.Normal#classifyShape"}
    78	{"totalCount":4,"usages":[{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":99,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":101,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":103,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":105,"usageType":"REFERENCE"}]}
    79	{"hierarchy":[{"column":7,"file":"${JDK}!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
    80	{"hierarchy":[],"method":{"column":10,"file":"src/main/java/demo/Quirks.java","kind":"CONSTRUCTOR","line":71,"name":"CoerceMode","qualifiedName":"demo.Quirks.CoerceMode#CoerceMode"}}
    81	{"file":"src/main/java/demo/Modern.java","language":"Java","structure":"Modern.java\n\npublic Modern (line 3)\n  public Point (line 4)\n    public sum(): int (line 5)\n    x: int (line 4)\n    y: int (line 4)\n  public sealed Animal (line 8)\n    name(): String (line 9)\n  public static final Cat (line 12)\n    public name(): String (line 13)\n  public static final Dog (line 16)\n    public name(): String (line 17)\n  public static probe(): int (line 20)"}
    82	{"file":"src/main/java/demo/Quirks.java","language":"Java","structure":"Quirks.java\n\npublic Quirks (line 9)\n  public static quirkLambda(String): int (line 11)\n    lambda$quirkLambda$0 Function<String, Integer> (line 12)\n  public static quirkVar(String): int (line 16)\n  public static quirkAnonClass(String): int (line 21)\n  public static quirkOptional(String): Optional<Integer> (line 31)\n  public static quirkTernary(String, boolean): int (line 35)\n    lambda$quirkTernary$1 Function<String, Integer> (line 37)\n  public static quirkCompletableFuture(String): CompletableFuture<Integer> (line 42)\n    lambda$quirkCompletableFuture$2 Supplier<Integer> (line 43)\n  public static quirkStreamMap(List<String>): List<Integer> (line 46)\n  public static quirkMapDispatch(String, String): int (line 50)\n    lambda$quirkMapDispatch$3 Function<String, Integer> (line 53)\n  static Coercer (line 57)\n    private final prefix: String (line 58)\n    Coercer(String) (line 59)\n    coerce(String): int (line 60)\n  Coerce (line 64)\n    run(String): int (line 64)\n  public static quirkFunctionalIface(String): int (line 66)\n  CoerceMode (line 71)\n    INT: CoerceMode (line 72)\n      apply(String): int \u2191CoerceMode (line 72)\n    ABS: CoerceMode (line 73)\n      apply(String): int \u2191CoerceMode (line 73)\n    abstract apply(String): int (line 74)\n  public static quirkEnumDispatch(String): int (line 77)\n  public static quirkSupplier(String): int (line 81)\n    lambda$quirkSupplier$4 Supplier<Integer> (line 82)\n  public static parse(String): int (line 86)\n  public static parse(String, int): int (line 90)\n  public static parseUsage(): int (line 94)"}
    83	{"analysisFresh":true,"analysisMessage":"Closed-file diagnostics use public batch analysis; weak warnings are only available when the file is open in an editor. Intentions are unavailable because the file is not open in an editor.","analysisTimedOut":false,"buildErrorCount":null,"buildErrors":null,"buildErrorsTruncated":null,"buildTimestamp":null,"buildWarningCount":null,"intentionCount":0,"intentions":[],"problemCount":10,"problems":[{"column":12,"endColumn":15,"endLine":7,"file":"src/main/java/demo/Normal.java","line":7,"message":"Method 'draw()' is never used","severity":"WARNING"},{"column":12,"endColumn":19,"endLine":13,"file":"src/main/java/demo/Normal.java","line":13,"message":"Method 'describe()' is never used","severity":"WARNING"},{"column":7,"endColumn":21,"endLine":62,"file":"src/main/java/demo/Normal.java","line":62,"message":"Class 'ShapeCollection' is never used","severity":"WARNING"},{"column":10,"endColumn":12,"endLine":65,"file":"src/main/java/demo/Normal.java","line":65,"message":"Method 'add(demo.Shape)' is never used","severity":"WARNING"},{"column":12,"endColumn":20,"endLine":69,"file":"src/main/java/demo/Normal.java","line":69,"message":"Method 'totalArea()' is never used","severity":"WARNING"},{"column":11,"endColumn":17,"endLine":77,"file":"src/main/java/demo/Normal.java","line":77,"message":"Method 'largest()' is never used","severity":"WARNING"},{"column":14,"endColumn":19,"endLine":86,"file":"src/main/java/demo/Normal.java","line":86,"message":"Class 'Normal' is never used","severity":"WARNING"},{"column":24,"endColumn":28,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Class 'Shape' is exposed outside its defined visibility scope","severity":"WARNING"},{"column":31,"endColumn":47,"endLine":87,"file":"src/main/java/demo/Normal.java","line":87,"message":"Method 'makeDefaultShapes()' is never used","severity":"WARNING"},{"column":16,"endColumn":28,"endLine":96,"file":"src/main/java/demo/Normal.java","line":96,"message":"Method 'classifyShape(demo.Shape)' is never used","severity":"WARNING"}],"testResults":null,"testResultsTruncated":null,"testSummary":null}

### live-test/kotlin/output.jsonl
     1	{"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"}
     2	{"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}
     3	{"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes","qualifiedName":"demo.NormalKt#makeDefaultShapes"}
     4	{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":27,"name":"coerceTo","qualifiedName":"demo.QuirksKt#coerceTo"}
     5	{"column":29,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":61,"name":"coerceFirst","qualifiedName":"demo.QuirksKt#coerceFirst"}
     6	{"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":10,"name":"create","qualifiedName":"demo.Counter.Companion#create"}
     7	{"column":19,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"PROPERTY","line":9,"name":"DEFAULT_LIMIT","qualifiedName":"demo.Counter#DEFAULT_LIMIT"}
     8	{"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue","qualifiedName":"demo.ModernKt#fetchValue"}
     9	{"totalCount":3,"usages":[{"column":74,"enclosingScope":["Shape","describe"],"file":"src/main/kotlin/demo/Normal.kt","line":10,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["ShapeCollection","totalArea","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":53,"enclosingScope":["ShapeCollection","largest","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    10	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/main/kotlin/demo/Normal.kt","line":29,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    11	{"totalCount":6,"usages":[{"column":36,"enclosingScope":["Circle"],"file":"src/main/kotlin/demo/Normal.kt","line":13,"usageType":"REFERENCE"},{"column":63,"enclosingScope":["Rectangle"],"file":"src/main/kotlin/demo/Normal.kt","line":18,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","shapes"],"file":"src/main/kotlin/demo/Normal.kt","line":26,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/main/kotlin/demo/Normal.kt","line":28,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["makeDefaultShapes"],"file":"src/main/kotlin/demo/Normal.kt","line":37,"usageType":"REFERENCE"}]}
    12	{"totalCount":3,"usages":[{"column":45,"enclosingScope":["Circle","area"],"file":"src/main/kotlin/demo/Normal.kt","line":14,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["Circle","area"],"file":"src/main/kotlin/demo/Normal.kt","line":14,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["Circle","draw"],"file":"src/main/kotlin/demo/Normal.kt","line":15,"usageType":"REFERENCE"}]}
    13	{"totalCount":1,"usages":[{"column":43,"enclosingScope":["quirkInfix"],"file":"src/main/kotlin/demo/Quirks.kt","line":59,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":2}
    15	{"implementations":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":2}
    16	{"implementations":[{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce"},{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce"}],"totalCount":2}
    17	{"implementations":[{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":39,"name":"apply","qualifiedName":"demo.Coercion.IntCoerce#apply"},{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":40,"name":"apply","qualifiedName":"demo.Coercion.AbsCoerce#apply"}],"totalCount":2}
    18	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":8,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":14,"name":"area","qualifiedName":"demo.Circle#area"}}
    19	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":8,"name":"area","qualifiedName":"demo.Shape#area"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}}
    20	{"hierarchy":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"}}
    21	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    22	{"tool_error_text":"Failed to build supertype hierarchy"}
    23	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    24	{"tool_error_text":"Browser refused element Coercion for SUBTYPES"}
    25	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes()","qualifiedName":"demo.NormalKt#makeDefaultShapes"}}
    26	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":10,"name":"Shape.describe()","qualifiedName":"demo.Shape#describe"},{"children":null,"column":9,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"ShapeCollection.totalArea()","qualifiedName":"demo.ShapeCollection#totalArea"},{"children":null,"column":9,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":34,"name":"ShapeCollection.largest()","qualifiedName":"demo.ShapeCollection#largest"}],"element":{"children":null,"column":18,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":8,"name":"Shape.area()","qualifiedName":"demo.Shape#area"}}
    27	{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
    28	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":17,"name":"quirkLet(String?)","qualifiedName":"demo.QuirksKt#quirkLet"}}
    29	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":21,"name":"quirkWith(String)","qualifiedName":"demo.QuirksKt#quirkWith"}}
    30	{"calls":[{"children":null,"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue()","qualifiedName":"demo.ModernKt#fetchValue"}],"element":{"children":null,"column":13,"enclosingScope":null,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":16,"name":"computeTotal()","qualifiedName":"demo.ModernKt#computeTotal"}}
    31	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Shape","totalCount":2}
    32	{"classes":[{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"DATA_CLASS","line":45,"name":"Coercer","qualifiedName":"demo.Coercer"}],"query":"Coercer","totalCount":1}
    33	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Modern.kt","kind":"CLASS","line":3,"name":"Counter","qualifiedName":"demo.Counter"}],"query":"Counter","totalCount":1}
    34	{"query":"area","symbols":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":8,"name":"area","qualifiedName":"demo.Shape#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"},{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":4}
    35	{"query":"quirkLambda","symbols":[{"column":5,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":3,"name":"quirkLambda","qualifiedName":"demo.QuirksKt#quirkLambda"}],"totalCount":1}
    36	{"query":"fetchValue","symbols":[{"column":13,"file":"src/main/kotlin/demo/Modern.kt","kind":"FUNCTION","line":14,"name":"fetchValue","qualifiedName":"demo.ModernKt#fetchValue"}],"totalCount":1}
    37	{"file":"src/main/kotlin/demo/Normal.kt","language":"Kotlin","structure":"Normal.kt\n\nDrawable (line 3)\n  draw(): String (line 4)\nabstract Shape (line 7)\n  abstract area(): Double (line 8)\n  open describe(): String (line 10)\nCircle (line 13)\n  constructor Circle(Double) (line 13)\n  radius: Double (line 13)\n  override area(): Double (line 14)\n  override draw(): String (line 15)\nopen Rectangle (line 18)\n  constructor Rectangle(Double, Double) (line 18)\n  width: Double (line 18)\n  height: Double (line 18)\n  override area(): Double (line 19)\n  override draw(): String (line 20)\nSquare (line 23)\n  constructor Square(Double) (line 23)\nShapeCollection (line 25)\n  shapes: MutableList<Shape> (line 26)\n  add(Shape): Unit (line 28)\n  totalArea(): Double (line 32)\n  largest(): Shape? (line 34)\nmakeDefaultShapes(): List<Shape> (line 37)"}
    38	{"file":"src/main/kotlin/demo/Quirks.kt","language":"Kotlin","structure":"Quirks.kt\n\nquirkLambda(String): Int (line 3)\nquirkFunctionRef(String): Int (line 8)\nquirkApply(String): Int (line 13)\nquirkLet(String?): Int (line 17)\nquirkWith(String): Int (line 21)\nquirkRun(String): Int (line 25)\nString.coerceTo(Int): Int (line 27)\nquirkExtensionFn(String): Int (line 29)\nquirkWhen(String, String): Int (line 31)\nsealed Coercion (line 37)\n  abstract apply(String): Int (line 38)\n  object IntCoerce (line 39)\n    override apply(String): Int (line 39)\n  object AbsCoerce (line 40)\n    override apply(String): Int (line 40)\nquirkSealed(Coercion, String): Int (line 43)\ndata Coercer (line 45)\n  constructor Coercer(String) (line 45)\n  prefix: String (line 45)\n  coerce(String): Int (line 46)\nquirkDataClass(String): Int (line 49)\nquirkDispatchMap(String, String): Int (line 51)\nquirkInfix(String): Int (line 59)\ninfix Pair<String, Int>.coerceFirst(): Int (line 61)"}
    39	{"totalCount":2,"usages":[{"column":45,"enclosingScope":["Circle"],"file":"src/main/kotlin/demo/Normal.kt","line":13,"usageType":"REFERENCE"},{"column":72,"enclosingScope":["Rectangle"],"file":"src/main/kotlin/demo/Normal.kt","line":18,"usageType":"REFERENCE"}]}
    40	{"totalCount":2,"usages":[{"column":30,"enclosingScope":["Square"],"file":"src/main/kotlin/demo/Normal.kt","line":23,"usageType":"REFERENCE"},{"column":60,"enclosingScope":["makeDefaultShapes"],"file":"src/main/kotlin/demo/Normal.kt","line":37,"usageType":"REFERENCE"}]}
    41	{"implementations":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"},{"column":12,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"totalCount":3}
    42	{"implementations":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"totalCount":1}
    43	{"hierarchy":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"}],"method":{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"METHOD","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}}
    44	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":38,"name":"apply","qualifiedName":"demo.Coercion#apply"}],"method":{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":39,"name":"apply","qualifiedName":"demo.Coercion.IntCoerce#apply"}}
    45	{"hierarchy":[{"column":18,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":38,"name":"apply","qualifiedName":"demo.Coercion#apply"}],"method":{"column":50,"file":"src/main/kotlin/demo/Quirks.kt","kind":"METHOD","line":40,"name":"apply","qualifiedName":"demo.Coercion.AbsCoerce#apply"}}
    46	{"element":{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[]}
    47	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
    48	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    49	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    50	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square"}],"query":"Square","totalCount":1}
    51	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    52	{"classes":[{"column":11,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable"}],"query":"Drawable","totalCount":1}
    53	{"query":"describe","symbols":[{"column":14,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":10,"name":"describe","qualifiedName":"demo.Shape#describe"}],"totalCount":1}
    54	{"query":"totalArea","symbols":[{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":32,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":1}
    55	{"query":"draw","symbols":[{"column":11,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable"},{"column":9,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":4,"name":"draw","qualifiedName":"demo.Drawable#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":15,"name":"draw","qualifiedName":"demo.Circle#draw"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":20,"name":"draw","qualifiedName":"demo.Rectangle#draw"}],"totalCount":4}
    56	{"totalCount":3,"usages":[{"column":74,"enclosingScope":["Shape","describe"],"file":"src/main/kotlin/demo/Normal.kt","line":10,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["ShapeCollection","totalArea","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":53,"enclosingScope":["ShapeCollection","largest","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    57	{"query":"area","symbols":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":4}
    58	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh","totalCount":2}
    59	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"},{"column":12,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce"}],"query":"SC","totalCount":2}
    61	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    62	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    63	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":37,"name":"makeDefaultShapes()","qualifiedName":"demo.NormalKt#makeDefaultShapes"}}
    64	{"query":"Coercer.coerce","symbols":[{"column":9,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":46,"name":"coerce","qualifiedName":"demo.Coercer#coerce"}],"totalCount":1}
    65	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    66	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    67	{"classes":[{"column":16,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape"},{"column":7,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection"}],"query":"Sh*","totalCount":2}

### live-test/python/output.jsonl
     1	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     2	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     3	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}
     4	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}
     5	{"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes","qualifiedName":"normal.make_default_shapes"}
     6	{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
     7	{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
     8	{"column":5,"enclosingScope":null,"file":"${PYTHON_STDLIB}/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
     9	{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    10	{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    11	{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    12	{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    13	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":69,"name":"result","qualifiedName":"quirks.quirk_walrus"}
    14	{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    15	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"FUNCTION","line":80,"name":"get_coercer","qualifiedName":"quirks.quirk_nested_return"}
    16	{"totalCount":3,"usages":[{"column":51,"enclosingScope":["Shape","describe"],"file":"src/normal.py","line":16,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":47,"enclosingScope":["ShapeCollection","largest","key"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    17	{"totalCount":5,"usages":[{"column":14,"enclosingScope":["ShapeCollection","__init__"],"file":"src/normal.py","line":49,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.py","line":52,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":58,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    18	{"totalCount":6,"usages":[{"column":14,"enclosingScope":["Circle"],"file":"src/normal.py","line":19,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["Rectangle"],"file":"src/normal.py","line":30,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["ShapeCollection","__init__"],"file":"src/normal.py","line":49,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/normal.py","line":51,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.py","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["make_default_shapes"],"file":"src/normal.py","line":63,"usageType":"REFERENCE"}]}
    19	{"totalCount":36,"usages":[{"column":37,"enclosingScope":["quirk_name_rebinding"],"file":"src/quirks.py","line":11,"usageType":"REFERENCE"},{"column":10,"enclosingScope":["quirk_name_rebinding"],"file":"src/quirks.py","line":12,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_getattr_module"],"file":"src/quirks.py","line":16,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_getattr_module"],"file":"src/quirks.py","line":18,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_functools_partial"],"file":"src/quirks.py","line":21,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["quirk_functools_partial"],"file":"src/quirks.py","line":22,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":26,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":27,"usageType":"REFERENCE"},{"column":50,"enclosingScope":["quirk_dict_dispatch"],"file":"src/quirks.py","line":27,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["quirk_lambda_wrap"],"file":"src/quirks.py","line":31,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["quirk_lambda_wrap"],"file":"src/quirks.py","line":32,"usageType":"REFERENCE"},{"column":36,"enclosingScope":["quirk_list_indexing"],"file":"src/quirks.py","line":36,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["quirk_list_indexing"],"file":"src/quirks.py","line":37,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["quirk_conditional_expr"],"file":"src/quirks.py","line":41,"usageType":"REFERENCE"},{"column":10,"enclosingScope":["quirk_conditional_expr"],"file":"src/quirks.py","line":42,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":46,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["quirk_decorator_wrap"],"file":"src/quirks.py","line":51,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["quirk_decorator_wrap"],"file":"src/quirks.py","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["quirk_class_method"],"file":"src/quirks.py","line":61,"usageType":"REFERENCE"},{"column":39,"enclosingScope":["quirk_class_method","Coercer","coerce"],"file":"src/quirks.py","line":63,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["quirk_class_method","Coercer","coerce"],"file":"src/quirks.py","line":64,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["quirk_walrus"],"file":"src/quirks.py","line":68,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["quirk_walrus"],"file":"src/quirks.py","line":69,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["quirk_unpacking"],"file":"src/quirks.py","line":74,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_unpacking"],"file":"src/quirks.py","line":75,"usageType":"REFERENCE"},{"column":36,"enclosingScope":["quirk_nested_return"],"file":"src/quirks.py","line":79,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["quirk_nested_return","get_coercer"],"file":"src/quirks.py","line":81,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["quirk_map_filter"],"file":"src/quirks.py","line":85,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["quirk_map_filter"],"file":"src/quirks.py","line":86,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["quirk_reduce"],"file":"src/quirks.py","line":89,"usageType":"REFERENCE"},{"column":50,"enclosingScope":["quirk_reduce"],"file":"src/quirks.py","line":90,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["quirk_chained_getattr"],"file":"src/quirks.py","line":93,"usageType":"REFERENCE"},{"column":15,"enclosingScope":["quirk_chained_getattr"],"file":"src/quirks.py","line":95,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["quirk_multiple_assignment"],"file":"src/quirks.py","line":98,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["quirk_multiple_assignment"],"file":"src/quirks.py","line":99,"usageType":"REFERENCE"}]}
    20	{"totalCount":4,"usages":[{"column":14,"enclosingScope":["Circle","__init__"],"file":"src/normal.py","line":21,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["Circle","area"],"file":"src/normal.py","line":24,"usageType":"REFERENCE"},{"column":40,"enclosingScope":["Circle","area"],"file":"src/normal.py","line":24,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["Circle","draw"],"file":"src/normal.py","line":27,"usageType":"REFERENCE"}]}
    21	{"implementations":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}],"totalCount":2}
    22	{"implementations":[],"totalCount":0}
    23	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":31,"name":"__init__","qualifiedName":"normal.Rectangle.__init__"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":43,"name":"__init__","qualifiedName":"normal.Square.__init__"}}
    24	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"}}
    25	{"hierarchy":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"}],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}}
    26	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
    27	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
    28	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
    29	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
    30	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"Shape.describe(self)","qualifiedName":"normal.Shape.describe"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}}
    31	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}
    32	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Shape","totalCount":2}
    33	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle","qualifiedName":"normal.Rectangle"}],"query":"Rectangle","totalCount":1}
    34	{"query":"area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":12,"name":"area","qualifiedName":"normal.Shape.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":54,"name":"total_area","qualifiedName":"normal.ShapeCollection.total_area"}],"totalCount":4}
    35	{"query":"quirk_name_rebinding","symbols":[{"column":5,"file":"src/quirks.py","kind":"FUNCTION","line":11,"name":"quirk_name_rebinding","qualifiedName":"quirks.quirk_name_rebinding"}],"totalCount":1}
    36	{"file":"src/normal.py","language":"Python","structure":"normal.py\n\nDrawable(Protocol) (line 6)\n  draw(self) (line 7)\nShape(ABC) (line 10)\n  area(self) (line 12)\n  describe(self) (line 15)\nCircle(Shape) (line 19)\n  __init__(self, radius) (line 20)\n  area(self) (line 23)\n  draw(self) (line 26)\n  radius (line 21)\nRectangle(Shape) (line 30)\n  __init__(self, width, height) (line 31)\n  area(self) (line 35)\n  draw(self) (line 38)\n  height (line 33)\n  width (line 32)\nSquare(Rectangle) (line 42)\n  __init__(self, side) (line 43)\nShapeCollection (line 47)\n  __init__(self) (line 48)\n  add(self, shape) (line 51)\n  total_area(self) (line 54)\n  largest(self) (line 57)\n  shapes (line 49)\nmake_default_shapes() (line 63)"}
    37	{"file":"src/quirks.py","language":"Python","structure":"quirks.py\n\nquirk_name_rebinding(x) (line 11)\nquirk_getattr_module(name) (line 16)\nquirk_functools_partial(x) (line 21)\nquirk_dict_dispatch(key, x) (line 26)\nquirk_lambda_wrap(x) (line 31)\nquirk_list_indexing(x) (line 36)\nquirk_conditional_expr(x, use_int) (line 41)\nquirk_star_import_simulation(x) (line 46)\nquirk_decorator_wrap(x) (line 51)\n  with_logging(fn) (line 52)\n    wrapper(*args, **kwargs) (line 54)\nquirk_class_method(x) (line 61)\n  Coercer (line 62)\n    coerce(self, raw) (line 63)\nquirk_walrus(x) (line 68)\nquirk_unpacking(x) (line 74)\nquirk_nested_return(x) (line 79)\n  get_coercer() (line 80)\nquirk_map_filter(items) (line 85)\nquirk_reduce(values) (line 89)\nquirk_chained_getattr(x) (line 93)\nquirk_multiple_assignment(x) (line 98)\nclassify_shape(s) (line 103)"}
    38	{"totalCount":0,"usages":[]}
    39	{"totalCount":4,"usages":[{"column":14,"enclosingScope":["Square"],"file":"src/normal.py","line":42,"usageType":"REFERENCE"},{"column":26,"enclosingScope":["make_default_shapes"],"file":"src/normal.py","line":64,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":105,"usageType":"IMPORT"},{"column":24,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":108,"usageType":"REFERENCE"}]}
    40	{"implementations":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle","qualifiedName":"normal.Rectangle"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"totalCount":3}
    41	{"implementations":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"totalCount":1}
    42	{"implementations":[],"totalCount":0}
    43	{"implementations":[],"totalCount":0}
    44	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"}}
    45	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":38,"name":"draw","qualifiedName":"normal.Rectangle.draw"}}
    46	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable(Protocol)","qualifiedName":"normal.Drawable","supertypes":null},"subtypes":[],"supertypes":[]}
    47	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
    48	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}
    49	{"calls":[],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"Shape.describe(self)","qualifiedName":"normal.Shape.describe"}}
    50	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}
    51	{"calls":[],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":51,"name":"ShapeCollection.add(self, shape)","qualifiedName":"normal.ShapeCollection.add"}}
    52	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"}],"query":"Circle","totalCount":1}
    53	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square","qualifiedName":"normal.Square"}],"query":"Square","totalCount":1}
    54	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable","qualifiedName":"normal.Drawable"}],"query":"Drawable","totalCount":1}
    55	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    56	{"query":"describe","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}],"totalCount":1}
    57	{"query":"total_area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":54,"name":"total_area","qualifiedName":"normal.ShapeCollection.total_area"}],"totalCount":1}
    58	{"query":"draw","symbols":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":6,"name":"Drawable","qualifiedName":"normal.Drawable"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":7,"name":"draw","qualifiedName":"normal.Drawable.draw"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":38,"name":"draw","qualifiedName":"normal.Rectangle.draw"}],"totalCount":4}
    59	{"query":"largest","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":57,"name":"largest","qualifiedName":"normal.ShapeCollection.largest"}],"totalCount":1}
    60	{"totalCount":3,"usages":[{"column":51,"enclosingScope":["Shape","describe"],"file":"src/normal.py","line":16,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":47,"enclosingScope":["ShapeCollection","largest","key"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    61	{"query":"area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}],"totalCount":4}
    62	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Sh","totalCount":2}
    63	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle","qualifiedName":"normal.Circle"}],"query":"Circle","totalCount":1}
    64	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"SC","totalCount":1}
    65	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
    66	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
    67	{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
    68	{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}
    69	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    70	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    71	{"classes":[{"column":7,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape","qualifiedName":"normal.Shape"},{"column":7,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection"}],"query":"Sh*","totalCount":2}
    72	{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":107,"name":"kind","qualifiedName":"quirks.classify_shape"}
    73	{"totalCount":4,"usages":[{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":107,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":109,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":111,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":112,"usageType":"REFERENCE"}]}
    74	{"hierarchy":[],"method":{"column":9,"file":"src/normal.py","kind":"METHOD","line":26,"name":"draw","qualifiedName":"normal.Circle.draw"}}
    75	{"query":"Coercer.coerce","symbols":[{"column":13,"file":"src/quirks.py","kind":"METHOD","line":63,"name":"coerce","qualifiedName":"quirks.quirk_class_method"}],"totalCount":1}
    76	{"totalCount":2,"usages":[{"column":33,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":47,"usageType":"IMPORT"},{"column":12,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"}]}

### live-test/javascript/output.jsonl
     1	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}
     2	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}
     3	{"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes","qualifiedName":"makeDefaultShapes"}
     4	{"column":11,"enclosingScope":null,"file":"src/quirks.js","kind":"CONSTANT","line":5,"name":"fn","qualifiedName":"fn"}
     5	{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     6	{"column":23,"enclosingScope":null,"file":"src/quirks.js","kind":"CONSTANT","line":36,"name":"p","qualifiedName":"p"}
     7	{"column":13,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
     8	{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     9	{"column":17,"enclosingScope":null,"file":"src/quirks.js","kind":"PROPERTY","line":80,"name":"parse","qualifiedName":"proxy.parse"}
    10	{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
    11	{"totalCount":5,"usages":[{"column":12,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":63,"enclosingScope":["Shape","describe"],"file":"src/normal.js","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"}]}
    12	{"totalCount":3,"usages":[{"column":18,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.js","line":41,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":49,"usageType":"REFERENCE"}]}
    13	{"totalCount":5,"usages":[{"column":9,"enclosingScope":[],"file":"src/accessors.js","line":2,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["Box"],"file":"src/accessors.js","line":4,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Circle"],"file":"src/normal.js","line":12,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Rectangle"],"file":"src/normal.js","line":21,"usageType":"REFERENCE"},{"column":30,"enclosingScope":["Shape"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":3}
    15	{"implementations":[],"totalCount":0}
    16	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}}
    17	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}}
    18	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    19	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    20	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    21	{"calls":[{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    22	{"calls":[{"children":[{"children":null,"column":1,"enclosingScope":["/","home","ubuntu","dev","jetbrains-index-mcp-plugin","live-test","javascript","src"],"file":"src/consumer.js","kind":"FILE","line":1,"name":"consumer.js","qualifiedName":null}],"column":10,"enclosingScope":null,"file":"src/consumer.js","kind":"FUNCTION","line":4,"name":"use()","qualifiedName":"use"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":9,"name":"Shape.describe()","qualifiedName":"Shape.describe"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":42,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":47,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":8,"name":"Shape.area()","qualifiedName":"Shape.area"}}
    23	{"calls":[],"element":{"children":null,"column":16,"enclosingScope":null,"file":"src/quirks.js","kind":"FUNCTION","line":64,"name":"qAwait(x)","qualifiedName":"qAwait"}}
    24	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Shape","totalCount":2}
    25	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"}],"query":"Rectangle","totalCount":1}
    26	{"query":"area","symbols":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":5}
    27	{"query":"qRebind","symbols":[{"column":10,"file":"src/quirks.js","kind":"FUNCTION","line":4,"name":"qRebind","qualifiedName":"qRebind"},{"column":5,"file":"src/quirks.js","kind":"PROPERTY","line":86,"name":"qRebind","qualifiedName":"exports.qRebind"}],"totalCount":2}
    28	{"file":"src/normal.js","language":"JavaScript","structure":"normal.js\n\nexports (line 1)\n  Drawable (line 60)\n  Shape (line 60)\n  Circle (line 60)\n  Rectangle (line 60)\n  Square (line 60)\n  ShapeCollection (line 60)\n  makeDefaultShapes (line 60)\nDrawable (line 3)\n  draw(): never (line 4)\nShape (line 7)\n  area(): never (line 8)\n  describe(): string (line 9)\nCircle (line 12)\n  constructor(radius) (line 13)\n  radius (line 15)\n  area() (line 17)\n  draw(): string (line 18)\nRectangle (line 21)\n  constructor(width, height) (line 22)\n  width (line 24)\n  height (line 25)\n  area() (line 27)\n  draw(): string (line 28)\nSquare (line 31)\n  constructor(side) (line 32)\nShapeCollection (line 37)\n  constructor() (line 38)\n  shapes (line 39)\n  add(shape): void (line 41)\n  totalArea() (line 42)\n  largest() (line 47)\nmakeDefaultShapes() (line 56)"}
    29	{"file":"src/quirks.js","language":"JavaScript","structure":"quirks.js\n\nexports (line 1)\n  qRebind (line 86)\n  qComputed (line 86)\n  qObjLit (line 86)\n  qCond (line 86)\n  qReturned (line 86)\n  qArrayIdx (line 86)\n  qDestructured (line 87)\n  qSpread (line 87)\n  qBind (line 87)\n  qForEach (line 87)\n  qPromise (line 87)\n  qAwait (line 87)\n  qOpt (line 88)\n  qNullish (line 88)\n  qProxy (line 88)\nqRebind(x) (line 4)\nqComputed(name, x) (line 10)\nqObjLit(x) (line 15)\n  parse (line 16)\nqCond(flag, x) (line 20)\nqReturned(x) (line 25)\nqArrayIdx(x) (line 30)\nqDestructured(x) (line 35)\nqSpread(x) (line 41)\nqBind(x) (line 47)\nqForEach(x) (line 52)\nqPromise(x) (line 59)\nasync qAwait(x) (line 64)\nqOpt(x) (line 70)\nqNullish(x) (line 75)\nproxy (line 80)\n  parse (line 80)\nqProxy(x) (line 81)"}
    30	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"CONSTRUCTOR","line":13,"name":"constructor","qualifiedName":"Circle"}
    31	{"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"CONSTRUCTOR","line":22,"name":"constructor","qualifiedName":"Rectangle"}
    32	{"totalCount":4,"usages":[{"column":9,"enclosingScope":[],"file":"src/consumer.js","line":2,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Circle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    33	{"totalCount":1,"usages":[{"column":20,"enclosingScope":["Drawable"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    34	{"totalCount":3,"usages":[{"column":22,"enclosingScope":["Square"],"file":"src/normal.js","line":31,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["Rectangle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    35	{"totalCount":0,"usages":[]}
    36	{"implementations":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    37	{"implementations":[],"totalCount":0}
    38	{"implementations":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"totalCount":1}
    39	{"implementations":[],"totalCount":0}
    40	{"hierarchy":[],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"}}
    41	{"hierarchy":[],"method":{"column":5,"file":"src/normal.js","kind":"METHOD","line":28,"name":"draw","qualifiedName":"Rectangle.draw"}}
    42	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},"subtypes":[],"supertypes":[]}
    43	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    44	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    45	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":42,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"}}
    46	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":9,"name":"Shape.describe()","qualifiedName":"Shape.describe"}}
    47	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.js","kind":"METHOD","line":47,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}}
    48	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    49	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"query":"Square","totalCount":1}
    50	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable"}],"query":"Drawable","totalCount":1}
    51	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    52	{"query":"describe","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":9,"name":"describe","qualifiedName":"Shape.describe"}],"totalCount":1}
    53	{"query":"totalArea","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":1}
    54	{"query":"draw","symbols":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":3,"name":"Drawable","qualifiedName":"Drawable"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":4,"name":"draw","qualifiedName":"Drawable.draw"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":28,"name":"draw","qualifiedName":"Rectangle.draw"},{"column":20,"file":"src/normal.js","kind":"PROPERTY","line":60,"name":"Drawable","qualifiedName":"exports.Drawable"}],"totalCount":5}
    55	{"query":"largest","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":47,"name":"largest","qualifiedName":"ShapeCollection.largest"}],"totalCount":1}
    56	{"totalCount":4,"usages":[{"column":12,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"}]}
    57	{"query":"area","symbols":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}],"totalCount":5}
    58	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh","totalCount":2}
    59	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    60	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"SC","totalCount":1}
    61	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    62	{"element":{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    63	{"calls":[{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":12,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":7,"enclosingScope":null,"file":"src/normal.js","kind":"CLASS","line":31,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/normal.js","kind":"FUNCTION","line":56,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    64	{"query":"Shape.area","symbols":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":42,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}
    65	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    66	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    67	{"classes":[{"column":7,"file":"src/normal.js","kind":"CLASS","line":7,"name":"Shape","qualifiedName":"Shape"},{"column":7,"file":"src/normal.js","kind":"CLASS","line":37,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh*","totalCount":2}
    68	{"totalCount":4,"usages":[{"column":9,"enclosingScope":[],"file":"src/consumer.js","line":2,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.js","line":57,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Circle"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    69	{"file":"src/accessors.js","language":"JavaScript","structure":"accessors.js\n\nexports (line 1)\n  Box (line 14)\nShape (line 2)\nBox (line 4)\n  constructor(s) (line 5)\n  s (line 7)\n  get area (line 9)"}
    70	{"hierarchy":[{"column":5,"file":"src/normal.js","kind":"METHOD","line":8,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"}}
    71	{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}

### live-test/typescript/output.jsonl
     1	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}
     2	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}
     3	{"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes","qualifiedName":"makeDefaultShapes"}
     4	{"column":11,"enclosingScope":null,"file":"src/quirks.ts","kind":"FUNCTION","line":2,"name":"fn","qualifiedName":"fn"}
     5	{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
     6	{"column":27,"enclosingScope":null,"file":"src/quirks.ts","kind":"TYPE_PARAMETER","line":52,"name":"T","qualifiedName":"TypedCoercer"}
     7	{"totalCount":4,"usages":[{"column":54,"enclosingScope":["Shape","describe"],"file":"src/normal.ts","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"}]}
     8	{"totalCount":3,"usages":[{"column":31,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":48,"usageType":"REFERENCE"}]}
     9	{"totalCount":7,"usages":[{"column":29,"enclosingScope":["Circle"],"file":"src/normal.ts","line":13,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["Rectangle"],"file":"src/normal.ts","line":21,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.ts","line":36,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":46,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["ShapeCollection","largest","best"],"file":"src/normal.ts","line":47,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.ts","line":55,"usageType":"REFERENCE"}]}
    10	{"totalCount":2,"usages":[{"column":26,"enclosingScope":["intCoercer"],"file":"src/quirks.ts","line":44,"usageType":"REFERENCE"},{"column":39,"enclosingScope":["qInterfaceDispatch","c"],"file":"src/quirks.ts","line":48,"usageType":"REFERENCE"}]}
    11	{"totalCount":1,"usages":[{"column":12,"enclosingScope":["qTypeAlias"],"file":"src/quirks.ts","line":65,"usageType":"REFERENCE"}]}
    12	{"totalCount":2,"usages":[{"column":46,"enclosingScope":["Circle"],"file":"src/normal.ts","line":13,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["Rectangle"],"file":"src/normal.ts","line":21,"usageType":"REFERENCE"}]}
    13	{"implementations":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":2}
    14	{"implementations":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}],"totalCount":2}
    15	{"implementations":[],"totalCount":0}
    16	{"hierarchy":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"}}
    17	{"hierarchy":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}}
    18	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    19	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    20	{"element":{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    21	{"element":{"column":14,"enclosingScope":null,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer","supertypes":null},"subtypes":[],"supertypes":[]}
    22	{"element":{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    23	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    24	{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"Shape.describe()","qualifiedName":"Shape.describe"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}],"element":{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}}
    25	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Shape","totalCount":2}
    26	{"classes":[{"column":18,"file":"src/quirks.ts","kind":"INTERFACE","line":42,"name":"Coercer","qualifiedName":"Coercer"},{"column":14,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer"}],"query":"Coercer","totalCount":2}
    27	{"classes":[{"column":18,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable"}],"query":"Drawable","totalCount":1}
    28	{"query":"area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":4}
    29	{"query":"qLambda","symbols":[{"column":17,"file":"src/quirks.ts","kind":"FUNCTION","line":1,"name":"qLambda","qualifiedName":"qLambda"},{"column":17,"file":"src/quirks.ts","kind":"FUNCTION","line":11,"name":"qGenericLambda","qualifiedName":"qGenericLambda"}],"totalCount":2}
    30	{"file":"src/normal.ts","language":"TypeScript","structure":"normal.ts\n\nexport Drawable (line 1)\n  draw(): string (line 2)\nexport abstract Shape (line 5)\n  abstract area(): number (line 6)\n  describe(): string (line 8)\nexport Circle (line 13)\n  constructor(radius: number) (line 14)\n  public readonly radius: number (line 14)\n  area(): number (line 17)\n  draw(): string (line 18)\nexport Rectangle (line 21)\n  constructor(width: number, height: number) (line 22)\n  public readonly width: number (line 22)\n  public readonly height: number (line 22)\n  area(): number (line 25)\n  draw(): string (line 26)\nexport Square (line 29)\n  constructor(side: number) (line 30)\nexport ShapeCollection (line 35)\n  readonly shapes: Shape[] (line 36)\n  add(shape: Shape): void (line 38)\n  totalArea(): number (line 40)\n  largest(): Shape | null (line 46)\nexport makeDefaultShapes(): Shape[] (line 55)"}
    31	{"file":"src/quirks.ts","language":"TypeScript","structure":"quirks.ts\n\nexport qLambda(x: string): number (line 1)\n  fn(s: any) (line 2)\nexport qFunctionRef(x: string): number (line 6)\nexport qGenericLambda(x: T): number (line 11)\n  fn(s: T): number (line 12)\nexport qConditionalType(mode: T, x: string): number (line 16)\n  Fn (line 17)\nexport qDispatchMap(key: string, x: string): number (line 22)\n  dispatch: Record<string, (s: string) => number> (line 23)\n    int(s: any) (line 24)\n    abs(s: any) (line 25)\nexport qOptional(x?: string): number (line 30)\nexport qNonNullAssertion(x: string | undefined): number (line 34)\nexport qAsCast(x: unknown): number (line 38)\nexport Coercer (line 42)\n  coerce(x: string): number (line 42)\nexport intCoercer: Coercer (line 44)\n  coerce(x: string) (line 45)\nexport qInterfaceDispatch(c: Coercer, x: string): number (line 48)\nexport TypedCoercer<T> (line 52)\n  coerce(x: T): number (line 53)\nexport qGenericClass(x: string): number (line 56)\nexport Coerce (line 60)\naliasedCoerce(s: any) (line 62)\nexport qTypeAlias(x: string): number (line 64)"}
    32	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"CONSTRUCTOR","line":14,"name":"constructor","qualifiedName":"Circle"}
    33	{"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"CONSTRUCTOR","line":22,"name":"constructor","qualifiedName":"Rectangle"}
    34	{"column":14,"enclosingScope":null,"file":"src/quirks.ts","kind":"CLASS","line":52,"name":"TypedCoercer","qualifiedName":"TypedCoercer"}
    35	{"totalCount":1,"usages":[{"column":17,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.ts","line":56,"usageType":"REFERENCE"}]}
    36	{"implementations":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    37	{"implementations":[],"totalCount":0}
    38	{"implementations":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"totalCount":3}
    39	{"hierarchy":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"}}
    40	{"hierarchy":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"}],"method":{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}}
    41	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}
    42	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    43	{"calls":[{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"Circle.area()","qualifiedName":"Circle.area"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"Rectangle.area()","qualifiedName":"Rectangle.area"}],"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"ShapeCollection.totalArea()","qualifiedName":"ShapeCollection.totalArea"}}
    44	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"Shape.describe()","qualifiedName":"Shape.describe"}}
    45	{"calls":[{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"Circle.area()","qualifiedName":"Circle.area"},{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"Rectangle.area()","qualifiedName":"Rectangle.area"}],"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"Shape.area()","qualifiedName":"Shape.area"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"ShapeCollection.largest()","qualifiedName":"ShapeCollection.largest"}}
    46	{"calls":[],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"Drawable.draw()","qualifiedName":"Drawable.draw"}}
    47	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    48	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"query":"Square","totalCount":1}
    49	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    50	{"query":"describe","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":8,"name":"describe","qualifiedName":"Shape.describe"}],"totalCount":1}
    51	{"query":"totalArea","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":1}
    52	{"query":"draw","symbols":[{"column":18,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":2,"name":"draw","qualifiedName":"Drawable.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":18,"name":"draw","qualifiedName":"Circle.draw"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":26,"name":"draw","qualifiedName":"Rectangle.draw"}],"totalCount":4}
    53	{"query":"largest","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":46,"name":"largest","qualifiedName":"ShapeCollection.largest"}],"totalCount":1}
    54	{"totalCount":4,"usages":[{"column":54,"enclosingScope":["Shape","describe"],"file":"src/normal.ts","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"}]}
    55	{"query":"area","symbols":[{"column":5,"file":"src/normal.ts","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":25,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":4}
    56	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh","totalCount":2}
    57	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"}],"query":"Circle","totalCount":1}
    58	{"classes":[{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"SC","totalCount":1}
    59	{"element":{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null},"subtypes":[],"supertypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":[{"column":18,"enclosingScope":null,"file":"src/normal.ts","kind":"INTERFACE","line":1,"name":"Drawable","qualifiedName":"Drawable","supertypes":null},{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null}]}]}
    60	{"element":{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    62	{"query":"Shape.area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh*","totalCount":2}
    66	{"element":{"column":13,"enclosingScope":null,"file":"src/quirks.ts","kind":"TYPE_ALIAS","line":60,"name":"Coerce","qualifiedName":"Coerce","supertypes":null},"subtypes":[],"supertypes":[]}

### live-test/php/output.jsonl
     1	{"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"}
     2	{"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}
     3	{"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}
     4	{"column":10,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
     5	{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":4,"name":"Quirks","qualifiedName":"\\Demo\\Quirks"}
     6	{"column":55,"enclosingScope":null,"file":"src/Normal.php","kind":"PARAMETER","line":17,"name":"radius","qualifiedName":"\\Demo\\Circle::__construct(radius)"}
     7	{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status"}
     8	{"column":10,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM_CASE","line":5,"name":"Active","qualifiedName":"\\Demo\\Status::Active"}
     9	{"totalCount":4,"usages":[{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"}]}
    10	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["Demo","ShapeCollection","add"],"file":"src/Normal.php","line":51,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":56,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":64,"usageType":"REFERENCE"}]}
    11	{"totalCount":7,"usages":[{"column":26,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"PARAMETER"},{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Demo","Circle"],"file":"src/Normal.php","line":16,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","Rectangle"],"file":"src/Normal.php","line":28,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["Demo","ShapeCollection"],"file":"src/Normal.php","line":47,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","ShapeCollection","add","shape"],"file":"src/Normal.php","line":50,"usageType":"PARAMETER"},{"column":33,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":62,"usageType":"REFERENCE"}]}
    12	{"totalCount":2,"usages":[{"column":22,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":42,"usageType":"REFERENCE"}]}
    13	{"totalCount":5,"usages":[{"column":22,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":9,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":10,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Demo","Status","label"],"file":"src/Modern.php","line":11,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["Demo","defaultStatus"],"file":"src/Modern.php","line":21,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["Demo","defaultStatus"],"file":"src/Modern.php","line":22,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}],"totalCount":2}
    15	{"implementations":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}],"totalCount":2}
    16	{"implementations":[{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer"}],"totalCount":2}
    17	{"implementations":[{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":84,"name":"coerce","qualifiedName":"\\Demo\\IntCoercer::coerce"},{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":88,"name":"coerce","qualifiedName":"\\Demo\\LenCoercer::coerce"}],"totalCount":2}
    18	{"hierarchy":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"}}
    19	{"hierarchy":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}}
    20	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"}}
    21	{"hierarchy":[{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":80,"name":"coerce","qualifiedName":"\\Demo\\Coercer::coerce"}],"method":{"column":21,"file":"src/Quirks.php","kind":"METHOD","line":84,"name":"coerce","qualifiedName":"\\Demo\\IntCoercer::coerce"}}
    22	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}]}
    23	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}
    24	{"element":{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    25	{"element":{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    26	{"element":{"column":11,"enclosingScope":null,"file":"src/Quirks.php","kind":"INTERFACE","line":79,"name":"Coercer","qualifiedName":"\\Demo\\Coercer","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer","supertypes":null}],"supertypes":[]}
    27	{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
    28	{"calls":[],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}}
    29	{"calls":[{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"Shape.describe","qualifiedName":"\\Demo\\Shape::describe"},{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"ShapeCollection.totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"},{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"ShapeCollection.largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}],"element":{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}}
    30	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Shape","totalCount":2}
    31	{"classes":[{"column":11,"file":"src/Quirks.php","kind":"INTERFACE","line":79,"name":"Coercer","qualifiedName":"\\Demo\\Coercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":83,"name":"IntCoercer","qualifiedName":"\\Demo\\IntCoercer"},{"column":7,"file":"src/Quirks.php","kind":"CLASS","line":87,"name":"LenCoercer","qualifiedName":"\\Demo\\LenCoercer"}],"query":"Coercer","totalCount":3}
    32	{"classes":[{"column":11,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable"}],"query":"Drawable","totalCount":1}
    33	{"classes":[{"column":6,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status"}],"query":"Status","totalCount":1}
    34	{"query":"area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":4}
    35	{"query":"qNameRebind","symbols":[{"column":28,"file":"src/Quirks.php","kind":"METHOD","line":6,"name":"qNameRebind","qualifiedName":"\\Demo\\Quirks::qNameRebind"}],"totalCount":1}
    36	{"file":"src/Normal.php","language":"PHP","structure":"Normal.php\n\nabstract public Drawable (line 4)\n  public draw(): string (line 5)\nabstract public Shape (line 8)\n  abstract public area(): float (line 9)\n  public describe(): string (line 11)\npublic Circle (line 16)\n  public radius: float (line 17)\n  public __construct(radius: float) (line 17)\n  public area(): float \u2191Shape (line 19)\n  public draw(): string \u2191Drawable (line 23)\npublic Rectangle (line 28)\n  public width: float (line 29)\n  public height: float (line 29)\n  public __construct(width: float, height: float) (line 29)\n  public area(): float \u2191Shape (line 31)\n  public draw(): string \u2191Drawable (line 35)\npublic Square (line 40)\n  public __construct(side: float) \u2191Rectangle (line 41)\npublic ShapeCollection (line 46)\n  public shapes: Shape[] = [...] (line 48)\n  public add(shape: Shape): void (line 50)\n  public totalArea(): float (line 54)\n  public largest(): Shape|null (line 62)\nmakeDefaultShapes(): array (line 73)"}
    37	{"file":"src/Quirks.php","language":"PHP","structure":"Quirks.php\n\npublic Quirks (line 4)\n  public static qNameRebind(x: string): int (line 6)\n  public static qVariableFunction(x: string): int (line 11)\n  public static qClosure(x: string): int (line 16)\n    Lambda(s: string): int (line 17)\n  public static qArrowFn(x: string): int (line 21)\n    Lambda(s: string): int (line 22)\n  public static qArrayDispatch(key: string, x: string): int (line 26)\n  public static qCallableArray(x: string): int (line 32)\n  public static qCallUserFunc(x: string): int (line 37)\n  public static qStaticMethodVariable(x: string): int (line 41)\n  public static qFromCallable(x: string): int (line 46)\n  public static qTernary(flag: bool, x: string): int (line 51)\n  public static qNullCoalesce(x: string): int (line 56)\n  public static qMatch(mode: string, x: string): int (line 61)\n    Lambda(s): int (line 63)\n    Lambda(s): int (line 65)\n  public static qCoerceUsage(c: Coercer, x: string): int (line 70)\n  public static qPromotedRead(c: Circle): float (line 74)\nabstract public Coercer (line 79)\n  public coerce(x: string): int (line 80)\npublic IntCoercer (line 83)\n  public coerce(x: string): int \u2191Coercer (line 84)\npublic LenCoercer (line 87)\n  public coerce(x: string): int \u2191Coercer (line 88)"}
    38	{"file":"src/Modern.php","language":"PHP","structure":"Modern.php\n\npublic final Status (line 4)\n  public static final Active: Status = 'A' (line 5)\n  public static final Inactive: Status = 'I' (line 6)\n  public label(): string (line 8)\npublic final Color (line 16)\n  public static final Red: Color (line 17)\n  public static final Green: Color (line 18)\ndefaultStatus(): Status (line 21)"}
    39	{"totalCount":2,"usages":[{"column":39,"enclosingScope":["Demo","Circle"],"file":"src/Normal.php","line":16,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["Demo","Rectangle"],"file":"src/Normal.php","line":28,"usageType":"REFERENCE"}]}
    40	{"totalCount":7,"usages":[{"column":16,"enclosingScope":["Demo","Rectangle","area"],"file":"src/Normal.php","line":32,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["Demo","Rectangle","area"],"file":"src/Normal.php","line":32,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Demo","Rectangle","draw"],"file":"src/Normal.php","line":36,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["Demo","Rectangle","draw"],"file":"src/Normal.php","line":36,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Demo","Square"],"file":"src/Normal.php","line":40,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Demo","Square","__construct"],"file":"src/Normal.php","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["Demo","makeDefaultShapes"],"file":"src/Normal.php","line":74,"usageType":"REFERENCE"}]}
    41	{"implementations":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square"}],"totalCount":3}
    42	{"implementations":[],"totalCount":0}
    43	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}}
    44	{"hierarchy":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":29,"name":"__construct","qualifiedName":"\\Demo\\Rectangle::__construct"}],"method":{"column":21,"file":"src/Normal.php","kind":"METHOD","line":41,"name":"__construct","qualifiedName":"\\Demo\\Square::__construct"}}
    45	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}
    46	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    47	{"calls":[{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"ShapeCollection.totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}}
    48	{"calls":[],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"Shape.describe","qualifiedName":"\\Demo\\Shape::describe"}}
    49	{"calls":[{"children":null,"column":30,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"Shape.area","qualifiedName":"\\Demo\\Shape::area"}],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"ShapeCollection.largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}}
    50	{"calls":[],"element":{"children":null,"column":21,"enclosingScope":null,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"Drawable.draw","qualifiedName":"\\Demo\\Drawable::draw"}}
    51	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"}],"query":"Circle","totalCount":1}
    52	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square"}],"query":"Square","totalCount":1}
    53	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    54	{"query":"describe","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":11,"name":"describe","qualifiedName":"\\Demo\\Shape::describe"}],"totalCount":1}
    55	{"query":"totalArea","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":1}
    56	{"query":"draw","symbols":[{"column":11,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":5,"name":"draw","qualifiedName":"\\Demo\\Drawable::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":23,"name":"draw","qualifiedName":"\\Demo\\Circle::draw"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":35,"name":"draw","qualifiedName":"\\Demo\\Rectangle::draw"}],"totalCount":4}
    57	{"query":"largest","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":62,"name":"largest","qualifiedName":"\\Demo\\ShapeCollection::largest"}],"totalCount":1}
    58	{"totalCount":4,"usages":[{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"}]}
    59	{"query":"area","symbols":[{"column":21,"file":"src/Normal.php","kind":"METHOD","line":19,"name":"area","qualifiedName":"\\Demo\\Circle::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":31,"name":"area","qualifiedName":"\\Demo\\Rectangle::area"}],"totalCount":4}
    60	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Sh","totalCount":2}
    61	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle"}],"query":"Circle","totalCount":1}
    62	{"classes":[{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"SC","totalCount":1}
    63	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}]}
    64	{"element":{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    65	{"calls":[],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}}
    66	{"query":"Shape::area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":2}
    67	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    68	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    69	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Sh*","totalCount":2}
    70	{"totalCount":2,"usages":[{"column":35,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":43,"usageType":"REFERENCE"}]}

### live-test/go/output.jsonl
     1	{"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"}
     2	{"column":20,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"}
     3	{"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}
     4	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":11,"name":"fn","qualifiedName":"main.fn"}
     5	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     6	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     7	{"column":9,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":119,"name":"s","qualifiedName":"main.s"}
     8	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":51,"name":"Coerce","qualifiedName":"main.Coerce"}
     9	{"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":6,"name":"Draw","qualifiedName":"main.Draw"}
    10	{"totalCount":3,"usages":[{"column":12,"enclosingScope":["TotalArea"],"file":"normal.go","line":57,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Largest"],"file":"normal.go","line":65,"usageType":"REFERENCE"},{"column":37,"enclosingScope":["Largest"],"file":"normal.go","line":65,"usageType":"REFERENCE"}]}
    11	{"totalCount":4,"usages":[{"column":5,"enclosingScope":["Add"],"file":"normal.go","line":51,"usageType":"REFERENCE"},{"column":24,"enclosingScope":["Add"],"file":"normal.go","line":51,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["TotalArea"],"file":"normal.go","line":56,"usageType":"REFERENCE"},{"column":23,"enclosingScope":["Largest"],"file":"normal.go","line":64,"usageType":"REFERENCE"}]}
    12	{"totalCount":6,"usages":[{"column":11,"enclosingScope":["ShapeCollection"],"file":"normal.go","line":47,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["Add"],"file":"normal.go","line":50,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["Largest"],"file":"normal.go","line":62,"usageType":"REFERENCE"},{"column":11,"enclosingScope":["Largest"],"file":"normal.go","line":63,"usageType":"REFERENCE"},{"column":28,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":72,"usageType":"REFERENCE"},{"column":11,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":73,"usageType":"REFERENCE"}]}
    13	{"totalCount":0,"usages":[]}
    14	{"totalCount":0,"usages":[]}
    15	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    16	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    17	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    18	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape","supertypes":null},"subtypes":[],"supertypes":[]}
    19	{"calls":[{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":42,"name":"NewSquare","qualifiedName":"main.NewSquare"}],"element":{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}}
    20	{"calls":[{"children":null,"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":24,"name":"Circle.Describe","qualifiedName":"main.Circle.Describe"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":23,"name":"Circle.Area","qualifiedName":"main.Circle.Area"}}
    21	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Shape","totalCount":3}
    22	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle"}],"query":"Rectangle","totalCount":1}
    23	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable"}],"query":"Drawable","totalCount":1}
    24	{"query":"Area","symbols":[{"column":2,"file":"normal.go","kind":"METHOD","line":10,"name":"Area","qualifiedName":"main.Area"},{"column":17,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"},{"column":20,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"},{"column":28,"file":"normal.go","kind":"METHOD","line":54,"name":"TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}],"totalCount":4}
    25	{"query":"qFnVar","symbols":[{"column":6,"file":"quirks.go","kind":"FUNCTION","line":10,"name":"qFnVar","qualifiedName":"main.qFnVar"}],"totalCount":1}
    26	{"file":"normal.go","language":"Go","structure":"normal.go\n\nDrawable (line 5)\n  Draw() string (line 6)\nShape (line 9)\n  Area() float64 (line 10)\n  Describe() string (line 11)\nbaseShape (line 14)\n  Describe() string (line 16)\nCircle (line 18)\n  baseShape (line 19)\n  Radius: float64 (line 20)\n  Area() float64 (line 23)\n  Describe() string (line 24)\n  Draw() string (line 27)\nRectangle (line 29)\n  baseShape (line 30)\n  Width: float64 (line 31)\n  Height: float64 (line 31)\n  Area() float64 (line 34)\n  Describe() string (line 35)\n  Draw() string (line 38)\nSquare (line 40)\n  Rectangle (line 40)\nShapeCollection (line 46)\n  Shapes: []Shape (line 47)\n  Add(s Shape) (line 50)\n  TotalArea() float64 (line 54)\n  Largest() Shape (line 62)\nLabeled embed.go (line 5)\n  baseShape embed.go (line 6)\n  label: string embed.go (line 7)\n  Note() string embed.go (line 10)\nCoercer quirks.go (line 50)\n  Coerce(s string) int quirks.go (line 51)\nIntCoercer quirks.go (line 54)\n  Coerce(s string) int quirks.go (line 56)\nLenCoercer quirks.go (line 61)\n  Coerce(s string) int quirks.go (line 63)\nNewSquare(side float64) Square (line 42)\nMakeDefaultShapes() []Shape (line 72)\nqFnVar(x string) int quirks.go (line 10)\nqClosure(x string) int quirks.go (line 17)\nqMapDispatch(key string, x string) int quirks.go (line 26)\nqSliceIdx(x string) int quirks.go (line 38)\nqInterfaceDispatch(c Coercer, x string) int quirks.go (line 65)\nqGoroutine(x string) int quirks.go (line 70)\nqDefer(x string) (out int) quirks.go (line 80)\nqMethodValue(x string) int quirks.go (line 89)\nqMethodExpression(x string) int quirks.go (line 96)\nqVariadic(xs ...string) int quirks.go (line 102)\nqTypeAssertion(x interface{}) int quirks.go (line 111)\nqTypeSwitch(x interface{}) int quirks.go (line 118)\nqPrintToUpper(x string) quirks.go (line 129)"}
    27	{"file":"quirks.go","language":"Go","structure":"quirks.go\n\nCoercer (line 50)\n  Coerce(s string) int (line 51)\nIntCoercer (line 54)\n  Coerce(s string) int (line 56)\nLenCoercer (line 61)\n  Coerce(s string) int (line 63)\nLabeled embed.go (line 5)\n  baseShape embed.go (line 6)\n  label: string embed.go (line 7)\n  Note() string embed.go (line 10)\nDrawable normal.go (line 5)\n  Draw() string normal.go (line 6)\nShape normal.go (line 9)\n  Area() float64 normal.go (line 10)\n  Describe() string normal.go (line 11)\nbaseShape normal.go (line 14)\n  Describe() string normal.go (line 16)\nCircle normal.go (line 18)\n  baseShape normal.go (line 19)\n  Radius: float64 normal.go (line 20)\n  Area() float64 normal.go (line 23)\n  Describe() string normal.go (line 24)\n  Draw() string normal.go (line 27)\nRectangle normal.go (line 29)\n  baseShape normal.go (line 30)\n  Width: float64 normal.go (line 31)\n  Height: float64 normal.go (line 31)\n  Area() float64 normal.go (line 34)\n  Describe() string normal.go (line 35)\n  Draw() string normal.go (line 38)\nSquare normal.go (line 40)\n  Rectangle normal.go (line 40)\nShapeCollection normal.go (line 46)\n  Shapes: []Shape normal.go (line 47)\n  Add(s Shape) normal.go (line 50)\n  TotalArea() float64 normal.go (line 54)\n  Largest() Shape normal.go (line 62)\nqFnVar(x string) int (line 10)\nqClosure(x string) int (line 17)\nqMapDispatch(key string, x string) int (line 26)\nqSliceIdx(x string) int (line 38)\nqInterfaceDispatch(c Coercer, x string) int (line 65)\nqGoroutine(x string) int (line 70)\nqDefer(x string) (out int) (line 80)\nqMethodValue(x string) int (line 89)\nqMethodExpression(x string) int (line 96)\nqVariadic(xs ...string) int (line 102)\nqTypeAssertion(x interface{}) int (line 111)\nqTypeSwitch(x interface{}) int (line 118)\nqPrintToUpper(x string) (line 129)\nNewSquare(side float64) Square normal.go (line 42)\nMakeDefaultShapes() []Shape normal.go (line 72)"}
    28	{"totalCount":0,"usages":[]}
    29	{"totalCount":4,"usages":[{"column":9,"enclosingScope":["Area","c"],"file":"normal.go","line":23,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Describe","c"],"file":"normal.go","line":24,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["Draw","c"],"file":"normal.go","line":27,"usageType":"REFERENCE"},{"column":3,"enclosingScope":["MakeDefaultShapes"],"file":"normal.go","line":74,"usageType":"REFERENCE"}]}
    30	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},"subtypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null}],"supertypes":[]}
    31	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[]}
    32	{"calls":[{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":10,"name":"Shape.Area","qualifiedName":"main.Area"}],"element":{"children":null,"column":28,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":54,"name":"*ShapeCollection.TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}}
    33	{"calls":[],"element":{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":11,"name":"Shape.Describe","qualifiedName":"main.Describe"}}
    34	{"calls":[],"element":{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":6,"name":"Drawable.Draw","qualifiedName":"main.Draw"}}
    35	{"calls":[{"children":null,"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":10,"name":"Shape.Area","qualifiedName":"main.Area"}],"element":{"children":null,"column":28,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":62,"name":"*ShapeCollection.Largest","qualifiedName":"main.ShapeCollection.Largest"}}
    36	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}
    37	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square"}],"query":"Square","totalCount":1}
    38	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"ShapeCollection","totalCount":1}
    39	{"query":"Describe","symbols":[{"column":2,"file":"normal.go","kind":"METHOD","line":11,"name":"Describe","qualifiedName":"main.Describe"},{"column":20,"file":"normal.go","kind":"METHOD","line":16,"name":"Describe","qualifiedName":"main.baseShape.Describe"},{"column":17,"file":"normal.go","kind":"METHOD","line":24,"name":"Describe","qualifiedName":"main.Circle.Describe"},{"column":20,"file":"normal.go","kind":"METHOD","line":35,"name":"Describe","qualifiedName":"main.Rectangle.Describe"}],"totalCount":4}
    40	{"query":"TotalArea","symbols":[{"column":28,"file":"normal.go","kind":"METHOD","line":54,"name":"TotalArea","qualifiedName":"main.ShapeCollection.TotalArea"}],"totalCount":1}
    41	{"query":"Draw","symbols":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable"},{"column":2,"file":"normal.go","kind":"METHOD","line":6,"name":"Draw","qualifiedName":"main.Draw"},{"column":17,"file":"normal.go","kind":"METHOD","line":27,"name":"Draw","qualifiedName":"main.Circle.Draw"},{"column":20,"file":"normal.go","kind":"METHOD","line":38,"name":"Draw","qualifiedName":"main.Rectangle.Draw"}],"totalCount":4}
    42	{"query":"Largest","symbols":[{"column":28,"file":"normal.go","kind":"METHOD","line":62,"name":"Largest","qualifiedName":"main.ShapeCollection.Largest"}],"totalCount":1}
    43	{"totalCount":1,"usages":[{"column":46,"enclosingScope":["Describe"],"file":"normal.go","line":25,"usageType":"REFERENCE"}]}
    44	{"query":"Area","symbols":[{"column":17,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"},{"column":20,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"}],"totalCount":4}
    45	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh","totalCount":2}
    46	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}
    47	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"SC","totalCount":1}
    48	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    49	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null},"subtypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null}],"supertypes":[]}
    50	{"calls":[{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":42,"name":"NewSquare","qualifiedName":"main.NewSquare"}],"element":{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}}
    51	{"query":"IntCoercer.Coerce","symbols":[{"column":19,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}],"totalCount":1}
    52	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    53	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    54	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh*","totalCount":3}
    55	{"totalCount":1,"usages":[{"column":42,"enclosingScope":["Note"],"file":"embed.go","line":11,"usageType":"REFERENCE"}]}
    56	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Shape","totalCount":2}
    57	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}

### live-test/rust/output.jsonl
     1	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}
     2	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"area","qualifiedName":"crate::area"}
     3	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes","qualifiedName":"crate::make_default_shapes"}
     4	{"column":8,"enclosingScope":null,"file":"src/quirks.rs","kind":"METHOD","line":29,"name":"coerce","qualifiedName":"crate::Coercer::coerce"}
     5	{"column":5,"enclosingScope":null,"file":"src/quirks.rs","kind":"ENUM_VARIANT","line":67,"name":"Int","qualifiedName":"crate::CoerceMode::Int"}
     6	{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
     7	{"column":4,"enclosingScope":null,"file":"src/quirks.rs","kind":"FUNCTION","line":11,"name":"parse_or_zero","qualifiedName":"crate::parse_or_zero"}
     8	{"column":17,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
     9	{"column":12,"enclosingScope":null,"file":"src/quirks.rs","kind":"METHOD","line":72,"name":"apply","qualifiedName":"crate::apply"}
    10	{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
    11	{"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"draw","qualifiedName":"crate::Drawable::draw"}
    12	{"column":12,"enclosingScope":null,"file":"src/extra.rs","kind":"FUNCTION","line":2,"name":"nested_helper","qualifiedName":"crate::inner::nested_helper"}
    13	{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}
    14	{"totalCount":7,"usages":[{"column":44,"enclosingScope":["Shape","describe","self.area()"],"file":"src/normal.rs","line":9,"usageType":"METHOD_CALL"},{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"},{"column":48,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":44,"usageType":"METHOD_CALL"},{"column":20,"enclosingScope":["area"],"file":"src/normal.rs","line":66,"usageType":"METHOD_CALL"},{"column":38,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"METHOD_CALL"},{"column":15,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"},{"column":37,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"}]}
    15	{"totalCount":4,"usages":[{"column":27,"enclosingScope":["new"],"file":"src/normal.rs","line":76,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["add"],"file":"src/normal.rs","line":80,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["largest"],"file":"src/normal.rs","line":88,"usageType":"FIELD_ACCESS"}]}
    16	{"totalCount":7,"usages":[{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":17,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":38,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":64,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.rs","line":71,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["add"],"file":"src/normal.rs","line":79,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["largest"],"file":"src/normal.rs","line":87,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":94,"usageType":"REFERENCE"}]}
    17	{"totalCount":6,"usages":[{"column":29,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"REFERENCE"},{"column":43,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"FIELD_ACCESS"},{"column":24,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":38,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":37,"enclosingScope":["draw","self.radius"],"file":"src/normal.rs","line":29,"usageType":"FIELD_ACCESS"},{"column":27,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":96,"usageType":"REFERENCE"}]}
    18	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"Circle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"Rectangle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":65,"name":"Square::area","qualifiedName":"crate::area"}],"totalCount":3}
    19	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"Circle::draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"Rectangle::draw","qualifiedName":"crate::draw"}],"totalCount":2}
    20	{"implementations":[{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":34,"name":"IntCoercer::coerce","qualifiedName":"crate::coerce"},{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":41,"name":"LenCoercer::coerce","qualifiedName":"crate::coerce"}],"totalCount":2}
    21	{"implementations":[{"column":18,"file":"src/quirks.rs","kind":"IMPL","line":33,"name":"impl Coercer for IntCoercer","qualifiedName":null},{"column":18,"file":"src/quirks.rs","kind":"IMPL","line":40,"name":"impl Coercer for LenCoercer","qualifiedName":null}],"totalCount":2}
    22	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    23	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    24	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null}],"supertypes":[]}
    25	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
    26	{"calls":[{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"Shape::describe(...)","qualifiedName":"crate::Shape::describe"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"Shape::area(...)","qualifiedName":"crate::Shape::area"}}
    27	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Shape","totalCount":2}
    28	{"classes":[{"column":11,"file":"src/quirks.rs","kind":"TRAIT","line":28,"name":"Coercer","qualifiedName":"crate::Coercer"},{"column":12,"file":"src/quirks.rs","kind":"STRUCT","line":32,"name":"IntCoercer","qualifiedName":"crate::IntCoercer"},{"column":12,"file":"src/quirks.rs","kind":"STRUCT","line":39,"name":"LenCoercer","qualifiedName":"crate::LenCoercer"}],"query":"Coercer","totalCount":3}
    29	{"classes":[{"column":16,"file":"src/extra.rs","kind":"STRUCT","line":3,"name":"Marker","qualifiedName":"crate::inner::Marker"}],"query":"Marker","totalCount":1}
    30	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":65,"name":"area","qualifiedName":"crate::area"},{"column":12,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"total_area","qualifiedName":"crate::total_area"}],"totalCount":5}
    31	{"query":"q_closure","symbols":[{"column":8,"file":"src/quirks.rs","kind":"FUNCTION","line":1,"name":"q_closure","qualifiedName":"crate::q_closure"}],"totalCount":1}
    32	{"query":"extra_function","symbols":[{"column":8,"file":"src/extra.rs","kind":"FUNCTION","line":6,"name":"extra_function","qualifiedName":"crate::extra_function"}],"totalCount":1}
    33	{"file":"src/normal.rs","language":"Rust","structure":"normal.rs\n\npub Drawable (line 1)\n  draw() -> String (line 2)\npub Shape (line 5)\n  area() -> f64 (line 6)\n  describe() -> String (line 8)\npub Circle (line 13)\n  pub radius: f64 (line 14)\nShape for Circle (line 17)\n  area() -> f64 (line 18)\n  describe() -> String (line 22)\nDrawable for Circle (line 27)\n  draw() -> String (line 28)\npub Rectangle (line 33)\n  pub width: f64 (line 34)\n  pub height: f64 (line 35)\nShape for Rectangle (line 38)\n  area() -> f64 (line 39)\n  describe() -> String (line 43)\nDrawable for Rectangle (line 48)\n  draw() -> String (line 49)\npub Square (line 54)\n  inner: Rectangle (line 55)\nSquare (line 58)\n  pub new(f64) -> Self (line 59)\nShape for Square (line 64)\n  area() -> f64 (line 65)\npub ShapeCollection (line 70)\n  pub shapes: Vec<Box<dyn Shape>> (line 71)\nShapeCollection (line 74)\n  pub new() -> Self (line 75)\n  pub add(Box<dyn Shape>) (line 79)\n  pub total_area() -> f64 (line 83)\n  pub largest() -> Option<&Box<dyn Shape>> (line 87)\npub make_default_shapes() -> Vec<Box<dyn Shape>> (line 94)"}
    34	{"file":"src/quirks.rs","language":"Rust","structure":"quirks.rs\n\npub q_closure(&str) -> i32 (line 1)\npub q_fn_pointer(&str) -> i32 (line 6)\nparse_or_zero(&str) -> i32 (line 11)\npub q_box_dyn_fn(&str) -> i32 (line 15)\npub q_match_dispatch(&str, &str) -> i32 (line 20)\npub Coercer (line 28)\n  coerce(&str) -> i32 (line 29)\npub IntCoercer (line 32)\nCoercer for IntCoercer (line 33)\n  coerce(&str) -> i32 (line 34)\npub LenCoercer (line 39)\nCoercer for LenCoercer (line 40)\n  coerce(&str) -> i32 (line 41)\npub q_trait_object(&dyn Coercer, &str) -> i32 (line 46)\npub q_generic_bound(&C, &str) -> i32 (line 50)\npub q_impl_trait_arg(impl Coercer, &str) -> i32 (line 54)\npub q_impl_trait_return(bool) -> Box<dyn Coercer> (line 58)\npub CoerceMode (line 66)\n  Int (line 67)\n  Len (line 68)\nCoerceMode (line 71)\n  pub apply(&str) -> i32 (line 72)\npub q_enum_dispatch(CoerceMode, &str) -> i32 (line 80)\npub q_iter_map(&[&str]) -> Vec<i32> (line 84)\npub q_iter_filter_map(&[&str]) -> Vec<i32> (line 88)\npub q_iter_fold(&[&str]) -> i32 (line 92)\npub q_question_mark(&str) -> Result<i32, ::ParseIntError> (line 96)\npub q_if_let(Option<&str>) -> i32 (line 101)"}
    35	{"file":"src/extra.rs","language":"Rust","structure":"extra.rs\n\npub inner (line 1)\n  pub nested_helper() -> i32 (line 2)\n  pub Marker (line 3)\npub extra_function(&str) -> i32 (line 6)\npub use_quirks_circle() -> f64 (line 10)"}
    36	{"totalCount":2,"usages":[{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":27,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":48,"usageType":"REFERENCE"}]}
    37	{"totalCount":5,"usages":[{"column":16,"enclosingScope":[],"file":"src/normal.rs","line":38,"usageType":"REFERENCE"},{"column":19,"enclosingScope":[],"file":"src/normal.rs","line":48,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["Square","inner"],"file":"src/normal.rs","line":55,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["new"],"file":"src/normal.rs","line":60,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":97,"usageType":"REFERENCE"}]}
    38	{"totalCount":6,"usages":[{"column":6,"enclosingScope":[],"file":"src/quirks.rs","line":33,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/quirks.rs","line":40,"usageType":"REFERENCE"},{"column":31,"enclosingScope":["q_trait_object"],"file":"src/quirks.rs","line":46,"usageType":"REFERENCE"},{"column":27,"enclosingScope":["q_generic_bound","C"],"file":"src/quirks.rs","line":50,"usageType":"REFERENCE"},{"column":33,"enclosingScope":["q_impl_trait_arg"],"file":"src/quirks.rs","line":54,"usageType":"REFERENCE"},{"column":54,"enclosingScope":["q_impl_trait_return"],"file":"src/quirks.rs","line":58,"usageType":"REFERENCE"}]}
    39	{"implementations":[{"column":16,"file":"src/normal.rs","kind":"IMPL","line":17,"name":"impl Shape for Circle","qualifiedName":null},{"column":16,"file":"src/normal.rs","kind":"IMPL","line":38,"name":"impl Shape for Rectangle","qualifiedName":null},{"column":16,"file":"src/normal.rs","kind":"IMPL","line":64,"name":"impl Shape for Square","qualifiedName":null}],"totalCount":3}
    40	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":22,"name":"Circle::describe","qualifiedName":"crate::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":43,"name":"Rectangle::describe","qualifiedName":"crate::describe"}],"totalCount":2}
    41	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null}],"supertypes":[]}
    42	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    43	{"element":{"column":10,"enclosingScope":null,"file":"src/quirks.rs","kind":"ENUM","line":66,"name":"CoerceMode","qualifiedName":"crate::CoerceMode","supertypes":null},"subtypes":[],"supertypes":[]}
    44	{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
    45	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
    46	{"calls":[],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"Shape::describe(...)","qualifiedName":"crate::Shape::describe"}}
    47	{"calls":[],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"Drawable::draw(...)","qualifiedName":"crate::Drawable::draw"}}
    48	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}],"query":"Circle","totalCount":1}
    49	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle"}],"query":"Rectangle","totalCount":1}
    50	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square"}],"query":"Square","totalCount":1}
    51	{"query":"describe","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":8,"name":"describe","qualifiedName":"crate::Shape::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":22,"name":"describe","qualifiedName":"crate::describe"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":43,"name":"describe","qualifiedName":"crate::describe"}],"totalCount":3}
    52	{"query":"total_area","symbols":[{"column":12,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"total_area","qualifiedName":"crate::total_area"}],"totalCount":1}
    53	{"query":"draw","symbols":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"draw","qualifiedName":"crate::Drawable::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"draw","qualifiedName":"crate::draw"}],"totalCount":4}
    54	{"totalCount":1,"usages":[{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"}]}
    55	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}],"totalCount":5}
    56	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh","totalCount":2}
    57	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}],"query":"Circle","totalCount":1}
    58	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"SC","totalCount":1}
    59	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    60	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
    62	{"query":"IntCoercer::coerce","symbols":[],"totalCount":0}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh*","totalCount":2}

exec
/usr/bin/zsh -lc "rg --files -g 'pyproject.toml' -g 'uv.lock' -g '.venv' -g 'AGENTS.md' -g '.python-version'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "command -v jq || true; command -v uv || true; [ -d .venv ] && printf '.venv exists\\n' || true" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
/usr/bin/jq
/home/ubuntu/.local/share/mise/installs/uv/0.10.6/uv-x86_64-unknown-linux-musl/uv

 succeeded in 0ms:
live-test/python/pyproject.toml
live-test/python/uv.lock

exec
/usr/bin/zsh -lc "for f in live-test/java/src/main/java/demo/Normal.java live-test/java/src/main/java/demo/Quirks.java live-test/java/src/main/java/demo/Modern.java; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/java/src/main/java/demo/Normal.java
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

### live-test/java/src/main/java/demo/Quirks.java
     1	package demo;
     2	
     3	import java.util.*;
     4	import java.util.concurrent.CompletableFuture;
     5	import java.util.function.Function;
     6	import java.util.function.Supplier;
     7	import java.util.stream.Collectors;
     8	
     9	public class Quirks {
    10	
    11	    public static int quirkLambda(String x) {
    12	        Function<String, Integer> fn = s -> Integer.parseInt(s);
    13	        return fn.apply(x);
    14	    }
    15	
    16	    public static int quirkVar(String x) {
    17	        var coerce = (Function<String, Integer>) Integer::parseInt;
    18	        return coerce.apply(x);
    19	    }
    20	
    21	    public static int quirkAnonClass(String x) {
    22	        Function<String, Integer> fn = new Function<>() {
    23	            @Override
    24	            public Integer apply(String s) {
    25	                return Integer.parseInt(s);
    26	            }
    27	        };
    28	        return fn.apply(x);
    29	    }
    30	
    31	    public static Optional<Integer> quirkOptional(String x) {
    32	        return Optional.of(x).map(Integer::parseInt);
    33	    }
    34	
    35	    public static int quirkTernary(String x, boolean stripPlus) {
    36	        Function<String, Integer> fn = stripPlus
    37	            ? s -> Integer.parseInt(s.replace("+", ""))
    38	            : Integer::parseInt;
    39	        return fn.apply(x);
    40	    }
    41	
    42	    public static CompletableFuture<Integer> quirkCompletableFuture(String x) {
    43	        return CompletableFuture.supplyAsync(() -> Integer.parseInt(x));
    44	    }
    45	
    46	    public static List<Integer> quirkStreamMap(List<String> xs) {
    47	        return xs.stream().map(Integer::parseInt).collect(Collectors.toList());
    48	    }
    49	
    50	    public static int quirkMapDispatch(String key, String x) {
    51	        Map<String, Function<String, Integer>> dispatch = new HashMap<>();
    52	        dispatch.put("int", Integer::parseInt);
    53	        dispatch.put("abs", s -> Math.abs(Integer.parseInt(s)));
    54	        return dispatch.get(key).apply(x);
    55	    }
    56	
    57	    static class Coercer {
    58	        private final String prefix;
    59	        Coercer(String prefix) { this.prefix = prefix; }
    60	        int coerce(String x) { return Integer.parseInt(x.replace(prefix, "")); }
    61	    }
    62	
    63	    @FunctionalInterface
    64	    interface Coerce { int run(String s); }
    65	
    66	    public static int quirkFunctionalIface(String x) {
    67	        Coerce c = Integer::parseInt;
    68	        return c.run(x);
    69	    }
    70	
    71	    enum CoerceMode {
    72	        INT { int apply(String s) { return Integer.parseInt(s); } },
    73	        ABS { int apply(String s) { return Math.abs(Integer.parseInt(s)); } };
    74	        abstract int apply(String s);
    75	    }
    76	
    77	    public static int quirkEnumDispatch(String x) {
    78	        return CoerceMode.INT.apply(x);
    79	    }
    80	
    81	    public static int quirkSupplier(String x) {
    82	        Supplier<Integer> supplier = () -> Integer.parseInt(x);
    83	        return supplier.get();
    84	    }
    85	
    86	    public static int parse(String s) {
    87	        return Integer.parseInt(s);
    88	    }
    89	
    90	    public static int parse(String s, int radix) {
    91	        return Integer.parseInt(s, radix);
    92	    }
    93	
    94	    public static int parseUsage() {
    95	        return parse("42") + parse("ff", 16);
    96	    }
    97	}

### live-test/java/src/main/java/demo/Modern.java
     1	package demo;
     2	
     3	public class Modern {
     4	    public record Point(int x, int y) {
     5	        public int sum() { return x + y; }
     6	    }
     7	
     8	    public sealed interface Animal permits Cat, Dog {
     9	        String name();
    10	    }
    11	
    12	    public static final class Cat implements Animal {
    13	        @Override public String name() { return "cat"; }
    14	    }
    15	
    16	    public static final class Dog implements Animal {
    17	        @Override public String name() { return "dog"; }
    18	    }
    19	
    20	    public static int probe() {
    21	        Point p = new Point(3, 4);
    22	        return p.sum();
    23	    }
    24	}

exec
/usr/bin/zsh -lc "for f in live-test/kotlin/src/main/kotlin/demo/Normal.kt live-test/kotlin/src/main/kotlin/demo/Quirks.kt live-test/kotlin/src/main/kotlin/demo/Modern.kt; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/kotlin/src/main/kotlin/demo/Normal.kt
     1	package demo
     2	
     3	interface Drawable {
     4	    fun draw(): String
     5	}
     6	
     7	abstract class Shape {
     8	    abstract fun area(): Double
     9	
    10	    open fun describe(): String = "${this::class.simpleName} with area ${area()}"
    11	}
    12	
    13	class Circle(val radius: Double) : Shape(), Drawable {
    14	    override fun area(): Double = 3.14159 * radius * radius
    15	    override fun draw(): String = "circle r=$radius"
    16	}
    17	
    18	open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
    19	    override fun area(): Double = width * height
    20	    override fun draw(): String = "rect ${width}x$height"
    21	}
    22	
    23	class Square(side: Double) : Rectangle(side, side)
    24	
    25	class ShapeCollection {
    26	    val shapes: MutableList<Shape> = mutableListOf()
    27	
    28	    fun add(shape: Shape) {
    29	        shapes.add(shape)
    30	    }
    31	
    32	    fun totalArea(): Double = shapes.sumOf { it.area() }
    33	
    34	    fun largest(): Shape? = shapes.maxByOrNull { it.area() }
    35	}
    36	
    37	fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))

### live-test/kotlin/src/main/kotlin/demo/Quirks.kt
     1	package demo
     2	
     3	fun quirkLambda(x: String): Int {
     4	    val coerce: (String) -> Int = { it.toInt() }
     5	    return coerce(x)
     6	}
     7	
     8	fun quirkFunctionRef(x: String): Int {
     9	    val coerce: (String) -> Int = String::toInt
    10	    return coerce(x)
    11	}
    12	
    13	fun quirkApply(x: String): Int {
    14	    return StringBuilder().apply { append(x) }.toString().toInt()
    15	}
    16	
    17	fun quirkLet(x: String?): Int {
    18	    return x?.let { it.toInt() } ?: 0
    19	}
    20	
    21	fun quirkWith(x: String): Int {
    22	    return with(x) { toInt() }
    23	}
    24	
    25	fun quirkRun(x: String): Int = x.run { toInt() }
    26	
    27	fun String.coerceTo(default: Int): Int = this.toIntOrNull() ?: default
    28	
    29	fun quirkExtensionFn(x: String): Int = x.coerceTo(0)
    30	
    31	fun quirkWhen(mode: String, x: String): Int = when (mode) {
    32	    "int" -> x.toInt()
    33	    "abs" -> Math.abs(x.toInt())
    34	    else -> 0
    35	}
    36	
    37	sealed class Coercion {
    38	    abstract fun apply(x: String): Int
    39	    object IntCoerce : Coercion() { override fun apply(x: String): Int = x.toInt() }
    40	    object AbsCoerce : Coercion() { override fun apply(x: String): Int = Math.abs(x.toInt()) }
    41	}
    42	
    43	fun quirkSealed(c: Coercion, x: String): Int = c.apply(x)
    44	
    45	data class Coercer(val prefix: String) {
    46	    fun coerce(x: String): Int = x.removePrefix(prefix).toInt()
    47	}
    48	
    49	fun quirkDataClass(x: String): Int = Coercer("+").coerce(x)
    50	
    51	fun quirkDispatchMap(key: String, x: String): Int {
    52	    val dispatch: Map<String, (String) -> Int> = mapOf(
    53	        "int" to String::toInt,
    54	        "abs" to { s -> Math.abs(s.toInt()) }
    55	    )
    56	    return dispatch[key]?.invoke(x) ?: 0
    57	}
    58	
    59	fun quirkInfix(x: String): Int = (x to 0).coerceFirst()
    60	
    61	infix fun Pair<String, Int>.coerceFirst(): Int = this.first.toIntOrNull() ?: this.second

### live-test/kotlin/src/main/kotlin/demo/Modern.kt
     1	package demo
     2	
     3	class Counter {
     4	    private var n = 0
     5	    fun increment() { n++ }
     6	    fun value(): Int = n
     7	
     8	    companion object {
     9	        const val DEFAULT_LIMIT = 100
    10	        fun create(): Counter = Counter()
    11	    }
    12	}
    13	
    14	suspend fun fetchValue(): Int = 42
    15	
    16	suspend fun computeTotal(): Int {
    17	    val a = fetchValue()
    18	    val b = fetchValue()
    19	    return a + b
    20	}
    21	
    22	fun useCounter(): Int {
    23	    val c = Counter.create()
    24	    c.increment()
    25	    return Counter.DEFAULT_LIMIT + c.value()
    26	}

exec
/usr/bin/zsh -lc "for f in live-test/python/src/normal.py live-test/python/src/quirks.py; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/python/src/normal.py
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

### live-test/python/src/quirks.py
     1	"""Language-quirk patterns for the live MCP test harness.
     2	
     3	Each function exercises a Python-specific indirection or rebinding pattern.
     4	The target functions are deliberately neutral — the patterns themselves are
     5	what the navigation tools must resolve.
     6	"""
     7	import functools
     8	import operator
     9	
    10	
    11	def quirk_name_rebinding(x: str) -> int:
    12	    fn = int
    13	    return fn(x)
    14	
    15	
    16	def quirk_getattr_module(name: str) -> int:
    17	    fn = getattr(operator, "abs")
    18	    return fn(int(name))
    19	
    20	
    21	def quirk_functools_partial(x: str) -> int:
    22	    coerce = functools.partial(int)
    23	    return coerce(x)
    24	
    25	
    26	def quirk_dict_dispatch(key: str, x: str) -> int:
    27	    dispatch = {"int": int, "abs": lambda v: abs(int(v))}
    28	    return dispatch[key](x)
    29	
    30	
    31	def quirk_lambda_wrap(x: str) -> int:
    32	    coerce = lambda v: int(v)
    33	    return coerce(x)
    34	
    35	
    36	def quirk_list_indexing(x: str) -> int:
    37	    funcs = [int, str, float]
    38	    return funcs[0](x)
    39	
    40	
    41	def quirk_conditional_expr(x: str, use_int: bool) -> int | float:
    42	    fn = int if use_int else float
    43	    return fn(x)
    44	
    45	
    46	def quirk_star_import_simulation(x: str) -> int:
    47	    from operator import abs as a
    48	    return a(int(x))
    49	
    50	
    51	def quirk_decorator_wrap(x: str) -> int:
    52	    def with_logging(fn):
    53	        @functools.wraps(fn)
    54	        def wrapper(*args, **kwargs):
    55	            return fn(*args, **kwargs)
    56	        return wrapper
    57	    wrapped = with_logging(int)
    58	    return wrapped(x)
    59	
    60	
    61	def quirk_class_method(x: str) -> int:
    62	    class Coercer:
    63	        def coerce(self, raw: str) -> int:
    64	            return int(raw)
    65	    return Coercer().coerce(x)
    66	
    67	
    68	def quirk_walrus(x: str) -> int:
    69	    if (result := int(x)):
    70	        return result
    71	    return 0
    72	
    73	
    74	def quirk_unpacking(x: str) -> int:
    75	    fn, *_ = [int, float]
    76	    return fn(x)
    77	
    78	
    79	def quirk_nested_return(x: str) -> int:
    80	    def get_coercer():
    81	        return int
    82	    return get_coercer()(x)
    83	
    84	
    85	def quirk_map_filter(items: list[str]) -> list[int]:
    86	    return list(map(int, items))
    87	
    88	
    89	def quirk_reduce(values: list[str]) -> int:
    90	    return functools.reduce(lambda acc, v: acc + int(v), values, 0)
    91	
    92	
    93	def quirk_chained_getattr(x: str) -> int:
    94	    fn = getattr(getattr(operator, "abs"), "__call__")
    95	    return fn(int(x))
    96	
    97	
    98	def quirk_multiple_assignment(x: str) -> int:
    99	    a = b = int
   100	    return a(x) + b(x)
   101	
   102	
   103	def classify_shape(s):
   104	    """Issue #11: variable-assign in if/else for find_usages coverage."""
   105	    from .normal import Circle, Rectangle
   106	    if isinstance(s, Circle):
   107	        kind = 1
   108	    elif isinstance(s, Rectangle):
   109	        kind = 2
   110	    else:
   111	        kind = 0
   112	    return kind

exec
/usr/bin/zsh -lc "for f in live-test/javascript/src/normal.js live-test/javascript/src/quirks.js live-test/javascript/src/accessors.js live-test/javascript/src/consumer.js live-test/typescript/src/normal.ts live-test/typescript/src/quirks.ts; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/javascript/src/normal.js
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

### live-test/javascript/src/quirks.js
     1	'use strict';
     2	
     3	// Name rebinding
     4	function qRebind(x) {
     5	    const fn = Number.parseInt;
     6	    return fn(x, 10);
     7	}
     8	
     9	// Computed property access
    10	function qComputed(name, x) {
    11	    return Number[name](x, 10);
    12	}
    13	
    14	// Object literal dispatch
    15	function qObjLit(x) {
    16	    return ({ parse: Number.parseInt }).parse(x, 10);
    17	}
    18	
    19	// Conditional expression
    20	function qCond(flag, x) {
    21	    return (flag ? Number.parseInt : Number.parseFloat)(x, 10);
    22	}
    23	
    24	// IIFE returning sink
    25	function qReturned(x) {
    26	    return (() => Number.parseInt)()(x, 10);
    27	}
    28	
    29	// Array-indexed dispatch
    30	function qArrayIdx(x) {
    31	    return [Number.parseInt, Number.parseFloat][0](x, 10);
    32	}
    33	
    34	// Destructured rebind
    35	function qDestructured(x) {
    36	    const { parseInt: p } = Number;
    37	    return p(x, 10);
    38	}
    39	
    40	// Spread/rest unpacking
    41	function qSpread(x) {
    42	    const [fn] = [Number.parseInt];
    43	    return fn(x, 10);
    44	}
    45	
    46	// bind/call/apply
    47	function qBind(x) {
    48	    return Number.parseInt.call(null, x, 10);
    49	}
    50	
    51	// Higher-order forEach
    52	function qForEach(x) {
    53	    const out = [];
    54	    [Number.parseInt].forEach((fn) => out.push(fn(x, 10)));
    55	    return out[0];
    56	}
    57	
    58	// Promise chain
    59	function qPromise(x) {
    60	    return Promise.resolve(Number.parseInt).then((fn) => fn(x, 10));
    61	}
    62	
    63	// async/await wrapping
    64	async function qAwait(x) {
    65	    const fn = await (async () => Number.parseInt)();
    66	    return fn(x, 10);
    67	}
    68	
    69	// Optional chaining
    70	function qOpt(x) {
    71	    return Number?.parseInt(x, 10);
    72	}
    73	
    74	// Nullish-coalesced sink
    75	function qNullish(x) {
    76	    return (Number.parseInt ?? (() => 0))(x, 10);
    77	}
    78	
    79	// Re-export proxy
    80	const proxy = { parse: Number.parseInt };
    81	function qProxy(x) {
    82	    return proxy.parse(x, 10);
    83	}
    84	
    85	module.exports = {
    86	    qRebind, qComputed, qObjLit, qCond, qReturned, qArrayIdx,
    87	    qDestructured, qSpread, qBind, qForEach, qPromise, qAwait,
    88	    qOpt, qNullish, qProxy,
    89	};

### live-test/javascript/src/accessors.js
     1	'use strict';
     2	const { Shape } = require('./normal');
     3	
     4	class Box extends Shape {
     5	    constructor(s) {
     6	        super();
     7	        this.s = s;
     8	    }
     9	    get area() {
    10	        return this.s * this.s;
    11	    }
    12	}
    13	
    14	module.exports = { Box };

### live-test/javascript/src/consumer.js
     1	'use strict';
     2	const { Circle, makeDefaultShapes } = require('./normal');
     3	
     4	function use() {
     5	    return new Circle(2).area() + makeDefaultShapes().length;
     6	}
     7	
     8	module.exports = { use };

### live-test/typescript/src/normal.ts
     1	export interface Drawable {
     2	    draw(): string;
     3	}
     4	
     5	export abstract class Shape {
     6	    abstract area(): number;
     7	
     8	    describe(): string {
     9	        return `${this.constructor.name} with area ${this.area()}`;
    10	    }
    11	}
    12	
    13	export class Circle extends Shape implements Drawable {
    14	    constructor(public readonly radius: number) {
    15	        super();
    16	    }
    17	    area(): number { return 3.14159 * this.radius * this.radius; }
    18	    draw(): string { return `circle r=${this.radius}`; }
    19	}
    20	
    21	export class Rectangle extends Shape implements Drawable {
    22	    constructor(public readonly width: number, public readonly height: number) {
    23	        super();
    24	    }
    25	    area(): number { return this.width * this.height; }
    26	    draw(): string { return `rect ${this.width}x${this.height}`; }
    27	}
    28	
    29	export class Square extends Rectangle {
    30	    constructor(side: number) {
    31	        super(side, side);
    32	    }
    33	}
    34	
    35	export class ShapeCollection {
    36	    readonly shapes: Shape[] = [];
    37	
    38	    add(shape: Shape): void { this.shapes.push(shape); }
    39	
    40	    totalArea(): number {
    41	        let sum = 0;
    42	        for (const s of this.shapes) sum += s.area();
    43	        return sum;
    44	    }
    45	
    46	    largest(): Shape | null {
    47	        let best: Shape | null = null;
    48	        for (const s of this.shapes) {
    49	            if (best === null || s.area() > best.area()) best = s;
    50	        }
    51	        return best;
    52	    }
    53	}
    54	
    55	export function makeDefaultShapes(): Shape[] {
    56	    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
    57	}

### live-test/typescript/src/quirks.ts
     1	export function qLambda(x: string): number {
     2	    const fn: (s: string) => number = (s) => Number.parseInt(s, 10);
     3	    return fn(x);
     4	}
     5	
     6	export function qFunctionRef(x: string): number {
     7	    const fn = Number.parseInt;
     8	    return fn(x, 10);
     9	}
    10	
    11	export function qGenericLambda<T extends string>(x: T): number {
    12	    const fn = (s: T): number => Number.parseInt(s, 10);
    13	    return fn(x);
    14	}
    15	
    16	export function qConditionalType<T extends "int" | "float">(mode: T, x: string): number {
    17	    type Fn = T extends "int" ? typeof Number.parseInt : typeof Number.parseFloat;
    18	    const fn = (mode === "int" ? Number.parseInt : Number.parseFloat) as Fn;
    19	    return fn(x, 10);
    20	}
    21	
    22	export function qDispatchMap(key: string, x: string): number {
    23	    const dispatch: Record<string, (s: string) => number> = {
    24	        int: (s) => Number.parseInt(s, 10),
    25	        abs: (s) => Math.abs(Number.parseInt(s, 10)),
    26	    };
    27	    return dispatch[key](x);
    28	}
    29	
    30	export function qOptional(x?: string): number {
    31	    return x?.length ? Number.parseInt(x, 10) : 0;
    32	}
    33	
    34	export function qNonNullAssertion(x: string | undefined): number {
    35	    return Number.parseInt(x!, 10);
    36	}
    37	
    38	export function qAsCast(x: unknown): number {
    39	    return Number.parseInt(x as string, 10);
    40	}
    41	
    42	export interface Coercer { coerce(x: string): number; }
    43	
    44	export const intCoercer: Coercer = {
    45	    coerce(x: string) { return Number.parseInt(x, 10); }
    46	};
    47	
    48	export function qInterfaceDispatch(c: Coercer, x: string): number {
    49	    return c.coerce(x);
    50	}
    51	
    52	export class TypedCoercer<T extends string> {
    53	    coerce(x: T): number { return Number.parseInt(x, 10); }
    54	}
    55	
    56	export function qGenericClass(x: string): number {
    57	    return new TypedCoercer<string>().coerce(x);
    58	}
    59	
    60	export type Coerce = (s: string) => number;
    61	
    62	export const aliasedCoerce: Coerce = (s) => Number.parseInt(s, 10);
    63	
    64	export function qTypeAlias(x: string): number {
    65	    return aliasedCoerce(x);
    66	}

exec
/usr/bin/zsh -lc "for f in live-test/php/src/Normal.php live-test/php/src/Quirks.php live-test/php/src/Modern.php; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/php/src/Normal.php
     1	<?php
     2	namespace Demo;
     3	
     4	interface Drawable {
     5	    public function draw(): string;
     6	}
     7	
     8	abstract class Shape {
     9	    abstract public function area(): float;
    10	
    11	    public function describe(): string {
    12	        return get_class($this) . " with area " . $this->area();
    13	    }
    14	}
    15	
    16	class Circle extends Shape implements Drawable {
    17	    public function __construct(public readonly float $radius) {}
    18	
    19	    public function area(): float {
    20	        return 3.14159 * $this->radius * $this->radius;
    21	    }
    22	
    23	    public function draw(): string {
    24	        return "circle r={$this->radius}";
    25	    }
    26	}
    27	
    28	class Rectangle extends Shape implements Drawable {
    29	    public function __construct(public readonly float $width, public readonly float $height) {}
    30	
    31	    public function area(): float {
    32	        return $this->width * $this->height;
    33	    }
    34	
    35	    public function draw(): string {
    36	        return "rect {$this->width}x{$this->height}";
    37	    }
    38	}
    39	
    40	class Square extends Rectangle {
    41	    public function __construct(float $side) {
    42	        parent::__construct($side, $side);
    43	    }
    44	}
    45	
    46	class ShapeCollection {
    47	    /** @var Shape[] */
    48	    public array $shapes = [];
    49	
    50	    public function add(Shape $shape): void {
    51	        $this->shapes[] = $shape;
    52	    }
    53	
    54	    public function totalArea(): float {
    55	        $sum = 0.0;
    56	        foreach ($this->shapes as $s) {
    57	            $sum += $s->area();
    58	        }
    59	        return $sum;
    60	    }
    61	
    62	    public function largest(): ?Shape {
    63	        $best = null;
    64	        foreach ($this->shapes as $s) {
    65	            if ($best === null || $s->area() > $best->area()) {
    66	                $best = $s;
    67	            }
    68	        }
    69	        return $best;
    70	    }
    71	}
    72	
    73	function makeDefaultShapes(): array {
    74	    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
    75	}

### live-test/php/src/Quirks.php
     1	<?php
     2	namespace Demo;
     3	
     4	class Quirks {
     5	
     6	    public static function qNameRebind(string $x): int {
     7	        $fn = 'intval';
     8	        return $fn($x);
     9	    }
    10	
    11	    public static function qVariableFunction(string $x): int {
    12	        $fname = 'intval';
    13	        return $fname($x);
    14	    }
    15	
    16	    public static function qClosure(string $x): int {
    17	        $coerce = function (string $s): int { return intval($s); };
    18	        return $coerce($x);
    19	    }
    20	
    21	    public static function qArrowFn(string $x): int {
    22	        $coerce = fn(string $s): int => intval($s);
    23	        return $coerce($x);
    24	    }
    25	
    26	    public static function qArrayDispatch(string $key, string $x): int {
    27	        $dispatch = ['int' => 'intval', 'len' => 'strlen'];
    28	        $fn = $dispatch[$key];
    29	        return $fn($x);
    30	    }
    31	
    32	    public static function qCallableArray(string $x): int {
    33	        $callable = [self::class, 'qNameRebind'];
    34	        return call_user_func($callable, $x);
    35	    }
    36	
    37	    public static function qCallUserFunc(string $x): int {
    38	        return call_user_func('intval', $x);
    39	    }
    40	
    41	    public static function qStaticMethodVariable(string $x): int {
    42	        $cls = self::class;
    43	        return $cls::qNameRebind($x);
    44	    }
    45	
    46	    public static function qFromCallable(string $x): int {
    47	        $coerce = \Closure::fromCallable('intval');
    48	        return $coerce($x);
    49	    }
    50	
    51	    public static function qTernary(bool $flag, string $x): int {
    52	        $fn = $flag ? 'intval' : 'strlen';
    53	        return $fn($x);
    54	    }
    55	
    56	    public static function qNullCoalesce(string $x): int {
    57	        $fn = null ?? 'intval';
    58	        return $fn($x);
    59	    }
    60	
    61	    public static function qMatch(string $mode, string $x): int {
    62	        $fn = match ($mode) {
    63	            'int' => fn($s) => intval($s),
    64	            'len' => 'strlen',
    65	            default => fn($s) => 0,
    66	        };
    67	        return $fn($x);
    68	    }
    69	
    70	    public static function qCoerceUsage(Coercer $c, string $x): int {
    71	        return $c->coerce($x);
    72	    }
    73	
    74	    public static function qPromotedRead(\Demo\Circle $c): float {
    75	        return $c->radius;
    76	    }
    77	}
    78	
    79	interface Coercer {
    80	    public function coerce(string $x): int;
    81	}
    82	
    83	class IntCoercer implements Coercer {
    84	    public function coerce(string $x): int { return intval($x); }
    85	}
    86	
    87	class LenCoercer implements Coercer {
    88	    public function coerce(string $x): int { return strlen($x); }
    89	}

### live-test/php/src/Modern.php
     1	<?php
     2	namespace Demo;
     3	
     4	enum Status: string {
     5	    case Active = 'A';
     6	    case Inactive = 'I';
     7	
     8	    public function label(): string {
     9	        return match($this) {
    10	            Status::Active => 'Active',
    11	            Status::Inactive => 'Inactive',
    12	        };
    13	    }
    14	}
    15	
    16	enum Color {
    17	    case Red;
    18	    case Green;
    19	}
    20	
    21	function defaultStatus(): Status {
    22	    return Status::Active;
    23	}

exec
/usr/bin/zsh -lc "for f in live-test/go/normal.go live-test/go/quirks.go live-test/go/embed.go; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/go/normal.go
     1	package main
     2	
     3	import "fmt"
     4	
     5	type Drawable interface {
     6		Draw() string
     7	}
     8	
     9	type Shape interface {
    10		Area() float64
    11		Describe() string
    12	}
    13	
    14	type baseShape struct{}
    15	
    16	func (b baseShape) Describe() string { return "shape with unknown area" }
    17	
    18	type Circle struct {
    19		baseShape
    20		Radius float64
    21	}
    22	
    23	func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
    24	func (c Circle) Describe() string {
    25		return fmt.Sprintf("Circle with area %f", c.Area())
    26	}
    27	func (c Circle) Draw() string { return fmt.Sprintf("circle r=%f", c.Radius) }
    28	
    29	type Rectangle struct {
    30		baseShape
    31		Width, Height float64
    32	}
    33	
    34	func (r Rectangle) Area() float64 { return r.Width * r.Height }
    35	func (r Rectangle) Describe() string {
    36		return fmt.Sprintf("Rectangle with area %f", r.Area())
    37	}
    38	func (r Rectangle) Draw() string { return fmt.Sprintf("rect %fx%f", r.Width, r.Height) }
    39	
    40	type Square struct{ Rectangle }
    41	
    42	func NewSquare(side float64) Square {
    43		return Square{Rectangle: Rectangle{Width: side, Height: side}}
    44	}
    45	
    46	type ShapeCollection struct {
    47		Shapes []Shape
    48	}
    49	
    50	func (sc *ShapeCollection) Add(s Shape) {
    51		sc.Shapes = append(sc.Shapes, s)
    52	}
    53	
    54	func (sc *ShapeCollection) TotalArea() float64 {
    55		sum := 0.0
    56		for _, s := range sc.Shapes {
    57			sum += s.Area()
    58		}
    59		return sum
    60	}
    61	
    62	func (sc *ShapeCollection) Largest() Shape {
    63		var best Shape
    64		for _, s := range sc.Shapes {
    65			if best == nil || s.Area() > best.Area() {
    66				best = s
    67			}
    68		}
    69		return best
    70	}
    71	
    72	func MakeDefaultShapes() []Shape {
    73		return []Shape{
    74			Circle{Radius: 1.0},
    75			Rectangle{Width: 2.0, Height: 3.0},
    76			NewSquare(4.0),
    77		}
    78	}

### live-test/go/quirks.go
     1	package main
     2	
     3	import (
     4		"fmt"
     5		"strconv"
     6		"strings"
     7	)
     8	
     9	// Function variable
    10	func qFnVar(x string) int {
    11		fn := strconv.Atoi
    12		v, _ := fn(x)
    13		return v
    14	}
    15	
    16	// Closure
    17	func qClosure(x string) int {
    18		coerce := func(s string) int {
    19			v, _ := strconv.Atoi(s)
    20			return v
    21		}
    22		return coerce(x)
    23	}
    24	
    25	// Map of functions
    26	func qMapDispatch(key, x string) int {
    27		dispatch := map[string]func(string) int{
    28			"int": func(s string) int {
    29				v, _ := strconv.Atoi(s)
    30				return v
    31			},
    32			"len": func(s string) int { return len(s) },
    33		}
    34		return dispatch[key](x)
    35	}
    36	
    37	// Slice of functions
    38	func qSliceIdx(x string) int {
    39		fns := []func(string) int{
    40			func(s string) int {
    41				v, _ := strconv.Atoi(s)
    42				return v
    43			},
    44			func(s string) int { return len(s) },
    45		}
    46		return fns[0](x)
    47	}
    48	
    49	// Interface dispatch
    50	type Coercer interface {
    51		Coerce(s string) int
    52	}
    53	
    54	type IntCoercer struct{}
    55	
    56	func (IntCoercer) Coerce(s string) int {
    57		v, _ := strconv.Atoi(s)
    58		return v
    59	}
    60	
    61	type LenCoercer struct{}
    62	
    63	func (LenCoercer) Coerce(s string) int { return len(s) }
    64	
    65	func qInterfaceDispatch(c Coercer, x string) int {
    66		return c.Coerce(x)
    67	}
    68	
    69	// Goroutine + channel
    70	func qGoroutine(x string) int {
    71		ch := make(chan int, 1)
    72		go func() {
    73			v, _ := strconv.Atoi(x)
    74			ch <- v
    75		}()
    76		return <-ch
    77	}
    78	
    79	// Defer
    80	func qDefer(x string) (out int) {
    81		defer func() {
    82			v, _ := strconv.Atoi(x)
    83			out = v
    84		}()
    85		return 0
    86	}
    87	
    88	// Method value
    89	func qMethodValue(x string) int {
    90		c := IntCoercer{}
    91		fn := c.Coerce
    92		return fn(x)
    93	}
    94	
    95	// Method expression
    96	func qMethodExpression(x string) int {
    97		fn := IntCoercer.Coerce
    98		return fn(IntCoercer{}, x)
    99	}
   100	
   101	// Variadic
   102	func qVariadic(xs ...string) int {
   103		if len(xs) == 0 {
   104			return 0
   105		}
   106		v, _ := strconv.Atoi(xs[0])
   107		return v
   108	}
   109	
   110	// Type assertion
   111	func qTypeAssertion(x interface{}) int {
   112		s := x.(string)
   113		v, _ := strconv.Atoi(s)
   114		return v
   115	}
   116	
   117	// Type switch
   118	func qTypeSwitch(x interface{}) int {
   119		switch s := x.(type) {
   120		case string:
   121			v, _ := strconv.Atoi(s)
   122			return v
   123		default:
   124			return 0
   125		}
   126	}
   127	
   128	// Naked print to keep package "used"
   129	func qPrintToUpper(x string) {
   130		fmt.Println(strings.ToUpper(x))
   131	}

### live-test/go/embed.go
     1	package main
     2	
     3	import "fmt"
     4	
     5	type Labeled struct {
     6		baseShape
     7		label string
     8	}
     9	
    10	func (l Labeled) Note() string {
    11		return fmt.Sprintf("%s: %s", l.label, l.Describe())
    12	}

exec
/usr/bin/zsh -lc "for f in live-test/rust/src/normal.rs live-test/rust/src/quirks.rs live-test/rust/src/extra.rs live-test/rust/src/lib.rs; do printf '\\n### %s\\n' \""'$f"; nl -ba "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:

### live-test/rust/src/normal.rs
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

### live-test/rust/src/quirks.rs
     1	pub fn q_closure(x: &str) -> i32 {
     2	    let coerce = |s: &str| s.parse::<i32>().unwrap_or(0);
     3	    coerce(x)
     4	}
     5	
     6	pub fn q_fn_pointer(x: &str) -> i32 {
     7	    let coerce: fn(&str) -> i32 = parse_or_zero;
     8	    coerce(x)
     9	}
    10	
    11	fn parse_or_zero(s: &str) -> i32 {
    12	    s.parse().unwrap_or(0)
    13	}
    14	
    15	pub fn q_box_dyn_fn(x: &str) -> i32 {
    16	    let coerce: Box<dyn Fn(&str) -> i32> = Box::new(|s| s.parse().unwrap_or(0));
    17	    coerce(x)
    18	}
    19	
    20	pub fn q_match_dispatch(mode: &str, x: &str) -> i32 {
    21	    match mode {
    22	        "int" => x.parse().unwrap_or(0),
    23	        "len" => x.len() as i32,
    24	        _ => 0,
    25	    }
    26	}
    27	
    28	pub trait Coercer {
    29	    fn coerce(&self, x: &str) -> i32;
    30	}
    31	
    32	pub struct IntCoercer;
    33	impl Coercer for IntCoercer {
    34	    fn coerce(&self, x: &str) -> i32 {
    35	        x.parse().unwrap_or(0)
    36	    }
    37	}
    38	
    39	pub struct LenCoercer;
    40	impl Coercer for LenCoercer {
    41	    fn coerce(&self, x: &str) -> i32 {
    42	        x.len() as i32
    43	    }
    44	}
    45	
    46	pub fn q_trait_object(c: &dyn Coercer, x: &str) -> i32 {
    47	    c.coerce(x)
    48	}
    49	
    50	pub fn q_generic_bound<C: Coercer>(c: &C, x: &str) -> i32 {
    51	    c.coerce(x)
    52	}
    53	
    54	pub fn q_impl_trait_arg(c: impl Coercer, x: &str) -> i32 {
    55	    c.coerce(x)
    56	}
    57	
    58	pub fn q_impl_trait_return(use_int: bool) -> Box<dyn Coercer> {
    59	    if use_int {
    60	        Box::new(IntCoercer)
    61	    } else {
    62	        Box::new(LenCoercer)
    63	    }
    64	}
    65	
    66	pub enum CoerceMode {
    67	    Int,
    68	    Len,
    69	}
    70	
    71	impl CoerceMode {
    72	    pub fn apply(&self, x: &str) -> i32 {
    73	        match self {
    74	            CoerceMode::Int => x.parse().unwrap_or(0),
    75	            CoerceMode::Len => x.len() as i32,
    76	        }
    77	    }
    78	}
    79	
    80	pub fn q_enum_dispatch(mode: CoerceMode, x: &str) -> i32 {
    81	    mode.apply(x)
    82	}
    83	
    84	pub fn q_iter_map(xs: &[&str]) -> Vec<i32> {
    85	    xs.iter().map(|s| s.parse().unwrap_or(0)).collect()
    86	}
    87	
    88	pub fn q_iter_filter_map(xs: &[&str]) -> Vec<i32> {
    89	    xs.iter().filter_map(|s| s.parse().ok()).collect()
    90	}
    91	
    92	pub fn q_iter_fold(xs: &[&str]) -> i32 {
    93	    xs.iter().fold(0, |acc, s| acc + s.parse::<i32>().unwrap_or(0))
    94	}
    95	
    96	pub fn q_question_mark(x: &str) -> Result<i32, std::num::ParseIntError> {
    97	    let v: i32 = x.parse()?;
    98	    Ok(v)
    99	}
   100	
   101	pub fn q_if_let(x: Option<&str>) -> i32 {
   102	    if let Some(s) = x {
   103	        s.parse().unwrap_or(0)
   104	    } else {
   105	        0
   106	    }
   107	}

### live-test/rust/src/extra.rs
     1	pub mod inner {
     2	    pub fn nested_helper() -> i32 { 42 }
     3	    pub struct Marker;
     4	}
     5	
     6	pub fn extra_function(s: &str) -> i32 {
     7	    inner::nested_helper() + s.len() as i32
     8	}
     9	
    10	pub fn use_quirks_circle() -> f64 {
    11	    crate::normal::Circle { radius: 1.0 }.radius
    12	}

### live-test/rust/src/lib.rs
     1	pub mod normal;
     2	pub mod quirks;
     3	pub mod extra;

codex
So far I see the expected/output drift is exactly the pre/post normalization shape in many rows, but I’m not assuming all drift is harmless. Next I’m going to run a few mechanical checks: duplicate probe IDs, row-count alignment, remaining absolute paths, and what token each line/column actually lands on.
exec
/usr/bin/zsh -lc "rg -n '(/home/|/Users/|C:|\\\\\\\\Users\\\\\\\\|\\.cache/JetBrains|\\.gradle|\\.local/share|Toolbox|RustRover|PyCharm|PhpStorm|WebStorm|jdk|java.base|typeshed|php\\.jar|kotlin-stdlib|stdlib-local-copy)' live-test/*/expected.jsonl live-test/*/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/kotlin/expected.jsonl:21:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:22:{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:23:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:24:{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:27:{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
live-test/kotlin/expected.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/expected.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/expected.jsonl:61:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/expected.jsonl:62:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/javascript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:7:{"column":13,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
live-test/javascript/expected.jsonl:8:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:10:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/expected.jsonl:71:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/kotlin/output.jsonl:27:{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
live-test/rust/expected.jsonl:6:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:8:{"column":17,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
live-test/rust/expected.jsonl:10:{"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/expected.jsonl:25:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/rust/expected.jsonl:44:{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
live-test/rust/expected.jsonl:45:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
live-test/rust/expected.jsonl:61:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/typescript/expected.jsonl:5:{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/php/expected.jsonl:4:{"column":10,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
live-test/php/expected.jsonl:27:{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/php-impl/lib/php.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
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
live-test/java/output.jsonl:24:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/output.jsonl:25:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:26:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/output.jsonl:27:{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
live-test/java/output.jsonl:28:{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:30:{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/output.jsonl:45:{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
live-test/java/output.jsonl:55:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:70:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/output.jsonl:71:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/output.jsonl:72:{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/output.jsonl:79:{"hierarchy":[{"column":7,"file":"${JDK}!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
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

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json, pathlib, re
root=pathlib.Path('live-test')
langs=['java','kotlin','python','javascript','typescript','php','go','rust']
for lang in langs:
    inp=root/lang/'input.jsonl'
    exp=root/lang/'expected.jsonl'
    out=root/lang/'output.jsonl'
    inputs=[]
    ids={}
    bad=[]
    for n,line in enumerate(inp.read_text().splitlines(),1):
        if not line.strip(): continue
        obj=json.loads(line)
        inputs.append((n,obj))
        ids.setdefault(obj.get('id'),[]).append(n)
    dups={k:v for k,v in ids.items() if len(v)>1}
    print(f'{lang}: inputs={len(inputs)} expected={sum(1 for l in exp.read_text().splitlines() if l.strip()) if exp.exists() else \"missing\"} output={sum(1 for l in out.read_text().splitlines() if l.strip()) if out.exists() else \"missing\"} duplicate_ids={dups or \"none\"}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib
root=pathlib.Path('live-test')
for langdir in sorted(p for p in root.iterdir() if (p/'input.jsonl').exists()):
    exp=(langdir/'expected.jsonl').read_text().splitlines() if (langdir/'expected.jsonl').exists() else []
    out=(langdir/'output.jsonl').read_text().splitlines() if (langdir/'output.jsonl').exists() else []
    diffs=[]
    for i,(e,a) in enumerate(zip(exp,out),1):
        if e"'!=a:
            try:
                eo=json.loads(e); ao=json.loads(a)
            except Exception:
                diffs.append((i,'"'not-json'))
                continue
            diffs.append((i, 'obj-eq' if eo==ao else 'obj-diff'))
    extra=[]
    if len(exp)"'!=len(out): extra.append(f'"'len expected={len(exp)} output={len(out)}')
    print(langdir.name, 'diffs', len(diffs), diffs[:20], extra)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 143ms:
java: inputs=83 expected=83 output=83 duplicate_ids=none
kotlin: inputs=67 expected=67 output=67 duplicate_ids=none
python: inputs=76 expected=76 output=76 duplicate_ids=none
javascript: inputs=71 expected=71 output=71 duplicate_ids=none
typescript: inputs=66 expected=66 output=66 duplicate_ids=none
php: inputs=70 expected=70 output=70 duplicate_ids=none
go: inputs=57 expected=57 output=57 duplicate_ids=none
rust: inputs=65 expected=65 output=65 duplicate_ids=none

 succeeded in 140ms:
go diffs 0 [] []
java diffs 12 [(24, 'obj-diff'), (25, 'obj-diff'), (26, 'obj-diff'), (27, 'obj-diff'), (28, 'obj-diff'), (30, 'obj-diff'), (45, 'obj-diff'), (55, 'obj-diff'), (70, 'obj-diff'), (71, 'obj-diff'), (72, 'obj-diff'), (79, 'obj-diff')] []
javascript diffs 5 [(5, 'obj-diff'), (7, 'obj-diff'), (8, 'obj-diff'), (10, 'obj-diff'), (71, 'obj-diff')] []
kotlin diffs 9 [(21, 'obj-diff'), (22, 'obj-diff'), (23, 'obj-diff'), (24, 'obj-diff'), (27, 'obj-diff'), (47, 'obj-diff'), (48, 'obj-diff'), (61, 'obj-diff'), (62, 'obj-diff')] []
php diffs 2 [(4, 'obj-diff'), (27, 'obj-diff')] []
python diffs 19 [(6, 'obj-diff'), (7, 'obj-diff'), (8, 'obj-diff'), (9, 'obj-diff'), (10, 'obj-diff'), (11, 'obj-diff'), (12, 'obj-diff'), (14, 'obj-diff'), (26, 'obj-diff'), (27, 'obj-diff'), (28, 'obj-diff'), (29, 'obj-diff'), (31, 'obj-diff'), (47, 'obj-diff'), (48, 'obj-diff'), (50, 'obj-diff'), (65, 'obj-diff'), (66, 'obj-diff'), (67, 'obj-diff')] []
rust diffs 7 [(6, 'obj-diff'), (8, 'obj-diff'), (10, 'obj-diff'), (25, 'obj-diff'), (44, 'obj-diff'), (45, 'obj-diff'), (61, 'obj-diff')] []
typescript diffs 1 [(5, 'obj-diff')] []

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib,re
root=pathlib.Path('live-test')
word_re=re.compile(r'[A-Za-z_"'$][A-Za-z0-9_$]*|[A-Za-z_][A-Za-z0-9_]*|'"\\\\?[A-Za-z_][A-Za-z0-9_]*|__construct')
for langdir in sorted(p for p in root.iterdir() if (p/'input.jsonl').exists()):
    print(f'### {langdir.name}')
    for n,line in enumerate((langdir/'input.jsonl').read_text().splitlines(),1):
        if not line.strip(): continue
        e=json.loads(line)
        p=e.get('params',{})
        if 'file' not in p or 'line' not in p or 'column' not in p: continue
        f=langdir/p['file']; ln=p['line']; col=p['column']
        try:
            text=f.read_text().splitlines()[ln-1]
        except Exception as ex:
            print(f'{n}: {e[\"id\"]}: BADFILE {p} {ex}')
            continue
        pos=max(0,col-1)
        ch=text[pos] if pos < len(text) else '<eol>'
        token=''
        for m in re.finditer(r'[A-Za-z_"'$][A-Za-z0-9_$]*|__construct'"', text):
            if m.start() <= pos < m.end():
                token=m.group(0); break
        if not token:
            # nearest token after/before within 2 chars
            near=[]
            for m in re.finditer(r'[A-Za-z_"'$][A-Za-z0-9_$]*|__construct'"', text):
                if abs(m.start()-pos)<=2 or abs(m.end()-1-pos)<=2:
                    near.append(f'{m.group(0)}@{m.start()+1}-{m.end()}')
            print(f'{n}: {e[\"id\"]}: col={col} ch={ch"'!r} NO_TOKEN near={near} :: {text}'"')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 133ms:
### go
### java
### javascript
### kotlin
### php
### python
### rust
### typescript

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib,re
root=pathlib.Path('live-test')
for langdir in sorted(p for p in root.iterdir() if (p/'input.jsonl').exists()):
    print(f'### {langdir.name}')
    for n,line in enumerate((langdir/'input.jsonl').read_text().splitlines(),1):
        if not line.strip(): continue
        e=json.loads(line); p=e.get('params',{})
        if 'file' not in p or 'line' not in p or 'column' not in p: continue
        text=(langdir/p['file']).read_text().splitlines()[p['line']-1]
        pos=p['column']-1
        token=None
        span=None
        for m in re.finditer(r'[A-Za-z_"'$][A-Za-z0-9_$]*|__construct'"', text):
            if m.start() <= pos < m.end(): token=m.group(0); span=(m.start()+1,m.end()); break
        print(f'{n:3} {e[\"id\"]:<42} {p[\"file\"]}:{p[\"line\"]}:{p[\"column\"]} token={token}@{span} line={text.strip()}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 134ms:
### go
  1 def-circle-area                            normal.go:23:18 token=Area@(17, 20) line=func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
  2 def-rectangle-area                         normal.go:34:20 token=Area@(20, 23) line=func (r Rectangle) Area() float64 { return r.Width * r.Height }
  3 def-MakeDefaultShapes                      normal.go:72:6 token=MakeDefaultShapes@(6, 22) line=func MakeDefaultShapes() []Shape {
  4 def-quirks-fnvar-resolve                   quirks.go:12:10 token=fn@(10, 11) line=v, _ := fn(x)
  5 def-quirks-method-value                    quirks.go:91:10 token=Coerce@(10, 15) line=fn := c.Coerce
  6 def-quirks-method-expr                     quirks.go:97:19 token=Coerce@(19, 24) line=fn := IntCoercer.Coerce
  7 def-quirks-type-switch-s                   quirks.go:121:24 token=s@(24, 24) line=v, _ := strconv.Atoi(s)
  8 def-quirks-iface-dispatch                  quirks.go:66:11 token=Coerce@(11, 16) line=return c.Coerce(x)
  9 def-drawable-draw                          normal.go:6:2 token=Draw@(2, 5) line=Draw() string
 10 refs-area-from-totalArea                   normal.go:57:12 token=Area@(12, 15) line=sum += s.Area()
 11 refs-shapes-field                          normal.go:47:2 token=Shapes@(2, 7) line=Shapes []Shape
 12 refs-shape-iface                           normal.go:47:11 token=Shape@(11, 15) line=Shapes []Shape
 13 refs-drawable-draw                         normal.go:6:2 token=Draw@(2, 5) line=Draw() string
 14 refs-shape-collection-add                  normal.go:50:28 token=Add@(28, 30) line=func (sc *ShapeCollection) Add(s Shape) {
 15 type-hier-Circle                           normal.go:18:6 token=Circle@(6, 11) line=type Circle struct {
 16 type-hier-Rectangle                        normal.go:29:6 token=Rectangle@(6, 14) line=type Rectangle struct {
 17 type-hier-Square                           normal.go:40:6 token=Square@(6, 11) line=type Square struct{ Rectangle }
 18 type-hier-baseShape                        normal.go:14:6 token=baseShape@(6, 14) line=type baseShape struct{}
 19 call-hier-MakeDefault                      normal.go:72:6 token=MakeDefaultShapes@(6, 22) line=func MakeDefaultShapes() []Shape {
 20 call-hier-area-callers                     normal.go:23:18 token=Area@(17, 20) line=func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
 28 refs-Drawable-iface                        normal.go:5:6 token=Drawable@(6, 13) line=type Drawable interface {
 29 refs-Circle-struct                         normal.go:18:6 token=Circle@(6, 11) line=type Circle struct {
 30 type-hier-Drawable                         normal.go:5:6 token=Drawable@(6, 13) line=type Drawable interface {
 31 type-hier-ShapeCollection                  normal.go:46:6 token=ShapeCollection@(6, 20) line=type ShapeCollection struct {
 32 call-hier-TotalArea-callees                normal.go:54:28 token=TotalArea@(28, 36) line=func (sc *ShapeCollection) TotalArea() float64 {
 33 call-hier-Describe-callers                 normal.go:11:2 token=Describe@(2, 9) line=Describe() string
 34 call-hier-Draw-callers                     normal.go:6:2 token=Draw@(2, 5) line=Draw() string
 35 call-hier-Largest-callees                  normal.go:62:28 token=Largest@(28, 34) line=func (sc *ShapeCollection) Largest() Shape {
 43 audit-refs-area-libraries-scope            normal.go:23:18 token=Area@(17, 20) line=func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
 48 audit-type-hier-Square-supertypes          normal.go:40:7 token=Square@(6, 11) line=type Square struct{ Rectangle }
 49 audit-type-hier-Shape-subtypes             normal.go:9:6 token=Shape@(6, 10) line=type Shape interface {
 50 audit-call-hier-makeDefault-d3             normal.go:72:6 token=MakeDefaultShapes@(6, 22) line=func MakeDefaultShapes() []Shape {
 55 audit-refs-baseShape-Describe-promoted     normal.go:16:20 token=Describe@(20, 27) line=func (b baseShape) Describe() string { return "shape with unknown area" }
### java
  1 def-circle-area-decl                       src/main/java/demo/Normal.java:26:12 token=area@(12, 15) line=double area() {
  2 def-rectangle-area-decl                    src/main/java/demo/Normal.java:46:12 token=area@(12, 15) line=double area() {
  3 def-makeDefaultShapes-decl                 src/main/java/demo/Normal.java:87:31 token=makeDefaultShapes@(31, 47) line=public static List<Shape> makeDefaultShapes() {
  4 def-parse-string-overload                  src/main/java/demo/Quirks.java:95:16 token=parse@(16, 20) line=return parse("42") + parse("ff", 16);
  5 def-parse-stringint-overload               src/main/java/demo/Quirks.java:95:30 token=parse@(30, 34) line=return parse("42") + parse("ff", 16);
  6 def-CoerceMode-INT-apply                   src/main/java/demo/Quirks.java:78:31 token=apply@(31, 35) line=return CoerceMode.INT.apply(x);
  7 def-quirkVar-coerce                        src/main/java/demo/Quirks.java:18:16 token=coerce@(16, 21) line=return coerce.apply(x);
  8 def-Point-record-decl                      src/main/java/demo/Modern.java:4:19 token=Point@(19, 23) line=public record Point(int x, int y) {
  9 def-Animal-sealed-decl                     src/main/java/demo/Modern.java:8:29 token=Animal@(29, 34) line=public sealed interface Animal permits Cat, Dog {
 10 refs-area-from-totalArea                   src/main/java/demo/Normal.java:72:22 token=area@(22, 25) line=sum += s.area();
 11 refs-shapes-field                          src/main/java/demo/Normal.java:63:33 token=shapes@(31, 36) line=private final List<Shape> shapes = new ArrayList<>();
 12 refs-shape-class                           src/main/java/demo/Normal.java:10:16 token=Shape@(16, 20) line=abstract class Shape {
 13 refs-Circle-ctor                           src/main/java/demo/Normal.java:21:5 token=Circle@(5, 10) line=Circle(double radius) {
 14 impls-shape-area                           src/main/java/demo/Normal.java:11:21 token=area@(21, 24) line=abstract double area();
 15 impls-drawable-draw                        src/main/java/demo/Normal.java:7:12 token=draw@(12, 15) line=String draw();
 16 impls-functional-Coerce                    src/main/java/demo/Quirks.java:64:15 token=Coerce@(15, 20) line=interface Coerce { int run(String s); }
 17 impls-Coerce-via-lambda                    src/main/java/demo/Quirks.java:68:18 token=run@(18, 20) line=return c.run(x);
 18 impls-CoerceMode-apply                     src/main/java/demo/Quirks.java:74:22 token=apply@(22, 26) line=abstract int apply(String s);
 19 impls-coerce-run                           src/main/java/demo/Quirks.java:64:28 token=run@(28, 30) line=interface Coerce { int run(String s); }
 20 impls-Animal-name                          src/main/java/demo/Modern.java:9:16 token=name@(16, 19) line=String name();
 21 super-circle-area                          src/main/java/demo/Normal.java:26:12 token=area@(12, 15) line=double area() {
 22 super-rectangle-area                       src/main/java/demo/Normal.java:46:12 token=area@(12, 15) line=double area() {
 23 super-Circle-draw                          src/main/java/demo/Normal.java:31:19 token=draw@(19, 22) line=public String draw() {
 24 type-hier-square                           src/main/java/demo/Normal.java:56:7 token=Square@(7, 12) line=class Square extends Rectangle {
 25 type-hier-rectangle                        src/main/java/demo/Normal.java:36:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape implements Drawable {
 26 type-hier-shape                            src/main/java/demo/Normal.java:10:16 token=Shape@(16, 20) line=abstract class Shape {
 27 type-hier-CoerceMode                       src/main/java/demo/Quirks.java:71:10 token=CoerceMode@(10, 19) line=enum CoerceMode {
 28 type-hier-Point                            src/main/java/demo/Modern.java:4:19 token=Point@(19, 23) line=public record Point(int x, int y) {
 29 type-hier-Animal                           src/main/java/demo/Modern.java:8:29 token=Animal@(29, 34) line=public sealed interface Animal permits Cat, Dog {
 30 call-hier-makeDefault                      src/main/java/demo/Normal.java:87:31 token=makeDefaultShapes@(31, 47) line=public static List<Shape> makeDefaultShapes() {
 31 call-hier-area-callers                     src/main/java/demo/Normal.java:11:21 token=area@(21, 24) line=abstract double area();
 32 call-hier-parse-string-callers             src/main/java/demo/Quirks.java:86:23 token=parse@(23, 27) line=public static int parse(String s) {
 42 def-Circle-via-new-call                    src/main/java/demo/Normal.java:89:24 token=Circle@(24, 29) line=shapes.add(new Circle(1.0));
 43 def-Rectangle-via-new-call                 src/main/java/demo/Normal.java:90:24 token=Rectangle@(24, 32) line=shapes.add(new Rectangle(2.0, 3.0));
 44 def-Point-via-new-record-call              src/main/java/demo/Modern.java:21:23 token=Point@(23, 27) line=Point p = new Point(3, 4);
 45 def-ArrayList-via-new-jdk-call             src/main/java/demo/Normal.java:88:34 token=ArrayList@(34, 42) line=List<Shape> shapes = new ArrayList<>();
 46 refs-Circle-via-new-call-site              src/main/java/demo/Normal.java:89:24 token=Circle@(24, 29) line=shapes.add(new Circle(1.0));
 49 refs-Drawable-interface                    src/main/java/demo/Normal.java:6:11 token=Drawable@(11, 18) line=interface Drawable {
 50 refs-Rectangle-class                       src/main/java/demo/Normal.java:36:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape implements Drawable {
 51 super-Rectangle-draw                       src/main/java/demo/Normal.java:51:19 token=draw@(19, 22) line=public String draw() {
 52 super-Cat-name                             src/main/java/demo/Modern.java:13:33 token=name@(33, 36) line=@Override public String name() { return "cat"; }
 53 super-Dog-name                             src/main/java/demo/Modern.java:17:33 token=name@(33, 36) line=@Override public String name() { return "dog"; }
 54 type-hier-Drawable                         src/main/java/demo/Normal.java:6:11 token=Drawable@(11, 18) line=interface Drawable {
 55 type-hier-Circle                           src/main/java/demo/Normal.java:18:7 token=Circle@(7, 12) line=class Circle extends Shape implements Drawable {
 56 call-hier-totalArea-callees                src/main/java/demo/Normal.java:69:12 token=totalArea@(12, 20) line=double totalArea() {
 57 call-hier-describe-callers                 src/main/java/demo/Normal.java:13:12 token=describe@(12, 19) line=String describe() {
 58 call-hier-largest-callees                  src/main/java/demo/Normal.java:77:11 token=largest@(11, 17) line=Shape largest() {
 65 audit-refs-area-libraries-scope            src/main/java/demo/Normal.java:11:21 token=area@(21, 24) line=abstract double area();
 70 audit-type-hier-Square-supertypes          src/main/java/demo/Normal.java:56:7 token=Square@(7, 12) line=class Square extends Rectangle {
 71 audit-type-hier-Shape-subtypes             src/main/java/demo/Normal.java:10:16 token=Shape@(16, 20) line=abstract class Shape {
 72 audit-call-hier-makeDefault-d3             src/main/java/demo/Normal.java:87:31 token=makeDefaultShapes@(31, 47) line=public static List<Shape> makeDefaultShapes() {
 77 audit-def-classifyShape-kind-decl          src/main/java/demo/Normal.java:97:13 token=kind@(13, 16) line=int kind;
 78 audit-refs-classifyShape-kind              src/main/java/demo/Normal.java:97:13 token=kind@(13, 16) line=int kind;
 79 audit-super-anon-apply                     src/main/java/demo/Quirks.java:24:28 token=apply@(28, 32) line=public Integer apply(String s) {
 80 audit-super-CoerceMode-INT-apply           src/main/java/demo/Quirks.java:72:19 token=apply@(19, 23) line=INT { int apply(String s) { return Integer.parseInt(s); } },
### javascript
  1 def-circle-area                            src/normal.js:17:5 token=area@(5, 8) line=area() { return 3.14159 * this.radius * this.radius; }
  2 def-rectangle-area                         src/normal.js:27:5 token=area@(5, 8) line=area() { return this.width * this.height; }
  3 def-makeDefaultShapes                      src/normal.js:56:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes() {
  4 def-quirks-fn-localbind                    src/quirks.js:6:12 token=fn@(12, 13) line=return fn(x, 10);
  5 def-quirks-parseInt-property-on-Number     src/quirks.js:5:23 token=parseInt@(23, 30) line=const fn = Number.parseInt;
  6 def-quirks-parseint-destructured           src/quirks.js:37:12 token=p@(12, 12) line=return p(x, 10);
  7 def-quirks-destructured-Number-receiver    src/quirks.js:36:29 token=Number@(29, 34) line=const { parseInt: p } = Number;
  8 def-quirks-parseint-optional               src/quirks.js:71:20 token=parseInt@(20, 27) line=return Number?.parseInt(x, 10);
  9 def-quirks-proxy-call                      src/quirks.js:82:18 token=parse@(18, 22) line=return proxy.parse(x, 10);
 10 def-quirks-proxy-call-source               src/quirks.js:80:31 token=parseInt@(31, 38) line=const proxy = { parse: Number.parseInt };
 11 refs-area-from-totalArea                   src/normal.js:8:5 token=area@(5, 8) line=area() { throw new Error('abstract'); }
 12 refs-shapes-field                          src/normal.js:39:14 token=shapes@(14, 19) line=this.shapes = [];
 13 refs-shape-class                           src/normal.js:7:7 token=Shape@(7, 11) line=class Shape {
 14 impls-shape-area-direct-overrides-only     src/normal.js:8:5 token=area@(5, 8) line=area() { throw new Error('abstract'); }
 15 impls-Drawable-draw                        src/normal.js:4:5 token=draw@(5, 8) line=draw() { throw new Error('not implemented'); }
 16 super-circle-area                          src/normal.js:17:5 token=area@(5, 8) line=area() { return 3.14159 * this.radius * this.radius; }
 17 super-rectangle-area                       src/normal.js:27:5 token=area@(5, 8) line=area() { return this.width * this.height; }
 18 type-hier-square                           src/normal.js:31:7 token=Square@(7, 12) line=class Square extends Rectangle {
 19 type-hier-rectangle                        src/normal.js:21:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape {
 20 type-hier-shape                            src/normal.js:7:7 token=Shape@(7, 11) line=class Shape {
 21 call-hier-makeDefault                      src/normal.js:56:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes() {
 22 call-hier-area-callers                     src/normal.js:8:5 token=area@(5, 8) line=area() { throw new Error('abstract'); }
 23 call-hier-qAwait-callees                   src/quirks.js:64:16 token=qAwait@(16, 21) line=async function qAwait(x) {
 30 def-Circle-via-new-call                    src/normal.js:57:17 token=Circle@(17, 22) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 31 def-Rectangle-via-new-call                 src/normal.js:57:34 token=Rectangle@(34, 42) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 32 refs-Circle-via-new-call-site              src/normal.js:57:17 token=Circle@(17, 22) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 33 refs-Drawable-class                        src/normal.js:3:7 token=Drawable@(7, 14) line=class Drawable {
 34 refs-Rectangle-class                       src/normal.js:21:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape {
 35 refs-describe-method                       src/normal.js:9:5 token=describe@(5, 12) line=describe() { return `${this.constructor.name} with area ${this.area()}`; }
 36 impls-Shape-class                          src/normal.js:7:7 token=Shape@(7, 11) line=class Shape {
 37 impls-Shape-describe                       src/normal.js:9:5 token=describe@(5, 12) line=describe() { return `${this.constructor.name} with area ${this.area()}`; }
 38 impls-Rectangle-class                      src/normal.js:21:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape {
 39 impls-Circle-class                         src/normal.js:12:7 token=Circle@(7, 12) line=class Circle extends Shape {
 40 super-Circle-draw                          src/normal.js:18:5 token=draw@(5, 8) line=draw() { return `circle r=${this.radius}`; }
 41 super-Rectangle-draw                       src/normal.js:28:5 token=draw@(5, 8) line=draw() { return `rect ${this.width}x${this.height}`; }
 42 type-hier-Drawable                         src/normal.js:3:7 token=Drawable@(7, 14) line=class Drawable {
 43 type-hier-Circle                           src/normal.js:12:7 token=Circle@(7, 12) line=class Circle extends Shape {
 44 type-hier-ShapeCollection                  src/normal.js:37:7 token=ShapeCollection@(7, 21) line=class ShapeCollection {
 45 call-hier-totalArea-callees                src/normal.js:42:5 token=totalArea@(5, 13) line=totalArea() {
 46 call-hier-describe-callers                 src/normal.js:9:5 token=describe@(5, 12) line=describe() { return `${this.constructor.name} with area ${this.area()}`; }
 47 call-hier-largest-callees                  src/normal.js:47:5 token=largest@(5, 11) line=largest() {
 56 audit-refs-area-libraries-scope            src/normal.js:17:5 token=area@(5, 8) line=area() { return 3.14159 * this.radius * this.radius; }
 61 audit-type-hier-Square-supertypes          src/normal.js:31:7 token=Square@(7, 12) line=class Square extends Rectangle {
 62 audit-type-hier-Shape-subtypes             src/normal.js:7:7 token=Shape@(7, 11) line=class Shape {
 63 audit-call-hier-makeDefault-d3             src/normal.js:56:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes() {
 68 audit-refs-Circle-cross-file               src/normal.js:12:7 token=Circle@(7, 12) line=class Circle extends Shape {
 70 audit-super-Box-area                       src/accessors.js:9:9 token=area@(9, 12) line=get area() {
 71 audit-def-Number-parseInt-library          src/quirks.js:5:23 token=parseInt@(23, 30) line=const fn = Number.parseInt;
### kotlin
  1 def-circle-area                            src/main/kotlin/demo/Normal.kt:14:18 token=area@(18, 21) line=override fun area(): Double = 3.14159 * radius * radius
  2 def-rectangle-area                         src/main/kotlin/demo/Normal.kt:19:18 token=area@(18, 21) line=override fun area(): Double = width * height
  3 def-makeDefaultShapes                      src/main/kotlin/demo/Normal.kt:37:5 token=makeDefaultShapes@(5, 21) line=fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))
  4 def-extension-coerceTo                     src/main/kotlin/demo/Quirks.kt:29:42 token=coerceTo@(42, 49) line=fun quirkExtensionFn(x: String): Int = x.coerceTo(0)
  5 def-infix-coerceFirst                      src/main/kotlin/demo/Quirks.kt:59:43 token=coerceFirst@(43, 53) line=fun quirkInfix(x: String): Int = (x to 0).coerceFirst()
  6 def-companion-create-call                  src/main/kotlin/demo/Modern.kt:23:21 token=create@(21, 26) line=val c = Counter.create()
  7 def-companion-DEFAULT-LIMIT                src/main/kotlin/demo/Modern.kt:25:20 token=DEFAULT_LIMIT@(20, 32) line=return Counter.DEFAULT_LIMIT + c.value()
  8 def-suspend-fetchValue-call                src/main/kotlin/demo/Modern.kt:17:13 token=fetchValue@(13, 22) line=val a = fetchValue()
  9 refs-area-from-totalArea                   src/main/kotlin/demo/Normal.kt:32:49 token=area@(49, 52) line=fun totalArea(): Double = shapes.sumOf { it.area() }
 10 refs-shapes-field                          src/main/kotlin/demo/Normal.kt:26:9 token=shapes@(9, 14) line=val shapes: MutableList<Shape> = mutableListOf()
 11 refs-shape-class                           src/main/kotlin/demo/Normal.kt:7:16 token=Shape@(16, 20) line=abstract class Shape {
 12 refs-Circle-radius                         src/main/kotlin/demo/Normal.kt:13:18 token=radius@(18, 23) line=class Circle(val radius: Double) : Shape(), Drawable {
 13 refs-coerceFirst-callsite                  src/main/kotlin/demo/Quirks.kt:61:29 token=coerceFirst@(29, 39) line=infix fun Pair<String, Int>.coerceFirst(): Int = this.first.toIntOrNull() ?: this.second
 14 impls-shape-area                           src/main/kotlin/demo/Normal.kt:8:18 token=area@(18, 21) line=abstract fun area(): Double
 15 impls-drawable-draw                        src/main/kotlin/demo/Normal.kt:4:9 token=draw@(9, 12) line=fun draw(): String
 16 impls-coercion                             src/main/kotlin/demo/Quirks.kt:37:14 token=Coercion@(14, 21) line=sealed class Coercion {
 17 impls-Coercion-apply                       src/main/kotlin/demo/Quirks.kt:38:18 token=apply@(18, 22) line=abstract fun apply(x: String): Int
 18 super-circle-area                          src/main/kotlin/demo/Normal.kt:14:18 token=area@(18, 21) line=override fun area(): Double = 3.14159 * radius * radius
 19 super-rectangle-area                       src/main/kotlin/demo/Normal.kt:19:18 token=area@(18, 21) line=override fun area(): Double = width * height
 20 super-Circle-draw-from-Drawable            src/main/kotlin/demo/Normal.kt:15:18 token=draw@(18, 21) line=override fun draw(): String = "circle r=$radius"
 21 type-hier-square                           src/main/kotlin/demo/Normal.kt:23:7 token=Square@(7, 12) line=class Square(side: Double) : Rectangle(side, side)
 22 type-hier-rectangle                        src/main/kotlin/demo/Normal.kt:18:12 token=Rectangle@(12, 20) line=open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
 23 type-hier-shape                            src/main/kotlin/demo/Normal.kt:7:16 token=Shape@(16, 20) line=abstract class Shape {
 24 type-hier-Coercion-sealed                  src/main/kotlin/demo/Quirks.kt:37:14 token=Coercion@(14, 21) line=sealed class Coercion {
 25 call-hier-makeDefault                      src/main/kotlin/demo/Normal.kt:37:5 token=makeDefaultShapes@(5, 21) line=fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))
 26 call-hier-area-callers                     src/main/kotlin/demo/Normal.kt:8:18 token=area@(18, 21) line=abstract fun area(): Double
 27 call-hier-quirkApply-callees               src/main/kotlin/demo/Quirks.kt:13:5 token=quirkApply@(5, 14) line=fun quirkApply(x: String): Int {
 28 call-hier-quirkLet-callees                 src/main/kotlin/demo/Quirks.kt:17:5 token=quirkLet@(5, 12) line=fun quirkLet(x: String?): Int {
 29 call-hier-quirkWith-callees                src/main/kotlin/demo/Quirks.kt:21:5 token=quirkWith@(5, 13) line=fun quirkWith(x: String): Int {
 30 call-hier-computeTotal-callees             src/main/kotlin/demo/Modern.kt:16:13 token=computeTotal@(13, 24) line=suspend fun computeTotal(): Int {
 39 refs-Drawable-interface                    src/main/kotlin/demo/Normal.kt:3:11 token=Drawable@(11, 18) line=interface Drawable {
 40 refs-Rectangle-class                       src/main/kotlin/demo/Normal.kt:18:12 token=Rectangle@(12, 20) line=open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
 41 impls-Shape-class                          src/main/kotlin/demo/Normal.kt:7:16 token=Shape@(16, 20) line=abstract class Shape {
 42 impls-Rectangle-class                      src/main/kotlin/demo/Normal.kt:18:12 token=Rectangle@(12, 20) line=open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
 43 super-Rectangle-draw                       src/main/kotlin/demo/Normal.kt:20:18 token=draw@(18, 21) line=override fun draw(): String = "rect ${width}x$height"
 44 super-IntCoerce-apply                      src/main/kotlin/demo/Quirks.kt:39:50 token=apply@(50, 54) line=object IntCoerce : Coercion() { override fun apply(x: String): Int = x.toInt() }
 45 super-AbsCoerce-apply                      src/main/kotlin/demo/Quirks.kt:40:50 token=apply@(50, 54) line=object AbsCoerce : Coercion() { override fun apply(x: String): Int = Math.abs(x.toInt()) }
 46 type-hier-Drawable                         src/main/kotlin/demo/Normal.kt:3:11 token=Drawable@(11, 18) line=interface Drawable {
 47 type-hier-Circle                           src/main/kotlin/demo/Normal.kt:13:7 token=Circle@(7, 12) line=class Circle(val radius: Double) : Shape(), Drawable {
 48 type-hier-ShapeCollection                  src/main/kotlin/demo/Normal.kt:25:7 token=ShapeCollection@(7, 21) line=class ShapeCollection {
 56 audit-refs-area-libraries-scope            src/main/kotlin/demo/Normal.kt:14:18 token=area@(18, 21) line=override fun area(): Double = 3.14159 * radius * radius
 61 audit-type-hier-Square-supertypes          src/main/kotlin/demo/Normal.kt:23:7 token=Square@(7, 12) line=class Square(side: Double) : Rectangle(side, side)
 62 audit-type-hier-Shape-subtypes             src/main/kotlin/demo/Normal.kt:7:16 token=Shape@(16, 20) line=abstract class Shape {
 63 audit-call-hier-makeDefault-d3             src/main/kotlin/demo/Normal.kt:37:5 token=makeDefaultShapes@(5, 21) line=fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))
### php
  1 def-circle-area                            src/Normal.php:19:21 token=area@(21, 24) line=public function area(): float {
  2 def-rectangle-area                         src/Normal.php:31:21 token=area@(21, 24) line=public function area(): float {
  3 def-makeDefaultShapes                      src/Normal.php:73:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes(): array {
  4 def-match-coerce                           src/Quirks.php:63:32 token=intval@(32, 37) line='int' => fn($s) => intval($s),
  5 def-self-class                             src/Quirks.php:33:22 token=self@(22, 25) line=$callable = [self::class, 'qNameRebind'];
  6 def-promoted-property                      src/Quirks.php:75:20 token=radius@(20, 25) line=return $c->radius;
  7 def-Status-enum-decl                       src/Modern.php:4:6 token=Status@(6, 11) line=enum Status: string {
  8 def-Status-Active-usage                    src/Modern.php:22:20 token=Active@(20, 25) line=return Status::Active;
  9 refs-area-from-totalArea                   src/Normal.php:9:30 token=area@(30, 33) line=abstract public function area(): float;
 10 refs-shapes-field                          src/Normal.php:48:18 token=$shapes@(18, 24) line=public array $shapes = [];
 11 refs-shape-class                           src/Normal.php:8:16 token=Shape@(16, 20) line=abstract class Shape {
 12 refs-Quirks-class                          src/Quirks.php:4:7 token=Quirks@(7, 12) line=class Quirks {
 13 refs-Status-enum                           src/Modern.php:4:6 token=Status@(6, 11) line=enum Status: string {
 14 impls-shape-area                           src/Normal.php:9:30 token=area@(30, 33) line=abstract public function area(): float;
 15 impls-drawable-draw                        src/Normal.php:5:21 token=draw@(21, 24) line=public function draw(): string;
 16 impls-coercer                              src/Quirks.php:79:11 token=Coercer@(11, 17) line=interface Coercer {
 17 impls-coerce-at-usage                      src/Quirks.php:71:20 token=coerce@(20, 25) line=return $c->coerce($x);
 18 super-circle-area                          src/Normal.php:19:21 token=area@(21, 24) line=public function area(): float {
 19 super-rectangle-area                       src/Normal.php:31:21 token=area@(21, 24) line=public function area(): float {
 20 super-Circle-draw                          src/Normal.php:23:21 token=draw@(21, 24) line=public function draw(): string {
 21 super-IntCoercer-coerce                    src/Quirks.php:84:21 token=coerce@(21, 26) line=public function coerce(string $x): int { return intval($x); }
 22 type-hier-square                           src/Normal.php:40:7 token=Square@(7, 12) line=class Square extends Rectangle {
 23 type-hier-rectangle                        src/Normal.php:28:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape implements Drawable {
 24 type-hier-shape                            src/Normal.php:8:16 token=Shape@(16, 20) line=abstract class Shape {
 25 type-hier-Drawable                         src/Normal.php:4:11 token=Drawable@(11, 18) line=interface Drawable {
 26 type-hier-Coercer                          src/Quirks.php:79:11 token=Coercer@(11, 17) line=interface Coercer {
 27 type-hier-Status                           src/Modern.php:4:6 token=Status@(6, 11) line=enum Status: string {
 28 call-hier-makeDefault                      src/Normal.php:73:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes(): array {
 29 call-hier-area-callers                     src/Normal.php:9:30 token=area@(30, 33) line=abstract public function area(): float;
 39 refs-Drawable-interface                    src/Normal.php:4:11 token=Drawable@(11, 18) line=interface Drawable {
 40 refs-Rectangle-class                       src/Normal.php:28:7 token=Rectangle@(7, 15) line=class Rectangle extends Shape implements Drawable {
 41 impls-Shape-class                          src/Normal.php:8:16 token=Shape@(16, 20) line=abstract class Shape {
 42 impls-Shape-describe                       src/Normal.php:11:21 token=describe@(21, 28) line=public function describe(): string {
 43 super-Rectangle-draw                       src/Normal.php:35:21 token=draw@(21, 24) line=public function draw(): string {
 44 super-Square-construct                     src/Normal.php:41:21 token=__construct@(21, 31) line=public function __construct(float $side) {
 45 type-hier-Circle                           src/Normal.php:16:7 token=Circle@(7, 12) line=class Circle extends Shape implements Drawable {
 46 type-hier-ShapeCollection                  src/Normal.php:46:7 token=ShapeCollection@(7, 21) line=class ShapeCollection {
 47 call-hier-totalArea-callees                src/Normal.php:54:21 token=totalArea@(21, 29) line=public function totalArea(): float {
 48 call-hier-describe-callers                 src/Normal.php:11:21 token=describe@(21, 28) line=public function describe(): string {
 49 call-hier-largest-callees                  src/Normal.php:62:21 token=largest@(21, 27) line=public function largest(): ?Shape {
 50 call-hier-draw-callers                     src/Normal.php:5:21 token=draw@(21, 24) line=public function draw(): string;
 58 audit-refs-area-libraries-scope            src/Normal.php:9:30 token=area@(30, 33) line=abstract public function area(): float;
 63 audit-type-hier-Square-supertypes          src/Normal.php:40:7 token=Square@(7, 12) line=class Square extends Rectangle {
 64 audit-type-hier-Shape-subtypes             src/Normal.php:8:16 token=Shape@(16, 20) line=abstract class Shape {
 65 audit-call-hier-makeDefault-d3             src/Normal.php:73:10 token=makeDefaultShapes@(10, 26) line=function makeDefaultShapes(): array {
 70 audit-refs-qNameRebind-callable-array      src/Quirks.php:6:28 token=qNameRebind@(28, 38) line=public static function qNameRebind(string $x): int {
### python
  1 def-circle-from-constructor-call           src/normal.py:64:13 token=Circle@(13, 18) line=return [Circle(1.0), Rectangle(2.0, 3.0), Square(4.0)]
  2 def-Circle-constructor-call                src/normal.py:64:13 token=Circle@(13, 18) line=return [Circle(1.0), Rectangle(2.0, 3.0), Square(4.0)]
  3 def-rectangle-area-decl                    src/normal.py:35:9 token=area@(9, 12) line=def area(self) -> float:
  4 def-shape-describe-decl                    src/normal.py:15:9 token=describe@(9, 16) line=def describe(self) -> str:
  5 def-make-default-shapes-decl               src/normal.py:63:5 token=make_default_shapes@(5, 23) line=def make_default_shapes() -> list[Shape]:
  6 def-int-builtin-via-rebinding-rhs          src/quirks.py:12:10 token=int@(10, 12) line=fn = int
  7 def-super-init                             src/normal.py:44:9 token=super@(9, 13) line=super().__init__(side, side)
  8 def-abstractmethod-decorator               src/normal.py:11:6 token=abstractmethod@(6, 19) line=@abstractmethod
  9 def-dict-dispatch-int                      src/quirks.py:27:26 token=int@(24, 26) line=dispatch = {"int": int, "abs": lambda v: abs(int(v))}
 10 def-lambda-wrap-int                        src/quirks.py:32:24 token=int@(24, 26) line=coerce = lambda v: int(v)
 11 def-list-indexing-int                      src/quirks.py:37:14 token=int@(14, 16) line=funcs = [int, str, float]
 12 def-quirk-rebound-fn                       src/quirks.py:13:12 token=fn@(12, 13) line=return fn(x)
 13 def-walrus-result                          src/quirks.py:70:16 token=result@(16, 21) line=return result
 14 def-multiple-assign-b                      src/quirks.py:100:19 token=b@(19, 19) line=return a(x) + b(x)
 15 def-nested-return-fn                       src/quirks.py:82:12 token=get_coercer@(12, 22) line=return get_coercer()(x)
 16 refs-area-from-describe                    src/normal.py:12:9 token=area@(9, 12) line=def area(self) -> float:
 17 refs-shapes-field                          src/normal.py:49:14 token=shapes@(14, 19) line=self.shapes: list[Shape] = []
 18 refs-shape-class                           src/normal.py:10:7 token=Shape@(7, 11) line=class Shape(ABC):
 19 refs-int-builtin-in-quirks                 src/quirks.py:64:20 token=int@(20, 22) line=return int(raw)
 20 refs-circle-radius                         src/normal.py:21:14 token=radius@(14, 19) line=self.radius = radius
 21 impls-shape-area                           src/normal.py:12:9 token=area@(9, 12) line=def area(self) -> float:
 22 impls-drawable-draw                        src/normal.py:7:9 token=draw@(9, 12) line=def draw(self) -> str: ...
 23 super-Square-init                          src/normal.py:43:9 token=__init__@(9, 16) line=def __init__(self, side: float) -> None:
 24 super-circle-area                          src/normal.py:23:9 token=area@(9, 12) line=def area(self) -> float:
 25 super-rectangle-area                       src/normal.py:35:9 token=area@(9, 12) line=def area(self) -> float:
 26 type-hier-square                           src/normal.py:42:7 token=Square@(7, 12) line=class Square(Rectangle):
 27 type-hier-rectangle                        src/normal.py:30:7 token=Rectangle@(7, 15) line=class Rectangle(Shape):
 28 type-hier-shape                            src/normal.py:10:7 token=Shape@(7, 11) line=class Shape(ABC):
 29 call-hier-make-default                     src/normal.py:63:5 token=make_default_shapes@(5, 23) line=def make_default_shapes() -> list[Shape]:
 30 call-hier-area-callers                     src/normal.py:12:9 token=area@(9, 12) line=def area(self) -> float:
 31 call-hier-total-area                       src/normal.py:54:9 token=total_area@(9, 18) line=def total_area(self) -> float:
 38 refs-Drawable-protocol                     src/normal.py:6:7 token=Drawable@(7, 14) line=class Drawable(Protocol):
 39 refs-Rectangle-class                       src/normal.py:30:7 token=Rectangle@(7, 15) line=class Rectangle(Shape):
 40 impls-Shape-class                          src/normal.py:10:7 token=Shape@(7, 11) line=class Shape(ABC):
 41 impls-Rectangle-class                      src/normal.py:30:7 token=Rectangle@(7, 15) line=class Rectangle(Shape):
 42 impls-Circle-class                         src/normal.py:19:7 token=Circle@(7, 12) line=class Circle(Shape):
 43 impls-Shape-describe                       src/normal.py:15:9 token=describe@(9, 16) line=def describe(self) -> str:
 44 super-Circle-draw                          src/normal.py:26:9 token=draw@(9, 12) line=def draw(self) -> str:
 45 super-Rectangle-draw                       src/normal.py:38:9 token=draw@(9, 12) line=def draw(self) -> str:
 46 type-hier-Drawable                         src/normal.py:6:7 token=Drawable@(7, 14) line=class Drawable(Protocol):
 47 type-hier-Circle                           src/normal.py:19:7 token=Circle@(7, 12) line=class Circle(Shape):
 48 type-hier-ShapeCollection                  src/normal.py:47:7 token=ShapeCollection@(7, 21) line=class ShapeCollection:
 49 call-hier-describe-callers                 src/normal.py:15:9 token=describe@(9, 16) line=def describe(self) -> str:
 50 call-hier-largest-callees                  src/normal.py:57:9 token=largest@(9, 15) line=def largest(self) -> Shape | None:
 51 call-hier-add-callers                      src/normal.py:51:9 token=add@(9, 11) line=def add(self, shape: Shape) -> None:
 60 audit-refs-area-libraries-scope            src/normal.py:12:9 token=area@(9, 12) line=def area(self) -> float:
 65 audit-type-hier-Square-supertypes          src/normal.py:42:7 token=Square@(7, 12) line=class Square(Rectangle):
 66 audit-type-hier-Shape-subtypes             src/normal.py:10:7 token=Shape@(7, 11) line=class Shape(ABC):
 67 audit-call-hier-makeDefault-d3             src/normal.py:63:5 token=make_default_shapes@(5, 23) line=def make_default_shapes() -> list[Shape]:
 72 audit-def-classify-shape-kind              src/quirks.py:107:9 token=kind@(9, 12) line=kind = 1
 73 audit-refs-classify-shape-kind             src/quirks.py:107:9 token=kind@(9, 12) line=kind = 1
 74 audit-super-Circle-draw-protocol           src/normal.py:26:9 token=draw@(9, 12) line=def draw(self) -> str:
 76 audit-refs-import-alias-a                  src/quirks.py:47:33 token=a@(33, 33) line=from operator import abs as a
### rust
  1 def-circle-area                            src/normal.rs:23:48 token=area@(45, 48) line=format!("Circle with area {}", self.area())
  2 def-rectangle-area                         src/normal.rs:44:48 token=area@(48, 51) line=format!("Rectangle with area {}", self.area())
  3 def-make-default-shapes-decl               src/normal.rs:94:8 token=make_default_shapes@(8, 26) line=pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
  4 def-trait-object-coerce                    src/quirks.rs:47:11 token=coerce@(7, 12) line=c.coerce(x)
  5 def-enum-variant-coerce-int                src/quirks.rs:74:25 token=Int@(25, 27) line=CoerceMode::Int => x.parse().unwrap_or(0),
  6 def-q-stdlib-parse                         src/quirks.rs:97:21 token=parse@(20, 24) line=let v: i32 = x.parse()?;
  7 def-q-fn-pointer                           src/quirks.rs:7:35 token=parse_or_zero@(35, 47) line=let coerce: fn(&str) -> i32 = parse_or_zero;
  8 def-q-box-dyn-fn                           src/quirks.rs:16:25 token=Fn@(25, 26) line=let coerce: Box<dyn Fn(&str) -> i32> = Box::new(|s| s.parse().unwrap_or(0));
  9 def-CoerceMode-apply                       src/quirks.rs:81:10 token=apply@(10, 14) line=mode.apply(x)
 10 def-q-iter-parse                           src/quirks.rs:85:25 token=parse@(25, 29) line=xs.iter().map(|s| s.parse().unwrap_or(0)).collect()
 11 def-drawable-draw-decl                     src/normal.rs:2:8 token=draw@(8, 11) line=fn draw(&self) -> String;
 12 def-cross-mod-helper                       src/extra.rs:7:12 token=nested_helper@(12, 24) line=inner::nested_helper() + s.len() as i32
 13 def-cross-file-Circle                      src/extra.rs:11:20 token=Circle@(20, 25) line=crate::normal::Circle { radius: 1.0 }.radius
 14 refs-area-from-total                       src/normal.rs:84:38 token=area@(38, 41) line=self.shapes.iter().map(|s| s.area()).sum()
 15 refs-shapes-field                          src/normal.rs:71:9 token=shapes@(9, 14) line=pub shapes: Vec<Box<dyn Shape>>,
 16 refs-shape-trait                           src/normal.rs:5:11 token=Shape@(11, 15) line=pub trait Shape {
 17 refs-circle-radius                         src/normal.rs:14:9 token=radius@(9, 14) line=pub radius: f64,
 18 impls-shape-area                           src/normal.rs:6:8 token=area@(8, 11) line=fn area(&self) -> f64;
 19 impls-drawable-draw                        src/normal.rs:2:8 token=draw@(8, 11) line=fn draw(&self) -> String;
 20 impls-coercer                              src/quirks.rs:29:11 token=coerce@(8, 13) line=fn coerce(&self, x: &str) -> i32;
 21 impls-generic-bound-coercer                src/quirks.rs:50:27 token=Coercer@(27, 33) line=pub fn q_generic_bound<C: Coercer>(c: &C, x: &str) -> i32 {
 22 type-hier-circle                           src/normal.rs:13:12 token=Circle@(12, 17) line=pub struct Circle {
 23 type-hier-rectangle                        src/normal.rs:33:12 token=Rectangle@(12, 20) line=pub struct Rectangle {
 24 type-hier-shape-trait                      src/normal.rs:5:11 token=Shape@(11, 15) line=pub trait Shape {
 25 call-hier-make-default                     src/normal.rs:94:8 token=make_default_shapes@(8, 26) line=pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
 26 call-hier-area-callers                     src/normal.rs:6:8 token=area@(8, 11) line=fn area(&self) -> f64;
 36 refs-Drawable-trait                        src/normal.rs:1:11 token=Drawable@(11, 18) line=pub trait Drawable {
 37 refs-Rectangle-struct                      src/normal.rs:33:12 token=Rectangle@(12, 20) line=pub struct Rectangle {
 38 refs-Coercer-trait                         src/quirks.rs:28:11 token=Coercer@(11, 17) line=pub trait Coercer {
 39 impls-Shape-trait                          src/normal.rs:5:11 token=Shape@(11, 15) line=pub trait Shape {
 40 impls-Shape-describe                       src/normal.rs:8:8 token=describe@(8, 15) line=fn describe(&self) -> String {
 41 type-hier-Drawable                         src/normal.rs:1:11 token=Drawable@(11, 18) line=pub trait Drawable {
 42 type-hier-Square                           src/normal.rs:54:12 token=Square@(12, 17) line=pub struct Square {
 43 type-hier-CoerceMode                       src/quirks.rs:66:10 token=CoerceMode@(10, 19) line=pub enum CoerceMode {
 44 call-hier-total-area-callees               src/normal.rs:83:12 token=total_area@(12, 21) line=pub fn total_area(&self) -> f64 {
 45 call-hier-largest-callees                  src/normal.rs:87:12 token=largest@(12, 18) line=pub fn largest(&self) -> Option<&Box<dyn Shape>> {
 46 call-hier-describe-callers                 src/normal.rs:8:8 token=describe@(8, 15) line=fn describe(&self) -> String {
 47 call-hier-draw-callers                     src/normal.rs:2:8 token=draw@(8, 11) line=fn draw(&self) -> String;
 54 audit-refs-area-libraries-scope            src/normal.rs:18:8 token=area@(8, 11) line=fn area(&self) -> f64 {
 59 audit-type-hier-Square-supertypes          src/normal.rs:54:12 token=Square@(12, 17) line=pub struct Square {
 60 audit-type-hier-Shape-subtypes             src/normal.rs:5:11 token=Shape@(11, 15) line=pub trait Shape {
 61 audit-call-hier-makeDefault-d3             src/normal.rs:94:8 token=make_default_shapes@(8, 26) line=pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
### typescript
  1 def-circle-area                            src/normal.ts:17:5 token=area@(5, 8) line=area(): number { return 3.14159 * this.radius * this.radius; }
  2 def-rectangle-area                         src/normal.ts:25:5 token=area@(5, 8) line=area(): number { return this.width * this.height; }
  3 def-makeDefaultShapes                      src/normal.ts:55:17 token=makeDefaultShapes@(17, 33) line=export function makeDefaultShapes(): Shape[] {
  4 def-quirks-parseint-from-typed-lambda      src/quirks.ts:3:12 token=fn@(12, 13) line=return fn(x);
  5 def-quirks-parseint-arg-cast               src/quirks.ts:39:19 token=parseInt@(19, 26) line=return Number.parseInt(x as string, 10);
  6 def-T-typeparam-use                        src/quirks.ts:53:15 token=T@(15, 15) line=coerce(x: T): number { return Number.parseInt(x, 10); }
  7 refs-area-from-totalArea                   src/normal.ts:6:14 token=area@(14, 17) line=abstract area(): number;
  8 refs-shapes-field                          src/normal.ts:36:14 token=shapes@(14, 19) line=readonly shapes: Shape[] = [];
  9 refs-shape-class                           src/normal.ts:5:23 token=Shape@(23, 27) line=export abstract class Shape {
 10 refs-Coercer-interface                     src/quirks.ts:42:18 token=Coercer@(18, 24) line=export interface Coercer { coerce(x: string): number; }
 11 refs-aliasedCoerce                         src/quirks.ts:62:14 token=aliasedCoerce@(14, 26) line=export const aliasedCoerce: Coerce = (s) => Number.parseInt(s, 10);
 12 refs-Drawable-class                        src/normal.ts:1:18 token=Drawable@(18, 25) line=export interface Drawable {
 13 impls-shape-area                           src/normal.ts:6:14 token=area@(14, 17) line=abstract area(): number;
 14 impls-drawable-draw                        src/normal.ts:2:5 token=draw@(5, 8) line=draw(): string;
 15 impls-coercer                              src/quirks.ts:42:18 token=Coercer@(18, 24) line=export interface Coercer { coerce(x: string): number; }
 16 super-circle-area                          src/normal.ts:17:5 token=area@(5, 8) line=area(): number { return 3.14159 * this.radius * this.radius; }
 17 super-rectangle-area                       src/normal.ts:25:5 token=area@(5, 8) line=area(): number { return this.width * this.height; }
 18 type-hier-square                           src/normal.ts:29:14 token=Square@(14, 19) line=export class Square extends Rectangle {
 19 type-hier-rectangle                        src/normal.ts:21:14 token=Rectangle@(14, 22) line=export class Rectangle extends Shape implements Drawable {
 20 type-hier-shape                            src/normal.ts:5:23 token=Shape@(23, 27) line=export abstract class Shape {
 21 type-hier-TypedCoercer                     src/quirks.ts:52:14 token=TypedCoercer@(14, 25) line=export class TypedCoercer<T extends string> {
 22 type-hier-Drawable                         src/normal.ts:1:18 token=Drawable@(18, 25) line=export interface Drawable {
 23 call-hier-makeDefault                      src/normal.ts:55:17 token=makeDefaultShapes@(17, 33) line=export function makeDefaultShapes(): Shape[] {
 24 call-hier-area-callers                     src/normal.ts:6:14 token=area@(14, 17) line=abstract area(): number;
 32 def-Circle-via-new-call                    src/normal.ts:56:17 token=Circle@(17, 22) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 33 def-Rectangle-via-new-call                 src/normal.ts:56:34 token=Rectangle@(34, 42) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 34 def-TypedCoercer-via-new-generic           src/quirks.ts:57:16 token=TypedCoercer@(16, 27) line=return new TypedCoercer<string>().coerce(x);
 35 refs-Circle-via-new-call-site              src/normal.ts:56:17 token=Circle@(17, 22) line=return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
 36 impls-Shape-class                          src/normal.ts:5:23 token=Shape@(23, 27) line=export abstract class Shape {
 37 impls-Shape-describe                       src/normal.ts:8:5 token=describe@(5, 12) line=describe(): string {
 38 impls-Drawable-interface                   src/normal.ts:1:18 token=Drawable@(18, 25) line=export interface Drawable {
 39 super-Circle-draw                          src/normal.ts:18:5 token=draw@(5, 8) line=draw(): string { return `circle r=${this.radius}`; }
 40 super-Rectangle-draw                       src/normal.ts:26:5 token=draw@(5, 8) line=draw(): string { return `rect ${this.width}x${this.height}`; }
 41 type-hier-Circle                           src/normal.ts:13:14 token=Circle@(14, 19) line=export class Circle extends Shape implements Drawable {
 42 type-hier-ShapeCollection                  src/normal.ts:35:14 token=ShapeCollection@(14, 28) line=export class ShapeCollection {
 43 call-hier-totalArea-callees                src/normal.ts:40:5 token=totalArea@(5, 13) line=totalArea(): number {
 44 call-hier-describe-callers                 src/normal.ts:8:5 token=describe@(5, 12) line=describe(): string {
 45 call-hier-largest-callees                  src/normal.ts:46:5 token=largest@(5, 11) line=largest(): Shape | null {
 46 call-hier-draw-callers                     src/normal.ts:2:5 token=draw@(5, 8) line=draw(): string;
 54 audit-refs-area-libraries-scope            src/normal.ts:6:14 token=area@(14, 17) line=abstract area(): number;
 59 audit-type-hier-Square-supertypes          src/normal.ts:29:14 token=Square@(14, 19) line=export class Square extends Rectangle {
 60 audit-type-hier-Shape-subtypes             src/normal.ts:5:23 token=Shape@(23, 27) line=export abstract class Shape {
 61 audit-call-hier-makeDefault-d3             src/normal.ts:55:17 token=makeDefaultShapes@(17, 33) line=export function makeDefaultShapes(): Shape[] {
 66 audit-type-hier-Coerce-alias               src/quirks.ts:60:13 token=Coerce@(13, 18) line=export type Coerce = (s: string) => number;

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib
root=pathlib.Path('live-test')
no_match_ids={'audit-find-class-no-match','audit-find-symbol-no-match'}
for langdir in sorted(p for p in root.iterdir() if (p/'input.jsonl').exists()):
    inputs=[json.loads(l) for l in (langdir/'input.jsonl').read_text().splitlines() if l.strip()]
    exp=[json.loads(l) for l in (langdir/'expected.jsonl').read_text().splitlines() if l.strip()]
    print(f'### {langdir.name}')
    for i,(inp,out) in enumerate(zip(inputs,exp),1):
        eid=inp['id']; tool=inp['tool']
        empty=[]
        for key in ['usages','references','implementations','subtypes','supertypes','classes','symbols','files','matches','problems','intentions','buildErrors','testResults','hierarchy','calls','children']:
            if key in out and out[key]==[]:
                empty.append(key)
        if empty and 'no-match' not in eid and 'diagnostics' not in eid and 'index-status' not in eid:
            print(f'{i:3} {eid:<45} tool={tool:<28} empty={empty} out_keys={list(out)}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 145ms:
### go
 13 refs-drawable-draw                            tool=ide_find_usages              empty=['usages'] out_keys=['totalCount', 'usages']
 14 refs-shape-collection-add                     tool=ide_find_usages              empty=['usages'] out_keys=['totalCount', 'usages']
 15 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 16 type-hier-Rectangle                           tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 17 type-hier-Square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 18 type-hier-baseShape                           tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 28 refs-Drawable-iface                           tool=ide_find_usages              empty=['usages'] out_keys=['totalCount', 'usages']
 30 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 31 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 33 call-hier-Describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 34 call-hier-Draw-callers                        tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 48 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 49 audit-type-hier-Shape-subtypes                tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
### java
 24 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 28 type-hier-Point                               tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 29 type-hier-Animal                              tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 54 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 55 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 57 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 70 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 80 audit-super-CoerceMode-INT-apply              tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
### javascript
 15 impls-Drawable-draw                           tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 18 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 20 type-hier-shape                               tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 23 call-hier-qAwait-callees                      tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 35 refs-describe-method                          tool=ide_find_usages              empty=['usages'] out_keys=['totalCount', 'usages']
 37 impls-Shape-describe                          tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 39 impls-Circle-class                            tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 40 super-Circle-draw                             tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
 41 super-Rectangle-draw                          tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
 42 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 43 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 44 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 45 call-hier-totalArea-callees                   tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 46 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 47 call-hier-largest-callees                     tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 61 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 62 audit-type-hier-Shape-subtypes                tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
### kotlin
 21 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 25 call-hier-makeDefault                         tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 28 call-hier-quirkLet-callees                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 29 call-hier-quirkWith-callees                   tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 46 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 47 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 48 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 61 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 63 audit-call-hier-makeDefault-d3                tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
### php
 22 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 24 type-hier-shape                               tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 25 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 26 type-hier-Coercer                             tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 27 type-hier-Status                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 28 call-hier-makeDefault                         tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 42 impls-Shape-describe                          tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 45 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 46 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 48 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 50 call-hier-draw-callers                        tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 63 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 64 audit-type-hier-Shape-subtypes                tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 65 audit-call-hier-makeDefault-d3                tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
### python
 22 impls-drawable-draw                           tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 26 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 38 refs-Drawable-protocol                        tool=ide_find_usages              empty=['usages'] out_keys=['totalCount', 'usages']
 42 impls-Circle-class                            tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 43 impls-Shape-describe                          tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 44 super-Circle-draw                             tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
 45 super-Rectangle-draw                          tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
 46 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 47 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 48 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 49 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 51 call-hier-add-callers                         tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 65 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 74 audit-super-Circle-draw-protocol              tool=ide_find_super_methods       empty=['hierarchy'] out_keys=['hierarchy', 'method']
### rust
 22 type-hier-circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 23 type-hier-rectangle                           tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 24 type-hier-shape-trait                         tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 41 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 42 type-hier-Square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 43 type-hier-CoerceMode                          tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 46 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 47 call-hier-draw-callers                        tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 59 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 60 audit-type-hier-Shape-subtypes                tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 62 audit-find-symbol-qualified-IntCoercer-coerce tool=ide_find_symbol              empty=['symbols'] out_keys=['query', 'symbols', 'totalCount']
### typescript
 15 impls-coercer                                 tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 18 type-hier-square                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 20 type-hier-shape                               tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 21 type-hier-TypedCoercer                        tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 22 type-hier-Drawable                            tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 37 impls-Shape-describe                          tool=ide_find_implementations     empty=['implementations'] out_keys=['implementations', 'totalCount']
 41 type-hier-Circle                              tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 42 type-hier-ShapeCollection                     tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 44 call-hier-describe-callers                    tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 46 call-hier-draw-callers                        tool=ide_call_hierarchy           empty=['calls'] out_keys=['calls', 'element']
 59 audit-type-hier-Square-supertypes             tool=ide_type_hierarchy           empty=['subtypes'] out_keys=['element', 'subtypes', 'supertypes']
 60 audit-type-hier-Shape-subtypes                tool=ide_type_hierarchy           empty=['supertypes'] out_keys=['element', 'subtypes', 'supertypes']
 66 audit-type-hier-Coerce-alias                  tool=ide_type_hierarchy           empty=['subtypes', 'supertypes'] out_keys=['element', 'subtypes', 'supertypes']

exec
/usr/bin/zsh -lc "nl -ba live-test/python/input.jsonl | sed -n '1,12p'; nl -ba live-test/python/expected.jsonl | sed -n '1,12p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
     2	{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
     3	{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
     4	{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
     5	{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
     6	{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
     7	{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
     8	{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
     9	{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
    10	{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
    11	{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
    12	{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
     1	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     2	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}
     3	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}
     4	{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}
     5	{"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes","qualifiedName":"normal.make_default_shapes"}
     6	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
     7	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
     8	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/uv/python/cpython-3.11.14-linux-x86_64-gnu/lib/python3.11/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
     9	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    10	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
    11	{"column":7,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
    12	{"column":9,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}

exec
/usr/bin/zsh -lc "nl -ba live-test/rust/input.jsonl | sed -n '50,65p'; nl -ba live-test/rust/expected.jsonl | sed -n '54,65p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
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
    54	{"totalCount":1,"usages":[{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"}]}
    55	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}],"totalCount":5}
    56	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh","totalCount":2}
    57	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle"}],"query":"Circle","totalCount":1}
    58	{"classes":[{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"SC","totalCount":1}
    59	{"element":{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null}]}
    60	{"element":{"column":11,"enclosingScope":null,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle","qualifiedName":"crate::Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle","qualifiedName":"crate::Rectangle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square","qualifiedName":"crate::Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
    62	{"query":"IntCoercer::coerce","symbols":[],"totalCount":0}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh*","totalCount":2}

exec
/usr/bin/zsh -lc "nl -ba live-test/go/input.jsonl | sed -n '45,57p'; nl -ba live-test/go/expected.jsonl | sed -n '45,57p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
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
    57	{"id":"audit-find-class-exact-Circle","tool":"ide_find_class","params":{"query":"Circle","matchMode":"exact"}}
    45	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh","totalCount":2}
    46	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}
    47	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"SC","totalCount":1}
    48	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":5,"name":"Drawable","qualifiedName":"main.Drawable","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null}]}
    49	{"element":{"column":6,"enclosingScope":null,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape","supertypes":null},"subtypes":[{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":29,"name":"Rectangle","qualifiedName":"main.Rectangle","supertypes":null},{"column":6,"enclosingScope":null,"file":"normal.go","kind":"STRUCT","line":40,"name":"Square","qualifiedName":"main.Square","supertypes":null}],"supertypes":[]}
    50	{"calls":[{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":42,"name":"NewSquare","qualifiedName":"main.NewSquare"}],"element":{"children":null,"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}}
    51	{"query":"IntCoercer.Coerce","symbols":[{"column":19,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}],"totalCount":1}
    52	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    53	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    54	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":14,"name":"baseShape","qualifiedName":"main.baseShape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Sh*","totalCount":3}
    55	{"totalCount":1,"usages":[{"column":42,"enclosingScope":["Note"],"file":"embed.go","line":11,"usageType":"REFERENCE"}]}
    56	{"classes":[{"column":6,"file":"normal.go","kind":"INTERFACE","line":9,"name":"Shape","qualifiedName":"main.Shape"},{"column":6,"file":"normal.go","kind":"STRUCT","line":46,"name":"ShapeCollection","qualifiedName":"main.ShapeCollection"}],"query":"Shape","totalCount":2}
    57	{"classes":[{"column":6,"file":"normal.go","kind":"STRUCT","line":18,"name":"Circle","qualifiedName":"main.Circle"}],"query":"Circle","totalCount":1}

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib
for langdir in sorted(p for p in pathlib.Path('live-test').iterdir() if (p/'input.jsonl').exists()):
    inputs=[json.loads(l) for l in (langdir/'input.jsonl').read_text().splitlines() if l.strip()]
    exp=[json.loads(l) for l in (langdir/'expected.jsonl').read_text().splitlines() if l.strip()]
    for i,(inp,out) in enumerate(zip(inputs,exp),1):
        if 'qualified' in inp['id'] or 'wildcard' in inp['id']:
            print(langdir.name, i, inp['id'], inp['params'], out)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 146ms:
go 51 audit-find-symbol-qualified-IntCoercer-Coerce {'query': 'IntCoercer.Coerce'} {'query': 'IntCoercer.Coerce', 'symbols': [{'column': 19, 'file': 'quirks.go', 'kind': 'METHOD', 'line': 56, 'name': 'Coerce', 'qualifiedName': 'main.IntCoercer.Coerce'}], 'totalCount': 1}
go 54 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 6, 'file': 'normal.go', 'kind': 'INTERFACE', 'line': 9, 'name': 'Shape', 'qualifiedName': 'main.Shape'}, {'column': 6, 'file': 'normal.go', 'kind': 'STRUCT', 'line': 14, 'name': 'baseShape', 'qualifiedName': 'main.baseShape'}, {'column': 6, 'file': 'normal.go', 'kind': 'STRUCT', 'line': 46, 'name': 'ShapeCollection', 'qualifiedName': 'main.ShapeCollection'}], 'query': 'Sh*', 'totalCount': 3}
java 73 audit-find-symbol-qualified-Shape-area {'query': 'Shape.area'} {'query': 'Shape.area', 'symbols': [{'column': 21, 'file': 'src/main/java/demo/Normal.java', 'kind': 'METHOD', 'line': 11, 'name': 'area', 'qualifiedName': 'demo.Shape#area'}, {'column': 12, 'file': 'src/main/java/demo/Normal.java', 'kind': 'METHOD', 'line': 69, 'name': 'totalArea', 'qualifiedName': 'demo.ShapeCollection#totalArea'}], 'totalCount': 2}
java 76 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 16, 'file': 'src/main/java/demo/Normal.java', 'kind': 'ABSTRACT_CLASS', 'line': 10, 'name': 'Shape', 'qualifiedName': 'demo.Shape'}, {'column': 7, 'file': 'src/main/java/demo/Normal.java', 'kind': 'CLASS', 'line': 62, 'name': 'ShapeCollection', 'qualifiedName': 'demo.ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
javascript 64 audit-find-symbol-qualified-Shape-area {'query': 'Shape.area'} {'query': 'Shape.area', 'symbols': [{'column': 5, 'file': 'src/normal.js', 'kind': 'METHOD', 'line': 8, 'name': 'area', 'qualifiedName': 'Shape.area'}, {'column': 5, 'file': 'src/normal.js', 'kind': 'METHOD', 'line': 42, 'name': 'totalArea', 'qualifiedName': 'ShapeCollection.totalArea'}], 'totalCount': 2}
javascript 67 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 7, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 7, 'name': 'Shape', 'qualifiedName': 'Shape'}, {'column': 7, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 37, 'name': 'ShapeCollection', 'qualifiedName': 'ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
kotlin 64 audit-find-symbol-qualified-Coercer-coerce {'query': 'Coercer.coerce'} {'query': 'Coercer.coerce', 'symbols': [{'column': 9, 'file': 'src/main/kotlin/demo/Quirks.kt', 'kind': 'FUNCTION', 'line': 46, 'name': 'coerce', 'qualifiedName': 'demo.Coercer#coerce'}], 'totalCount': 1}
kotlin 67 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 16, 'file': 'src/main/kotlin/demo/Normal.kt', 'kind': 'ABSTRACT_CLASS', 'line': 7, 'name': 'Shape', 'qualifiedName': 'demo.Shape'}, {'column': 7, 'file': 'src/main/kotlin/demo/Normal.kt', 'kind': 'CLASS', 'line': 25, 'name': 'ShapeCollection', 'qualifiedName': 'demo.ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
php 66 audit-find-symbol-qualified-Shape-area {'query': 'Shape::area'} {'query': 'Shape::area', 'symbols': [{'column': 30, 'file': 'src/Normal.php', 'kind': 'METHOD', 'line': 9, 'name': 'area', 'qualifiedName': '\\Demo\\Shape::area'}, {'column': 21, 'file': 'src/Normal.php', 'kind': 'METHOD', 'line': 54, 'name': 'totalArea', 'qualifiedName': '\\Demo\\ShapeCollection::totalArea'}], 'totalCount': 2}
php 69 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 16, 'file': 'src/Normal.php', 'kind': 'ABSTRACT_CLASS', 'line': 8, 'name': 'Shape', 'qualifiedName': '\\Demo\\Shape'}, {'column': 7, 'file': 'src/Normal.php', 'kind': 'CLASS', 'line': 46, 'name': 'ShapeCollection', 'qualifiedName': '\\Demo\\ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
python 68 audit-find-symbol-qualified-Coercer-coerce {'query': 'Coercer.coerce'} {'query': 'Coercer.coerce', 'symbols': [{'column': 13, 'file': 'src/quirks.py', 'kind': 'METHOD', 'line': 63, 'name': 'coerce', 'qualifiedName': 'quirks.quirk_class_method'}], 'totalCount': 1}
python 71 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 7, 'file': 'src/normal.py', 'kind': 'CLASS', 'line': 10, 'name': 'Shape', 'qualifiedName': 'normal.Shape'}, {'column': 7, 'file': 'src/normal.py', 'kind': 'CLASS', 'line': 47, 'name': 'ShapeCollection', 'qualifiedName': 'normal.ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
rust 62 audit-find-symbol-qualified-IntCoercer-coerce {'query': 'IntCoercer::coerce'} {'query': 'IntCoercer::coerce', 'symbols': [], 'totalCount': 0}
rust 65 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 11, 'file': 'src/normal.rs', 'kind': 'TRAIT', 'line': 5, 'name': 'Shape', 'qualifiedName': 'crate::Shape'}, {'column': 12, 'file': 'src/normal.rs', 'kind': 'STRUCT', 'line': 70, 'name': 'ShapeCollection', 'qualifiedName': 'crate::ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}
typescript 62 audit-find-symbol-qualified-Shape-area {'query': 'Shape.area'} {'query': 'Shape.area', 'symbols': [{'column': 14, 'file': 'src/normal.ts', 'kind': 'METHOD', 'line': 6, 'name': 'area', 'qualifiedName': 'Shape.area'}, {'column': 5, 'file': 'src/normal.ts', 'kind': 'METHOD', 'line': 40, 'name': 'totalArea', 'qualifiedName': 'ShapeCollection.totalArea'}], 'totalCount': 2}
typescript 65 audit-find-class-Shape-wildcard {'query': 'Sh*', 'matchMode': 'substring'} {'classes': [{'column': 23, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 5, 'name': 'Shape', 'qualifiedName': 'Shape'}, {'column': 14, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 35, 'name': 'ShapeCollection', 'qualifiedName': 'ShapeCollection'}], 'query': 'Sh*', 'totalCount': 2}

exec
/usr/bin/zsh -lc "nl -ba live-test/java/input.jsonl | sed -n '77,81p'; nl -ba live-test/java/expected.jsonl | sed -n '77,81p'; nl -ba live-test/java/output.jsonl | sed -n '77,81p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    77	{"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
    78	{"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
    79	{"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
    80	{"id":"audit-super-CoerceMode-INT-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":72,"column":19}}
    81	{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
    77	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"LOCAL_VARIABLE","line":97,"name":"kind","qualifiedName":"demo.Normal#classifyShape"}
    78	{"totalCount":4,"usages":[{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":99,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":101,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":103,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":105,"usageType":"REFERENCE"}]}
    79	{"hierarchy":[{"column":7,"file":"/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
    80	{"hierarchy":[],"method":{"column":10,"file":"src/main/java/demo/Quirks.java","kind":"CONSTRUCTOR","line":71,"name":"CoerceMode","qualifiedName":"demo.Quirks.CoerceMode#CoerceMode"}}
    81	{"file":"src/main/java/demo/Modern.java","language":"Java","structure":"Modern.java\n\npublic Modern (line 3)\n  public Point (line 4)\n    public sum(): int (line 5)\n    x: int (line 4)\n    y: int (line 4)\n  public sealed Animal (line 8)\n    name(): String (line 9)\n  public static final Cat (line 12)\n    public name(): String (line 13)\n  public static final Dog (line 16)\n    public name(): String (line 17)\n  public static probe(): int (line 20)"}
    77	{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"LOCAL_VARIABLE","line":97,"name":"kind","qualifiedName":"demo.Normal#classifyShape"}
    78	{"totalCount":4,"usages":[{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":99,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":101,"usageType":"REFERENCE"},{"column":13,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":103,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Normal","classifyShape"],"file":"src/main/java/demo/Normal.java","line":105,"usageType":"REFERENCE"}]}
    79	{"hierarchy":[{"column":7,"file":"${JDK}!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}
    80	{"hierarchy":[],"method":{"column":10,"file":"src/main/java/demo/Quirks.java","kind":"CONSTRUCTOR","line":71,"name":"CoerceMode","qualifiedName":"demo.Quirks.CoerceMode#CoerceMode"}}
    81	{"file":"src/main/java/demo/Modern.java","language":"Java","structure":"Modern.java\n\npublic Modern (line 3)\n  public Point (line 4)\n    public sum(): int (line 5)\n    x: int (line 4)\n    y: int (line 4)\n  public sealed Animal (line 8)\n    name(): String (line 9)\n  public static final Cat (line 12)\n    public name(): String (line 13)\n  public static final Dog (line 16)\n    public name(): String (line 17)\n  public static probe(): int (line 20)"}

exec
/usr/bin/zsh -lc "nl -ba live-test/php/input.jsonl | sed -n '63,70p'; nl -ba live-test/php/expected.jsonl | sed -n '63,70p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    63	{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":40,"column":7,"direction":"supertypes"}}
    64	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":8,"column":16,"direction":"subtypes"}}
    65	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":3}}
    66	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}
    67	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    68	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    69	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    70	{"id":"audit-refs-qNameRebind-callable-array","tool":"ide_find_usages","params":{"file":"src/Quirks.php","line":6,"column":28}}
    63	{"element":{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/Normal.php","kind":"INTERFACE","line":4,"name":"Drawable","qualifiedName":"\\Demo\\Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null}]}]}
    64	{"element":{"column":16,"enclosingScope":null,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":16,"name":"Circle","qualifiedName":"\\Demo\\Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":28,"name":"Rectangle","qualifiedName":"\\Demo\\Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/Normal.php","kind":"CLASS","line":40,"name":"Square","qualifiedName":"\\Demo\\Square","supertypes":null}],"supertypes":[]}
    65	{"calls":[],"element":{"children":null,"column":10,"enclosingScope":null,"file":"src/Normal.php","kind":"FUNCTION","line":73,"name":"makeDefaultShapes","qualifiedName":"\\Demo\\makeDefaultShapes"}}
    66	{"query":"Shape::area","symbols":[{"column":30,"file":"src/Normal.php","kind":"METHOD","line":9,"name":"area","qualifiedName":"\\Demo\\Shape::area"},{"column":21,"file":"src/Normal.php","kind":"METHOD","line":54,"name":"totalArea","qualifiedName":"\\Demo\\ShapeCollection::totalArea"}],"totalCount":2}
    67	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    68	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    69	{"classes":[{"column":16,"file":"src/Normal.php","kind":"ABSTRACT_CLASS","line":8,"name":"Shape","qualifiedName":"\\Demo\\Shape"},{"column":7,"file":"src/Normal.php","kind":"CLASS","line":46,"name":"ShapeCollection","qualifiedName":"\\Demo\\ShapeCollection"}],"query":"Sh*","totalCount":2}
    70	{"totalCount":2,"usages":[{"column":35,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":43,"usageType":"REFERENCE"}]}

exec
/usr/bin/zsh -lc "nl -ba live-test/typescript/input.jsonl | sed -n '60,66p'; nl -ba live-test/typescript/expected.jsonl | sed -n '60,66p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    60	{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":5,"column":23,"direction":"subtypes"}}
    61	{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":3}}
    62	{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
    63	{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
    64	{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
    65	{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
    66	{"id":"audit-type-hier-Coerce-alias","tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":60,"column":13}}
    60	{"element":{"column":23,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape","supertypes":null},"subtypes":[{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle","supertypes":null},{"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square","supertypes":null}],"supertypes":[]}
    61	{"calls":[{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"Circle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":21,"name":"Rectangle","qualifiedName":"Rectangle"},{"children":null,"column":14,"enclosingScope":null,"file":"src/normal.ts","kind":"CLASS","line":29,"name":"Square","qualifiedName":"Square"}],"element":{"children":null,"column":17,"enclosingScope":null,"file":"src/normal.ts","kind":"FUNCTION","line":55,"name":"makeDefaultShapes()","qualifiedName":"makeDefaultShapes"}}
    62	{"query":"Shape.area","symbols":[{"column":14,"file":"src/normal.ts","kind":"METHOD","line":6,"name":"area","qualifiedName":"Shape.area"},{"column":5,"file":"src/normal.ts","kind":"METHOD","line":40,"name":"totalArea","qualifiedName":"ShapeCollection.totalArea"}],"totalCount":2}
    63	{"classes":[],"query":"NoSuchClassXyz","totalCount":0}
    64	{"query":"noSuchSymbolXyz","symbols":[],"totalCount":0}
    65	{"classes":[{"column":23,"file":"src/normal.ts","kind":"CLASS","line":5,"name":"Shape","qualifiedName":"Shape"},{"column":14,"file":"src/normal.ts","kind":"CLASS","line":35,"name":"ShapeCollection","qualifiedName":"ShapeCollection"}],"query":"Sh*","totalCount":2}
    66	{"element":{"column":13,"enclosingScope":null,"file":"src/quirks.ts","kind":"TYPE_ALIAS","line":60,"name":"Coerce","qualifiedName":"Coerce","supertypes":null},"subtypes":[],"supertypes":[]}

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib
for langdir in sorted(p for p in pathlib.Path('live-test').iterdir() if (p/'input.jsonl').exists()):
    inputs=[json.loads(l) for l in (langdir/'input.jsonl').read_text().splitlines() if l.strip()]
    exp=[json.loads(l) for l in (langdir/'expected.jsonl').read_text().splitlines() if l.strip()]
    for i,(inp,out) in enumerate(zip(inputs,exp),1):
        if 'call-hier-make' in inp['id'] or inp['id']=='call-hier-makeDefault':
            print(langdir.name, i, inp['id'], 'calls=', len(out.get('calls',[])), 'out=', out if len(str(out))<1000 else str(out)[:1000]+'...')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 155ms:
go 50 audit-call-hier-makeDefault-d3 calls= 1 out= {'calls': [{'children': None, 'column': 6, 'enclosingScope': None, 'file': 'normal.go', 'kind': 'FUNCTION', 'line': 42, 'name': 'NewSquare', 'qualifiedName': 'main.NewSquare'}], 'element': {'children': None, 'column': 6, 'enclosingScope': None, 'file': 'normal.go', 'kind': 'FUNCTION', 'line': 72, 'name': 'MakeDefaultShapes', 'qualifiedName': 'main.MakeDefaultShapes'}}
java 30 call-hier-makeDefault calls= 5 out= {'calls': [{'children': [{'children': None, 'column': 15, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class', 'kind': 'CONSTRUCTOR', 'line': 13, 'name': 'AbstractList.AbstractList()', 'qualifiedName': 'java.util.AbstractList#AbstractList'}], 'column': 12, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class', 'kind': 'CONSTRUCTOR', 'line': 40, 'name': 'ArrayList.ArrayList()', 'qualifiedName': 'java.util.ArrayList#ArrayList()'}, {'children': [{'children': None, 'column': 20, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class', 'kind': 'METHOD', 'line': 100, 'name': 'AbstractCollection.add(E)', 'qualifiedName': 'java.util.AbstractCollection#add'}, {'children': None, 'column': 20, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java...
java 72 audit-call-hier-makeDefault-d3 calls= 5 out= {'calls': [{'children': [{'children': [{'children': None, 'column': 15, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractCollection.class', 'kind': 'CONSTRUCTOR', 'line': 12, 'name': 'AbstractCollection.AbstractCollection()', 'qualifiedName': 'java.util.AbstractCollection#AbstractCollection'}], 'column': 15, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/AbstractList.class', 'kind': 'CONSTRUCTOR', 'line': 13, 'name': 'AbstractList.AbstractList()', 'qualifiedName': 'java.util.AbstractList#AbstractList'}], 'column': 12, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/mise/installs/java/openjdk-21!/java.base/java/util/ArrayList.class', 'kind': 'CONSTRUCTOR', 'line': 40, 'name': 'ArrayList.ArrayList()', 'qualifiedName': 'java.util.ArrayList#ArrayList()'}, {'children': [{'children': [{'children': None, 'column': 20, 'enclosingScope': None, 'file': '/...
javascript 21 call-hier-makeDefault calls= 3 out= {'calls': [{'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 12, 'name': 'Circle', 'qualifiedName': 'Circle'}, {'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 21, 'name': 'Rectangle', 'qualifiedName': 'Rectangle'}, {'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 31, 'name': 'Square', 'qualifiedName': 'Square'}], 'element': {'children': None, 'column': 10, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'FUNCTION', 'line': 56, 'name': 'makeDefaultShapes()', 'qualifiedName': 'makeDefaultShapes'}}
javascript 63 audit-call-hier-makeDefault-d3 calls= 3 out= {'calls': [{'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 12, 'name': 'Circle', 'qualifiedName': 'Circle'}, {'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 21, 'name': 'Rectangle', 'qualifiedName': 'Rectangle'}, {'children': None, 'column': 7, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'CLASS', 'line': 31, 'name': 'Square', 'qualifiedName': 'Square'}], 'element': {'children': None, 'column': 10, 'enclosingScope': None, 'file': 'src/normal.js', 'kind': 'FUNCTION', 'line': 56, 'name': 'makeDefaultShapes()', 'qualifiedName': 'makeDefaultShapes'}}
kotlin 25 call-hier-makeDefault calls= 0 out= {'calls': [], 'element': {'children': None, 'column': 5, 'enclosingScope': None, 'file': 'src/main/kotlin/demo/Normal.kt', 'kind': 'FUNCTION', 'line': 37, 'name': 'makeDefaultShapes()', 'qualifiedName': 'demo.NormalKt#makeDefaultShapes'}}
kotlin 63 audit-call-hier-makeDefault-d3 calls= 0 out= {'calls': [], 'element': {'children': None, 'column': 5, 'enclosingScope': None, 'file': 'src/main/kotlin/demo/Normal.kt', 'kind': 'FUNCTION', 'line': 37, 'name': 'makeDefaultShapes()', 'qualifiedName': 'demo.NormalKt#makeDefaultShapes'}}
php 28 call-hier-makeDefault calls= 0 out= {'calls': [], 'element': {'children': None, 'column': 10, 'enclosingScope': None, 'file': 'src/Normal.php', 'kind': 'FUNCTION', 'line': 73, 'name': 'makeDefaultShapes', 'qualifiedName': '\\Demo\\makeDefaultShapes'}}
php 65 audit-call-hier-makeDefault-d3 calls= 0 out= {'calls': [], 'element': {'children': None, 'column': 10, 'enclosingScope': None, 'file': 'src/Normal.php', 'kind': 'FUNCTION', 'line': 73, 'name': 'makeDefaultShapes', 'qualifiedName': '\\Demo\\makeDefaultShapes'}}
python 29 call-hier-make-default calls= 3 out= {'calls': [{'children': None, 'column': 9, 'enclosingScope': None, 'file': 'src/normal.py', 'kind': 'METHOD', 'line': 20, 'name': 'Circle.__init__(self, radius)', 'qualifiedName': 'normal.Circle.__init__'}, {'children': None, 'column': 9, 'enclosingScope': None, 'file': 'src/normal.py', 'kind': 'METHOD', 'line': 31, 'name': 'Rectangle.__init__(self, width, height)', 'qualifiedName': 'normal.Rectangle.__init__'}, {'children': [{'children': None, 'column': 9, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi', 'kind': 'METHOD', 'line': 244, 'name': 'super.__init__(self, t, obj, /)', 'qualifiedName': 'super.__init__'}, {'children': None, 'column': 9, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi', 'kind': 'METHOD', 'line': 246, 'name': 'super.__init__(self, t, /)', 'qualifiedName': 'super.__init__'...
python 67 audit-call-hier-makeDefault-d3 calls= 3 out= {'calls': [{'children': None, 'column': 9, 'enclosingScope': None, 'file': 'src/normal.py', 'kind': 'METHOD', 'line': 20, 'name': 'Circle.__init__(self, radius)', 'qualifiedName': 'normal.Circle.__init__'}, {'children': None, 'column': 9, 'enclosingScope': None, 'file': 'src/normal.py', 'kind': 'METHOD', 'line': 31, 'name': 'Rectangle.__init__(self, width, height)', 'qualifiedName': 'normal.Rectangle.__init__'}, {'children': [{'children': None, 'column': 9, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi', 'kind': 'METHOD', 'line': 244, 'name': 'super.__init__(self, t, obj, /)', 'qualifiedName': 'super.__init__'}, {'children': None, 'column': 9, 'enclosingScope': None, 'file': '/home/ubuntu/.local/share/JetBrains/Toolbox/apps/pycharm/plugins/python-ce/helpers/typeshed/stdlib/builtins.pyi', 'kind': 'METHOD', 'line': 246, 'name': 'super.__init__(self, t, /)', 'qualifiedName': 'super.__init__'...
rust 25 call-hier-make-default calls= 4 out= {'calls': [{'children': [{'children': None, 'column': 8, 'enclosingScope': None, 'file': '/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs', 'kind': 'FUNCTION', 'line': 246, 'name': 'box_new(...)', 'qualifiedName': 'crate::box_new'}], 'column': 12, 'enclosingScope': None, 'file': '/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs', 'kind': 'METHOD', 'line': 264, 'name': 'Box<T>::new(...)', 'qualifiedName': 'crate::new'}, {'children': None, 'column': 12, 'enclosingScope': None, 'file': 'src/normal.rs', 'kind': 'STRUCT', 'line': 13, 'name': 'Circle {...}', 'qualifiedName': 'crate::Circle'}, {'children': None, 'column': 12, 'enclosingScope': None, 'file': 'src/normal.rs', 'kind': 'STRUCT', 'line': 33, 'name': 'Rectangle {...}', 'qualifiedName': 'crate::Rectangle'}, {'children': [{'chil...
rust 61 audit-call-hier-makeDefault-d3 calls= 4 out= {'calls': [{'children': [{'children': None, 'column': 8, 'enclosingScope': None, 'file': '/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs', 'kind': 'FUNCTION', 'line': 246, 'name': 'box_new(...)', 'qualifiedName': 'crate::box_new'}], 'column': 12, 'enclosingScope': None, 'file': '/home/ubuntu/.cache/JetBrains/RustRover2026.1/intellij-rust/stdlib-local-copy/1.94.1-1e003699ff7b46ba4f5b14a32e8c0f77d43f8dc1/library/alloc/src/boxed.rs', 'kind': 'METHOD', 'line': 264, 'name': 'Box<T>::new(...)', 'qualifiedName': 'crate::new'}, {'children': None, 'column': 12, 'enclosingScope': None, 'file': 'src/normal.rs', 'kind': 'STRUCT', 'line': 13, 'name': 'Circle {...}', 'qualifiedName': 'crate::Circle'}, {'children': None, 'column': 12, 'enclosingScope': None, 'file': 'src/normal.rs', 'kind': 'STRUCT', 'line': 33, 'name': 'Rectangle {...}', 'qualifiedName': 'crate::Rectangle'}, {'children': [{'chil...
typescript 23 call-hier-makeDefault calls= 3 out= {'calls': [{'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 13, 'name': 'Circle', 'qualifiedName': 'Circle'}, {'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 21, 'name': 'Rectangle', 'qualifiedName': 'Rectangle'}, {'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 29, 'name': 'Square', 'qualifiedName': 'Square'}], 'element': {'children': None, 'column': 17, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'FUNCTION', 'line': 55, 'name': 'makeDefaultShapes()', 'qualifiedName': 'makeDefaultShapes'}}
typescript 61 audit-call-hier-makeDefault-d3 calls= 3 out= {'calls': [{'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 13, 'name': 'Circle', 'qualifiedName': 'Circle'}, {'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 21, 'name': 'Rectangle', 'qualifiedName': 'Rectangle'}, {'children': None, 'column': 14, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'CLASS', 'line': 29, 'name': 'Square', 'qualifiedName': 'Square'}], 'element': {'children': None, 'column': 17, 'enclosingScope': None, 'file': 'src/normal.ts', 'kind': 'FUNCTION', 'line': 55, 'name': 'makeDefaultShapes()', 'qualifiedName': 'makeDefaultShapes'}}

exec
/usr/bin/zsh -lc "nl -ba live-test/rust/input.jsonl | sed -n '14,20p;52,56p'; nl -ba live-test/rust/expected.jsonl | sed -n '14,20p;52,56p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    14	{"id":"refs-area-from-total",     "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":84,"column":38}}
    15	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":71,"column":9}}
    16	{"id":"refs-shape-trait",         "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":5,"column":11}}
    17	{"id":"refs-circle-radius",          "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":14,"column":9}}
    18	{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
    19	{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
    20	{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
    52	{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
    53	{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
    54	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
    55	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    56	{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
    14	{"totalCount":7,"usages":[{"column":44,"enclosingScope":["Shape","describe","self.area()"],"file":"src/normal.rs","line":9,"usageType":"METHOD_CALL"},{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"},{"column":48,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":44,"usageType":"METHOD_CALL"},{"column":20,"enclosingScope":["area"],"file":"src/normal.rs","line":66,"usageType":"METHOD_CALL"},{"column":38,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"METHOD_CALL"},{"column":15,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"},{"column":37,"enclosingScope":["largest"],"file":"src/normal.rs","line":89,"usageType":"METHOD_CALL"}]}
    15	{"totalCount":4,"usages":[{"column":27,"enclosingScope":["new"],"file":"src/normal.rs","line":76,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["add"],"file":"src/normal.rs","line":80,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["total_area"],"file":"src/normal.rs","line":84,"usageType":"FIELD_ACCESS"},{"column":14,"enclosingScope":["largest"],"file":"src/normal.rs","line":88,"usageType":"FIELD_ACCESS"}]}
    16	{"totalCount":7,"usages":[{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":17,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":38,"usageType":"REFERENCE"},{"column":6,"enclosingScope":[],"file":"src/normal.rs","line":64,"usageType":"REFERENCE"},{"column":29,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.rs","line":71,"usageType":"REFERENCE"},{"column":42,"enclosingScope":["add"],"file":"src/normal.rs","line":79,"usageType":"REFERENCE"},{"column":46,"enclosingScope":["largest"],"file":"src/normal.rs","line":87,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":94,"usageType":"REFERENCE"}]}
    17	{"totalCount":6,"usages":[{"column":29,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"REFERENCE"},{"column":43,"enclosingScope":["use_quirks_circle"],"file":"src/extra.rs","line":11,"usageType":"FIELD_ACCESS"},{"column":24,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":38,"enclosingScope":["area"],"file":"src/normal.rs","line":19,"usageType":"FIELD_ACCESS"},{"column":37,"enclosingScope":["draw","self.radius"],"file":"src/normal.rs","line":29,"usageType":"FIELD_ACCESS"},{"column":27,"enclosingScope":["make_default_shapes"],"file":"src/normal.rs","line":96,"usageType":"REFERENCE"}]}
    18	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"Circle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":39,"name":"Rectangle::area","qualifiedName":"crate::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":65,"name":"Square::area","qualifiedName":"crate::area"}],"totalCount":3}
    19	{"implementations":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"Circle::draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"Rectangle::draw","qualifiedName":"crate::draw"}],"totalCount":2}
    20	{"implementations":[{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":34,"name":"IntCoercer::coerce","qualifiedName":"crate::coerce"},{"column":8,"file":"src/quirks.rs","kind":"METHOD","line":41,"name":"LenCoercer::coerce","qualifiedName":"crate::coerce"}],"totalCount":2}
    52	{"query":"total_area","symbols":[{"column":12,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"total_area","qualifiedName":"crate::total_area"}],"totalCount":1}
    53	{"query":"draw","symbols":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":1,"name":"Drawable","qualifiedName":"crate::Drawable"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":2,"name":"draw","qualifiedName":"crate::Drawable::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":28,"name":"draw","qualifiedName":"crate::draw"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":49,"name":"draw","qualifiedName":"crate::draw"}],"totalCount":4}
    54	{"totalCount":1,"usages":[{"column":45,"enclosingScope":["describe","self.area()"],"file":"src/normal.rs","line":23,"usageType":"METHOD_CALL"}]}
    55	{"query":"area","symbols":[{"column":8,"file":"src/normal.rs","kind":"METHOD","line":6,"name":"area","qualifiedName":"crate::Shape::area"},{"column":8,"file":"src/normal.rs","kind":"METHOD","line":18,"name":"area","qualifiedName":"crate::area"}],"totalCount":5}
    56	{"classes":[{"column":11,"file":"src/normal.rs","kind":"TRAIT","line":5,"name":"Shape","qualifiedName":"crate::Shape"},{"column":12,"file":"src/normal.rs","kind":"STRUCT","line":70,"name":"ShapeCollection","qualifiedName":"crate::ShapeCollection"}],"query":"Sh","totalCount":2}

exec
/usr/bin/zsh -lc "nl -ba live-test/python/input.jsonl | sed -n '60,61p'; nl -ba live-test/python/expected.jsonl | sed -n '60,61p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    60	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
    61	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    60	{"totalCount":3,"usages":[{"column":51,"enclosingScope":["Shape","describe"],"file":"src/normal.py","line":16,"usageType":"REFERENCE"},{"column":20,"enclosingScope":["ShapeCollection","total_area"],"file":"src/normal.py","line":55,"usageType":"REFERENCE"},{"column":47,"enclosingScope":["ShapeCollection","largest","key"],"file":"src/normal.py","line":60,"usageType":"REFERENCE"}]}
    61	{"query":"area","symbols":[{"column":9,"file":"src/normal.py","kind":"METHOD","line":23,"name":"area","qualifiedName":"normal.Circle.area"},{"column":9,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}],"totalCount":4}

exec
/usr/bin/zsh -lc "nl -ba live-test/java/input.jsonl | sed -n '65,66p'; nl -ba live-test/java/expected.jsonl | sed -n '65,66p'; nl -ba live-test/kotlin/input.jsonl | sed -n '56,57p'; nl -ba live-test/kotlin/expected.jsonl | sed -n '56,57p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    65	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"scope":"project_and_libraries"}}
    66	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    65	{"totalCount":4,"usages":[{"column":61,"enclosingScope":["Shape","describe"],"file":"src/main/java/demo/Normal.java","line":14,"usageType":"METHOD_CALL"},{"column":22,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/main/java/demo/Normal.java","line":72,"usageType":"METHOD_CALL"},{"column":35,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"},{"column":49,"enclosingScope":["ShapeCollection","largest"],"file":"src/main/java/demo/Normal.java","line":80,"usageType":"METHOD_CALL"}]}
    66	{"query":"area","symbols":[{"column":21,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":11,"name":"area","qualifiedName":"demo.Shape#area"},{"column":12,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":69,"name":"totalArea","qualifiedName":"demo.ShapeCollection#totalArea"}],"totalCount":2}
    56	{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18,"scope":"project_and_libraries"}}
    57	{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
    56	{"totalCount":3,"usages":[{"column":74,"enclosingScope":["Shape","describe"],"file":"src/main/kotlin/demo/Normal.kt","line":10,"usageType":"REFERENCE"},{"column":49,"enclosingScope":["ShapeCollection","totalArea","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":32,"usageType":"REFERENCE"},{"column":53,"enclosingScope":["ShapeCollection","largest","<anonymous>"],"file":"src/main/kotlin/demo/Normal.kt","line":34,"usageType":"REFERENCE"}]}
    57	{"query":"area","symbols":[{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":14,"name":"area","qualifiedName":"demo.Circle#area"},{"column":18,"file":"src/main/kotlin/demo/Normal.kt","kind":"FUNCTION","line":19,"name":"area","qualifiedName":"demo.Rectangle#area"}],"totalCount":4}

exec
/usr/bin/zsh -lc "rg -n '(/home/|/Users/|C:|\\\\\\\\Users\\\\\\\\|\\.cache/JetBrains|\\.gradle|\\.local/share|Toolbox|RustRover|PyCharm|PhpStorm|WebStorm|cpython-[0-9]|openjdk-|kotlin-stdlib-[0-9]|stdlib-local-copy)' live-test/*/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 exited 1 in 0ms:
exec
/usr/bin/zsh -lc "rg -n '\\"'$'"\\{HOME\\}|\\"'$'"\\{JDK\\}|\\"'$'"\\{RUST_STDLIB\\}|\\"'$'"\\{KOTLIN_STDLIB\\}|\\"'$'"\\{PYCHARM_TYPESHED\\}|\\"'$'"\\{PYTHON_STDLIB\\}|\\"'$'"\\{PHP_STUBS\\}|\\"'$'"\\{WEBSTORM_JS_STUBS\\}' live-test/*/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/typescript/output.jsonl:5:{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/python/output.jsonl:6:{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/output.jsonl:7:{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"__init__","qualifiedName":"super.__init__"}
live-test/python/output.jsonl:8:{"column":5,"enclosingScope":null,"file":"${PYTHON_STDLIB}/abc.py","kind":"FUNCTION","line":7,"name":"abstractmethod","qualifiedName":"abc.abstractmethod"}
live-test/python/output.jsonl:9:{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/output.jsonl:10:{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/output.jsonl:11:{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":255,"name":"int","qualifiedName":"int"}
live-test/python/output.jsonl:12:{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/output.jsonl:14:{"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":257,"name":"__new__","qualifiedName":"int.__new__"}
live-test/python/output.jsonl:26:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/output.jsonl:27:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/output.jsonl:28:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/output.jsonl:29:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
live-test/python/output.jsonl:31:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1930,"name":"sum(iterable, /, start=0)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1932,"name":"sum(iterable, /)","qualifiedName":"sum"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1934,"name":"sum(iterable, /, start)","qualifiedName":"sum"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":12,"name":"Shape.area(self)","qualifiedName":"normal.Shape.area"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":54,"name":"ShapeCollection.total_area(self)","qualifiedName":"normal.ShapeCollection.total_area"}}
live-test/python/output.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}
live-test/python/output.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":47,"name":"ShapeCollection","qualifiedName":"normal.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}
live-test/python/output.jsonl:50:{"calls":[{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1679,"name":"max(arg1, arg2, /, *_args, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1683,"name":"max(arg1, arg2, /, *_args, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1685,"name":"max(iterable, /, *, key=None)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1687,"name":"max(iterable, /, *, key)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1689,"name":"max(iterable, /, *, key=None, default)","qualifiedName":"max"},{"children":null,"column":5,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"FUNCTION","line":1691,"name":"max(iterable, /, *, key, default)","qualifiedName":"max"}],"element":{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":57,"name":"ShapeCollection.largest(self)","qualifiedName":"normal.ShapeCollection.largest"}}
live-test/python/output.jsonl:65:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}]}]}
live-test/python/output.jsonl:66:{"element":{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":10,"name":"Shape(ABC)","qualifiedName":"normal.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":19,"name":"Circle(Shape)","qualifiedName":"normal.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":30,"name":"Rectangle(Shape)","qualifiedName":"normal.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/normal.py","kind":"CLASS","line":42,"name":"Square(Rectangle)","qualifiedName":"normal.Square","supertypes":null}],"supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/abc.pyi","kind":"CLASS","line":45,"name":"ABC(metaclass=ABCMeta)","qualifiedName":"abc.ABC","supertypes":[{"column":7,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"CLASS","line":109,"name":"object","qualifiedName":"object","supertypes":null}]}]}
live-test/python/output.jsonl:67:{"calls":[{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"Circle.__init__(self, radius)","qualifiedName":"normal.Circle.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"},{"children":[{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":244,"name":"super.__init__(self, t, obj, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":246,"name":"super.__init__(self, t, /)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"${PYCHARM_TYPESHED}/stdlib/builtins.pyi","kind":"METHOD","line":248,"name":"super.__init__(self)","qualifiedName":"super.__init__"},{"children":null,"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":31,"name":"Rectangle.__init__(self, width, height)","qualifiedName":"normal.Rectangle.__init__"}],"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":43,"name":"Square.__init__(self, side)","qualifiedName":"normal.Square.__init__"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/normal.py","kind":"FUNCTION","line":63,"name":"make_default_shapes()","qualifiedName":"normal.make_default_shapes"}}
live-test/rust/output.jsonl:6:{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/output.jsonl:8:{"column":17,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/ops/function.rs","kind":"TRAIT","line":76,"name":"Fn","qualifiedName":"crate::Fn"}
live-test/rust/output.jsonl:10:{"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/str/mod.rs","kind":"METHOD","line":2749,"name":"parse","qualifiedName":"crate::parse"}
live-test/rust/output.jsonl:25:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/rust/output.jsonl:44:{"calls":[{"children":[{"children":null,"column":28,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/adapters/map.rs","kind":"METHOD","line":68,"name":"Map<I, F>::new(...)","qualifiedName":"crate::new"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":777,"name":"Iterator::map(...)","qualifiedName":"crate::Iterator::map"},{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/accum.rs","kind":"METHOD","line":21,"name":"Sum::sum(...)","qualifiedName":"crate::Sum::sum"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3594,"name":"Iterator::sum(...)","qualifiedName":"crate::Iterator::sum"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":83,"name":"ShapeCollection::total_area(...)","qualifiedName":"crate::total_area"}}
live-test/rust/output.jsonl:45:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":2640,"name":"Iterator::reduce(...)","qualifiedName":"crate::Iterator::reduce"},{"children":null,"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"FUNCTION","line":3278,"name":"fold(...)","qualifiedName":"crate::Iterator::max_by::fold"}],"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/iter/traits/iterator.rs","kind":"METHOD","line":3272,"name":"Iterator::max_by(...)","qualifiedName":"crate::Iterator::max_by"},{"children":[{"children":null,"column":25,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/iter.rs","kind":"METHOD","line":96,"name":"Iter<'a, T>::new(...)","qualifiedName":"crate::new"}],"column":18,"enclosingScope":null,"file":"${RUST_STDLIB}/core/src/slice/mod.rs","kind":"METHOD","line":1040,"name":"[T]::iter(...)","qualifiedName":"crate::iter"}],"element":{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":87,"name":"ShapeCollection::largest(...)","qualifiedName":"crate::largest"}}
live-test/rust/output.jsonl:61:{"calls":[{"children":[{"children":null,"column":8,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"FUNCTION","line":246,"name":"box_new(...)","qualifiedName":"crate::box_new"}],"column":12,"enclosingScope":null,"file":"${RUST_STDLIB}/alloc/src/boxed.rs","kind":"METHOD","line":264,"name":"Box<T>::new(...)","qualifiedName":"crate::new"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":13,"name":"Circle {...}","qualifiedName":"crate::Circle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":33,"name":"Rectangle {...}","qualifiedName":"crate::Rectangle"},{"children":null,"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"STRUCT","line":54,"name":"Square {...}","qualifiedName":"crate::Square"}],"column":12,"enclosingScope":null,"file":"src/normal.rs","kind":"METHOD","line":59,"name":"Square::new(...)","qualifiedName":"crate::new"}],"element":{"children":null,"column":8,"enclosingScope":null,"file":"src/normal.rs","kind":"FUNCTION","line":94,"name":"make_default_shapes()","qualifiedName":"crate::make_default_shapes"}}
live-test/php/output.jsonl:4:{"column":10,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/standard/standard_5.php","kind":"FUNCTION","line":45,"name":"intval","qualifiedName":"\\intval"}
live-test/php/output.jsonl:27:{"element":{"column":6,"enclosingScope":null,"file":"src/Modern.php","kind":"ENUM","line":4,"name":"Status","qualifiedName":"\\Demo\\Status","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":951,"name":"BackedEnum","qualifiedName":"\\BackedEnum","supertypes":[{"column":11,"enclosingScope":null,"file":"${PHP_STUBS}.jar!/stubs/Core/Core_c.php","kind":"INTERFACE","line":937,"name":"UnitEnum","qualifiedName":"\\UnitEnum","supertypes":null}]}]}
live-test/kotlin/output.jsonl:21:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/output.jsonl:23:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/output.jsonl:27:{"calls":[{"children":null,"column":26,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":46,"name":"StringBuilder.append(String)","qualifiedName":"java.lang.StringBuilder#append(java.lang.String)"},{"children":null,"column":19,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/StringBuilder.class","kind":"METHOD","line":210,"name":"StringBuilder.toString()","qualifiedName":"java.lang.StringBuilder#toString"}],"element":{"children":null,"column":5,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"FUNCTION","line":13,"name":"quirkApply(String)","qualifiedName":"demo.QuirksKt#quirkApply"}}
live-test/kotlin/output.jsonl:47:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
live-test/kotlin/output.jsonl:48:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":25,"name":"ShapeCollection","qualifiedName":"demo.ShapeCollection","supertypes":null},"subtypes":[],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/kotlin/output.jsonl:61:{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
live-test/kotlin/output.jsonl:62:{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
live-test/javascript/output.jsonl:5:{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/output.jsonl:7:{"column":13,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es5.d.ts","kind":"VARIABLE","line":619,"name":"Number","qualifiedName":"Number"}
live-test/javascript/output.jsonl:8:{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/output.jsonl:10:{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/javascript/output.jsonl:71:{"column":5,"enclosingScope":null,"file":"${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
live-test/java/output.jsonl:24:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/output.jsonl:25:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:26:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/output.jsonl:27:{"element":{"column":10,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"ENUM","line":71,"name":"CoerceMode in Quirks","qualifiedName":"demo.Quirks.CoerceMode","supertypes":null},"subtypes":[{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":72,"name":"Enum constant 'INT' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#INT","supertypes":null},{"column":13,"enclosingScope":null,"file":"src/main/java/demo/Quirks.java","kind":"CLASS","line":73,"name":"Enum constant 'ABS' in 'CoerceMode in Quirks'","qualifiedName":"demo.Quirks.CoerceMode#ABS","supertypes":null}],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Enum.class","kind":"ABSTRACT_CLASS","line":23,"name":"Enum","qualifiedName":"java.lang.Enum","supertypes":[{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/io/Serializable.class","kind":"INTERFACE","line":8,"name":"Serializable","qualifiedName":"java.io.Serializable","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Comparable.class","kind":"INTERFACE","line":8,"name":"Comparable","qualifiedName":"java.lang.Comparable","supertypes":null},{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null},{"column":18,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/constant/Constable.class","kind":"INTERFACE","line":10,"name":"Constable","qualifiedName":"java.lang.constant.Constable","supertypes":null}]}]}
live-test/java/output.jsonl:28:{"element":{"column":19,"enclosingScope":null,"file":"src/main/java/demo/Modern.java","kind":"RECORD","line":4,"name":"Point in Modern","qualifiedName":"demo.Modern.Point","supertypes":null},"subtypes":[],"supertypes":[{"column":23,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Record.class","kind":"ABSTRACT_CLASS","line":8,"name":"Record","qualifiedName":"java.lang.Record","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:30:{"calls":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/output.jsonl:45:{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.java","kind":"CLASS","line":109,"name":"ArrayList","qualifiedName":"java.util.ArrayList"}
live-test/java/output.jsonl:55:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},"subtypes":[],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}
live-test/java/output.jsonl:70:{"element":{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"INTERFACE","line":6,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}]}]}
live-test/java/output.jsonl:71:{"element":{"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"ABSTRACT_CLASS","line":10,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":18,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":36,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CLASS","line":56,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":14,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CLASS","line":11,"name":"Object","qualifiedName":"java.lang.Object","supertypes":null}]}
live-test/java/output.jsonl:72:{"calls":[{"children":[{"children":[{"children":null,"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"CONSTRUCTOR","line":12,"name":"AbstractCollection.AbstractCollection()","qualifiedName":"java.util.AbstractCollection#AbstractCollection"}],"column":15,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"CONSTRUCTOR","line":13,"name":"AbstractList.AbstractList()","qualifiedName":"java.util.AbstractList#AbstractList"}],"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"CONSTRUCTOR","line":40,"name":"ArrayList.ArrayList()","qualifiedName":"java.util.ArrayList#ArrayList()"},{"children":[{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractQueue.class","kind":"METHOD","line":12,"name":"AbstractQueue.add(E)","qualifiedName":"java.util.AbstractQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayDeque.class","kind":"METHOD","line":300,"name":"ArrayDeque.add(E)","qualifiedName":"java.util.ArrayDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4383,"name":"add(E) in SetFromMap in Collections","qualifiedName":"java.util.Collections.SetFromMap#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":4509,"name":"add(E) in AsLIFOQueue in Collections","qualifiedName":"java.util.Collections.AsLIFOQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/HashSet.class","kind":"METHOD","line":57,"name":"HashSet.add(E)","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Hashtable.class","kind":"METHOD","line":788,"name":"add(Entry<K, V>) in EntrySet in Hashtable","qualifiedName":"java.util.Hashtable.EntrySet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/JumboEnumSet.class","kind":"METHOD","line":84,"name":"JumboEnumSet.add(E)","qualifiedName":"java.util.JumboEnumSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedHashSet.class","kind":"METHOD","line":1,"name":"add(E) in ReverseLinkedHashSetView in LinkedHashSet","qualifiedName":"add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/PriorityQueue.class","kind":"METHOD","line":114,"name":"PriorityQueue.add(E)","qualifiedName":"java.util.PriorityQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/RegularEnumSet.class","kind":"METHOD","line":60,"name":"RegularEnumSet.add(E)","qualifiedName":"java.util.RegularEnumSet#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderSortedSetView.class","kind":"METHOD","line":282,"name":"add(E) in Subset in ReverseOrderSortedSetView","qualifiedName":"java.util.ReverseOrderSortedSetView.Subset#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/TreeSet.class","kind":"METHOD","line":64,"name":"TreeSet.add(E)","qualifiedName":"java.util.TreeSet#add"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ArrayBlockingQueue.class","kind":"METHOD","line":170,"name":"ArrayBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.ArrayBlockingQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedDeque.class","kind":"METHOD","line":630,"name":"ConcurrentLinkedDeque.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentLinkedQueue.class","kind":"METHOD","line":64,"name":"ConcurrentLinkedQueue.add(E)","qualifiedName":"java.util.concurrent.ConcurrentLinkedQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ConcurrentSkipListSet.class","kind":"METHOD","line":70,"name":"ConcurrentSkipListSet.add(E)","qualifiedName":"java.util.concurrent.ConcurrentSkipListSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArraySet.class","kind":"METHOD","line":66,"name":"CopyOnWriteArraySet.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArraySet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/DelayQueue.class","kind":"METHOD","line":32,"name":"DelayQueue.add(E)","qualifiedName":"java.util.concurrent.DelayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedBlockingDeque.class","kind":"METHOD","line":532,"name":"LinkedBlockingDeque.add(E)","qualifiedName":"java.util.concurrent.LinkedBlockingDeque#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/LinkedTransferQueue.class","kind":"METHOD","line":390,"name":"LinkedTransferQueue.add(E)","qualifiedName":"java.util.concurrent.LinkedTransferQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/PriorityBlockingQueue.class","kind":"METHOD","line":248,"name":"PriorityBlockingQueue.add(E)","qualifiedName":"java.util.concurrent.PriorityBlockingQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.class","kind":"METHOD","line":544,"name":"add(Runnable) in DelayedWorkQueue in ScheduledThreadPoolExecutor","qualifiedName":"java.util.concurrent.ScheduledThreadPoolExecutor.DelayedWorkQueue#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/javax/security/auth/Subject.class","kind":"METHOD","line":740,"name":"add(T) in ClassSet in Subject","qualifiedName":"javax.security.auth.Subject.ClassSet#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/imageio/spi/PartiallyOrderedSet.class","kind":"METHOD","line":34,"name":"PartiallyOrderedSet.add(E)","qualifiedName":"javax.imageio.spi.PartiallyOrderedSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/javax/print/attribute/standard/JobStateReasons.class","kind":"METHOD","line":31,"name":"JobStateReasons.add(JobStateReason)","qualifiedName":"javax.print.attribute.standard.JobStateReasons#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.compiler/com/sun/tools/javac/util/ListBuffer.class","kind":"METHOD","line":175,"name":"ListBuffer.add(A)","qualifiedName":"com.sun.tools.javac.util.ListBuffer#add"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/jdk.jartool/sun/tools/jar/Main.class","kind":"METHOD","line":1,"name":"add(ZipEntry) in Anonymous in Main","qualifiedName":"java.util.HashSet#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractCollection.class","kind":"METHOD","line":100,"name":"AbstractCollection.add(E)","qualifiedName":"java.util.AbstractCollection#add"},{"children":[{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/AbstractList.class","kind":"METHOD","line":16,"name":"AbstractList.add(E)","qualifiedName":"java.util.AbstractList#add(E)"},{"children":[{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ArrayList.class","kind":"METHOD","line":220,"name":"ArrayList.add(E)","qualifiedName":"java.util.ArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":979,"name":"add(E) in UnmodifiableCollection in Collections","qualifiedName":"java.util.Collections.UnmodifiableCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":1905,"name":"add(E) in SynchronizedCollection in Collections","qualifiedName":"java.util.Collections.SynchronizedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Collections.class","kind":"METHOD","line":2772,"name":"add(E) in CheckedCollection in Collections","qualifiedName":"java.util.Collections.CheckedCollection#add"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ImmutableCollections.class","kind":"METHOD","line":144,"name":"add(E) in AbstractImmutableCollection in ImmutableCollections","qualifiedName":"java.util.ImmutableCollections.AbstractImmutableCollection#add"},{"children":[{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"}],"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":190,"name":"LinkedList.add(E)","qualifiedName":"java.util.LinkedList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/LinkedList.class","kind":"METHOD","line":1025,"name":"add(E) in ReverseOrderLinkedListView in LinkedList","qualifiedName":"java.util.LinkedList.ReverseOrderLinkedListView#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/ReverseOrderListView.class","kind":"METHOD","line":54,"name":"ReverseOrderListView.add(E)","qualifiedName":"java.util.ReverseOrderListView#add(E)"},{"children":null,"column":33,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/Vector.class","kind":"METHOD","line":342,"name":"Vector.add(E)","qualifiedName":"java.util.Vector#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":217,"name":"CopyOnWriteArrayList.add(E)","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1019,"name":"add(E) in COWSubList in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.COWSubList#add(E)"},{"children":null,"column":24,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/concurrent/CopyOnWriteArrayList.class","kind":"METHOD","line":1306,"name":"add(E) in Reversed in CopyOnWriteArrayList","qualifiedName":"java.util.concurrent.CopyOnWriteArrayList.Reversed#add(E)"},{"children":null,"column":1,"enclosingScope":null,"file":"${JDK}!/java.base/jdk/internal/org/objectweb/asm/tree/MethodNode.class","kind":"METHOD","line":1,"name":"add(Object) in Anonymous in MethodNode","qualifiedName":"java.util.ArrayList#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.desktop/sun/awt/util/IdentityArrayList.class","kind":"METHOD","line":125,"name":"IdentityArrayList.add(E)","qualifiedName":"sun.awt.util.IdentityArrayList#add(E)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/com/sun/jmx/remote/internal/ArrayQueue.class","kind":"METHOD","line":48,"name":"ArrayQueue.add(T)","qualifiedName":"com.sun.jmx.remote.internal.ArrayQueue#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/AttributeList.class","kind":"METHOD","line":78,"name":"AttributeList.add(Object)","qualifiedName":"javax.management.AttributeList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleList.class","kind":"METHOD","line":86,"name":"RoleList.add(Object)","qualifiedName":"javax.management.relation.RoleList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/java.management/javax/management/relation/RoleUnresolvedList.class","kind":"METHOD","line":86,"name":"RoleUnresolvedList.add(Object)","qualifiedName":"javax.management.relation.RoleUnresolvedList#add(java.lang.Object)"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jdi/com/sun/tools/jdi/EventSetImpl.class","kind":"METHOD","line":258,"name":"EventSetImpl.add(Event)","qualifiedName":"com.sun.tools.jdi.EventSetImpl#add"},{"children":null,"column":20,"enclosingScope":null,"file":"${JDK}!/jdk.jshell/jdk/jshell/DiagList.class","kind":"METHOD","line":59,"name":"DiagList.add(Diag)","qualifiedName":"jdk.jshell.DiagList#add(jdk.jshell.Diag)"}],"column":13,"enclosingScope":null,"file":"${JDK}!/java.base/java/util/List.class","kind":"METHOD","line":23,"name":"List.add(E)","qualifiedName":"java.util.List#add(E)"},{"children":[{"children":[{"children":null,"column":12,"enclosingScope":null,"file":"${JDK}!/java.base/java/lang/Object.class","kind":"CONSTRUCTOR","line":13,"name":"Object.Object()","qualifiedName":"java.lang.Object#Object"}],"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":21,"name":"Circle.Circle(double)","qualifiedName":"demo.Circle#Circle"},{"children":[{"children":null,"column":16,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":10,"name":"Shape.Shape()","qualifiedName":"demo.Shape#Shape"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"},{"children":[{"children":null,"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":40,"name":"Rectangle.Rectangle(double, double)","qualifiedName":"demo.Rectangle#Rectangle"}],"column":5,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"CONSTRUCTOR","line":57,"name":"Square.Square(double)","qualifiedName":"demo.Square#Square"}],"element":{"children":null,"column":31,"enclosingScope":null,"file":"src/main/java/demo/Normal.java","kind":"METHOD","line":87,"name":"Normal.makeDefaultShapes()","qualifiedName":"demo.Normal#makeDefaultShapes"}}
live-test/java/output.jsonl:79:{"hierarchy":[{"column":7,"file":"${JDK}!/java.base/java/util/function/Function.class","kind":"METHOD","line":12,"name":"apply","qualifiedName":"java.util.function.Function#apply"}],"method":{"column":28,"file":"src/main/java/demo/Quirks.java","kind":"METHOD","line":24,"name":"apply","qualifiedName":"java.util.function.Function#apply"}}

exec
/usr/bin/zsh -lc "nl -ba live-test/README.md | sed -n '1,220p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import json,pathlib,collections
for langdir in sorted(p for p in pathlib.Path('live-test').iterdir() if (p/'input.jsonl').exists()):
    groups=collections.defaultdict(list)
    for n,line in enumerate((langdir/'input.jsonl').read_text().splitlines(),1):
        if line.strip():
            obj=json.loads(line)
            key=(obj['tool'], json.dumps(obj.get('params',{}), sort_keys=True))
            groups[key].append((n,obj['id']))
    dups=[v for v in groups.values() if len(v)>1]
    if dups:
        print(langdir.name)
        for v in dups:
            print(' ', v)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 130ms:
go
  [(46, 'audit-find-class-Circle-exact'), (57, 'audit-find-class-exact-Circle')]
java
  [(41, 'file-structure-Quirks'), (82, 'audit-file-structure-Quirks')]
python
  [(1, 'def-circle-from-constructor-call'), (2, 'def-Circle-constructor-call')]
  [(44, 'super-Circle-draw'), (74, 'audit-super-Circle-draw-protocol')]
  [(68, 'audit-find-symbol-qualified-Coercer-coerce'), (75, 'audit-find-symbol-Coercer-coerce')]

exec
/usr/bin/zsh -lc "nl -ba live-test/typescript/input.jsonl | sed -n '7,9p'; nl -ba live-test/typescript/expected.jsonl | sed -n '7,9p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     7	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":6,"column":14}}
     8	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":36,"column":14}}
     9	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":5,"column":23}}
     7	{"totalCount":4,"usages":[{"column":54,"enclosingScope":["Shape","describe"],"file":"src/normal.ts","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":49,"usageType":"REFERENCE"}]}
     8	{"totalCount":3,"usages":[{"column":31,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.ts","line":42,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":48,"usageType":"REFERENCE"}]}
     9	{"totalCount":7,"usages":[{"column":29,"enclosingScope":["Circle"],"file":"src/normal.ts","line":13,"usageType":"REFERENCE"},{"column":32,"enclosingScope":["Rectangle"],"file":"src/normal.ts","line":21,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["ShapeCollection","shapes"],"file":"src/normal.ts","line":36,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","add","shape"],"file":"src/normal.ts","line":38,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.ts","line":46,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["ShapeCollection","largest","best"],"file":"src/normal.ts","line":47,"usageType":"REFERENCE"},{"column":38,"enclosingScope":["makeDefaultShapes"],"file":"src/normal.ts","line":55,"usageType":"REFERENCE"}]}

exec
/usr/bin/zsh -lc "nl -ba live-test/javascript/input.jsonl | sed -n '10,14p'; nl -ba live-test/javascript/expected.jsonl | sed -n '10,14p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    10	{"id":"def-quirks-proxy-call-source",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":80,"column":31}}
    11	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":8,"column":5}}
    12	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":39,"column":14}}
    13	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":7,"column":7}}
    14	{"id":"impls-shape-area-direct-overrides-only","tool":"ide_find_implementations","params":{"file":"src/normal.js","line":8,"column":5}}
    10	{"column":5,"enclosingScope":null,"file":"/home/ubuntu/.local/share/JetBrains/Toolbox/apps/webstorm/plugins/javascript-plugin/jsLanguageServicesImpl/external/lib.es2015.core.d.ts","kind":"METHOD","line":276,"name":"parseInt","qualifiedName":"NumberConstructor.parseInt"}
    11	{"totalCount":5,"usages":[{"column":12,"enclosingScope":["use"],"file":"src/consumer.js","line":5,"usageType":"REFERENCE"},{"column":63,"enclosingScope":["Shape","describe"],"file":"src/normal.js","line":9,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":34,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"},{"column":45,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":50,"usageType":"REFERENCE"}]}
    12	{"totalCount":3,"usages":[{"column":18,"enclosingScope":["ShapeCollection","add"],"file":"src/normal.js","line":41,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","totalArea"],"file":"src/normal.js","line":44,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["ShapeCollection","largest"],"file":"src/normal.js","line":49,"usageType":"REFERENCE"}]}
    13	{"totalCount":5,"usages":[{"column":9,"enclosingScope":[],"file":"src/accessors.js","line":2,"usageType":"REFERENCE"},{"column":19,"enclosingScope":["Box"],"file":"src/accessors.js","line":4,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Circle"],"file":"src/normal.js","line":12,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Rectangle"],"file":"src/normal.js","line":21,"usageType":"REFERENCE"},{"column":30,"enclosingScope":["Shape"],"file":"src/normal.js","line":60,"usageType":"REFERENCE"}]}
    14	{"implementations":[{"column":9,"file":"src/accessors.js","kind":"PROPERTY","line":9,"name":"area","qualifiedName":"Box.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":17,"name":"area","qualifiedName":"Circle.area"},{"column":5,"file":"src/normal.js","kind":"METHOD","line":27,"name":"area","qualifiedName":"Rectangle.area"}],"totalCount":3}

exec
/usr/bin/zsh -lc "nl -ba live-test/javascript/src/normal.js | sed -n '40,45p'; nl -ba live-test/typescript/src/normal.ts | sed -n '40,43p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    40	    }
    41	    add(shape) { this.shapes.push(shape); }
    42	    totalArea() {
    43	        let sum = 0;
    44	        for (const s of this.shapes) sum += s.area();
    45	        return sum;
    40	    totalArea(): number {
    41	        let sum = 0;
    42	        for (const s of this.shapes) sum += s.area();
    43	        return sum;

exec
/usr/bin/zsh -lc "nl -ba live-test/php/input.jsonl | sed -n '9,12p'; nl -ba live-test/php/expected.jsonl | sed -n '9,12p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     9	{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":9,"column":30}}
    10	{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":48,"column":18}}
    11	{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":8,"column":16}}
    12	{"id":"refs-Quirks-class",        "tool":"ide_find_usages",      "params":{"file":"src/Quirks.php","line":4,"column":7}}
     9	{"totalCount":4,"usages":[{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":21,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":57,"usageType":"REFERENCE"},{"column":35,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"},{"column":48,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":65,"usageType":"REFERENCE"}]}
    10	{"totalCount":3,"usages":[{"column":9,"enclosingScope":["Demo","ShapeCollection","add"],"file":"src/Normal.php","line":51,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","totalArea"],"file":"src/Normal.php","line":56,"usageType":"REFERENCE"},{"column":18,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":64,"usageType":"REFERENCE"}]}
    11	{"totalCount":7,"usages":[{"column":26,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"PARAMETER"},{"column":51,"enclosingScope":["Demo","Shape","describe"],"file":"src/Normal.php","line":12,"usageType":"REFERENCE"},{"column":22,"enclosingScope":["Demo","Circle"],"file":"src/Normal.php","line":16,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","Rectangle"],"file":"src/Normal.php","line":28,"usageType":"REFERENCE"},{"column":14,"enclosingScope":["Demo","ShapeCollection"],"file":"src/Normal.php","line":47,"usageType":"REFERENCE"},{"column":25,"enclosingScope":["Demo","ShapeCollection","add","shape"],"file":"src/Normal.php","line":50,"usageType":"PARAMETER"},{"column":33,"enclosingScope":["Demo","ShapeCollection","largest"],"file":"src/Normal.php","line":62,"usageType":"REFERENCE"}]}
    12	{"totalCount":2,"usages":[{"column":22,"enclosingScope":["Demo","Quirks","qCallableArray"],"file":"src/Quirks.php","line":33,"usageType":"REFERENCE"},{"column":16,"enclosingScope":["Demo","Quirks","qStaticMethodVariable"],"file":"src/Quirks.php","line":42,"usageType":"REFERENCE"}]}

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import importlib.util,json,pathlib
spec=importlib.util.spec_from_file_location('run','live-test/run.py')
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
for langdir in sorted(p for p in pathlib.Path('live-test').iterdir() if (p/'input.jsonl').exists()):
    exp=[json.loads(l) for l in (langdir/'expected.jsonl').read_text().splitlines() if l.strip()]
    out=[json.loads(l) for l in (langdir/'output.jsonl').read_text().splitlines() if l.strip()]
    bad=[]
    for i,(e,a) in enumerate(zip(exp,out),1):
        en=mod.normalize(e, str(langdir.resolve()))
        if en"'!=a:
            bad.append(i)
    if bad:
        print(langdir.name,bad)
PY' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 225ms:
kotlin [22, 24]

exec
/usr/bin/zsh -lc "nl -ba live-test/kotlin/expected.jsonl | sed -n '21,24p'; nl -ba live-test/kotlin/output.jsonl | sed -n '21,24p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    21	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    22	{"element":{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}
    23	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    24	{"element":{"column":14,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"SEALED_CLASS","line":37,"name":"Coercion","qualifiedName":"demo.Coercion","supertypes":null},"subtypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":39,"name":"IntCoerce","qualifiedName":"demo.Coercion.IntCoerce","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Quirks.kt","kind":"OBJECT","line":40,"name":"AbsCoerce","qualifiedName":"demo.Coercion.AbsCoerce","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"/home/ubuntu/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.0/b48df2c4aede9586cc931ead433bc02d6fd7879e/kotlin-stdlib-2.0.0.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    21	{"element":{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null},"subtypes":[],"supertypes":[{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":[{"column":11,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"INTERFACE","line":3,"name":"Drawable","qualifiedName":"demo.Drawable","supertypes":null},{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}]}]}
    22	{"tool_error_text":"Failed to build supertype hierarchy"}
    23	{"element":{"column":16,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"ABSTRACT_CLASS","line":7,"name":"Shape","qualifiedName":"demo.Shape","supertypes":null},"subtypes":[{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":13,"name":"Circle","qualifiedName":"demo.Circle","supertypes":null},{"column":12,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":18,"name":"Rectangle","qualifiedName":"demo.Rectangle","supertypes":null},{"column":7,"enclosingScope":null,"file":"src/main/kotlin/demo/Normal.kt","kind":"CLASS","line":23,"name":"Square","qualifiedName":"demo.Square","supertypes":null}],"supertypes":[{"column":19,"enclosingScope":null,"file":"${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins","kind":"CLASS","line":39,"name":"Any","qualifiedName":"kotlin.Any","supertypes":null}]}
    24	{"tool_error_text":"Browser refused element Coercion for SUBTYPES"}

exec
/usr/bin/zsh -lc "uv run --no-project python - <<'PY'
import importlib.util,json,pathlib,difflib
spec=importlib.util.spec_from_file_location('run','live-test/run.py')
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
langdir=pathlib.Path('live-test/kotlin').resolve()
exp=[json.loads(l) for l in (langdir/'expected.jsonl').read_text().splitlines() if l.strip()]
out=[json.loads(l) for l in (langdir/'output.jsonl').read_text().splitlines() if l.strip()]
for i in [22,24]:
    e=mod.normalize(exp[i-1], str(langdir)); a=out[i-1]
    print('--- row',i)
    for line in difflib.unified_diff(json.dumps(e,indent=2,sort_keys=True).splitlines(), json.dumps(a,indent=2,sort_keys=True).splitlines(), lineterm=''):
        print(line)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 221ms:
--- row 22
--- 
+++ 
@@ -1,57 +1,3 @@
 {
-  "element": {
-    "column": 12,
-    "enclosingScope": null,
-    "file": "src/main/kotlin/demo/Normal.kt",
-    "kind": "CLASS",
-    "line": 18,
-    "name": "Rectangle",
-    "qualifiedName": "demo.Rectangle",
-    "supertypes": null
-  },
-  "subtypes": [
-    {
-      "column": 7,
-      "enclosingScope": null,
-      "file": "src/main/kotlin/demo/Normal.kt",
-      "kind": "CLASS",
-      "line": 23,
-      "name": "Square",
-      "qualifiedName": "demo.Square",
-      "supertypes": null
-    }
-  ],
-  "supertypes": [
-    {
-      "column": 11,
-      "enclosingScope": null,
-      "file": "src/main/kotlin/demo/Normal.kt",
-      "kind": "INTERFACE",
-      "line": 3,
-      "name": "Drawable",
-      "qualifiedName": "demo.Drawable",
-      "supertypes": null
-    },
-    {
-      "column": 16,
-      "enclosingScope": null,
-      "file": "src/main/kotlin/demo/Normal.kt",
-      "kind": "ABSTRACT_CLASS",
-      "line": 7,
-      "name": "Shape",
-      "qualifiedName": "demo.Shape",
-      "supertypes": [
-        {
-          "column": 19,
-          "enclosingScope": null,
-          "file": "${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins",
-          "kind": "CLASS",
-          "line": 39,
-          "name": "Any",
-          "qualifiedName": "kotlin.Any",
-          "supertypes": null
-        }
-      ]
-    }
-  ]
+  "tool_error_text": "Failed to build supertype hierarchy"
 }
--- row 24
--- 
+++ 
@@ -1,46 +1,3 @@
 {
-  "element": {
-    "column": 14,
-    "enclosingScope": null,
-    "file": "src/main/kotlin/demo/Quirks.kt",
-    "kind": "SEALED_CLASS",
-    "line": 37,
-    "name": "Coercion",
-    "qualifiedName": "demo.Coercion",
-    "supertypes": null
-  },
-  "subtypes": [
-    {
-      "column": 12,
-      "enclosingScope": null,
-      "file": "src/main/kotlin/demo/Quirks.kt",
-      "kind": "OBJECT",
-      "line": 39,
-      "name": "IntCoerce",
-      "qualifiedName": "demo.Coercion.IntCoerce",
-      "supertypes": null
-    },
-    {
-      "column": 12,
-      "enclosingScope": null,
-      "file": "src/main/kotlin/demo/Quirks.kt",
-      "kind": "OBJECT",
-      "line": 40,
-      "name": "AbsCoerce",
-      "qualifiedName": "demo.Coercion.AbsCoerce",
-      "supertypes": null
-    }
-  ],
-  "supertypes": [
-    {
-      "column": 19,
-      "enclosingScope": null,
-      "file": "${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins",
-      "kind": "CLASS",
-      "line": 39,
-      "name": "Any",
-      "qualifiedName": "kotlin.Any",
-      "supertypes": null
-    }
-  ]
+  "tool_error_text": "Browser refused element Coercion for SUBTYPES"
 }

exec
/usr/bin/zsh -lc "nl -ba live-test/go/expected.jsonl | sed -n '1,9p'; nl -ba live-test/go/input.jsonl | sed -n '1,9p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
     1	{"column":17,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":23,"name":"Area","qualifiedName":"main.Circle.Area"}
     2	{"column":20,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":34,"name":"Area","qualifiedName":"main.Rectangle.Area"}
     3	{"column":6,"enclosingScope":null,"file":"normal.go","kind":"FUNCTION","line":72,"name":"MakeDefaultShapes","qualifiedName":"main.MakeDefaultShapes"}
     4	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":11,"name":"fn","qualifiedName":"main.fn"}
     5	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     6	{"column":19,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":56,"name":"Coerce","qualifiedName":"main.IntCoercer.Coerce"}
     7	{"column":9,"enclosingScope":null,"file":"quirks.go","kind":"VARIABLE","line":119,"name":"s","qualifiedName":"main.s"}
     8	{"column":2,"enclosingScope":null,"file":"quirks.go","kind":"METHOD","line":51,"name":"Coerce","qualifiedName":"main.Coerce"}
     9	{"column":2,"enclosingScope":null,"file":"normal.go","kind":"METHOD","line":6,"name":"Draw","qualifiedName":"main.Draw"}
     1	{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":23,"column":18}}
     2	{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":34,"column":20}}
     3	{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":72,"column":6}}
     4	{"id":"def-quirks-fnvar-resolve",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":12,"column":10}}
     5	{"id":"def-quirks-method-value",   "tool":"ide_find_definition", "params":{"file":"quirks.go","line":91,"column":10}}
     6	{"id":"def-quirks-method-expr",    "tool":"ide_find_definition", "params":{"file":"quirks.go","line":97,"column":19}}
     7	{"id":"def-quirks-type-switch-s",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":121,"column":24}}
     8	{"id":"def-quirks-iface-dispatch", "tool":"ide_find_definition", "params":{"file":"quirks.go","line":66,"column":11}}
     9	{"id":"def-drawable-draw",        "tool":"ide_find_definition", "params":{"file":"normal.go","line":6,"column":2}}

exec
/usr/bin/zsh -lc "rg -n 'tool_error_text|jsonrpc_error|transport_error|\"error\"' live-test/*/expected.jsonl live-test/*/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "rg -n 'No named element|Failed to build|Browser refused|tool_error_text' live-test/*/input.jsonl live-test/*/expected.jsonl live-test/*/output.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/kotlin/output.jsonl:22:{"tool_error_text":"Failed to build supertype hierarchy"}
live-test/kotlin/output.jsonl:24:{"tool_error_text":"Browser refused element Coercion for SUBTYPES"}

 succeeded in 0ms:
live-test/kotlin/output.jsonl:22:{"tool_error_text":"Failed to build supertype hierarchy"}
live-test/kotlin/output.jsonl:24:{"tool_error_text":"Browser refused element Coercion for SUBTYPES"}

codex
**Findings**
- **BUG** `--tool` mode compares and blesses the wrong rows. `inputs` are filtered in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225), but `expected_lines` stays unfiltered and is indexed by the filtered loop index in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246). Example: `--tool ide_find_class --language java` starts with [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), but compares it to [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:1). Worse, `--bless --tool ...` writes only the filtered rows back to the full snapshot at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:270).

- **BUG** The current diff state is not purely path rebless. After applying the current `normalize()` to committed expected rows, Kotlin still has semantic failures: [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:22) is `{"tool_error_text":"Failed to build supertype hierarchy"}` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:22), and [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:24) is `Browser refused element Coercion for SUBTYPES` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:24). Those are not path substitutions.

- **BUG** `LIBRARY_PATH_SUBS` is not portable outside this Linux `/home` + Toolbox layout. Every specific path regex and the catch-all are anchored to `/home/[^/]+` or Linux Toolbox paths in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50). macOS `/Users/...`, Windows `C:\Users\...`, `/Applications/...`, `/opt/...`, `/usr/lib/jvm/...`, SDKMAN JDKs, rustup sources, and non-Toolbox IDE installs will leak or partially normalize into unstable `${HOME}/...` paths instead of stable SDK tokens.

- **BUG** Extra committed snapshot rows are never detected. The harness only checks `expected.jsonl` being too short in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246); after the loop there is no check for `len(expected_lines) > len(inputs)` before writing output at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:267). Removing an input row can leave stale blessed output behind while the run still passes.

- **BUG** Qualified symbol snapshots bless obvious false positives. `Shape.area` / `Shape::area` queries include `ShapeCollection.totalArea`, which is not the qualified target: [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:73) vs [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:73), [javascript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/expected.jsonl:64), [typescript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/typescript/expected.jsonl:62), and [php/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/expected.jsonl:66).

- **BUG** Java `audit-super-CoerceMode-INT-apply` does not test what its ID claims. The probe points at `apply` in the enum constant body in [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:80), but the blessed result is a `CONSTRUCTOR` for `CoerceMode` with empty hierarchy in [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:80). That is not a super-method result for `apply`.

- **SMELL** Several exact duplicate probes add no coverage and can hide row-maintenance mistakes: [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1) and line 2 are identical; [go/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:46) and line 57 are identical; [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:41) and line 82 are identical; [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:44) and line 74 are identical.

- **SMELL** The harness drops fields that are correctness signals, not just noise. [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) removes `stale`, `hasMore`, `truncated`, `nextCursor`, and `pageSize` globally. That masks indexing races after the one-time precheck at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:211), and it means paged probes such as [rust/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:55) cannot assert cursor/has-more behavior.

- **SMELL** `post_jsonrpc()` can crash on a non-JSON HTTP 200 response despite its docstring promising structured transport/tool errors. `json.loads(resp.read())` in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:141) is outside any `JSONDecodeError` handler, and a non-dict JSON response would also fall through into `.get()` assumptions.

I did not count the known pre-rebless absolute paths in `expected.jsonl` as findings. Current `output.jsonl` has no raw `/home/...` paths from the checked patterns.
tokens used
171,408
**Findings**
- **BUG** `--tool` mode compares and blesses the wrong rows. `inputs` are filtered in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225), but `expected_lines` stays unfiltered and is indexed by the filtered loop index in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246). Example: `--tool ide_find_class --language java` starts with [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), but compares it to [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:1). Worse, `--bless --tool ...` writes only the filtered rows back to the full snapshot at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:270).

- **BUG** The current diff state is not purely path rebless. After applying the current `normalize()` to committed expected rows, Kotlin still has semantic failures: [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:22) is `{"tool_error_text":"Failed to build supertype hierarchy"}` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:22), and [kotlin/output.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/output.jsonl:24) is `Browser refused element Coercion for SUBTYPES` for [kotlin/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/kotlin/input.jsonl:24). Those are not path substitutions.

- **BUG** `LIBRARY_PATH_SUBS` is not portable outside this Linux `/home` + Toolbox layout. Every specific path regex and the catch-all are anchored to `/home/[^/]+` or Linux Toolbox paths in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50). macOS `/Users/...`, Windows `C:\Users\...`, `/Applications/...`, `/opt/...`, `/usr/lib/jvm/...`, SDKMAN JDKs, rustup sources, and non-Toolbox IDE installs will leak or partially normalize into unstable `${HOME}/...` paths instead of stable SDK tokens.

- **BUG** Extra committed snapshot rows are never detected. The harness only checks `expected.jsonl` being too short in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:246); after the loop there is no check for `len(expected_lines) > len(inputs)` before writing output at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:267). Removing an input row can leave stale blessed output behind while the run still passes.

- **BUG** Qualified symbol snapshots bless obvious false positives. `Shape.area` / `Shape::area` queries include `ShapeCollection.totalArea`, which is not the qualified target: [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:73) vs [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:73), [javascript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/expected.jsonl:64), [typescript/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/typescript/expected.jsonl:62), and [php/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/expected.jsonl:66).

- **BUG** Java `audit-super-CoerceMode-INT-apply` does not test what its ID claims. The probe points at `apply` in the enum constant body in [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:80), but the blessed result is a `CONSTRUCTOR` for `CoerceMode` with empty hierarchy in [java/expected.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/expected.jsonl:80). That is not a super-method result for `apply`.

- **SMELL** Several exact duplicate probes add no coverage and can hide row-maintenance mistakes: [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1) and line 2 are identical; [go/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:46) and line 57 are identical; [java/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:41) and line 82 are identical; [python/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:44) and line 74 are identical.

- **SMELL** The harness drops fields that are correctness signals, not just noise. [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) removes `stale`, `hasMore`, `truncated`, `nextCursor`, and `pageSize` globally. That masks indexing races after the one-time precheck at [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:211), and it means paged probes such as [rust/input.jsonl](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:55) cannot assert cursor/has-more behavior.

- **SMELL** `post_jsonrpc()` can crash on a non-JSON HTTP 200 response despite its docstring promising structured transport/tool errors. `json.loads(resp.read())` in [run.py](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:141) is outside any `JSONDecodeError` handler, and a non-dict JSON response would also fall through into `.get()` assumptions.

I did not count the known pre-rebless absolute paths in `expected.jsonl` as findings. Current `output.jsonl` has no raw `/home/...` paths from the checked patterns.
