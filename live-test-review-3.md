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
session id: 019e4ea6-5e5b-76d2-b392-b1f85b8541f9
--------
user
Peer review of harness hardening changes in /home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test. Two prior adversarial reviews are in live-test-review.md and live-test-review-2.md.

What was just implemented (verify each landed correctly + find new bugs introduced):

Bundle A — bless safety in live-test/run.py:
- New `_atomic_write(path, content)` helper using temp file + `os.replace`. All expected.jsonl and output.jsonl writes go through it.
- `_load_expected_by_id` is now strict: raises ValueError on malformed JSON, missing `id`/`result` fields, or duplicate expected ids.
- Bless refuses rows whose result contains `tool_error_text` / `transport_error` / `jsonrpc_error` unless `--bless-errors` is passed.
- Bless refuses to drop orphan expected ids (those whose id no longer exists in input) unless `--prune` is passed.
- `post_jsonrpc` now catches `JSONDecodeError` on envelope parse and returns a structured `transport_error`. Also rejects non-dict envelopes.
- New CLI flags: `--bless-errors`, `--prune`, `--check-fixtures`.

Bundle B — fixture / operational safety:
- New `check_fixtures()` function: offline validation, no IDE calls. Verifies per-language: input.jsonl rows have unique non-empty ids, expected.jsonl parses cleanly via the strict loader, no orphan expected ids, and each file+line+column probe targets an existing file, line within bounds, and non-whitespace character.
- Deduped one remaining python row: `audit-find-symbol-Coercer-coerce` deleted (kept `audit-find-symbol-qualified-Coercer-coerce` — same Coercer.coerce query).
- README updated: new 'Snapshot format' and 'Bless safety' sections; new `--check-fixtures` example; old 'MISSING line N' text fixed.

Plus an offline migration of expected.jsonl from positional format to id-keyed format, applying current normalize() (including LIBRARY_PATH_SUBS path tokenization). 549/549 PASS live + `--check-fixtures` reports 0 issues.

Your review focus:
1. Did each Bundle A + Bundle B item land correctly? Read the relevant run.py blocks and verify the implementation matches the description. Look for off-by-one, missed edge cases, races between the new atomic-write and pre-existing read paths.
2. New bugs introduced by the refactor? Examples I'd worry about: `--bless` with `--tool` filter where filtered_inputs is small but orphan detection sees the FULL expected — does the refusal logic correctly distinguish 'rows you didn't run' from 'orphans that should be removed'? What happens to `--bless --tool X --prune` when X matches no rows at all?
3. CLI argument interactions: `--check-fixtures` + `--bless`, `--check-fixtures` + `--tool`, etc. Does the code path order them correctly?
4. Any edge case where the strict expected loader now fails on a snapshot that's actually fine (e.g. empty trailing newline, JSON-with-trailing-whitespace)?
5. The `_atomic_write` helper uses pid-suffix temp file. Concurrent runs in the same dir from the same pid (impossible) — but what about systems where `os.replace` doesn't cross filesystem boundaries? Tempfile + replace on the same dir is fine, just confirm.
6. The anchor sanity check in check_fixtures: any false-positive risk (legitimate probes pointing at whitespace) or false-negative (broken probe slipping through)?

Output a findings table: severity (CRITICAL / HIGH / MEDIUM / LOW), file:line, finding, fix. Be specific. No 'maybe consider'. If everything looks correct, say so explicitly per Bundle item.
codex
I’ll inspect the live-test harness and the two prior review files, then cross-check the described bundles against the actual code paths and CLI behavior.
exec
/usr/bin/zsh -lc 'git status --short' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
 M live-test/README.md
 M live-test/go/expected.jsonl
 M live-test/go/input.jsonl
 M live-test/java/expected.jsonl
 M live-test/java/input.jsonl
 M live-test/javascript/expected.jsonl
 M live-test/kotlin/expected.jsonl
 M live-test/php/expected.jsonl
 M live-test/python/expected.jsonl
 M live-test/python/input.jsonl
 M live-test/run.py
 M live-test/rust/expected.jsonl
 M live-test/typescript/expected.jsonl
?? .classpath
?? .project
?? .settings/
?? live-test-review-2.md
?? live-test-review-3.md
?? live-test-review.md
?? live-test/__pycache__/
?? logs/

exec
/usr/bin/zsh -lc "pwd && rg --files -g 'AGENTS.md' -g 'live-test/**'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
/home/ubuntu/dev/jetbrains-index-mcp-plugin
live-test/run.py
live-test/.gitignore
live-test/java/target/classes/demo/Quirks$CoerceMode.class
live-test/java/output.jsonl
live-test/java/target/classes/demo/ShapeCollection.class
live-test/java/target/classes/demo/Modern$Animal.class
live-test/java/target/classes/demo/Quirks$Coerce.class
live-test/java/target/classes/demo/Modern$Dog.class
live-test/java/target/classes/demo/Rectangle.class
live-test/java/target/classes/demo/Shape.class
live-test/java/target/classes/demo/Modern$Cat.class
live-test/java/target/classes/demo/Modern$Point.class
live-test/java/target/classes/demo/Modern.class
live-test/java/target/classes/demo/Quirks.class
live-test/java/target/classes/demo/Drawable.class
live-test/java/target/classes/demo/Circle.class
live-test/java/target/classes/demo/Quirks$Coercer.class
live-test/java/target/classes/demo/Normal.class
live-test/java/target/classes/demo/Square.class
live-test/kotlin/gradlew.bat
live-test/kotlin/expected.jsonl
live-test/python/.gitignore
live-test/python/uv.lock
live-test/python/input.jsonl
live-test/python/expected.jsonl
live-test/java/.gitignore
live-test/java/input.jsonl
live-test/python/pyproject.toml
live-test/java/pom.xml
live-test/java/expected.jsonl
live-test/python/src/normal.py
live-test/python/src/__init__.py
live-test/python/src/quirks.py
live-test/python/output.jsonl
live-test/kotlin/build.gradle.kts
live-test/kotlin/input.jsonl
live-test/kotlin/gradlew
live-test/kotlin/.idea/workspace.xml
live-test/kotlin/.idea/compiler.xml
live-test/kotlin/.idea/.name
live-test/kotlin/.idea/.gitignore
live-test/kotlin/.idea/kotlinc.xml
live-test/kotlin/.idea/vcs.xml
live-test/kotlin/.idea/misc.xml
live-test/kotlin/.idea/gradle.xml
live-test/java/src/main/java/demo/Quirks.java
live-test/java/src/main/java/demo/Modern.java
live-test/java/src/main/java/demo/Normal.java
live-test/README.md
live-test/kotlin/src/main/kotlin/demo/Normal.kt
live-test/kotlin/src/main/kotlin/demo/Modern.kt
live-test/kotlin/src/main/kotlin/demo/Quirks.kt
live-test/kotlin/output.jsonl
live-test/kotlin/.gitignore
live-test/python/.idea/inspectionProfiles/Project_Default.xml
live-test/python/.idea/inspectionProfiles/profiles_settings.xml
live-test/python/.idea/workspace.xml
live-test/python/.idea/modules.xml
live-test/python/.idea/python.iml
live-test/python/.idea/.gitignore
live-test/python/.idea/vcs.xml
live-test/python/.idea/misc.xml
live-test/kotlin/build/reports/problems/problems-report.html
live-test/python/.venv/CACHEDIR.TAG
live-test/python/.venv/.gitignore
live-test/python/.venv/.lock
live-test/kotlin/settings.gradle.kts
live-test/python/.venv/bin/activate.csh
live-test/python/.venv/bin/deactivate.bat
live-test/python/.venv/bin/activate.fish
live-test/python/.venv/bin/activate.bat
live-test/php/expected.jsonl
live-test/php/vendor/autoload.php
live-test/kotlin/build/kotlin/compileKotlin/local-state/build-history.bin
live-test/kotlin/gradle/wrapper/gradle-wrapper.jar
live-test/kotlin/gradle/wrapper/gradle-wrapper.properties
live-test/kotlin/build/kotlin/compileKotlin/cacheable/dirty-sources.txt
live-test/php/vendor/composer/installed.php
live-test/php/vendor/composer/autoload_namespaces.php
live-test/php/vendor/composer/autoload_psr4.php
live-test/php/vendor/composer/ClassLoader.php
live-test/php/vendor/composer/LICENSE
live-test/php/vendor/composer/installed.json
live-test/php/vendor/composer/InstalledVersions.php
live-test/php/vendor/composer/autoload_static.php
live-test/php/vendor/composer/autoload_real.php
live-test/php/vendor/composer/autoload_classmap.php
live-test/php/composer.json
live-test/php/composer.lock
live-test/php/.gitignore
live-test/php/composer.phar
live-test/php/input.jsonl
live-test/php/src/Quirks.php
live-test/kotlin/.gradle/buildOutputCleanup/buildOutputCleanup.lock
live-test/typescript/expected.jsonl
live-test/python/.venv/pyvenv.cfg
live-test/php/src/Normal.php
live-test/php/src/Modern.php
live-test/php/output.jsonl
live-test/kotlin/.gradle/buildOutputCleanup/outputFiles.bin
live-test/kotlin/.gradle/buildOutputCleanup/cache.properties
live-test/typescript/package.json
live-test/typescript/tsconfig.json
live-test/typescript/.gitignore
live-test/typescript/input.jsonl
live-test/typescript/src/quirks.ts
live-test/typescript/src/normal.ts
live-test/typescript/output.jsonl
live-test/javascript/expected.jsonl
live-test/javascript/package.json
live-test/javascript/.gitignore
live-test/javascript/input.jsonl
live-test/__pycache__/run.cpython-314.pyc
live-test/__pycache__/run.cpython-312.pyc
live-test/python/.venv/lib/python3.11/site-packages/_virtualenv.py
live-test/javascript/src/quirks.js
live-test/python/.venv/bin/pydoc.bat
live-test/javascript/src/consumer.js
live-test/javascript/src/normal.js
live-test/javascript/src/accessors.js
live-test/javascript/output.jsonl
live-test/java/.idea/workspace.xml
live-test/java/.idea/compiler.xml
live-test/python/.venv/lib/python3.11/site-packages/__pycache__/_virtualenv.cpython-311.pyc
live-test/python/.venv/lib/python3.11/site-packages/_virtualenv.pth
live-test/java/.idea/.gitignore
live-test/java/.idea/vcs.xml
live-test/java/.idea/encodings.xml
live-test/java/.idea/misc.xml
live-test/java/.idea/jarRepositories.xml
live-test/python/.venv/bin/activate_this.py
live-test/python/.venv/bin/activate.nu
live-test/python/.venv/bin/activate.ps1
live-test/python/.venv/bin/activate
live-test/rust/.gitignore
live-test/rust/input.jsonl
live-test/rust/Cargo.lock
live-test/rust/expected.jsonl
live-test/kotlin/.gradle/9.3.0/gc.properties
live-test/rust/target/.rustc_info.json
live-test/php/.idea/phpspec.xml
live-test/typescript/.idea/workspace.xml
live-test/rust/src/extra.rs
live-test/rust/src/normal.rs
live-test/rust/src/lib.rs
live-test/rust/src/quirks.rs
live-test/rust/output.jsonl
live-test/php/.idea/workspace.xml
live-test/php/.idea/modules.xml
live-test/php/.idea/laravel-idea.xml
live-test/php/.idea/php.iml
live-test/php/.idea/php.xml
live-test/php/.idea/.gitignore
live-test/php/.idea/vcs.xml
live-test/kotlin/.gradle/vcs-1/gc.properties
live-test/kotlin/.gradle/file-system.probe
live-test/rust/target/debug/.cargo-lock
live-test/go/expected.jsonl
live-test/typescript/.idea/modules.xml
live-test/typescript/.idea/typescript.iml
live-test/go/go.mod
live-test/typescript/.idea/.gitignore
live-test/go/.gitignore
live-test/typescript/.idea/vcs.xml
live-test/go/embed.go
live-test/go/input.jsonl
live-test/go/normal.go
live-test/go/output.jsonl
live-test/rust/Cargo.toml
live-test/go/quirks.go
live-test/rust/target/CACHEDIR.TAG
live-test/rust/target/flycheck0/stderr
live-test/rust/target/flycheck0/stdout
live-test/javascript/.idea/workspace.xml
live-test/javascript/.idea/modules.xml
live-test/javascript/.idea/.gitignore
live-test/javascript/.idea/vcs.xml
live-test/javascript/.idea/javascript.iml
live-test/kotlin/.gradle/9.3.0/checksums/md5-checksums.bin
live-test/kotlin/.gradle/9.3.0/checksums/checksums.lock
live-test/kotlin/.gradle/9.3.0/checksums/sha1-checksums.bin
live-test/kotlin/.gradle/9.3.0/fileHashes/fileHashes.bin
live-test/kotlin/.gradle/9.3.0/fileHashes/fileHashes.lock
live-test/kotlin/.gradle/9.3.0/fileHashes/resourceHashesCache.bin
live-test/kotlin/.gradle/9.3.0/executionHistory/executionHistory.lock
live-test/kotlin/.gradle/9.3.0/executionHistory/executionHistory.bin
live-test/rust/.idea/workspace.xml
live-test/rust/.idea/modules.xml
live-test/rust/.idea/rust.iml
live-test/rust/.idea/.gitignore
live-test/rust/.idea/vcs.xml
live-test/rust/target/debug/.fingerprint/live-test-rust-15434ce4f70af249/test-lib-live_test_rust.json
live-test/rust/target/debug/.fingerprint/live-test-rust-15434ce4f70af249/invoked.timestamp
live-test/rust/target/debug/.fingerprint/live-test-rust-15434ce4f70af249/test-lib-live_test_rust
live-test/rust/target/debug/.fingerprint/live-test-rust-15434ce4f70af249/dep-test-lib-live_test_rust
live-test/kotlin/bin/main/demo/Normal.kt
live-test/kotlin/bin/main/demo/Modern.kt
live-test/kotlin/bin/main/demo/Quirks.kt
live-test/rust/target/debug/.fingerprint/live-test-rust-6c7eb3e2bcf4a8af/lib-live_test_rust
live-test/rust/target/debug/.fingerprint/live-test-rust-6c7eb3e2bcf4a8af/lib-live_test_rust.json
live-test/rust/target/debug/.fingerprint/live-test-rust-6c7eb3e2bcf4a8af/invoked.timestamp
live-test/rust/target/debug/.fingerprint/live-test-rust-6c7eb3e2bcf4a8af/dep-lib-live_test_rust
live-test/rust/target/debug/incremental/live_test_rust-35yeqchu2jh3q/s-hi80ga8n6i-1nc49jg.lock
live-test/kotlin/.gradle/9.3.0/fileChanges/last-build.bin
live-test/go/.idea/workspace.xml
live-test/go/.idea/modules.xml
live-test/go/.idea/go.iml
live-test/go/.idea/.gitignore
live-test/go/.idea/vcs.xml
live-test/go/.idea/go.imports.xml
live-test/rust/target/debug/incremental/live_test_rust-35yeqchu2jh3q/s-hi80ga8n6i-1nc49jg-asie3wg0vwe6rlf3xwvago2px/query-cache.bin
live-test/rust/target/debug/incremental/live_test_rust-35yeqchu2jh3q/s-hi80ga8n6i-1nc49jg-asie3wg0vwe6rlf3xwvago2px/dep-graph.bin
live-test/rust/target/debug/incremental/live_test_rust-35yeqchu2jh3q/s-hi80ga8n6i-1nc49jg-asie3wg0vwe6rlf3xwvago2px/work-products.bin
live-test/rust/target/debug/incremental/live_test_rust-2dw04gg3u1cue/s-hi80ga8n8s-04mwakk.lock
live-test/rust/target/debug/incremental/live_test_rust-2dw04gg3u1cue/s-hi80ga8n8s-04mwakk-b2udby8mue2eifb95anb99mmq/metadata.rmeta
live-test/rust/target/debug/incremental/live_test_rust-2dw04gg3u1cue/s-hi80ga8n8s-04mwakk-b2udby8mue2eifb95anb99mmq/query-cache.bin
live-test/rust/target/debug/incremental/live_test_rust-2dw04gg3u1cue/s-hi80ga8n8s-04mwakk-b2udby8mue2eifb95anb99mmq/work-products.bin
live-test/rust/target/debug/incremental/live_test_rust-2dw04gg3u1cue/s-hi80ga8n8s-04mwakk-b2udby8mue2eifb95anb99mmq/dep-graph.bin
live-test/rust/target/debug/deps/live_test_rust-15434ce4f70af249.d
live-test/rust/target/debug/deps/liblive_test_rust-15434ce4f70af249.rmeta
live-test/rust/target/debug/deps/liblive_test_rust-6c7eb3e2bcf4a8af.rmeta
live-test/rust/target/debug/deps/live_test_rust-6c7eb3e2bcf4a8af.d

exec
/usr/bin/zsh -lc "sed -n '1,240p' live-test-review.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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

exec
/usr/bin/zsh -lc "sed -n '1,260p' live-test-review-2.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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
import os
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
            body = resp.read()
    except urllib.error.URLError as e:
        return {"transport_error": str(e.reason)}
    except (TimeoutError, ConnectionError, OSError) as e:
        return {"transport_error": f"{type(e).__name__}: {e}"}
    try:
        raw = json.loads(body)
    except json.JSONDecodeError as e:
        return {"transport_error": f"non-JSON envelope: {e}"}
    if not isinstance(raw, dict):
        return {"transport_error": f"unexpected envelope type: {type(raw).__name__}"}
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
    """Read expected.jsonl into an id → result dict. Strict: raises on
    malformed JSON, missing fields, or duplicate ids — those signal a corrupt
    snapshot, not a soft MISSING.
    """
    if not expected_path.is_file():
        return {}
    out: dict[str, Any] = {}
    for i, line in enumerate(expected_path.read_text().splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as e:
            raise ValueError(f"{expected_path}:{i}: invalid JSON ({e})")
        if not isinstance(row, dict) or "id" not in row or "result" not in row:
            raise ValueError(f"{expected_path}:{i}: row missing 'id' or 'result' field")
        eid = row["id"]
        if eid in out:
            raise ValueError(f"{expected_path}:{i}: duplicate expected id '{eid}'")
        out[eid] = row["result"]
    return out


def _serialize_row(eid: str, result: Any) -> str:
    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))


def _atomic_write(path: Path, content: str) -> None:
    """Write content atomically: temp file + os.replace. Protects against
    SIGINT, crashes, and concurrent writers truncating the snapshot."""
    tmp = path.with_name(f".{path.name}.tmp.{os.getpid()}")
    try:
        tmp.write_text(content)
        os.replace(tmp, path)
    except Exception:
        try:
            tmp.unlink()
        except FileNotFoundError:
            pass
        raise


ERROR_KEYS = ("tool_error_text", "transport_error", "jsonrpc_error")


def _result_has_error(result: Any) -> bool:
    return isinstance(result, dict) and any(k in result for k in ERROR_KEYS)


def run_language(
    lang: str,
    project_path: Path,

codex
The current worktree is dirty, which is expected for this review. I’ve got the first half of `run.py`; next I’m reading the implementation with line numbers and checking the fixture validator against real fixture rows.
exec
/usr/bin/zsh -lc "sed -n '1,260p' live-test/README.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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
./run.py --check-fixtures         # validate input/expected files offline (no IDE calls)
```

## Snapshot format

Both `input.jsonl` and `expected.jsonl` are id-keyed JSONL. Each input row
needs a unique `"id"` string. Each expected row is
`{"id": <input-id>, "result": <normalized-output>}`. Output rows produced
by `./run.py` use the same shape, written to `output.jsonl`.

Rows are matched by id, not by position — so reordering input rows, adding
rows, or deleting rows doesn't shift the rest of the snapshot. The diff
output reports per-id status:

- `<id> PASS` — output matches blessed expected.
- `<id> FAIL` — output differs (unified diff shown).
- `<id> MISSING` — no expected entry for this id (bless to add).
- `ORPHAN expected id '<id>' has no matching input` — an expected entry
  whose id is no longer in `input.jsonl`. Remove from expected (with
  `--prune` during bless), or restore the input row.

## Bless safety

- `./run.py --bless` rewrites `expected.jsonl` from the live IDE output.
- A bless refuses if any row returned `tool_error_text` / `transport_error`
  / `jsonrpc_error` — use `--bless-errors` to override (rare; usually you
  want to fix the IDE/probe first).
- A bless refuses if it would drop orphan expected ids — use `--prune` to
  explicitly discard them.
- `--bless --tool X --language Y` only overwrites the rows whose `tool`
  matches `X`; other rows in `expected.jsonl` are preserved.
- Writes are atomic (temp file + `os.replace`), so SIGINT or concurrent
  runs can't corrupt a snapshot.

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
- **`MISSING (no expected entry for this id)`** — `expected.jsonl` is shorter
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
/usr/bin/zsh -lc "nl -ba live-test/run.py | sed -n '1,420p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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
    16	import os
    17	import re
    18	import sys
    19	import urllib.error
    20	import urllib.request
    21	from pathlib import Path
    22	from typing import Any
    23	
    24	PORT_BY_LANG = {
    25	    "python": 29172,
    26	    "java": 29170,
    27	    "kotlin": 29170,
    28	    "javascript": 29173,
    29	    "typescript": 29173,
    30	    "go": 29174,
    31	    "php": 29175,
    32	    "rust": 29178,
    33	}
    34	
    35	DROP_FIELDS = {
    36	    "preview", "nextCursor", "stale", "hasMore", "truncated",
    37	    "totalCollected", "offset", "pageSize",
    38	}
    39	
    40	SORTABLE_ARRAYS = {
    41	    "usages", "references", "implementations", "subtypes", "supertypes",
    42	    "classes", "symbols", "files", "matches",
    43	    "problems", "intentions", "buildErrors", "testResults", "hierarchy", "calls",
    44	    "children",
    45	}
    46	
    47	# Replace machine-specific library / SDK / stub paths with stable tokens so
    48	# expected.jsonl stays portable across machines, IDE installs, and runtime
    49	# versions. Order matters — more-specific patterns first; the catch-all home
    50	# substitution must run last.
    51	LIBRARY_PATH_SUBS: list[tuple[re.Pattern[str], str]] = [
    52	    # Rust stdlib via RustRover cache (version + 40-char git hash)
    53	    (re.compile(
    54	        r"/home/[^/]+/\.cache/JetBrains/RustRover[^/]+/intellij-rust/"
    55	        r"stdlib-local-copy/[^/]+/library/"),
    56	     "${RUST_STDLIB}/"),
    57	    # Kotlin stdlib JAR via Gradle cache (version + hash dir)
    58	    (re.compile(
    59	        r"/home/[^/]+/\.gradle/caches/modules-2/files-2\.1/"
    60	        r"org\.jetbrains\.kotlin/kotlin-stdlib/[^/]+/[0-9a-f]+/"
    61	        r"kotlin-stdlib-[^/]+\.jar!"),
    62	     "${KOTLIN_STDLIB}.jar!"),
    63	    # JDK installed via Gradle JDK manager
    64	    (re.compile(r"/home/[^/]+/\.gradle/jdks/[^!]+!"), "${JDK}!"),
    65	    # JDK installed via mise
    66	    (re.compile(r"/home/[^/]+/\.local/share/mise/installs/java/[^!]+!"), "${JDK}!"),
    67	    # PyCharm typeshed stubs
    68	    (re.compile(
    69	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/pycharm/plugins/"
    70	        r"python-ce/helpers/typeshed/"),
    71	     "${PYCHARM_TYPESHED}/"),
    72	    # Python stdlib installed via uv
    73	    (re.compile(
    74	        r"/home/[^/]+/\.local/share/uv/python/cpython-[^/]+/lib/python[^/]+/"),
    75	     "${PYTHON_STDLIB}/"),
    76	    # PhpStorm bundled PHP stubs
    77	    (re.compile(
    78	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/phpstorm/plugins/"
    79	        r"php-impl/lib/php\.jar!"),
    80	     "${PHP_STUBS}.jar!"),
    81	    # WebStorm bundled JS library stubs
    82	    (re.compile(
    83	        r"/home/[^/]+/\.local/share/JetBrains/Toolbox/apps/webstorm/plugins/"
    84	        r"javascript-plugin/jsLanguageServicesImpl/external/"),
    85	     "${WEBSTORM_JS_STUBS}/"),
    86	    # Catch-all: any remaining $HOME prefix
    87	    (re.compile(r"/home/[^/]+/"), "${HOME}/"),
    88	]
    89	
    90	
    91	def _normalize_library_paths(s: str) -> str:
    92	    for pattern, replacement in LIBRARY_PATH_SUBS:
    93	        s = pattern.sub(replacement, s)
    94	    return s
    95	
    96	
    97	def _sort_key(item: Any) -> tuple:
    98	    if isinstance(item, dict):
    99	        return (
   100	            item.get("file") or "",
   101	            item.get("line") or 0,
   102	            item.get("column") or 0,
   103	            json.dumps(item, sort_keys=True),
   104	        )
   105	    return ("", 0, 0, json.dumps(item, sort_keys=True) if item is not None else "")
   106	
   107	
   108	def normalize(obj: Any, project_root: str) -> Any:
   109	    """Drop noisy fields, sort known result arrays, substitute project paths."""
   110	    if isinstance(obj, dict):
   111	        out: dict[str, Any] = {}
   112	        for k, v in obj.items():
   113	            if k in DROP_FIELDS:
   114	                continue
   115	            v = normalize(v, project_root)
   116	            if k in SORTABLE_ARRAYS and isinstance(v, list):
   117	                v = sorted(v, key=_sort_key)
   118	            out[k] = v
   119	        return out
   120	    if isinstance(obj, list):
   121	        return [normalize(item, project_root) for item in obj]
   122	    if isinstance(obj, str):
   123	        return _normalize_library_paths(obj.replace(project_root, "${PROJECT_ROOT}"))
   124	    return obj
   125	
   126	
   127	def post_jsonrpc(url: str, request: dict, timeout: float = 60.0) -> Any:
   128	    """POST a JSON-RPC request, return the unwrapped tool result.
   129	
   130	    Possible return shapes:
   131	    - parsed JSON dict/list — happy path; the tool's payload
   132	    - {"transport_error": "..."} — curl-level failure
   133	    - {"jsonrpc_error": {...}} — JSON-RPC envelope-level error
   134	    - {"tool_error_text": "..."} — text payload that wasn't valid JSON
   135	    """
   136	    data = json.dumps(request).encode("utf-8")
   137	    req = urllib.request.Request(
   138	        url, data=data, headers={"Content-Type": "application/json"}, method="POST"
   139	    )
   140	    try:
   141	        with urllib.request.urlopen(req, timeout=timeout) as resp:
   142	            body = resp.read()
   143	    except urllib.error.URLError as e:
   144	        return {"transport_error": str(e.reason)}
   145	    except (TimeoutError, ConnectionError, OSError) as e:
   146	        return {"transport_error": f"{type(e).__name__}: {e}"}
   147	    try:
   148	        raw = json.loads(body)
   149	    except json.JSONDecodeError as e:
   150	        return {"transport_error": f"non-JSON envelope: {e}"}
   151	    if not isinstance(raw, dict):
   152	        return {"transport_error": f"unexpected envelope type: {type(raw).__name__}"}
   153	    if "error" in raw:
   154	        return {"jsonrpc_error": raw["error"]}
   155	    result = raw.get("result", {}) or {}
   156	    content = result.get("content") or []
   157	    text = content[0].get("text", "") if content else ""
   158	    try:
   159	        return json.loads(text)
   160	    except (json.JSONDecodeError, TypeError):
   161	        return {"tool_error_text": text}
   162	
   163	
   164	def build_request(input_entry: dict, project_path: str) -> dict:
   165	    return {
   166	        "jsonrpc": "2.0",
   167	        "id": 1,
   168	        "method": "tools/call",
   169	        "params": {
   170	            "name": input_entry["tool"],
   171	            "arguments": {
   172	                **input_entry.get("params", {}),
   173	                "project_path": project_path,
   174	            },
   175	        },
   176	    }
   177	
   178	
   179	def check_ready(url: str, project_path: str) -> str | None:
   180	    """Return None if ready; otherwise a diagnostic string."""
   181	    req = build_request(
   182	        {"tool": "ide_index_status", "params": {}}, project_path
   183	    )
   184	    result = post_jsonrpc(url, req, timeout=5.0)
   185	    if not isinstance(result, dict):
   186	        return f"unexpected ready-check shape: {result!r}"
   187	    if "transport_error" in result:
   188	        return f"cannot reach {url}: {result['transport_error']}"
   189	    if "tool_error_text" in result:
   190	        return f"unexpected text response: {result['tool_error_text']!r}"
   191	    if "jsonrpc_error" in result:
   192	        return f"jsonrpc error: {result['jsonrpc_error']}"
   193	    if "error" in result:
   194	        return f"{result['error']} — {result.get('message', '')}"
   195	    if result.get("isDumbMode") is True:
   196	        return "project is in dumb mode (still indexing)"
   197	    return None
   198	
   199	
   200	def diff_lines(expected: Any, actual: Any) -> str:
   201	    e = json.dumps(expected, indent=2, sort_keys=True).splitlines(keepends=True)
   202	    a = json.dumps(actual, indent=2, sort_keys=True).splitlines(keepends=True)
   203	    return "".join(
   204	        difflib.unified_diff(e, a, fromfile="expected", tofile="actual")
   205	    )
   206	
   207	
   208	def _load_expected_by_id(expected_path: Path) -> dict[str, Any]:
   209	    """Read expected.jsonl into an id → result dict. Strict: raises on
   210	    malformed JSON, missing fields, or duplicate ids — those signal a corrupt
   211	    snapshot, not a soft MISSING.
   212	    """
   213	    if not expected_path.is_file():
   214	        return {}
   215	    out: dict[str, Any] = {}
   216	    for i, line in enumerate(expected_path.read_text().splitlines(), 1):
   217	        if not line.strip():
   218	            continue
   219	        try:
   220	            row = json.loads(line)
   221	        except json.JSONDecodeError as e:
   222	            raise ValueError(f"{expected_path}:{i}: invalid JSON ({e})")
   223	        if not isinstance(row, dict) or "id" not in row or "result" not in row:
   224	            raise ValueError(f"{expected_path}:{i}: row missing 'id' or 'result' field")
   225	        eid = row["id"]
   226	        if eid in out:
   227	            raise ValueError(f"{expected_path}:{i}: duplicate expected id '{eid}'")
   228	        out[eid] = row["result"]
   229	    return out
   230	
   231	
   232	def _serialize_row(eid: str, result: Any) -> str:
   233	    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))
   234	
   235	
   236	def _atomic_write(path: Path, content: str) -> None:
   237	    """Write content atomically: temp file + os.replace. Protects against
   238	    SIGINT, crashes, and concurrent writers truncating the snapshot."""
   239	    tmp = path.with_name(f".{path.name}.tmp.{os.getpid()}")
   240	    try:
   241	        tmp.write_text(content)
   242	        os.replace(tmp, path)
   243	    except Exception:
   244	        try:
   245	            tmp.unlink()
   246	        except FileNotFoundError:
   247	            pass
   248	        raise
   249	
   250	
   251	ERROR_KEYS = ("tool_error_text", "transport_error", "jsonrpc_error")
   252	
   253	
   254	def _result_has_error(result: Any) -> bool:
   255	    return isinstance(result, dict) and any(k in result for k in ERROR_KEYS)
   256	
   257	
   258	def run_language(
   259	    lang: str,
   260	    project_path: Path,
   261	    url: str,
   262	    tool_filter: str | None,
   263	    bless: bool,
   264	    bless_errors: bool = False,
   265	    prune: bool = False,
   266	) -> tuple[int, int]:
   267	    """Returns (passed, failed).
   268	
   269	    Rows are matched by `id`, not by position. Output and expected both store
   270	    `{"id": ..., "result": ...}` per line so the snapshot survives row
   271	    insertions, reorderings, and `--tool` filtered blesses.
   272	    """
   273	    print(f"[{lang}] {url}")
   274	
   275	    err = check_ready(url, str(project_path))
   276	    if err is not None:
   277	        print(f"  PRECHECK: {err}")
   278	        print(f"[{lang}] SKIPPED (precheck failed)")
   279	        return 0, 1
   280	
   281	    input_path = project_path / "input.jsonl"
   282	    expected_path = project_path / "expected.jsonl"
   283	    output_path = project_path / "output.jsonl"
   284	    inputs = [
   285	        json.loads(line)
   286	        for line in input_path.read_text().splitlines()
   287	        if line.strip()
   288	    ]
   289	
   290	    # Detect duplicate input ids — id-keyed matching requires uniqueness.
   291	    seen: dict[str, int] = {}
   292	    for i, entry in enumerate(inputs):
   293	        eid = entry.get("id")
   294	        if not isinstance(eid, str) or not eid:
   295	            print(f"  ERROR: input row {i + 1} has no id; fix before running.")
   296	            return 0, 1
   297	        if eid in seen:
   298	            print(f"  ERROR: duplicate input id '{eid}' at rows {seen[eid] + 1} and {i + 1}.")
   299	            return 0, 1
   300	        seen[eid] = i
   301	
   302	    filtered_inputs = inputs
   303	    if tool_filter:
   304	        filtered_inputs = [e for e in inputs if e["tool"] == tool_filter]
   305	
   306	    try:
   307	        expected_by_id = _load_expected_by_id(expected_path)
   308	    except ValueError as e:
   309	        print(f"  ERROR: {e}")
   310	        return 0, 1
   311	
   312	    # Process filtered inputs
   313	    fresh_results: dict[str, Any] = {}
   314	    passed = failed = 0
   315	    for entry in filtered_inputs:
   316	        eid = entry["id"]
   317	        request = build_request(entry, str(project_path))
   318	        result = normalize(post_jsonrpc(url, request), str(project_path))
   319	        fresh_results[eid] = result
   320	
   321	        if bless:
   322	            print(f"  {eid} BLESS")
   323	            passed += 1
   324	            continue
   325	
   326	        if eid not in expected_by_id:
   327	            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
   328	            failed += 1
   329	            continue
   330	
   331	        if expected_by_id[eid] == result:
   332	            print(f"  {eid} PASS")
   333	            passed += 1
   334	        else:
   335	            print(f"  {eid} FAIL")
   336	            for line in diff_lines(expected_by_id[eid], result).splitlines():
   337	                print(f"    {line}")
   338	            failed += 1
   339	
   340	    # Output reflects only the rows we ran (filtered or full).
   341	    output_lines = [_serialize_row(e["id"], fresh_results[e["id"]]) for e in filtered_inputs]
   342	    _atomic_write(output_path, "\n".join(output_lines) + ("\n" if output_lines else ""))
   343	
   344	    # Detect orphan expected rows (ids present in expected but not in input).
   345	    # Only meaningful in full (non-filtered) diff runs.
   346	    input_ids = {e["id"] for e in inputs}
   347	    if not bless and tool_filter is None:
   348	        orphan_ids = sorted(set(expected_by_id) - input_ids)
   349	        for eid in orphan_ids:
   350	            print(f"  ORPHAN expected id '{eid}' has no matching input — remove or rename.")
   351	        failed += len(orphan_ids)
   352	
   353	    if bless:
   354	        # Refuse to bless rows that returned tool/transport errors unless
   355	        # explicitly overridden — otherwise a flaky IDE response gets locked
   356	        # in as truth.
   357	        error_ids = [eid for eid, r in fresh_results.items() if _result_has_error(r)]
   358	        if error_ids and not bless_errors:
   359	            print(f"  ERROR: refusing to bless {len(error_ids)} rows with tool/transport errors:")
   360	            for eid in error_ids[:5]:
   361	                print(f"    {eid}: {fresh_results[eid]}")
   362	            if len(error_ids) > 5:
   363	                print(f"    ... and {len(error_ids) - 5} more")
   364	            print(f"  Use --bless-errors to override.")
   365	            return passed, len(error_ids)
   366	        # Merge fresh results into existing expected, then write in input order.
   367	        merged: dict[str, Any] = dict(expected_by_id)
   368	        merged.update(fresh_results)
   369	        # Detect orphans (would be dropped). Require --prune to discard.
   370	        orphan_ids = sorted(set(merged) - input_ids)
   371	        if orphan_ids and not prune:
   372	            print(f"  ERROR: bless would drop {len(orphan_ids)} orphan expected ids: {orphan_ids[:5]}")
   373	            print(f"  Use --prune to discard them, or restore the input rows.")
   374	            return passed, len(orphan_ids)
   375	        if prune:
   376	            merged = {k: v for k, v in merged.items() if k in input_ids}
   377	        new_lines = [
   378	            _serialize_row(e["id"], merged[e["id"]])
   379	            for e in inputs
   380	            if e["id"] in merged
   381	        ]
   382	        _atomic_write(expected_path, "\n".join(new_lines) + ("\n" if new_lines else ""))
   383	        print(f"[{lang}] BLESSED {expected_path}")
   384	    else:
   385	        print(f"[{lang}] {passed} passed, {failed} failed")
   386	    return passed, failed
   387	
   388	
   389	def check_fixtures(root: Path, langs: list[str]) -> int:
   390	    """Offline validation: no IDE contact. Returns failure count.
   391	
   392	    Verifies per-language:
   393	      - input.jsonl: valid JSON, every row has a unique non-empty id.
   394	      - expected.jsonl: parses cleanly via _load_expected_by_id (strict).
   395	      - Orphan expected ids (id in expected but not in input).
   396	      - Anchor sanity: file+line+column probes target an existing file, a
   397	        line within bounds, and a non-whitespace character.
   398	    """
   399	    failures = 0
   400	    for lang in langs:
   401	        lang_dir = root / lang
   402	        input_path = lang_dir / "input.jsonl"
   403	        if not input_path.is_file():
   404	            print(f"[{lang}] no input.jsonl")
   405	            failures += 1
   406	            continue
   407	
   408	        inputs: list[dict] = []
   409	        seen: dict[str, int] = {}
   410	        per_lang_fail = 0
   411	        for i, line in enumerate(input_path.read_text().splitlines(), 1):
   412	            if not line.strip():
   413	                continue
   414	            try:
   415	                row = json.loads(line)
   416	            except json.JSONDecodeError as e:
   417	                print(f"[{lang}] input.jsonl:{i}: invalid JSON ({e})")
   418	                per_lang_fail += 1
   419	                continue
   420	            eid = row.get("id")

exec
/usr/bin/zsh -lc 'rg -n "MISSING|Snapshot format|Bless safety|check-fixtures|--bless-errors|--prune" live-test/README.md live-test/run.py' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/run.py:211:    snapshot, not a soft MISSING.
live-test/run.py:327:            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
live-test/run.py:364:            print(f"  Use --bless-errors to override.")
live-test/run.py:369:        # Detect orphans (would be dropped). Require --prune to discard.
live-test/run.py:373:            print(f"  Use --prune to discard them, or restore the input rows.")
live-test/run.py:514:        "--bless-errors",
live-test/run.py:519:        "--prune",
live-test/run.py:524:        "--check-fixtures",
live-test/README.md:37:./run.py --check-fixtures         # validate input/expected files offline (no IDE calls)
live-test/README.md:40:## Snapshot format
live-test/README.md:53:- `<id> MISSING` — no expected entry for this id (bless to add).
live-test/README.md:56:  `--prune` during bless), or restore the input row.
live-test/README.md:58:## Bless safety
live-test/README.md:62:  / `jsonrpc_error` — use `--bless-errors` to override (rare; usually you
live-test/README.md:64:- A bless refuses if it would drop orphan expected ids — use `--prune` to
live-test/README.md:104:- **`MISSING (no expected entry for this id)`** — `expected.jsonl` is shorter

exec
/usr/bin/zsh -lc "rg -n 'audit-find-symbol-Coercer-coerce|audit-find-symbol-qualified-Coercer-coerce|def-Circle-constructor-call|audit-super-Circle-draw-protocol|audit-find-class-exact-Circle|audit-file-structure-Quirks|audit-super-CoerceMode-INT-apply' live-test/*/input.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/python/input.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test/kotlin/input.jsonl:64:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}

exec
/usr/bin/zsh -lc 'for f in live-test/*/input.jsonl live-test/*/expected.jsonl live-test/*/output.jsonl; do [ -f "$f" ] && printf '"'%s ' \""'$f" && wc -l < "$f"; done' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/go/input.jsonl 56
live-test/java/input.jsonl 81
live-test/javascript/input.jsonl 71
live-test/kotlin/input.jsonl 67
live-test/php/input.jsonl 70
live-test/python/input.jsonl 73
live-test/rust/input.jsonl 65
live-test/typescript/input.jsonl 66
live-test/go/expected.jsonl 56
live-test/java/expected.jsonl 81
live-test/javascript/expected.jsonl 71
live-test/kotlin/expected.jsonl 67
live-test/php/expected.jsonl 70
live-test/python/expected.jsonl 73
live-test/rust/expected.jsonl 65
live-test/typescript/expected.jsonl 66
live-test/go/output.jsonl 56
live-test/java/output.jsonl 81
live-test/javascript/output.jsonl 71
live-test/kotlin/output.jsonl 67
live-test/php/output.jsonl 70
live-test/python/output.jsonl 73
live-test/rust/output.jsonl 65
live-test/typescript/output.jsonl 66

exec
/usr/bin/zsh -lc "nl -ba live-test/run.py | sed -n '389,590p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
   389	def check_fixtures(root: Path, langs: list[str]) -> int:
   390	    """Offline validation: no IDE contact. Returns failure count.
   391	
   392	    Verifies per-language:
   393	      - input.jsonl: valid JSON, every row has a unique non-empty id.
   394	      - expected.jsonl: parses cleanly via _load_expected_by_id (strict).
   395	      - Orphan expected ids (id in expected but not in input).
   396	      - Anchor sanity: file+line+column probes target an existing file, a
   397	        line within bounds, and a non-whitespace character.
   398	    """
   399	    failures = 0
   400	    for lang in langs:
   401	        lang_dir = root / lang
   402	        input_path = lang_dir / "input.jsonl"
   403	        if not input_path.is_file():
   404	            print(f"[{lang}] no input.jsonl")
   405	            failures += 1
   406	            continue
   407	
   408	        inputs: list[dict] = []
   409	        seen: dict[str, int] = {}
   410	        per_lang_fail = 0
   411	        for i, line in enumerate(input_path.read_text().splitlines(), 1):
   412	            if not line.strip():
   413	                continue
   414	            try:
   415	                row = json.loads(line)
   416	            except json.JSONDecodeError as e:
   417	                print(f"[{lang}] input.jsonl:{i}: invalid JSON ({e})")
   418	                per_lang_fail += 1
   419	                continue
   420	            eid = row.get("id")
   421	            if not isinstance(eid, str) or not eid:
   422	                print(f"[{lang}] input.jsonl:{i}: missing/empty id")
   423	                per_lang_fail += 1
   424	                continue
   425	            if eid in seen:
   426	                print(f"[{lang}] input.jsonl:{i}: duplicate id '{eid}' (also row {seen[eid]})")
   427	                per_lang_fail += 1
   428	                continue
   429	            seen[eid] = i
   430	            inputs.append(row)
   431	
   432	        # Anchor sanity
   433	        for entry in inputs:
   434	            params = entry.get("params", {})
   435	            file_rel = params.get("file")
   436	            line_no = params.get("line")
   437	            col = params.get("column")
   438	            if not (file_rel and isinstance(line_no, int) and isinstance(col, int)):
   439	                continue
   440	            file_path = lang_dir / file_rel
   441	            if not file_path.is_file():
   442	                print(f"[{lang}] {entry['id']}: file '{file_rel}' not found")
   443	                per_lang_fail += 1
   444	                continue
   445	            file_lines = file_path.read_text().splitlines()
   446	            if line_no < 1 or line_no > len(file_lines):
   447	                print(f"[{lang}] {entry['id']}: line {line_no} out of bounds (file has {len(file_lines)} lines)")
   448	                per_lang_fail += 1
   449	                continue
   450	            line_text = file_lines[line_no - 1]
   451	            if col < 1 or col > len(line_text):
   452	                print(f"[{lang}] {entry['id']}: column {col} beyond line length ({len(line_text)})")
   453	                per_lang_fail += 1
   454	                continue
   455	            char = line_text[col - 1]
   456	            if char.isspace():
   457	                print(f"[{lang}] {entry['id']}: column {col} on line {line_no} of '{file_rel}' is whitespace")
   458	                per_lang_fail += 1
   459	
   460	        # Expected.jsonl strict load + orphan check
   461	        expected_path = lang_dir / "expected.jsonl"
   462	        expected: dict[str, Any] = {}
   463	        try:
   464	            expected = _load_expected_by_id(expected_path)
   465	        except ValueError as e:
   466	            print(f"[{lang}] {e}")
   467	            per_lang_fail += 1
   468	        input_ids = {e["id"] for e in inputs}
   469	        orphans = sorted(set(expected) - input_ids)
   470	        for eid in orphans:
   471	            print(f"[{lang}] orphan expected id '{eid}'")
   472	            per_lang_fail += 1
   473	
   474	        print(f"[{lang}] {len(inputs)} inputs, {len(expected)} expected, {per_lang_fail} issues")
   475	        failures += per_lang_fail
   476	    return failures
   477	
   478	
   479	def discover_languages(root: Path, only: str | None) -> list[str]:
   480	    if only is not None:
   481	        if not (root / only / "input.jsonl").is_file():
   482	            print(f"No input.jsonl for language '{only}'", file=sys.stderr)
   483	            sys.exit(1)
   484	        return [only]
   485	    return sorted(
   486	        d.name
   487	        for d in root.iterdir()
   488	        if d.is_dir() and (d / "input.jsonl").is_file()
   489	    )
   490	
   491	
   492	def main() -> int:
   493	    parser = argparse.ArgumentParser(
   494	        description="Live MCP test harness runner.",
   495	        formatter_class=argparse.RawDescriptionHelpFormatter,
   496	        epilog=(
   497	            "Examples:\n"
   498	            "  ./run.py                          # diff every language\n"
   499	            "  ./run.py --bless                  # rewrite expected.jsonl\n"
   500	            "  ./run.py --language java          # one language\n"
   501	            "  ./run.py --tool ide_find_definition\n"
   502	            "  ./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http"
   503	        ),
   504	    )
   505	    parser.add_argument("--language", help="Restrict to one language.")
   506	    parser.add_argument("--tool", help="Restrict to one MCP tool.")
   507	    parser.add_argument("--url", help="Override server URL for the run.")
   508	    parser.add_argument(
   509	        "--bless",
   510	        action="store_true",
   511	        help="Rewrite expected.jsonl from server output instead of diffing.",
   512	    )
   513	    parser.add_argument(
   514	        "--bless-errors",
   515	        action="store_true",
   516	        help="Allow blessing rows that returned tool_error_text / transport_error / jsonrpc_error.",
   517	    )
   518	    parser.add_argument(
   519	        "--prune",
   520	        action="store_true",
   521	        help="During --bless, drop expected ids that no longer have a matching input row.",
   522	    )
   523	    parser.add_argument(
   524	        "--check-fixtures",
   525	        action="store_true",
   526	        help="Offline-validate input/expected files (no IDE calls).",
   527	    )
   528	    args = parser.parse_args()
   529	
   530	    root = Path(__file__).resolve().parent
   531	    langs = discover_languages(root, args.language)
   532	    if not langs:
   533	        print(f"No fixtures found in {root}", file=sys.stderr)
   534	        return 0
   535	
   536	    if args.check_fixtures:
   537	        failures = check_fixtures(root, langs)
   538	        print(f"ALL: {failures} issues")
   539	        return 0 if failures == 0 else 1
   540	
   541	    total_pass = total_fail = 0
   542	    for lang in langs:
   543	        if args.url:
   544	            url = args.url
   545	        else:
   546	            port = PORT_BY_LANG.get(lang)
   547	            if port is None:
   548	                print(f"No port mapped for language '{lang}'", file=sys.stderr)
   549	                return 1
   550	            url = f"http://127.0.0.1:{port}/index-mcp/streamable-http"
   551	        passed, failed = run_language(
   552	            lang, root / lang, url, args.tool, args.bless,
   553	            bless_errors=args.bless_errors, prune=args.prune,
   554	        )
   555	        total_pass += passed
   556	        total_fail += failed
   557	
   558	    print(f"ALL: {total_pass} passed, {total_fail} failed")
   559	    return 0 if total_fail == 0 else 1
   560	
   561	
   562	if __name__ == "__main__":
   563	    sys.exit(main())

codex
`run.py` now has the new strict loader, atomic writer, bless guards, and offline fixture checker. I’m going to execute the offline checker and then probe CLI edge cases by importing the module with temporary fixture directories, so the review can cover behavior rather than just static reading.
exec
/usr/bin/zsh -lc "rg -n '\"file\"|\"line\"|\"column\"|\"id\"|\"tool\"' live-test/*/input.jsonl" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test/typescript/input.jsonl:1:{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":17,"column":5}}
live-test/typescript/input.jsonl:2:{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":25,"column":5}}
live-test/typescript/input.jsonl:3:{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.ts","line":55,"column":17}}
live-test/typescript/input.jsonl:4:{"id":"def-quirks-parseint-from-typed-lambda","tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":3,"column":12}}
live-test/typescript/input.jsonl:5:{"id":"def-quirks-parseint-arg-cast",         "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":39,"column":19}}
live-test/typescript/input.jsonl:6:{"id":"def-T-typeparam-use",                  "tool":"ide_find_definition","params":{"file":"src/quirks.ts","line":53,"column":15}}
live-test/typescript/input.jsonl:7:{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":6,"column":14}}
live-test/typescript/input.jsonl:8:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":36,"column":14}}
live-test/typescript/input.jsonl:9:{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test/typescript/input.jsonl:10:{"id":"refs-Coercer-interface",               "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":42,"column":18}}
live-test/typescript/input.jsonl:11:{"id":"refs-aliasedCoerce",                   "tool":"ide_find_usages","params":{"file":"src/quirks.ts","line":62,"column":14}}
live-test/typescript/input.jsonl:12:{"id":"refs-Drawable-class",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test/typescript/input.jsonl:13:{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":6,"column":14}}
live-test/typescript/input.jsonl:14:{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":2,"column":5}}
live-test/typescript/input.jsonl:15:{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.ts","line":42,"column":18}}
live-test/typescript/input.jsonl:16:{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":17,"column":5}}
live-test/typescript/input.jsonl:17:{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":25,"column":5}}
live-test/typescript/input.jsonl:18:{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":29,"column":14}}
live-test/typescript/input.jsonl:19:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":21,"column":14}}
live-test/typescript/input.jsonl:20:{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test/typescript/input.jsonl:21:{"id":"type-hier-TypedCoercer",               "tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":52,"column":14}}
live-test/typescript/input.jsonl:22:{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test/typescript/input.jsonl:23:{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":2}}
live-test/typescript/input.jsonl:24:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":6,"column":14,"direction":"callers","maxDepth":2}}
live-test/typescript/input.jsonl:25:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/typescript/input.jsonl:26:{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test/typescript/input.jsonl:27:{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test/typescript/input.jsonl:28:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/typescript/input.jsonl:29:{"id":"find-symbol-qLambda",      "tool":"ide_find_symbol",          "params":{"query":"qLambda"}}
live-test/typescript/input.jsonl:30:{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.ts"}}
live-test/typescript/input.jsonl:31:{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.ts"}}
live-test/typescript/input.jsonl:32:{"id":"def-Circle-via-new-call",            "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":17}}
live-test/typescript/input.jsonl:33:{"id":"def-Rectangle-via-new-call",         "tool":"ide_find_definition",  "params":{"file":"src/normal.ts","line":56,"column":34}}
live-test/typescript/input.jsonl:34:{"id":"def-TypedCoercer-via-new-generic",   "tool":"ide_find_definition",  "params":{"file":"src/quirks.ts","line":57,"column":16}}
live-test/typescript/input.jsonl:35:{"id":"refs-Circle-via-new-call-site",      "tool":"ide_find_usages",      "params":{"file":"src/normal.ts","line":56,"column":17}}
live-test/typescript/input.jsonl:36:{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":5,"column":23}}
live-test/typescript/input.jsonl:37:{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":8,"column":5}}
live-test/typescript/input.jsonl:38:{"id":"impls-Drawable-interface",    "tool":"ide_find_implementations", "params":{"file":"src/normal.ts","line":1,"column":18}}
live-test/typescript/input.jsonl:39:{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":18,"column":5}}
live-test/typescript/input.jsonl:40:{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.ts","line":26,"column":5}}
live-test/typescript/input.jsonl:41:{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":13,"column":14}}
live-test/typescript/input.jsonl:42:{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.ts","line":35,"column":14}}
live-test/typescript/input.jsonl:43:{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":40,"column":5,"direction":"callees","maxDepth":2}}
live-test/typescript/input.jsonl:44:{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":8,"column":5,"direction":"callers","maxDepth":2}}
live-test/typescript/input.jsonl:45:{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":46,"column":5,"direction":"callees","maxDepth":2}}
live-test/typescript/input.jsonl:46:{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.ts","line":2,"column":5,"direction":"callers","maxDepth":2}}
live-test/typescript/input.jsonl:47:{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/typescript/input.jsonl:48:{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/typescript/input.jsonl:49:{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/typescript/input.jsonl:50:{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/typescript/input.jsonl:51:{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test/typescript/input.jsonl:52:{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/typescript/input.jsonl:53:{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test/typescript/input.jsonl:54:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.ts","line":6,"column":14,"scope":"project_and_libraries"}}
live-test/typescript/input.jsonl:55:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/typescript/input.jsonl:56:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/typescript/input.jsonl:57:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/typescript/input.jsonl:58:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/typescript/input.jsonl:59:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":29,"column":14,"direction":"supertypes"}}
live-test/typescript/input.jsonl:60:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.ts","line":5,"column":23,"direction":"subtypes"}}
live-test/typescript/input.jsonl:61:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.ts","line":55,"column":17,"direction":"callees","maxDepth":3}}
live-test/typescript/input.jsonl:62:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test/typescript/input.jsonl:63:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/typescript/input.jsonl:64:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/typescript/input.jsonl:65:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/typescript/input.jsonl:66:{"id":"audit-type-hier-Coerce-alias","tool":"ide_type_hierarchy","params":{"file":"src/quirks.ts","line":60,"column":13}}
live-test/rust/input.jsonl:1:{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":23,"column":48}}
live-test/rust/input.jsonl:2:{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":44,"column":48}}
live-test/rust/input.jsonl:3:{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":94,"column":8}}
live-test/rust/input.jsonl:4:{"id":"def-trait-object-coerce",     "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":47,"column":11}}
live-test/rust/input.jsonl:5:{"id":"def-enum-variant-coerce-int", "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":74,"column":25}}
live-test/rust/input.jsonl:6:{"id":"def-q-stdlib-parse",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":97,"column":21}}
live-test/rust/input.jsonl:7:{"id":"def-q-fn-pointer",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":7,"column":35}}
live-test/rust/input.jsonl:8:{"id":"def-q-box-dyn-fn",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":16,"column":25}}
live-test/rust/input.jsonl:9:{"id":"def-CoerceMode-apply",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":81,"column":10}}
live-test/rust/input.jsonl:10:{"id":"def-q-iter-parse",            "tool":"ide_find_definition",      "params":{"file":"src/quirks.rs","line":85,"column":25}}
live-test/rust/input.jsonl:11:{"id":"def-drawable-draw-decl",      "tool":"ide_find_definition",      "params":{"file":"src/normal.rs","line":2,"column":8}}
live-test/rust/input.jsonl:12:{"id":"def-cross-mod-helper",       "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":7,"column":12}}
live-test/rust/input.jsonl:13:{"id":"def-cross-file-Circle",      "tool":"ide_find_definition",      "params":{"file":"src/extra.rs","line":11,"column":20}}
live-test/rust/input.jsonl:14:{"id":"refs-area-from-total",     "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":84,"column":38}}
live-test/rust/input.jsonl:15:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":71,"column":9}}
live-test/rust/input.jsonl:16:{"id":"refs-shape-trait",         "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test/rust/input.jsonl:17:{"id":"refs-circle-radius",          "tool":"ide_find_usages",      "params":{"file":"src/normal.rs","line":14,"column":9}}
live-test/rust/input.jsonl:18:{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":6,"column":8}}
live-test/rust/input.jsonl:19:{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":2,"column":8}}
live-test/rust/input.jsonl:20:{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":29,"column":11}}
live-test/rust/input.jsonl:21:{"id":"impls-generic-bound-coercer", "tool":"ide_find_implementations", "params":{"file":"src/quirks.rs","line":50,"column":27}}
live-test/rust/input.jsonl:22:{"id":"type-hier-circle",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":13,"column":12}}
live-test/rust/input.jsonl:23:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":33,"column":12}}
live-test/rust/input.jsonl:24:{"id":"type-hier-shape-trait",    "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test/rust/input.jsonl:25:{"id":"call-hier-make-default",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":2}}
live-test/rust/input.jsonl:26:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":6,"column":8,"direction":"callers","maxDepth":2}}
live-test/rust/input.jsonl:27:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/rust/input.jsonl:28:{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test/rust/input.jsonl:29:{"id":"find-class-Marker",          "tool":"ide_find_class",           "params":{"query":"Marker"}}
live-test/rust/input.jsonl:30:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/rust/input.jsonl:31:{"id":"find-symbol-q_closure",    "tool":"ide_find_symbol",          "params":{"query":"q_closure"}}
live-test/rust/input.jsonl:32:{"id":"find-symbol-extra-function", "tool":"ide_find_symbol",          "params":{"query":"extra_function"}}
live-test/rust/input.jsonl:33:{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.rs"}}
live-test/rust/input.jsonl:34:{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.rs"}}
live-test/rust/input.jsonl:35:{"id":"file-structure-extra",     "tool":"ide_file_structure",       "params":{"file":"src/extra.rs"}}
live-test/rust/input.jsonl:36:{"id":"refs-Drawable-trait",         "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":1,"column":11}}
live-test/rust/input.jsonl:37:{"id":"refs-Rectangle-struct",       "tool":"ide_find_usages",          "params":{"file":"src/normal.rs","line":33,"column":12}}
live-test/rust/input.jsonl:38:{"id":"refs-Coercer-trait",          "tool":"ide_find_usages",          "params":{"file":"src/quirks.rs","line":28,"column":11}}
live-test/rust/input.jsonl:39:{"id":"impls-Shape-trait",           "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":5,"column":11}}
live-test/rust/input.jsonl:40:{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.rs","line":8,"column":8}}
live-test/rust/input.jsonl:41:{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":1,"column":11}}
live-test/rust/input.jsonl:42:{"id":"type-hier-Square",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.rs","line":54,"column":12}}
live-test/rust/input.jsonl:43:{"id":"type-hier-CoerceMode",        "tool":"ide_type_hierarchy",       "params":{"file":"src/quirks.rs","line":66,"column":10}}
live-test/rust/input.jsonl:44:{"id":"call-hier-total-area-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":83,"column":12,"direction":"callees","maxDepth":2}}
live-test/rust/input.jsonl:45:{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":87,"column":12,"direction":"callees","maxDepth":2}}
live-test/rust/input.jsonl:46:{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":8,"column":8,"direction":"callers","maxDepth":2}}
live-test/rust/input.jsonl:47:{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.rs","line":2,"column":8,"direction":"callers","maxDepth":2}}
live-test/rust/input.jsonl:48:{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/rust/input.jsonl:49:{"id":"find-class-Rectangle",        "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test/rust/input.jsonl:50:{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/rust/input.jsonl:51:{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/rust/input.jsonl:52:{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test/rust/input.jsonl:53:{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/rust/input.jsonl:54:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.rs","line":18,"column":8,"scope":"project_and_libraries"}}
live-test/rust/input.jsonl:55:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/rust/input.jsonl:56:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/rust/input.jsonl:57:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/rust/input.jsonl:58:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/rust/input.jsonl:59:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":54,"column":12,"direction":"supertypes"}}
live-test/rust/input.jsonl:60:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.rs","line":5,"column":11,"direction":"subtypes"}}
live-test/rust/input.jsonl:61:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.rs","line":94,"column":8,"direction":"callees","maxDepth":3}}
live-test/rust/input.jsonl:62:{"id":"audit-find-symbol-qualified-IntCoercer-coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer::coerce"}}
live-test/rust/input.jsonl:63:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/rust/input.jsonl:64:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/rust/input.jsonl:65:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/kotlin/input.jsonl:1:{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
live-test/kotlin/input.jsonl:2:{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
live-test/kotlin/input.jsonl:3:{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5}}
live-test/kotlin/input.jsonl:4:{"id":"def-extension-coerceTo",   "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":29,"column":42}}
live-test/kotlin/input.jsonl:5:{"id":"def-infix-coerceFirst",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":59,"column":43}}
live-test/kotlin/input.jsonl:6:{"id":"def-companion-create-call",    "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":23,"column":21}}
live-test/kotlin/input.jsonl:7:{"id":"def-companion-DEFAULT-LIMIT",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":25,"column":20}}
live-test/kotlin/input.jsonl:8:{"id":"def-suspend-fetchValue-call",  "tool":"ide_find_definition",      "params":{"file":"src/main/kotlin/demo/Modern.kt","line":17,"column":13}}
live-test/kotlin/input.jsonl:9:{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":32,"column":49}}
live-test/kotlin/input.jsonl:10:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":26,"column":9}}
live-test/kotlin/input.jsonl:11:{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test/kotlin/input.jsonl:12:{"id":"refs-Circle-radius",       "tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":18}}
live-test/kotlin/input.jsonl:13:{"id":"refs-coerceFirst-callsite","tool":"ide_find_usages",      "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":61,"column":29}}
live-test/kotlin/input.jsonl:14:{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18}}
live-test/kotlin/input.jsonl:15:{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":4,"column":9}}
live-test/kotlin/input.jsonl:16:{"id":"impls-coercion",           "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
live-test/kotlin/input.jsonl:17:{"id":"impls-Coercion-apply",     "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":38,"column":18}}
live-test/kotlin/input.jsonl:18:{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18}}
live-test/kotlin/input.jsonl:19:{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":19,"column":18}}
live-test/kotlin/input.jsonl:20:{"id":"super-Circle-draw-from-Drawable","tool":"ide_find_super_methods","params":{"file":"src/main/kotlin/demo/Normal.kt","line":15,"column":18}}
live-test/kotlin/input.jsonl:21:{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7}}
live-test/kotlin/input.jsonl:22:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test/kotlin/input.jsonl:23:{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test/kotlin/input.jsonl:24:{"id":"type-hier-Coercion-sealed","tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":37,"column":14}}
live-test/kotlin/input.jsonl:25:{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":2}}
live-test/kotlin/input.jsonl:26:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":8,"column":18,"direction":"callers","maxDepth":2}}
live-test/kotlin/input.jsonl:27:{"id":"call-hier-quirkApply-callees","tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":13,"column":5,"direction":"callees","maxDepth":2}}
live-test/kotlin/input.jsonl:28:{"id":"call-hier-quirkLet-callees",  "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":17,"column":5,"direction":"callees","maxDepth":2}}
live-test/kotlin/input.jsonl:29:{"id":"call-hier-quirkWith-callees", "tool":"ide_call_hierarchy",    "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":21,"column":5,"direction":"callees","maxDepth":2}}
live-test/kotlin/input.jsonl:30:{"id":"call-hier-computeTotal-callees","tool":"ide_call_hierarchy",       "params":{"file":"src/main/kotlin/demo/Modern.kt","line":16,"column":13,"direction":"callees","maxDepth":2}}
live-test/kotlin/input.jsonl:31:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/kotlin/input.jsonl:32:{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test/kotlin/input.jsonl:33:{"id":"find-class-Counter",       "tool":"ide_find_class",           "params":{"query":"Counter"}}
live-test/kotlin/input.jsonl:34:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/kotlin/input.jsonl:35:{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
live-test/kotlin/input.jsonl:36:{"id":"find-symbol-fetchValue",   "tool":"ide_find_symbol",          "params":{"query":"fetchValue"}}
live-test/kotlin/input.jsonl:37:{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Normal.kt"}}
live-test/kotlin/input.jsonl:38:{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/kotlin/demo/Quirks.kt"}}
live-test/kotlin/input.jsonl:39:{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
live-test/kotlin/input.jsonl:40:{"id":"refs-Rectangle-class",       "tool":"ide_find_usages",          "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test/kotlin/input.jsonl:41:{"id":"impls-Shape-class",          "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16}}
live-test/kotlin/input.jsonl:42:{"id":"impls-Rectangle-class",      "tool":"ide_find_implementations", "params":{"file":"src/main/kotlin/demo/Normal.kt","line":18,"column":12}}
live-test/kotlin/input.jsonl:43:{"id":"super-Rectangle-draw",       "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Normal.kt","line":20,"column":18}}
live-test/kotlin/input.jsonl:44:{"id":"super-IntCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":39,"column":50}}
live-test/kotlin/input.jsonl:45:{"id":"super-AbsCoerce-apply",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/kotlin/demo/Quirks.kt","line":40,"column":50}}
live-test/kotlin/input.jsonl:46:{"id":"type-hier-Drawable",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":3,"column":11}}
live-test/kotlin/input.jsonl:47:{"id":"type-hier-Circle",           "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":13,"column":7}}
live-test/kotlin/input.jsonl:48:{"id":"type-hier-ShapeCollection",  "tool":"ide_type_hierarchy",       "params":{"file":"src/main/kotlin/demo/Normal.kt","line":25,"column":7}}
live-test/kotlin/input.jsonl:49:{"id":"find-class-Circle",          "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/kotlin/input.jsonl:50:{"id":"find-class-Square",          "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/kotlin/input.jsonl:51:{"id":"find-class-ShapeCollection", "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/kotlin/input.jsonl:52:{"id":"find-class-Drawable",        "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test/kotlin/input.jsonl:53:{"id":"find-symbol-describe",       "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/kotlin/input.jsonl:54:{"id":"find-symbol-totalArea",      "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test/kotlin/input.jsonl:55:{"id":"find-symbol-draw",           "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/kotlin/input.jsonl:56:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/kotlin/demo/Normal.kt","line":14,"column":18,"scope":"project_and_libraries"}}
live-test/kotlin/input.jsonl:57:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/kotlin/input.jsonl:58:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/kotlin/input.jsonl:59:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/kotlin/input.jsonl:60:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/kotlin/input.jsonl:61:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":23,"column":7,"direction":"supertypes"}}
live-test/kotlin/input.jsonl:62:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":7,"column":16,"direction":"subtypes"}}
live-test/kotlin/input.jsonl:63:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/kotlin/demo/Normal.kt","line":37,"column":5,"direction":"callees","maxDepth":3}}
live-test/kotlin/input.jsonl:64:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test/kotlin/input.jsonl:65:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/kotlin/input.jsonl:66:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/kotlin/input.jsonl:67:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/php/input.jsonl:1:{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":19,"column":21}}
live-test/php/input.jsonl:2:{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":31,"column":21}}
live-test/php/input.jsonl:3:{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/Normal.php","line":73,"column":10}}
live-test/php/input.jsonl:4:{"id":"def-match-coerce",         "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":63,"column":32}}
live-test/php/input.jsonl:5:{"id":"def-self-class",           "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":33,"column":22}}
live-test/php/input.jsonl:6:{"id":"def-promoted-property",    "tool":"ide_find_definition",      "params":{"file":"src/Quirks.php","line":75,"column":20}}
live-test/php/input.jsonl:7:{"id":"def-Status-enum-decl",     "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test/php/input.jsonl:8:{"id":"def-Status-Active-usage",  "tool":"ide_find_definition",      "params":{"file":"src/Modern.php","line":22,"column":20}}
live-test/php/input.jsonl:9:{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":9,"column":30}}
live-test/php/input.jsonl:10:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":48,"column":18}}
live-test/php/input.jsonl:11:{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test/php/input.jsonl:12:{"id":"refs-Quirks-class",        "tool":"ide_find_usages",      "params":{"file":"src/Quirks.php","line":4,"column":7}}
live-test/php/input.jsonl:13:{"id":"refs-Status-enum",         "tool":"ide_find_usages",      "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test/php/input.jsonl:14:{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":9,"column":30}}
live-test/php/input.jsonl:15:{"id":"impls-drawable-draw",      "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":5,"column":21}}
live-test/php/input.jsonl:16:{"id":"impls-coercer",            "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":79,"column":11}}
live-test/php/input.jsonl:17:{"id":"impls-coerce-at-usage",    "tool":"ide_find_implementations", "params":{"file":"src/Quirks.php","line":71,"column":20}}
live-test/php/input.jsonl:18:{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":19,"column":21}}
live-test/php/input.jsonl:19:{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":31,"column":21}}
live-test/php/input.jsonl:20:{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":23,"column":21}}
live-test/php/input.jsonl:21:{"id":"super-IntCoercer-coerce",  "tool":"ide_find_super_methods",   "params":{"file":"src/Quirks.php","line":84,"column":21}}
live-test/php/input.jsonl:22:{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":40,"column":7}}
live-test/php/input.jsonl:23:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":28,"column":7}}
live-test/php/input.jsonl:24:{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test/php/input.jsonl:25:{"id":"type-hier-Drawable",       "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":4,"column":11}}
live-test/php/input.jsonl:26:{"id":"type-hier-Coercer",        "tool":"ide_type_hierarchy",       "params":{"file":"src/Quirks.php","line":79,"column":11}}
live-test/php/input.jsonl:27:{"id":"type-hier-Status",         "tool":"ide_type_hierarchy",       "params":{"file":"src/Modern.php","line":4,"column":6}}
live-test/php/input.jsonl:28:{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":2}}
live-test/php/input.jsonl:29:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":9,"column":30,"direction":"callers","maxDepth":2}}
live-test/php/input.jsonl:30:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/php/input.jsonl:31:{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test/php/input.jsonl:32:{"id":"find-class-Drawable",      "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test/php/input.jsonl:33:{"id":"find-class-Status",        "tool":"ide_find_class",           "params":{"query":"Status"}}
live-test/php/input.jsonl:34:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/php/input.jsonl:35:{"id":"find-symbol-qNameRebind",  "tool":"ide_find_symbol",          "params":{"query":"qNameRebind"}}
live-test/php/input.jsonl:36:{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/Normal.php"}}
live-test/php/input.jsonl:37:{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/Quirks.php"}}
live-test/php/input.jsonl:38:{"id":"file-structure-Modern",    "tool":"ide_file_structure",       "params":{"file":"src/Modern.php"}}
live-test/php/input.jsonl:39:{"id":"refs-Drawable-interface",     "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":4,"column":11}}
live-test/php/input.jsonl:40:{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/Normal.php","line":28,"column":7}}
live-test/php/input.jsonl:41:{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":8,"column":16}}
live-test/php/input.jsonl:42:{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/Normal.php","line":11,"column":21}}
live-test/php/input.jsonl:43:{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":35,"column":21}}
live-test/php/input.jsonl:44:{"id":"super-Square-construct",      "tool":"ide_find_super_methods",   "params":{"file":"src/Normal.php","line":41,"column":21}}
live-test/php/input.jsonl:45:{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":16,"column":7}}
live-test/php/input.jsonl:46:{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/Normal.php","line":46,"column":7}}
live-test/php/input.jsonl:47:{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":54,"column":21,"direction":"callees","maxDepth":2}}
live-test/php/input.jsonl:48:{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":11,"column":21,"direction":"callers","maxDepth":2}}
live-test/php/input.jsonl:49:{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":62,"column":21,"direction":"callees","maxDepth":2}}
live-test/php/input.jsonl:50:{"id":"call-hier-draw-callers",      "tool":"ide_call_hierarchy",       "params":{"file":"src/Normal.php","line":5,"column":21,"direction":"callers","maxDepth":2}}
live-test/php/input.jsonl:51:{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/php/input.jsonl:52:{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/php/input.jsonl:53:{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/php/input.jsonl:54:{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/php/input.jsonl:55:{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test/php/input.jsonl:56:{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/php/input.jsonl:57:{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test/php/input.jsonl:58:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/Normal.php","line":9,"column":30,"scope":"project_and_libraries"}}
live-test/php/input.jsonl:59:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/php/input.jsonl:60:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/php/input.jsonl:61:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/php/input.jsonl:62:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/php/input.jsonl:63:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":40,"column":7,"direction":"supertypes"}}
live-test/php/input.jsonl:64:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/Normal.php","line":8,"column":16,"direction":"subtypes"}}
live-test/php/input.jsonl:65:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/Normal.php","line":73,"column":10,"direction":"callees","maxDepth":3}}
live-test/php/input.jsonl:66:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape::area"}}
live-test/php/input.jsonl:67:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/php/input.jsonl:68:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/php/input.jsonl:69:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/php/input.jsonl:70:{"id":"audit-refs-qNameRebind-callable-array","tool":"ide_find_usages","params":{"file":"src/Quirks.php","line":6,"column":28}}
live-test/java/input.jsonl:1:{"id":"def-circle-area-decl",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
live-test/java/input.jsonl:2:{"id":"def-rectangle-area-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
live-test/java/input.jsonl:3:{"id":"def-makeDefaultShapes-decl","tool":"ide_find_definition",     "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31}}
live-test/java/input.jsonl:4:{"id":"def-parse-string-overload","tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":16}}
live-test/java/input.jsonl:5:{"id":"def-parse-stringint-overload","tool":"ide_find_definition",   "params":{"file":"src/main/java/demo/Quirks.java","line":95,"column":30}}
live-test/java/input.jsonl:6:{"id":"def-CoerceMode-INT-apply", "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":78,"column":31}}
live-test/java/input.jsonl:7:{"id":"def-quirkVar-coerce",      "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Quirks.java","line":18,"column":16}}
live-test/java/input.jsonl:8:{"id":"def-Point-record-decl",   "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
live-test/java/input.jsonl:9:{"id":"def-Animal-sealed-decl",  "tool":"ide_find_definition",      "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
live-test/java/input.jsonl:10:{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":72,"column":22}}
live-test/java/input.jsonl:11:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":63,"column":33}}
live-test/java/input.jsonl:12:{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
live-test/java/input.jsonl:13:{"id":"refs-Circle-ctor",         "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":21,"column":5}}
live-test/java/input.jsonl:14:{"id":"impls-shape-area",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21}}
live-test/java/input.jsonl:15:{"id":"impls-drawable-draw",       "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Normal.java","line":7,"column":12}}
live-test/java/input.jsonl:16:{"id":"impls-functional-Coerce",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":15}}
live-test/java/input.jsonl:17:{"id":"impls-Coerce-via-lambda",  "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":68,"column":18}}
live-test/java/input.jsonl:18:{"id":"impls-CoerceMode-apply",   "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":74,"column":22}}
live-test/java/input.jsonl:19:{"id":"impls-coerce-run",         "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Quirks.java","line":64,"column":28}}
live-test/java/input.jsonl:20:{"id":"impls-Animal-name",        "tool":"ide_find_implementations", "params":{"file":"src/main/java/demo/Modern.java","line":9,"column":16}}
live-test/java/input.jsonl:21:{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":26,"column":12}}
live-test/java/input.jsonl:22:{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":46,"column":12}}
live-test/java/input.jsonl:23:{"id":"super-Circle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":31,"column":19}}
live-test/java/input.jsonl:24:{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7}}
live-test/java/input.jsonl:25:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
live-test/java/input.jsonl:26:{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16}}
live-test/java/input.jsonl:27:{"id":"type-hier-CoerceMode",     "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Quirks.java","line":71,"column":10}}
live-test/java/input.jsonl:28:{"id":"type-hier-Point",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":4,"column":19}}
live-test/java/input.jsonl:29:{"id":"type-hier-Animal",         "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Modern.java","line":8,"column":29}}
live-test/java/input.jsonl:30:{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":2}}
live-test/java/input.jsonl:31:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"direction":"callers","maxDepth":2}}
live-test/java/input.jsonl:32:{"id":"call-hier-parse-string-callers","tool":"ide_call_hierarchy",  "params":{"file":"src/main/java/demo/Quirks.java","line":86,"column":23,"direction":"callers","maxDepth":2}}
live-test/java/input.jsonl:33:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/java/input.jsonl:34:{"id":"find-class-Coercer",       "tool":"ide_find_class",           "params":{"query":"Coercer"}}
live-test/java/input.jsonl:35:{"id":"find-class-Point",         "tool":"ide_find_class",           "params":{"query":"Point"}}
live-test/java/input.jsonl:36:{"id":"find-class-Animal",        "tool":"ide_find_class",           "params":{"query":"Animal"}}
live-test/java/input.jsonl:37:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/java/input.jsonl:38:{"id":"find-symbol-quirkLambda",  "tool":"ide_find_symbol",          "params":{"query":"quirkLambda"}}
live-test/java/input.jsonl:39:{"id":"find-symbol-parse",        "tool":"ide_find_symbol",          "params":{"query":"parse"}}
live-test/java/input.jsonl:40:{"id":"file-structure-Normal",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Normal.java"}}
live-test/java/input.jsonl:41:{"id":"file-structure-Quirks",    "tool":"ide_file_structure",       "params":{"file":"src/main/java/demo/Quirks.java"}}
live-test/java/input.jsonl:42:{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
live-test/java/input.jsonl:43:{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":90,"column":24}}
live-test/java/input.jsonl:44:{"id":"def-Point-via-new-record-call", "tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Modern.java","line":21,"column":23}}
live-test/java/input.jsonl:45:{"id":"def-ArrayList-via-new-jdk-call","tool":"ide_find_definition",  "params":{"file":"src/main/java/demo/Normal.java","line":88,"column":34}}
live-test/java/input.jsonl:46:{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/main/java/demo/Normal.java","line":89,"column":24}}
live-test/java/input.jsonl:47:{"id":"index-status",             "tool":"ide_index_status",         "params":{}}
live-test/java/input.jsonl:48:{"id":"diagnostics-Quirks-errors","tool":"ide_diagnostics",          "params":{"file":"src/main/java/demo/Quirks.java","severity":"errors"}}
live-test/java/input.jsonl:49:{"id":"refs-Drawable-interface",    "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
live-test/java/input.jsonl:50:{"id":"refs-Rectangle-class",      "tool":"ide_find_usages",          "params":{"file":"src/main/java/demo/Normal.java","line":36,"column":7}}
live-test/java/input.jsonl:51:{"id":"super-Rectangle-draw",      "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Normal.java","line":51,"column":19}}
live-test/java/input.jsonl:52:{"id":"super-Cat-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":13,"column":33}}
live-test/java/input.jsonl:53:{"id":"super-Dog-name",            "tool":"ide_find_super_methods",   "params":{"file":"src/main/java/demo/Modern.java","line":17,"column":33}}
live-test/java/input.jsonl:54:{"id":"type-hier-Drawable",        "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":6,"column":11}}
live-test/java/input.jsonl:55:{"id":"type-hier-Circle",          "tool":"ide_type_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":18,"column":7}}
live-test/java/input.jsonl:56:{"id":"call-hier-totalArea-callees","tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":69,"column":12,"direction":"callees","maxDepth":2}}
live-test/java/input.jsonl:57:{"id":"call-hier-describe-callers","tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":13,"column":12,"direction":"callers","maxDepth":2}}
live-test/java/input.jsonl:58:{"id":"call-hier-largest-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/main/java/demo/Normal.java","line":77,"column":11,"direction":"callees","maxDepth":2}}
live-test/java/input.jsonl:59:{"id":"find-class-Circle",         "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/java/input.jsonl:60:{"id":"find-class-Square",         "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/java/input.jsonl:61:{"id":"find-class-ShapeCollection","tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/java/input.jsonl:62:{"id":"find-symbol-describe",      "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/java/input.jsonl:63:{"id":"find-symbol-totalArea",     "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test/java/input.jsonl:64:{"id":"find-symbol-draw",          "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/java/input.jsonl:65:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/main/java/demo/Normal.java","line":11,"column":21,"scope":"project_and_libraries"}}
live-test/java/input.jsonl:66:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/java/input.jsonl:67:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/java/input.jsonl:68:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/java/input.jsonl:69:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/java/input.jsonl:70:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":56,"column":7,"direction":"supertypes"}}
live-test/java/input.jsonl:71:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":10,"column":16,"direction":"subtypes"}}
live-test/java/input.jsonl:72:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/main/java/demo/Normal.java","line":87,"column":31,"direction":"callees","maxDepth":3}}
live-test/java/input.jsonl:73:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test/java/input.jsonl:74:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/java/input.jsonl:75:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/java/input.jsonl:76:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/java/input.jsonl:77:{"id":"audit-def-classifyShape-kind-decl","tool":"ide_find_definition","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test/java/input.jsonl:78:{"id":"audit-refs-classifyShape-kind","tool":"ide_find_usages","params":{"file":"src/main/java/demo/Normal.java","line":97,"column":13}}
live-test/java/input.jsonl:79:{"id":"audit-super-anon-apply","tool":"ide_find_super_methods","params":{"file":"src/main/java/demo/Quirks.java","line":24,"column":28}}
live-test/java/input.jsonl:80:{"id":"audit-file-structure-Modern","tool":"ide_file_structure","params":{"file":"src/main/java/demo/Modern.java"}}
live-test/java/input.jsonl:81:{"id":"audit-diagnostics-warnings","tool":"ide_diagnostics","params":{"file":"src/main/java/demo/Normal.java","severity":"warnings"}}
live-test/javascript/input.jsonl:1:{"id":"def-circle-area",          "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":17,"column":5}}
live-test/javascript/input.jsonl:2:{"id":"def-rectangle-area",       "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":27,"column":5}}
live-test/javascript/input.jsonl:3:{"id":"def-makeDefaultShapes",    "tool":"ide_find_definition",      "params":{"file":"src/normal.js","line":56,"column":10}}
live-test/javascript/input.jsonl:4:{"id":"def-quirks-fn-localbind",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":6,"column":12}}
live-test/javascript/input.jsonl:5:{"id":"def-quirks-parseInt-property-on-Number","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23}}
live-test/javascript/input.jsonl:6:{"id":"def-quirks-parseint-destructured","tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":37,"column":12}}
live-test/javascript/input.jsonl:7:{"id":"def-quirks-destructured-Number-receiver","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":36,"column":29}}
live-test/javascript/input.jsonl:8:{"id":"def-quirks-parseint-optional",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":71,"column":20}}
live-test/javascript/input.jsonl:9:{"id":"def-quirks-proxy-call",           "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":82,"column":18}}
live-test/javascript/input.jsonl:10:{"id":"def-quirks-proxy-call-source",    "tool":"ide_find_definition",      "params":{"file":"src/quirks.js","line":80,"column":31}}
live-test/javascript/input.jsonl:11:{"id":"refs-area-from-totalArea", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":8,"column":5}}
live-test/javascript/input.jsonl:12:{"id":"refs-shapes-field",        "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":39,"column":14}}
live-test/javascript/input.jsonl:13:{"id":"refs-shape-class",         "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":7,"column":7}}
live-test/javascript/input.jsonl:14:{"id":"impls-shape-area-direct-overrides-only","tool":"ide_find_implementations","params":{"file":"src/normal.js","line":8,"column":5}}
live-test/javascript/input.jsonl:15:{"id":"impls-Drawable-draw",             "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":4,"column":5}}
live-test/javascript/input.jsonl:16:{"id":"super-circle-area",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":17,"column":5}}
live-test/javascript/input.jsonl:17:{"id":"super-rectangle-area",     "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":27,"column":5}}
live-test/javascript/input.jsonl:18:{"id":"type-hier-square",         "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":31,"column":7}}
live-test/javascript/input.jsonl:19:{"id":"type-hier-rectangle",      "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":21,"column":7}}
live-test/javascript/input.jsonl:20:{"id":"type-hier-shape",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":7,"column":7}}
live-test/javascript/input.jsonl:21:{"id":"call-hier-makeDefault",    "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":2}}
live-test/javascript/input.jsonl:22:{"id":"call-hier-area-callers",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":8,"column":5,"direction":"callers","maxDepth":2}}
live-test/javascript/input.jsonl:23:{"id":"call-hier-qAwait-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/quirks.js","line":64,"column":16,"direction":"callees","maxDepth":2}}
live-test/javascript/input.jsonl:24:{"id":"find-class-Shape",         "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/javascript/input.jsonl:25:{"id":"find-class-Rectangle",     "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test/javascript/input.jsonl:26:{"id":"find-symbol-area",         "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/javascript/input.jsonl:27:{"id":"find-symbol-qRebind",      "tool":"ide_find_symbol",          "params":{"query":"qRebind"}}
live-test/javascript/input.jsonl:28:{"id":"file-structure-normal",    "tool":"ide_file_structure",       "params":{"file":"src/normal.js"}}
live-test/javascript/input.jsonl:29:{"id":"file-structure-quirks",    "tool":"ide_file_structure",       "params":{"file":"src/quirks.js"}}
live-test/javascript/input.jsonl:30:{"id":"def-Circle-via-new-call",       "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":17}}
live-test/javascript/input.jsonl:31:{"id":"def-Rectangle-via-new-call",    "tool":"ide_find_definition",  "params":{"file":"src/normal.js","line":57,"column":34}}
live-test/javascript/input.jsonl:32:{"id":"refs-Circle-via-new-call-site", "tool":"ide_find_usages",      "params":{"file":"src/normal.js","line":57,"column":17}}
live-test/javascript/input.jsonl:33:{"id":"refs-Drawable-class",         "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":3,"column":7}}
live-test/javascript/input.jsonl:34:{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":21,"column":7}}
live-test/javascript/input.jsonl:35:{"id":"refs-describe-method",        "tool":"ide_find_usages",          "params":{"file":"src/normal.js","line":9,"column":5}}
live-test/javascript/input.jsonl:36:{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":7,"column":7}}
live-test/javascript/input.jsonl:37:{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":9,"column":5}}
live-test/javascript/input.jsonl:38:{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":21,"column":7}}
live-test/javascript/input.jsonl:39:{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.js","line":12,"column":7}}
live-test/javascript/input.jsonl:40:{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":18,"column":5}}
live-test/javascript/input.jsonl:41:{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.js","line":28,"column":5}}
live-test/javascript/input.jsonl:42:{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":3,"column":7}}
live-test/javascript/input.jsonl:43:{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":12,"column":7}}
live-test/javascript/input.jsonl:44:{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.js","line":37,"column":7}}
live-test/javascript/input.jsonl:45:{"id":"call-hier-totalArea-callees", "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":42,"column":5,"direction":"callees","maxDepth":2}}
live-test/javascript/input.jsonl:46:{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":9,"column":5,"direction":"callers","maxDepth":2}}
live-test/javascript/input.jsonl:47:{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.js","line":47,"column":5,"direction":"callees","maxDepth":2}}
live-test/javascript/input.jsonl:48:{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/javascript/input.jsonl:49:{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/javascript/input.jsonl:50:{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test/javascript/input.jsonl:51:{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/javascript/input.jsonl:52:{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/javascript/input.jsonl:53:{"id":"find-symbol-totalArea",       "tool":"ide_find_symbol",          "params":{"query":"totalArea"}}
live-test/javascript/input.jsonl:54:{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/javascript/input.jsonl:55:{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test/javascript/input.jsonl:56:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.js","line":17,"column":5,"scope":"project_and_libraries"}}
live-test/javascript/input.jsonl:57:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/javascript/input.jsonl:58:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/javascript/input.jsonl:59:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/javascript/input.jsonl:60:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/javascript/input.jsonl:61:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":31,"column":7,"direction":"supertypes"}}
live-test/javascript/input.jsonl:62:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.js","line":7,"column":7,"direction":"subtypes"}}
live-test/javascript/input.jsonl:63:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.js","line":56,"column":10,"direction":"callees","maxDepth":3}}
live-test/javascript/input.jsonl:64:{"id":"audit-find-symbol-qualified-Shape-area","tool":"ide_find_symbol",        "params":{"query":"Shape.area"}}
live-test/javascript/input.jsonl:65:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/javascript/input.jsonl:66:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/javascript/input.jsonl:67:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/javascript/input.jsonl:68:{"id":"audit-refs-Circle-cross-file","tool":"ide_find_usages","params":{"file":"src/normal.js","line":12,"column":7}}
live-test/javascript/input.jsonl:69:{"id":"audit-struct-accessors","tool":"ide_file_structure","params":{"file":"src/accessors.js"}}
live-test/javascript/input.jsonl:70:{"id":"audit-super-Box-area","tool":"ide_find_super_methods","params":{"file":"src/accessors.js","line":9,"column":9}}
live-test/javascript/input.jsonl:71:{"id":"audit-def-Number-parseInt-library","tool":"ide_find_definition","params":{"file":"src/quirks.js","line":5,"column":23,"scope":"project_and_libraries"}}
live-test/go/input.jsonl:1:{"id":"def-circle-area",         "tool":"ide_find_definition", "params":{"file":"normal.go","line":23,"column":18}}
live-test/go/input.jsonl:2:{"id":"def-rectangle-area",      "tool":"ide_find_definition", "params":{"file":"normal.go","line":34,"column":20}}
live-test/go/input.jsonl:3:{"id":"def-MakeDefaultShapes",   "tool":"ide_find_definition", "params":{"file":"normal.go","line":72,"column":6}}
live-test/go/input.jsonl:4:{"id":"def-quirks-fnvar-resolve",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":12,"column":10}}
live-test/go/input.jsonl:5:{"id":"def-quirks-method-value",   "tool":"ide_find_definition", "params":{"file":"quirks.go","line":91,"column":10}}
live-test/go/input.jsonl:6:{"id":"def-quirks-method-expr",    "tool":"ide_find_definition", "params":{"file":"quirks.go","line":97,"column":19}}
live-test/go/input.jsonl:7:{"id":"def-quirks-type-switch-s",  "tool":"ide_find_definition", "params":{"file":"quirks.go","line":121,"column":24}}
live-test/go/input.jsonl:8:{"id":"def-quirks-iface-dispatch", "tool":"ide_find_definition", "params":{"file":"quirks.go","line":66,"column":11}}
live-test/go/input.jsonl:9:{"id":"def-drawable-draw",        "tool":"ide_find_definition", "params":{"file":"normal.go","line":6,"column":2}}
live-test/go/input.jsonl:10:{"id":"refs-area-from-totalArea","tool":"ide_find_usages", "params":{"file":"normal.go","line":57,"column":12}}
live-test/go/input.jsonl:11:{"id":"refs-shapes-field",       "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":2}}
live-test/go/input.jsonl:12:{"id":"refs-shape-iface",        "tool":"ide_find_usages", "params":{"file":"normal.go","line":47,"column":11}}
live-test/go/input.jsonl:13:{"id":"refs-drawable-draw",      "tool":"ide_find_usages", "params":{"file":"normal.go","line":6,"column":2}}
live-test/go/input.jsonl:14:{"id":"refs-shape-collection-add","tool":"ide_find_usages","params":{"file":"normal.go","line":50,"column":28}}
live-test/go/input.jsonl:15:{"id":"type-hier-Circle",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":18,"column":6}}
live-test/go/input.jsonl:16:{"id":"type-hier-Rectangle",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":29,"column":6}}
live-test/go/input.jsonl:17:{"id":"type-hier-Square",        "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":40,"column":6}}
live-test/go/input.jsonl:18:{"id":"type-hier-baseShape",     "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":14,"column":6}}
live-test/go/input.jsonl:19:{"id":"call-hier-MakeDefault",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":2}}
live-test/go/input.jsonl:20:{"id":"call-hier-area-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":23,"column":18,"direction":"callers","maxDepth":2}}
live-test/go/input.jsonl:21:{"id":"find-class-Shape",        "tool":"ide_find_class",      "params":{"query":"Shape"}}
live-test/go/input.jsonl:22:{"id":"find-class-Rectangle",    "tool":"ide_find_class",      "params":{"query":"Rectangle"}}
live-test/go/input.jsonl:23:{"id":"find-class-Drawable",     "tool":"ide_find_class",      "params":{"query":"Drawable"}}
live-test/go/input.jsonl:24:{"id":"find-symbol-Area",        "tool":"ide_find_symbol",     "params":{"query":"Area"}}
live-test/go/input.jsonl:25:{"id":"find-symbol-qFnVar",      "tool":"ide_find_symbol",     "params":{"query":"qFnVar"}}
live-test/go/input.jsonl:26:{"id":"file-structure-normal",   "tool":"ide_file_structure",  "params":{"file":"normal.go"}}
live-test/go/input.jsonl:27:{"id":"file-structure-quirks",   "tool":"ide_file_structure",  "params":{"file":"quirks.go"}}
live-test/go/input.jsonl:28:{"id":"refs-Drawable-iface",         "tool":"ide_find_usages",     "params":{"file":"normal.go","line":5,"column":6}}
live-test/go/input.jsonl:29:{"id":"refs-Circle-struct",          "tool":"ide_find_usages",     "params":{"file":"normal.go","line":18,"column":6}}
live-test/go/input.jsonl:30:{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":5,"column":6}}
live-test/go/input.jsonl:31:{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",  "params":{"file":"normal.go","line":46,"column":6}}
live-test/go/input.jsonl:32:{"id":"call-hier-TotalArea-callees", "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":54,"column":28,"direction":"callees","maxDepth":2}}
live-test/go/input.jsonl:33:{"id":"call-hier-Describe-callers",  "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":11,"column":2,"direction":"callers","maxDepth":2}}
live-test/go/input.jsonl:34:{"id":"call-hier-Draw-callers",      "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":6,"column":2,"direction":"callers","maxDepth":2}}
live-test/go/input.jsonl:35:{"id":"call-hier-Largest-callees",   "tool":"ide_call_hierarchy",  "params":{"file":"normal.go","line":62,"column":28,"direction":"callees","maxDepth":2}}
live-test/go/input.jsonl:36:{"id":"find-class-Circle",           "tool":"ide_find_class",      "params":{"query":"Circle"}}
live-test/go/input.jsonl:37:{"id":"find-class-Square",           "tool":"ide_find_class",      "params":{"query":"Square"}}
live-test/go/input.jsonl:38:{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",      "params":{"query":"ShapeCollection"}}
live-test/go/input.jsonl:39:{"id":"find-symbol-Describe",        "tool":"ide_find_symbol",     "params":{"query":"Describe"}}
live-test/go/input.jsonl:40:{"id":"find-symbol-TotalArea",       "tool":"ide_find_symbol",     "params":{"query":"TotalArea"}}
live-test/go/input.jsonl:41:{"id":"find-symbol-Draw",            "tool":"ide_find_symbol",     "params":{"query":"Draw"}}
live-test/go/input.jsonl:42:{"id":"find-symbol-Largest",         "tool":"ide_find_symbol",     "params":{"query":"Largest"}}
live-test/go/input.jsonl:43:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"normal.go","line":23,"column":18,"scope":"project_and_libraries"}}
live-test/go/input.jsonl:44:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"Area","pageSize":2}}
live-test/go/input.jsonl:45:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/go/input.jsonl:46:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/go/input.jsonl:47:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/go/input.jsonl:48:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":40,"column":7,"direction":"supertypes"}}
live-test/go/input.jsonl:49:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"normal.go","line":9,"column":6,"direction":"subtypes"}}
live-test/go/input.jsonl:50:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"normal.go","line":72,"column":6,"direction":"callees","maxDepth":3}}
live-test/go/input.jsonl:51:{"id":"audit-find-symbol-qualified-IntCoercer-Coerce","tool":"ide_find_symbol","params":{"query":"IntCoercer.Coerce"}}
live-test/go/input.jsonl:52:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/go/input.jsonl:53:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/go/input.jsonl:54:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/go/input.jsonl:55:{"id":"audit-refs-baseShape-Describe-promoted","tool":"ide_find_usages","params":{"file":"normal.go","line":16,"column":20}}
live-test/go/input.jsonl:56:{"id":"audit-find-class-prefix-Shape","tool":"ide_find_class","params":{"query":"Shape","matchMode":"prefix"}}
live-test/python/input.jsonl:1:{"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
live-test/python/input.jsonl:2:{"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
live-test/python/input.jsonl:3:{"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
live-test/python/input.jsonl:4:{"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
live-test/python/input.jsonl:5:{"id":"def-int-builtin-via-rebinding-rhs","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":12,"column":10}}
live-test/python/input.jsonl:6:{"id":"def-super-init",             "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":44,"column":9}}
live-test/python/input.jsonl:7:{"id":"def-abstractmethod-decorator","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":11,"column":6}}
live-test/python/input.jsonl:8:{"id":"def-dict-dispatch-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":27,"column":26}}
live-test/python/input.jsonl:9:{"id":"def-lambda-wrap-int",        "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":32,"column":24}}
live-test/python/input.jsonl:10:{"id":"def-list-indexing-int",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":37,"column":14}}
live-test/python/input.jsonl:11:{"id":"def-quirk-rebound-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":13,"column":12}}
live-test/python/input.jsonl:12:{"id":"def-walrus-result",          "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":70,"column":16}}
live-test/python/input.jsonl:13:{"id":"def-multiple-assign-b",      "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":100,"column":19}}
live-test/python/input.jsonl:14:{"id":"def-nested-return-fn",       "tool":"ide_find_definition",      "params":{"file":"src/quirks.py","line":82,"column":12}}
live-test/python/input.jsonl:15:{"id":"refs-area-from-describe",    "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":12,"column":9}}
live-test/python/input.jsonl:16:{"id":"refs-shapes-field",          "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":49,"column":14}}
live-test/python/input.jsonl:17:{"id":"refs-shape-class",           "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":10,"column":7}}
live-test/python/input.jsonl:18:{"id":"refs-int-builtin-in-quirks", "tool":"ide_find_usages",      "params":{"file":"src/quirks.py","line":64,"column":20}}
live-test/python/input.jsonl:19:{"id":"refs-circle-radius",         "tool":"ide_find_usages",      "params":{"file":"src/normal.py","line":21,"column":14}}
live-test/python/input.jsonl:20:{"id":"impls-shape-area",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":12,"column":9}}
live-test/python/input.jsonl:21:{"id":"impls-drawable-draw",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":7,"column":9}}
live-test/python/input.jsonl:22:{"id":"super-Square-init",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":43,"column":9}}
live-test/python/input.jsonl:23:{"id":"super-circle-area",          "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":23,"column":9}}
live-test/python/input.jsonl:24:{"id":"super-rectangle-area",       "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":35,"column":9}}
live-test/python/input.jsonl:25:{"id":"type-hier-square",           "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":42,"column":7}}
live-test/python/input.jsonl:26:{"id":"type-hier-rectangle",        "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":30,"column":7}}
live-test/python/input.jsonl:27:{"id":"type-hier-shape",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":10,"column":7}}
live-test/python/input.jsonl:28:{"id":"call-hier-make-default",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":2}}
live-test/python/input.jsonl:29:{"id":"call-hier-area-callers",     "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":12,"column":9,"direction":"callers","maxDepth":2}}
live-test/python/input.jsonl:30:{"id":"call-hier-total-area",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":54,"column":9,"direction":"callees","maxDepth":2}}
live-test/python/input.jsonl:31:{"id":"find-class-Shape",           "tool":"ide_find_class",           "params":{"query":"Shape"}}
live-test/python/input.jsonl:32:{"id":"find-class-Rectangle",       "tool":"ide_find_class",           "params":{"query":"Rectangle"}}
live-test/python/input.jsonl:33:{"id":"find-symbol-area",           "tool":"ide_find_symbol",          "params":{"query":"area"}}
live-test/python/input.jsonl:34:{"id":"find-symbol-quirk-rebind",   "tool":"ide_find_symbol",          "params":{"query":"quirk_name_rebinding"}}
live-test/python/input.jsonl:35:{"id":"file-structure-normal",      "tool":"ide_file_structure",       "params":{"file":"src/normal.py"}}
live-test/python/input.jsonl:36:{"id":"file-structure-quirks",      "tool":"ide_file_structure",       "params":{"file":"src/quirks.py"}}
live-test/python/input.jsonl:37:{"id":"refs-Drawable-protocol",      "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":6,"column":7}}
live-test/python/input.jsonl:38:{"id":"refs-Rectangle-class",        "tool":"ide_find_usages",          "params":{"file":"src/normal.py","line":30,"column":7}}
live-test/python/input.jsonl:39:{"id":"impls-Shape-class",           "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":10,"column":7}}
live-test/python/input.jsonl:40:{"id":"impls-Rectangle-class",       "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":30,"column":7}}
live-test/python/input.jsonl:41:{"id":"impls-Circle-class",          "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":19,"column":7}}
live-test/python/input.jsonl:42:{"id":"impls-Shape-describe",        "tool":"ide_find_implementations", "params":{"file":"src/normal.py","line":15,"column":9}}
live-test/python/input.jsonl:43:{"id":"super-Circle-draw",           "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":26,"column":9}}
live-test/python/input.jsonl:44:{"id":"super-Rectangle-draw",        "tool":"ide_find_super_methods",   "params":{"file":"src/normal.py","line":38,"column":9}}
live-test/python/input.jsonl:45:{"id":"type-hier-Drawable",          "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":6,"column":7}}
live-test/python/input.jsonl:46:{"id":"type-hier-Circle",            "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":19,"column":7}}
live-test/python/input.jsonl:47:{"id":"type-hier-ShapeCollection",   "tool":"ide_type_hierarchy",       "params":{"file":"src/normal.py","line":47,"column":7}}
live-test/python/input.jsonl:48:{"id":"call-hier-describe-callers",  "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":15,"column":9,"direction":"callers","maxDepth":2}}
live-test/python/input.jsonl:49:{"id":"call-hier-largest-callees",   "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":57,"column":9,"direction":"callees","maxDepth":2}}
live-test/python/input.jsonl:50:{"id":"call-hier-add-callers",       "tool":"ide_call_hierarchy",       "params":{"file":"src/normal.py","line":51,"column":9,"direction":"callers","maxDepth":2}}
live-test/python/input.jsonl:51:{"id":"find-class-Circle",           "tool":"ide_find_class",           "params":{"query":"Circle"}}
live-test/python/input.jsonl:52:{"id":"find-class-Square",           "tool":"ide_find_class",           "params":{"query":"Square"}}
live-test/python/input.jsonl:53:{"id":"find-class-Drawable",         "tool":"ide_find_class",           "params":{"query":"Drawable"}}
live-test/python/input.jsonl:54:{"id":"find-class-ShapeCollection",  "tool":"ide_find_class",           "params":{"query":"ShapeCollection"}}
live-test/python/input.jsonl:55:{"id":"find-symbol-describe",        "tool":"ide_find_symbol",          "params":{"query":"describe"}}
live-test/python/input.jsonl:56:{"id":"find-symbol-total_area",      "tool":"ide_find_symbol",          "params":{"query":"total_area"}}
live-test/python/input.jsonl:57:{"id":"find-symbol-draw",            "tool":"ide_find_symbol",          "params":{"query":"draw"}}
live-test/python/input.jsonl:58:{"id":"find-symbol-largest",         "tool":"ide_find_symbol",          "params":{"query":"largest"}}
live-test/python/input.jsonl:59:{"id":"audit-refs-area-libraries-scope",      "tool":"ide_find_usages",         "params":{"file":"src/normal.py","line":12,"column":9,"scope":"project_and_libraries"}}
live-test/python/input.jsonl:60:{"id":"audit-find-symbol-area-paged",         "tool":"ide_find_symbol",         "params":{"query":"area","pageSize":2}}
live-test/python/input.jsonl:61:{"id":"audit-find-class-Sh-prefix",           "tool":"ide_find_class",          "params":{"query":"Sh","matchMode":"prefix"}}
live-test/python/input.jsonl:62:{"id":"audit-find-class-Circle-exact",        "tool":"ide_find_class",          "params":{"query":"Circle","matchMode":"exact"}}
live-test/python/input.jsonl:63:{"id":"audit-find-class-SC-camelCase",        "tool":"ide_find_class",          "params":{"query":"SC","matchMode":"camelCase"}}
live-test/python/input.jsonl:64:{"id":"audit-type-hier-Square-supertypes",    "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":42,"column":7,"direction":"supertypes"}}
live-test/python/input.jsonl:65:{"id":"audit-type-hier-Shape-subtypes",       "tool":"ide_type_hierarchy",      "params":{"file":"src/normal.py","line":10,"column":7,"direction":"subtypes"}}
live-test/python/input.jsonl:66:{"id":"audit-call-hier-makeDefault-d3",       "tool":"ide_call_hierarchy",      "params":{"file":"src/normal.py","line":63,"column":5,"direction":"callees","maxDepth":3}}
live-test/python/input.jsonl:67:{"id":"audit-find-symbol-qualified-Coercer-coerce","tool":"ide_find_symbol",   "params":{"query":"Coercer.coerce"}}
live-test/python/input.jsonl:68:{"id":"audit-find-class-no-match",            "tool":"ide_find_class",          "params":{"query":"NoSuchClassXyz"}}
live-test/python/input.jsonl:69:{"id":"audit-find-symbol-no-match",           "tool":"ide_find_symbol",         "params":{"query":"noSuchSymbolXyz"}}
live-test/python/input.jsonl:70:{"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
live-test/python/input.jsonl:71:{"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
live-test/python/input.jsonl:72:{"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
live-test/python/input.jsonl:73:{"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}

exec
/usr/bin/zsh -lc 'uv run python live-test/run.py --check-fixtures' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc 'head -n 3 live-test/python/expected.jsonl && tail -n 3 live-test/python/expected.jsonl' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
{"id":"def-circle-from-constructor-call","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}}
{"id":"def-rectangle-area-decl","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}}
{"id":"def-shape-describe-decl","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}}
{"id":"audit-def-classify-shape-kind","result":{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":107,"name":"kind","qualifiedName":"quirks.classify_shape"}}
{"id":"audit-refs-classify-shape-kind","result":{"totalCount":4,"usages":[{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":107,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":109,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":111,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":112,"usageType":"REFERENCE"}]}}
{"id":"audit-refs-import-alias-a","result":{"totalCount":2,"usages":[{"column":33,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":47,"usageType":"IMPORT"},{"column":12,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"}]}}

exec
/usr/bin/zsh -lc 'head -n 3 live-test/python/output.jsonl && tail -n 3 live-test/python/output.jsonl' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
{"id":"def-circle-from-constructor-call","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":20,"name":"__init__","qualifiedName":"normal.Circle.__init__"}}
{"id":"def-rectangle-area-decl","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":35,"name":"area","qualifiedName":"normal.Rectangle.area"}}
{"id":"def-shape-describe-decl","result":{"column":9,"enclosingScope":null,"file":"src/normal.py","kind":"METHOD","line":15,"name":"describe","qualifiedName":"normal.Shape.describe"}}
{"id":"audit-def-classify-shape-kind","result":{"column":9,"enclosingScope":null,"file":"src/quirks.py","kind":"VARIABLE","line":107,"name":"kind","qualifiedName":"quirks.classify_shape"}}
{"id":"audit-refs-classify-shape-kind","result":{"totalCount":4,"usages":[{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":107,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":109,"usageType":"REFERENCE"},{"column":9,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":111,"usageType":"REFERENCE"},{"column":12,"enclosingScope":["classify_shape"],"file":"src/quirks.py","line":112,"usageType":"REFERENCE"}]}}
{"id":"audit-refs-import-alias-a","result":{"totalCount":2,"usages":[{"column":33,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":47,"usageType":"IMPORT"},{"column":12,"enclosingScope":["quirk_star_import_simulation"],"file":"src/quirks.py","line":48,"usageType":"REFERENCE"}]}}

 succeeded in 230ms:
[go] 56 inputs, 56 expected, 0 issues
[java] 81 inputs, 81 expected, 0 issues
[javascript] 71 inputs, 71 expected, 0 issues
[kotlin] 67 inputs, 67 expected, 0 issues
[php] 70 inputs, 70 expected, 0 issues
[python] 73 inputs, 73 expected, 0 issues
[rust] 65 inputs, 65 expected, 0 issues
[typescript] 66 inputs, 66 expected, 0 issues
ALL: 0 issues

codex
The offline fixture check reports `0 issues`, and the expected/output counts are aligned at 549 rows. I’m now checking whether the new anchor validation is only syntactic or actually catches wrong-symbol drift, and simulating the `--tool` no-match cases.
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
root=Path('live-test')
for input_path in sorted(root.glob('*/input.jsonl')):
    lang=input_path.parent.name
    for i,line in enumerate(input_path.read_text().splitlines(),1):
        if not line.strip(): continue
        row=json.loads(line)
        p=row.get('params',{})
        if all(k in p for k in ('file','line','column')):
            file_path=input_path.parent / p['file']
            lines=file_path.read_text().splitlines()
            ch=lines[p['line']-1][p['column']-1]
            if not (ch=='_' or ch.isalnum()):
                text=lines[p['line']-1]
                start=max(0,p['column']-8); end=min(len(text),p['column']+8)
                print(f\"{input_path}:{i}: {row['id']} char={ch"'!r} context={text[start:end]!r}")
PY' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, json, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run)

def fake_ready(url, project_path): return None
def fake_post(url, request, timeout=60.0): return {'ok': request['params']['name']}
run.check_ready = fake_ready
run.post_jsonrpc = fake_post
with tempfile.TemporaryDirectory() as d:
    p=Path(d)
    (p/'input.jsonl').write_text(json.dumps({'id':'a','tool':'t1','params':{}})+'\\n')
    (p/'expected.jsonl').write_text(json.dumps({'id':'a','result':{'old':1}}, separators=(',',':'))+'\\n')
    passed, failed = run.run_language('tmp', p, 'http://unused', 'no_such_tool', True, prune=True)
    print('returned', passed, failed)
    print('expected:', (p/'expected.jsonl').read_text())
    print('output:', repr((p/'output.jsonl').read_text()))
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run)
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'expected.jsonl'
    p.write_text('{\"id\": [\"x\"], \"result\": {}}\\n')
    try:
        print(run._load_expected_by_id(p))
    except Exception as e:
        print(type(e).__name__, e)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run)
class FakeResp:
    def __enter__(self): return self
    def __exit__(self,*a): pass
    def read(self): return b'{\"result\": []}'
def fake_urlopen(req, timeout=60.0): return FakeResp()
run.urllib.request.urlopen = fake_urlopen
try:
    print(run.post_jsonrpc('http://unused', {'x':1}))
except Exception as e:
    print(type(e).__name__, e)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 152ms:
live-test/php/input.jsonl:10: refs-shapes-field char='$' context=' array $shapes ='

 succeeded in 211ms:
[tmp] http://unused
[tmp] BLESSED /tmp/tmpnadz795_/expected.jsonl
returned 0 0
expected: {"id":"a","result":{"old":1}}

output: ''

 succeeded in 177ms:
TypeError cannot use 'list' as a dict key (unhashable type: 'list')

 succeeded in 192ms:
{'tool_error_text': ''}

exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, json
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run)
for body in [b'{\"result\": [1]}', b'{\"result\": \"x\"}', b'{\"result\": {\"content\": [\"x\"]}}', b'{\"result\": {\"content\": [{\"text\": {}}]}}']:
    class FakeResp:
        def __enter__(self): return self
        def __exit__(self,*a): pass
        def read(self): return body
    run.urllib.request.urlopen = lambda req, timeout=60.0: FakeResp()
    try:
        print(body, '=>', run.post_jsonrpc('http://unused', {'x':1}))
    except Exception as e:
        print(body, '=>', type(e).__name__, e)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
for exp in sorted(Path('live-test').glob('*/expected.jsonl')):
    n=0; errors=0; ids=[]
    for line in exp.read_text().splitlines():
        if not line.strip(): continue
        n+=1
        row=json.loads(line)
        ids.append(row.get('id'))
        r=row.get('result')
        if isinstance(r, dict) and any(k in r for k in ('tool_error_text','transport_error','jsonrpc_error')):
            errors+=1
    print(f'{exp}: rows={n} unique={len(set(ids))} error_rows={errors}')
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
root=Path('live-test')
for exp in sorted(root.glob('*/expected.jsonl')):
    for i,line in enumerate(exp.read_text().splitlines(),1):
        if not line.strip(): continue
        row=json.loads(line)
        def walk(obj):
            if isinstance(obj, str):
                if '/home/' in obj or '/Users/' in obj or '\\\\\\\\' in obj or 'C:/' in obj or 'C:\\\\' in obj:
                    print(f'{exp}:{i}: {row.get(\"id\")}: {obj}')
            elif isinstance(obj, dict):
                for v in obj.values(): walk(v)
            elif isinstance(obj, list):
                for v in obj: walk(v)
        walk(row.get('result'))
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 145ms:
live-test/go/expected.jsonl: rows=56 unique=56 error_rows=0
live-test/java/expected.jsonl: rows=81 unique=81 error_rows=0
live-test/javascript/expected.jsonl: rows=71 unique=71 error_rows=0
live-test/kotlin/expected.jsonl: rows=67 unique=67 error_rows=0
live-test/php/expected.jsonl: rows=70 unique=70 error_rows=0
live-test/python/expected.jsonl: rows=73 unique=73 error_rows=0
live-test/rust/expected.jsonl: rows=65 unique=65 error_rows=0
live-test/typescript/expected.jsonl: rows=66 unique=66 error_rows=0

 succeeded in 148ms:
 succeeded in 197ms:
b'{"result": [1]}' => AttributeError 'list' object has no attribute 'get'
b'{"result": "x"}' => AttributeError 'str' object has no attribute 'get'
b'{"result": {"content": ["x"]}}' => AttributeError 'str' object has no attribute 'get'
b'{"result": {"content": [{"text": {}}]}}' => {'tool_error_text': {}}

exec
/usr/bin/zsh -lc "git diff -- live-test/run.py | sed -n '261,560p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
+            print(f"  ORPHAN expected id '{eid}' has no matching input — remove or rename.")
+        failed += len(orphan_ids)
 
     if bless:
-        expected_path.write_text(body)
+        # Refuse to bless rows that returned tool/transport errors unless
+        # explicitly overridden — otherwise a flaky IDE response gets locked
+        # in as truth.
+        error_ids = [eid for eid, r in fresh_results.items() if _result_has_error(r)]
+        if error_ids and not bless_errors:
+            print(f"  ERROR: refusing to bless {len(error_ids)} rows with tool/transport errors:")
+            for eid in error_ids[:5]:
+                print(f"    {eid}: {fresh_results[eid]}")
+            if len(error_ids) > 5:
+                print(f"    ... and {len(error_ids) - 5} more")
+            print(f"  Use --bless-errors to override.")
+            return passed, len(error_ids)
+        # Merge fresh results into existing expected, then write in input order.
+        merged: dict[str, Any] = dict(expected_by_id)
+        merged.update(fresh_results)
+        # Detect orphans (would be dropped). Require --prune to discard.
+        orphan_ids = sorted(set(merged) - input_ids)
+        if orphan_ids and not prune:
+            print(f"  ERROR: bless would drop {len(orphan_ids)} orphan expected ids: {orphan_ids[:5]}")
+            print(f"  Use --prune to discard them, or restore the input rows.")
+            return passed, len(orphan_ids)
+        if prune:
+            merged = {k: v for k, v in merged.items() if k in input_ids}
+        new_lines = [
+            _serialize_row(e["id"], merged[e["id"]])
+            for e in inputs
+            if e["id"] in merged
+        ]
+        _atomic_write(expected_path, "\n".join(new_lines) + ("\n" if new_lines else ""))
         print(f"[{lang}] BLESSED {expected_path}")
     else:
         print(f"[{lang}] {passed} passed, {failed} failed")
     return passed, failed
 
 
+def check_fixtures(root: Path, langs: list[str]) -> int:
+    """Offline validation: no IDE contact. Returns failure count.
+
+    Verifies per-language:
+      - input.jsonl: valid JSON, every row has a unique non-empty id.
+      - expected.jsonl: parses cleanly via _load_expected_by_id (strict).
+      - Orphan expected ids (id in expected but not in input).
+      - Anchor sanity: file+line+column probes target an existing file, a
+        line within bounds, and a non-whitespace character.
+    """
+    failures = 0
+    for lang in langs:
+        lang_dir = root / lang
+        input_path = lang_dir / "input.jsonl"
+        if not input_path.is_file():
+            print(f"[{lang}] no input.jsonl")
+            failures += 1
+            continue
+
+        inputs: list[dict] = []
+        seen: dict[str, int] = {}
+        per_lang_fail = 0
+        for i, line in enumerate(input_path.read_text().splitlines(), 1):
+            if not line.strip():
+                continue
+            try:
+                row = json.loads(line)
+            except json.JSONDecodeError as e:
+                print(f"[{lang}] input.jsonl:{i}: invalid JSON ({e})")
+                per_lang_fail += 1
+                continue
+            eid = row.get("id")
+            if not isinstance(eid, str) or not eid:
+                print(f"[{lang}] input.jsonl:{i}: missing/empty id")
+                per_lang_fail += 1
+                continue
+            if eid in seen:
+                print(f"[{lang}] input.jsonl:{i}: duplicate id '{eid}' (also row {seen[eid]})")
+                per_lang_fail += 1
+                continue
+            seen[eid] = i
+            inputs.append(row)
+
+        # Anchor sanity
+        for entry in inputs:
+            params = entry.get("params", {})
+            file_rel = params.get("file")
+            line_no = params.get("line")
+            col = params.get("column")
+            if not (file_rel and isinstance(line_no, int) and isinstance(col, int)):
+                continue
+            file_path = lang_dir / file_rel
+            if not file_path.is_file():
+                print(f"[{lang}] {entry['id']}: file '{file_rel}' not found")
+                per_lang_fail += 1
+                continue
+            file_lines = file_path.read_text().splitlines()
+            if line_no < 1 or line_no > len(file_lines):
+                print(f"[{lang}] {entry['id']}: line {line_no} out of bounds (file has {len(file_lines)} lines)")
+                per_lang_fail += 1
+                continue
+            line_text = file_lines[line_no - 1]
+            if col < 1 or col > len(line_text):
+                print(f"[{lang}] {entry['id']}: column {col} beyond line length ({len(line_text)})")
+                per_lang_fail += 1
+                continue
+            char = line_text[col - 1]
+            if char.isspace():
+                print(f"[{lang}] {entry['id']}: column {col} on line {line_no} of '{file_rel}' is whitespace")
+                per_lang_fail += 1
+
+        # Expected.jsonl strict load + orphan check
+        expected_path = lang_dir / "expected.jsonl"
+        expected: dict[str, Any] = {}
+        try:
+            expected = _load_expected_by_id(expected_path)
+        except ValueError as e:
+            print(f"[{lang}] {e}")
+            per_lang_fail += 1
+        input_ids = {e["id"] for e in inputs}
+        orphans = sorted(set(expected) - input_ids)
+        for eid in orphans:
+            print(f"[{lang}] orphan expected id '{eid}'")
+            per_lang_fail += 1
+
+        print(f"[{lang}] {len(inputs)} inputs, {len(expected)} expected, {per_lang_fail} issues")
+        failures += per_lang_fail
+    return failures
+
+
 def discover_languages(root: Path, only: str | None) -> list[str]:
     if only is not None:
         if not (root / only / "input.jsonl").is_file():
@@ -259,6 +510,21 @@ def main() -> int:
         action="store_true",
         help="Rewrite expected.jsonl from server output instead of diffing.",
     )
+    parser.add_argument(
+        "--bless-errors",
+        action="store_true",
+        help="Allow blessing rows that returned tool_error_text / transport_error / jsonrpc_error.",
+    )
+    parser.add_argument(
+        "--prune",
+        action="store_true",
+        help="During --bless, drop expected ids that no longer have a matching input row.",
+    )
+    parser.add_argument(
+        "--check-fixtures",
+        action="store_true",
+        help="Offline-validate input/expected files (no IDE calls).",
+    )
     args = parser.parse_args()
 
     root = Path(__file__).resolve().parent
@@ -267,6 +533,11 @@ def main() -> int:
         print(f"No fixtures found in {root}", file=sys.stderr)
         return 0
 
+    if args.check_fixtures:
+        failures = check_fixtures(root, langs)
+        print(f"ALL: {failures} issues")
+        return 0 if failures == 0 else 1
+
     total_pass = total_fail = 0
     for lang in langs:
         if args.url:
@@ -278,7 +549,8 @@ def main() -> int:
                 return 1
             url = f"http://127.0.0.1:{port}/index-mcp/streamable-http"
         passed, failed = run_language(
-            lang, root / lang, url, args.tool, args.bless
+            lang, root / lang, url, args.tool, args.bless,
+            bless_errors=args.bless_errors, prune=args.prune,
         )
         total_pass += passed
         total_fail += failed

exec
/usr/bin/zsh -lc "git diff -- live-test/run.py | sed -n '1,260p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
diff --git a/live-test/run.py b/live-test/run.py
index 44efb59..a2cb38f 100755
--- a/live-test/run.py
+++ b/live-test/run.py
@@ -13,6 +13,8 @@ from __future__ import annotations
 import argparse
 import difflib
 import json
+import os
+import re
 import sys
 import urllib.error
 import urllib.request
@@ -42,6 +44,55 @@ SORTABLE_ARRAYS = {
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
@@ -69,7 +120,7 @@ def normalize(obj: Any, project_root: str) -> Any:
     if isinstance(obj, list):
         return [normalize(item, project_root) for item in obj]
     if isinstance(obj, str):
-        return obj.replace(project_root, "${PROJECT_ROOT}")
+        return _normalize_library_paths(obj.replace(project_root, "${PROJECT_ROOT}"))
     return obj
 
 
@@ -88,11 +139,17 @@ def post_jsonrpc(url: str, request: dict, timeout: float = 60.0) -> Any:
     )
     try:
         with urllib.request.urlopen(req, timeout=timeout) as resp:
-            raw = json.loads(resp.read())
+            body = resp.read()
     except urllib.error.URLError as e:
         return {"transport_error": str(e.reason)}
     except (TimeoutError, ConnectionError, OSError) as e:
         return {"transport_error": f"{type(e).__name__}: {e}"}
+    try:
+        raw = json.loads(body)
+    except json.JSONDecodeError as e:
+        return {"transport_error": f"non-JSON envelope: {e}"}
+    if not isinstance(raw, dict):
+        return {"transport_error": f"unexpected envelope type: {type(raw).__name__}"}
     if "error" in raw:
         return {"jsonrpc_error": raw["error"]}
     result = raw.get("result", {}) or {}
@@ -148,14 +205,71 @@ def diff_lines(expected: Any, actual: Any) -> str:
     )
 
 
+def _load_expected_by_id(expected_path: Path) -> dict[str, Any]:
+    """Read expected.jsonl into an id → result dict. Strict: raises on
+    malformed JSON, missing fields, or duplicate ids — those signal a corrupt
+    snapshot, not a soft MISSING.
+    """
+    if not expected_path.is_file():
+        return {}
+    out: dict[str, Any] = {}
+    for i, line in enumerate(expected_path.read_text().splitlines(), 1):
+        if not line.strip():
+            continue
+        try:
+            row = json.loads(line)
+        except json.JSONDecodeError as e:
+            raise ValueError(f"{expected_path}:{i}: invalid JSON ({e})")
+        if not isinstance(row, dict) or "id" not in row or "result" not in row:
+            raise ValueError(f"{expected_path}:{i}: row missing 'id' or 'result' field")
+        eid = row["id"]
+        if eid in out:
+            raise ValueError(f"{expected_path}:{i}: duplicate expected id '{eid}'")
+        out[eid] = row["result"]
+    return out
+
+
+def _serialize_row(eid: str, result: Any) -> str:
+    return json.dumps({"id": eid, "result": result}, sort_keys=True, separators=(",", ":"))
+
+
+def _atomic_write(path: Path, content: str) -> None:
+    """Write content atomically: temp file + os.replace. Protects against
+    SIGINT, crashes, and concurrent writers truncating the snapshot."""
+    tmp = path.with_name(f".{path.name}.tmp.{os.getpid()}")
+    try:
+        tmp.write_text(content)
+        os.replace(tmp, path)
+    except Exception:
+        try:
+            tmp.unlink()
+        except FileNotFoundError:
+            pass
+        raise
+
+
+ERROR_KEYS = ("tool_error_text", "transport_error", "jsonrpc_error")
+
+
+def _result_has_error(result: Any) -> bool:
+    return isinstance(result, dict) and any(k in result for k in ERROR_KEYS)
+
+
 def run_language(
     lang: str,
     project_path: Path,
     url: str,
     tool_filter: str | None,
     bless: bool,
+    bless_errors: bool = False,
+    prune: bool = False,
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
@@ -172,59 +286,196 @@ def run_language(
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
+    try:
+        expected_by_id = _load_expected_by_id(expected_path)
+    except ValueError as e:
+        print(f"  ERROR: {e}")
+        return 0, 1
 
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
-            failed += 1
-            continue
-
-        try:
-            expected_obj = json.loads(expected_lines[i])
-        except json.JSONDecodeError as e:
-            print(f"  [{i + 1}] {eid} ERROR (expected.jsonl line {i + 1} not JSON: {e})")
+        if eid not in expected_by_id:
+            print(f"  {eid} MISSING (no expected entry for this id — bless?)")
             failed += 1
             continue
 
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
+    _atomic_write(output_path, "\n".join(output_lines) + ("\n" if output_lines else ""))
+
+    # Detect orphan expected rows (ids present in expected but not in input).
+    # Only meaningful in full (non-filtered) diff runs.
+    input_ids = {e["id"] for e in inputs}
+    if not bless and tool_filter is None:
+        orphan_ids = sorted(set(expected_by_id) - input_ids)
+        for eid in orphan_ids:

exec
/usr/bin/zsh -lc "git diff -- live-test/python/input.jsonl live-test/go/input.jsonl live-test/java/input.jsonl | sed -n '1,220p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
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
index 95c7332..b15b1d9 100644
--- a/live-test/python/input.jsonl
+++ b/live-test/python/input.jsonl
@@ -1,5 +1,4 @@
 {"id":"def-circle-from-constructor-call", "tool":"ide_find_definition",   "params":{"file":"src/normal.py","line":64,"column":13}}
-{"id":"def-Circle-constructor-call","tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":64,"column":13}}
 {"id":"def-rectangle-area-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":35,"column":9}}
 {"id":"def-shape-describe-decl",    "tool":"ide_find_definition",      "params":{"file":"src/normal.py","line":15,"column":9}}
 {"id":"def-make-default-shapes-decl","tool":"ide_find_definition",     "params":{"file":"src/normal.py","line":63,"column":5}}
@@ -71,6 +70,4 @@
 {"id":"audit-find-class-Shape-wildcard",      "tool":"ide_find_class",          "params":{"query":"Sh*","matchMode":"substring"}}
 {"id":"audit-def-classify-shape-kind","tool":"ide_find_definition","params":{"file":"src/quirks.py","line":107,"column":9}}
 {"id":"audit-refs-classify-shape-kind","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":107,"column":9}}
-{"id":"audit-super-Circle-draw-protocol","tool":"ide_find_super_methods","params":{"file":"src/normal.py","line":26,"column":9}}
-{"id":"audit-find-symbol-Coercer-coerce","tool":"ide_find_symbol","params":{"query":"Coercer.coerce"}}
 {"id":"audit-refs-import-alias-a","tool":"ide_find_usages","params":{"file":"src/quirks.py","line":47,"column":33}}

exec
/usr/bin/zsh -lc "git diff -- live-test/README.md | sed -n '1,260p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
diff --git a/live-test/README.md b/live-test/README.md
index 9f3547a..3222322 100644
--- a/live-test/README.md
+++ b/live-test/README.md
@@ -34,8 +34,40 @@ then run the harness.
 ./run.py --language python        # one language only
 ./run.py --tool ide_find_definition   # one tool across all languages
 ./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http   # override URL
+./run.py --check-fixtures         # validate input/expected files offline (no IDE calls)
 ```
 
+## Snapshot format
+
+Both `input.jsonl` and `expected.jsonl` are id-keyed JSONL. Each input row
+needs a unique `"id"` string. Each expected row is
+`{"id": <input-id>, "result": <normalized-output>}`. Output rows produced
+by `./run.py` use the same shape, written to `output.jsonl`.
+
+Rows are matched by id, not by position — so reordering input rows, adding
+rows, or deleting rows doesn't shift the rest of the snapshot. The diff
+output reports per-id status:
+
+- `<id> PASS` — output matches blessed expected.
+- `<id> FAIL` — output differs (unified diff shown).
+- `<id> MISSING` — no expected entry for this id (bless to add).
+- `ORPHAN expected id '<id>' has no matching input` — an expected entry
+  whose id is no longer in `input.jsonl`. Remove from expected (with
+  `--prune` during bless), or restore the input row.
+
+## Bless safety
+
+- `./run.py --bless` rewrites `expected.jsonl` from the live IDE output.
+- A bless refuses if any row returned `tool_error_text` / `transport_error`
+  / `jsonrpc_error` — use `--bless-errors` to override (rare; usually you
+  want to fix the IDE/probe first).
+- A bless refuses if it would drop orphan expected ids — use `--prune` to
+  explicitly discard them.
+- `--bless --tool X --language Y` only overwrites the rows whose `tool`
+  matches `X`; other rows in `expected.jsonl` are preserved.
+- Writes are atomic (temp file + `os.replace`), so SIGINT or concurrent
+  runs can't corrupt a snapshot.
+
 ## Version-bump workflow
 
 1. Bump `pluginVersion` in `gradle.properties`.
@@ -69,7 +101,7 @@ responses without re-blessing or scraping diff output.
   configured a non-default value in Settings → Tools → Index MCP Server.
 - **`PRECHECK: project is in dumb mode`** — wait for indexing to finish
   in the IDE, then retry.
-- **`MISSING (no expected.jsonl line N)`** — `expected.jsonl` is shorter
+- **`MISSING (no expected entry for this id)`** — `expected.jsonl` is shorter
   than `input.jsonl`. Likely you added a new entry to `input.jsonl` and
   haven't blessed yet. Run `--bless` to regenerate.
 - **All entries `FAIL` after a JDK / language toolchain update** — the

exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, json, tempfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('run','live-test/run.py')
run=importlib.util.module_from_spec(spec); spec.loader.exec_module(run)
with tempfile.TemporaryDirectory() as d:
    root=Path(d); lang=root/'x'; lang.mkdir(); (lang/'a.txt').write_text('abc\\n')
    (lang/'input.jsonl').write_text(json.dumps({'id':'a','tool':'t','params':{'file':'a.txt','line':1,'column':1}})+'\\n')
    (lang/'expected.jsonl').write_text('')
    print('failures=', run.check_fixtures(root, ['x']))
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 188ms:
[x] 1 inputs, 0 expected, 0 issues
failures= 0

exec
/usr/bin/zsh -lc "rg -n \"write_text|os\\.replace|_atomic_write|read_text\" live-test/run.py" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
216:    for i, line in enumerate(expected_path.read_text().splitlines(), 1):
236:def _atomic_write(path: Path, content: str) -> None:
237:    """Write content atomically: temp file + os.replace. Protects against
241:        tmp.write_text(content)
242:        os.replace(tmp, path)
286:        for line in input_path.read_text().splitlines()
342:    _atomic_write(output_path, "\n".join(output_lines) + ("\n" if output_lines else ""))
382:        _atomic_write(expected_path, "\n".join(new_lines) + ("\n" if new_lines else ""))
411:        for i, line in enumerate(input_path.read_text().splitlines(), 1):
445:            file_lines = file_path.read_text().splitlines()

exec
/usr/bin/zsh -lc 'tail -n 180 live-test-review.md' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
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

exec
/usr/bin/zsh -lc 'rg -n "''^('"\\|?\\s*(CRITICAL|HIGH|MEDIUM|LOW|BUG|SMELL|NIT)|Findings|severity|Trigger|Symptom|Blast)\" live-test-review.md live-test-review-2.md" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
live-test-review-2.md:2916:severity                  e.g. java:81:audit-diagnostics-warnings /problems[]/severity
live-test-review-2.md:3891:| CRITICAL | [run.py:273](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:273), [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless` to “migrate” legacy expected files | It does not migrate old snapshots by id; it rebaselines from the live IDE. Any current IDE/plugin regression becomes blessed truth. | Suite-wide | Add a one-time offline migration pairing current `input.jsonl` order to legacy `expected.jsonl`, then require a separate reviewed live rebless. |
live-test-review-2.md:3892:| HIGH | [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless --tool` before expected files are fully id-keyed | `_load_expected_by_id()` loads nothing from legacy rows, so the filtered bless writes only that tool’s rows and drops the rest. | 1 language or suite-wide | Refuse `--bless --tool` when any expected row lacks `id`/`result`. |
live-test-review-2.md:3893:| HIGH | [run.py:214](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:214) | Partial migration, bad merge, malformed JSON, duplicate expected IDs | Malformed/legacy rows are silently skipped; duplicate expected IDs silently last-write-win. User sees `MISSING`, not “corrupt snapshot.” | 1 row to 1 language | Make expected loading strict: fail on malformed rows, legacy rows after migration, and duplicate expected IDs. |
live-test-review-2.md:3894:| HIGH | [run.py:281](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:281), [run.py:284](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:284) | IDE returns `transport_error`, `jsonrpc_error`, or `tool_error_text` during bless | Harness blesses the error payload as expected output. | 1 row to suite-wide | During bless, fail unless result matches a known success schema or `--bless-errors` is explicit. |
live-test-review-2.md:3895:| HIGH | [run.py:242](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:242), [run.py:278](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:278) | IDE enters dumb mode after the precheck | Mid-run rows fail or get blessed as errors even though precheck passed. | 1 language | Recheck index status before each row or retry rows that return indexing/dumb-mode errors. |
live-test-review-2.md:3896:| HIGH | [run.py:303](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:303), [run.py:329](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:329) | SIGINT, crash, or two terminals blessing at once | `write_text()` can truncate or race `output.jsonl`/`expected.jsonl`. | 1 language | Write to a temp file, `fsync`, atomic `os.replace`, and take a per-language lock. |
live-test-review-2.md:3897:| HIGH | [run.py:321](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:321) | Accidental input row deletion followed by bless | Expected IDs without inputs are silently pruned during bless. | 1 row to 1 language | Print pruned IDs and fail unless `--prune` is passed. |
live-test-review-2.md:3898:| MEDIUM | [run.py:307](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307) | Running only `--tool` in daily workflow | Orphans are detected only in full non-filtered runs. Renames/deletions outside that tool rot unnoticed. | 1 language | Add `--check-fixtures` that validates IDs/orphans without calling IDEs, and run it before filtered runs. |
live-test-review-2.md:3899:| MEDIUM | [run.py:257](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257) | A row for another tool lacks `id` | Even `--tool some_other_tool` fails before filtering. | 1 language | Keep strict validation, but expose `--check-fixtures` so fixture errors are diagnosed separately from live runs. |
live-test-review-2.md:3900:| HIGH | [run.py:50](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50) | macOS, Windows, non-Toolbox installs, SDKMAN/asdf/system JDKs, rustup sources | Absolute SDK/stub paths leak or normalize only to `${HOME}/...`, causing noisy diffs. | Suite-wide on new host | Normalize via `Path.home()` plus platform-specific path families; add tests for `/Users`, Windows, `/usr/lib/jvm`, SDKMAN, rustup, JetBrains non-Toolbox layouts. |
live-test-review-2.md:3901:| MEDIUM | [run.py:85](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:85) | New under-home toolchain layout appears | Catch-all `${HOME}` hides the missing specific normalizer but leaves versioned subdirs to drift. | 1 language | Report any `${HOME}/.gradle`, `${HOME}/.cache/JetBrains`, `${HOME}/.local/share/JetBrains`, `${HOME}/.rustup` in output as normalization failures. |
live-test-review-2.md:3902:| HIGH | [rust/output.jsonl:6](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/output.jsonl:6), [python/output.jsonl:7](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/output.jsonl:7), [javascript/output.jsonl:5](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/output.jsonl:5) | Rust/Python/JS/PHP/JDK library version bump | Paths are tokenized, but library `line`/`column`, `name`, and overload lists still change. | Many rows in affected languages | For library files, compare `file` token + `qualifiedName` + `kind`; drop or separately bucket library line/column. |
live-test-review-2.md:3903:| MEDIUM | [run.py:34](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) | Tool adds meaningful `offset`, `pageSize`, `truncated`, `hasMore`, etc. | Normalizer drops fields globally, hiding pagination regressions. | Any paged tool | Drop noisy fields only by response type/path, not by key name everywhere. |
live-test-review-2.md:3904:| MEDIUM | [run.py:39](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:39), [run.py:115](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:115) | IDE changes order in hierarchy/call trees where order is semantic | Harness sorts `children`, `calls`, `hierarchy`, masking ordering bugs. | Call/type/super hierarchy rows | Sort only sets where order is contractually irrelevant; keep ordered snapshots for tree traversal. |
live-test-review-2.md:3905:| MEDIUM | [run.py:96](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:96) | Schema changes `line`/`column` to strings or nullable mixed types | Python sort can throw `TypeError`, aborting the language run. | 1 language | Coerce sort fields to typed tuples and validate schema before sorting. |
live-test-review-2.md:3906:| HIGH | [run.py:126](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:126) | MCP response moves from first text content to `structuredContent`, multiple content items, or `isError` | Harness ignores structured data or treats tool errors as JSON text. | Suite-wide after MCP/plugin change | Parse MCP result according to current schema: handle `isError`, all content items, and `structuredContent`. |
live-test-review-2.md:3907:| MEDIUM | [run.py:139](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:139) | IDE returns HTML/empty/non-JSON response | `json.loads(resp.read())` is uncaught; whole run crashes instead of row failure. | 1 language | Catch envelope `JSONDecodeError` and return structured transport failure. |
live-test-review-2.md:3908:| MEDIUM | [run.py:157](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:157) | Tool schemas rename `project_path`, `file`, `line`, `column`, `matchMode`, or `scope` | Every affected request fails or returns schema errors. | Tool-wide or suite-wide | Fetch/list tool schemas before running and validate input params against them. |
live-test-review-2.md:3909:| MEDIUM | [run.py:23](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:23), [run.py:380](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:380) | Java/Kotlin share one port, JS/TS share one port, or `--url` is used across all languages | Requests go to the wrong IDE/project or only one fixture is open. | 1-2 languages or suite-wide | Support per-language URLs/env vars and verify the IDE has the requested `project_path` open. |
live-test-review-2.md:3910:| MEDIUM | [java/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:1), [python/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1), [rust/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:1) | Inserting lines in fixtures before probed symbols | Line/column anchors hit the wrong token or no token. | Dozens of rows per edited file | Add a preflight that checks each anchor still points at an expected identifier string. |
live-test-review-2.md:3911:| HIGH | [java/input.jsonl:10](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:10), [javascript/input.jsonl:11](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/input.jsonl:11) | Adding a legitimate usage upstream/downstream | `find_usages` counts and ordering change across many snapshots. | 1 language | Keep usage fixtures frozen or split “search universe” files from editable fixture examples. |
live-test-review-2.md:3912:| HIGH | [java/input.jsonl:33](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), [go/input.jsonl:21](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:21) | Adding `ShapeFactory`, `ShapeError`, etc. | Broad `find_class` queries return extra hits. | Many find-class rows | Use `matchMode:"exact"` for baseline invariants; keep fuzzy matching in dedicated small fixtures. |
live-test-review-2.md:3913:| HIGH | [java/input.jsonl:37](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:37), [rust/input.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:30) | Adding methods named `area`, `draw`, `Describe`, etc. | Broad `find_symbol` rows become noisy. | Many find-symbol rows | Assert exact expected subset plus count only for specific fuzzy-search tests. |
live-test-review-2.md:3914:| MEDIUM | [java/output.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:73), [php/output.jsonl:66](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/output.jsonl:66) | Qualified search behavior improves | Current snapshots bless false positives like `ShapeCollection.totalArea` for `Shape.area`. A real fix will look like a regression. | 4 languages | Mark these as known-bad assertions or change expected semantics to require only the qualified target. |
live-test-review-2.md:3915:| LOW | [python/input.jsonl:67](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:67), [python/input.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:73) | Either row changes/fails | Two IDs still run identical `Coercer.coerce` queries and duplicate noise. | 2 rows | Delete one or change one query to exercise a distinct path. |
live-test-review-2.md:3916:| HIGH | [java/input.jsonl:40](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:40), [python/input.jsonl:35](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:35) | Any fixture refactor or line move | `ide_file_structure` snapshots churn on line/column/name ordering, not just structural regressions. | Whole file-structure row | Compare normalized structure names/kinds separately from locations. |
live-test-review-2.md:3917:| HIGH | [java/output.jsonl:81](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:81) | JetBrains inspection text changes, file-open state changes, weak-warning policy changes | Diagnostics row fails on English message text and warning counts. | Java diagnostics rows | Snapshot stable fields; put message text behind targeted golden tests only. |
live-test-review-2.md:3918:| HIGH | [java/output.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:30) | JDK adds/removes implementations or changes class stubs | Call hierarchy rows explode with JDK/library callees. | Java/Kotlin and Rust/Python library-heavy rows | Default hierarchy/call tests to project scope; add one explicit library-scope smoke test. |
live-test-review-2.md:3919:| LOW | [README.md:72](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:72) | Someone follows troubleshooting after id-keying | README still says “no expected.jsonl line N”; current harness reports missing IDs. | Human workflow | Update README to id-keyed migration and orphan behavior. |
live-test-review-2.md:3935:| CRITICAL | [run.py:273](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:273), [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless` to “migrate” legacy expected files | It does not migrate old snapshots by id; it rebaselines from the live IDE. Any current IDE/plugin regression becomes blessed truth. | Suite-wide | Add a one-time offline migration pairing current `input.jsonl` order to legacy `expected.jsonl`, then require a separate reviewed live rebless. |
live-test-review-2.md:3936:| HIGH | [run.py:317](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:317) | Running `--bless --tool` before expected files are fully id-keyed | `_load_expected_by_id()` loads nothing from legacy rows, so the filtered bless writes only that tool’s rows and drops the rest. | 1 language or suite-wide | Refuse `--bless --tool` when any expected row lacks `id`/`result`. |
live-test-review-2.md:3937:| HIGH | [run.py:214](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:214) | Partial migration, bad merge, malformed JSON, duplicate expected IDs | Malformed/legacy rows are silently skipped; duplicate expected IDs silently last-write-win. User sees `MISSING`, not “corrupt snapshot.” | 1 row to 1 language | Make expected loading strict: fail on malformed rows, legacy rows after migration, and duplicate expected IDs. |
live-test-review-2.md:3938:| HIGH | [run.py:281](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:281), [run.py:284](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:284) | IDE returns `transport_error`, `jsonrpc_error`, or `tool_error_text` during bless | Harness blesses the error payload as expected output. | 1 row to suite-wide | During bless, fail unless result matches a known success schema or `--bless-errors` is explicit. |
live-test-review-2.md:3939:| HIGH | [run.py:242](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:242), [run.py:278](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:278) | IDE enters dumb mode after the precheck | Mid-run rows fail or get blessed as errors even though precheck passed. | 1 language | Recheck index status before each row or retry rows that return indexing/dumb-mode errors. |
live-test-review-2.md:3940:| HIGH | [run.py:303](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:303), [run.py:329](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:329) | SIGINT, crash, or two terminals blessing at once | `write_text()` can truncate or race `output.jsonl`/`expected.jsonl`. | 1 language | Write to a temp file, `fsync`, atomic `os.replace`, and take a per-language lock. |
live-test-review-2.md:3941:| HIGH | [run.py:321](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:321) | Accidental input row deletion followed by bless | Expected IDs without inputs are silently pruned during bless. | 1 row to 1 language | Print pruned IDs and fail unless `--prune` is passed. |
live-test-review-2.md:3942:| MEDIUM | [run.py:307](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:307) | Running only `--tool` in daily workflow | Orphans are detected only in full non-filtered runs. Renames/deletions outside that tool rot unnoticed. | 1 language | Add `--check-fixtures` that validates IDs/orphans without calling IDEs, and run it before filtered runs. |
live-test-review-2.md:3943:| MEDIUM | [run.py:257](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:257) | A row for another tool lacks `id` | Even `--tool some_other_tool` fails before filtering. | 1 language | Keep strict validation, but expose `--check-fixtures` so fixture errors are diagnosed separately from live runs. |
live-test-review-2.md:3944:| HIGH | [run.py:50](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:50) | macOS, Windows, non-Toolbox installs, SDKMAN/asdf/system JDKs, rustup sources | Absolute SDK/stub paths leak or normalize only to `${HOME}/...`, causing noisy diffs. | Suite-wide on new host | Normalize via `Path.home()` plus platform-specific path families; add tests for `/Users`, Windows, `/usr/lib/jvm`, SDKMAN, rustup, JetBrains non-Toolbox layouts. |
live-test-review-2.md:3945:| MEDIUM | [run.py:85](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:85) | New under-home toolchain layout appears | Catch-all `${HOME}` hides the missing specific normalizer but leaves versioned subdirs to drift. | 1 language | Report any `${HOME}/.gradle`, `${HOME}/.cache/JetBrains`, `${HOME}/.local/share/JetBrains`, `${HOME}/.rustup` in output as normalization failures. |
live-test-review-2.md:3946:| HIGH | [rust/output.jsonl:6](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/output.jsonl:6), [python/output.jsonl:7](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/output.jsonl:7), [javascript/output.jsonl:5](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/output.jsonl:5) | Rust/Python/JS/PHP/JDK library version bump | Paths are tokenized, but library `line`/`column`, `name`, and overload lists still change. | Many rows in affected languages | For library files, compare `file` token + `qualifiedName` + `kind`; drop or separately bucket library line/column. |
live-test-review-2.md:3947:| MEDIUM | [run.py:34](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:34) | Tool adds meaningful `offset`, `pageSize`, `truncated`, `hasMore`, etc. | Normalizer drops fields globally, hiding pagination regressions. | Any paged tool | Drop noisy fields only by response type/path, not by key name everywhere. |
live-test-review-2.md:3948:| MEDIUM | [run.py:39](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:39), [run.py:115](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:115) | IDE changes order in hierarchy/call trees where order is semantic | Harness sorts `children`, `calls`, `hierarchy`, masking ordering bugs. | Call/type/super hierarchy rows | Sort only sets where order is contractually irrelevant; keep ordered snapshots for tree traversal. |
live-test-review-2.md:3949:| MEDIUM | [run.py:96](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:96) | Schema changes `line`/`column` to strings or nullable mixed types | Python sort can throw `TypeError`, aborting the language run. | 1 language | Coerce sort fields to typed tuples and validate schema before sorting. |
live-test-review-2.md:3950:| HIGH | [run.py:126](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:126) | MCP response moves from first text content to `structuredContent`, multiple content items, or `isError` | Harness ignores structured data or treats tool errors as JSON text. | Suite-wide after MCP/plugin change | Parse MCP result according to current schema: handle `isError`, all content items, and `structuredContent`. |
live-test-review-2.md:3951:| MEDIUM | [run.py:139](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:139) | IDE returns HTML/empty/non-JSON response | `json.loads(resp.read())` is uncaught; whole run crashes instead of row failure. | 1 language | Catch envelope `JSONDecodeError` and return structured transport failure. |
live-test-review-2.md:3952:| MEDIUM | [run.py:157](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:157) | Tool schemas rename `project_path`, `file`, `line`, `column`, `matchMode`, or `scope` | Every affected request fails or returns schema errors. | Tool-wide or suite-wide | Fetch/list tool schemas before running and validate input params against them. |
live-test-review-2.md:3953:| MEDIUM | [run.py:23](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:23), [run.py:380](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:380) | Java/Kotlin share one port, JS/TS share one port, or `--url` is used across all languages | Requests go to the wrong IDE/project or only one fixture is open. | 1-2 languages or suite-wide | Support per-language URLs/env vars and verify the IDE has the requested `project_path` open. |
live-test-review-2.md:3954:| MEDIUM | [java/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:1), [python/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:1), [rust/input.jsonl:1](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:1) | Inserting lines in fixtures before probed symbols | Line/column anchors hit the wrong token or no token. | Dozens of rows per edited file | Add a preflight that checks each anchor still points at an expected identifier string. |
live-test-review-2.md:3955:| HIGH | [java/input.jsonl:10](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:10), [javascript/input.jsonl:11](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/javascript/input.jsonl:11) | Adding a legitimate usage upstream/downstream | `find_usages` counts and ordering change across many snapshots. | 1 language | Keep usage fixtures frozen or split “search universe” files from editable fixture examples. |
live-test-review-2.md:3956:| HIGH | [java/input.jsonl:33](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:33), [go/input.jsonl:21](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/go/input.jsonl:21) | Adding `ShapeFactory`, `ShapeError`, etc. | Broad `find_class` queries return extra hits. | Many find-class rows | Use `matchMode:"exact"` for baseline invariants; keep fuzzy matching in dedicated small fixtures. |
live-test-review-2.md:3957:| HIGH | [java/input.jsonl:37](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:37), [rust/input.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/rust/input.jsonl:30) | Adding methods named `area`, `draw`, `Describe`, etc. | Broad `find_symbol` rows become noisy. | Many find-symbol rows | Assert exact expected subset plus count only for specific fuzzy-search tests. |
live-test-review-2.md:3958:| MEDIUM | [java/output.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:73), [php/output.jsonl:66](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/php/output.jsonl:66) | Qualified search behavior improves | Current snapshots bless false positives like `ShapeCollection.totalArea` for `Shape.area`. A real fix will look like a regression. | 4 languages | Mark these as known-bad assertions or change expected semantics to require only the qualified target. |
live-test-review-2.md:3959:| LOW | [python/input.jsonl:67](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:67), [python/input.jsonl:73](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:73) | Either row changes/fails | Two IDs still run identical `Coercer.coerce` queries and duplicate noise. | 2 rows | Delete one or change one query to exercise a distinct path. |
live-test-review-2.md:3960:| HIGH | [java/input.jsonl:40](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/input.jsonl:40), [python/input.jsonl:35](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/python/input.jsonl:35) | Any fixture refactor or line move | `ide_file_structure` snapshots churn on line/column/name ordering, not just structural regressions. | Whole file-structure row | Compare normalized structure names/kinds separately from locations. |
live-test-review-2.md:3961:| HIGH | [java/output.jsonl:81](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:81) | JetBrains inspection text changes, file-open state changes, weak-warning policy changes | Diagnostics row fails on English message text and warning counts. | Java diagnostics rows | Snapshot stable fields; put message text behind targeted golden tests only. |
live-test-review-2.md:3962:| HIGH | [java/output.jsonl:30](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/java/output.jsonl:30) | JDK adds/removes implementations or changes class stubs | Call hierarchy rows explode with JDK/library callees. | Java/Kotlin and Rust/Python library-heavy rows | Default hierarchy/call tests to project scope; add one explicit library-scope smoke test. |
live-test-review-2.md:3963:| LOW | [README.md:72](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:72) | Someone follows troubleshooting after id-keying | README still says “no expected.jsonl line N”; current harness reports missing IDs. | Human workflow | Update README to id-keyed migration and orphan behavior. |

exec
/usr/bin/zsh -lc 'tail -n 220 live-test-review-2.md' in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
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

exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import json
from pathlib import Path
bad=0; total=0
for exp in sorted(Path('live-test').glob('*/expected.jsonl')):
    out=exp.with_name('output.jsonl')
    if not out.exists(): continue
    exp_rows=[json.loads(l) for l in exp.read_text().splitlines() if l.strip()]
    out_rows=[json.loads(l) for l in out.read_text().splitlines() if l.strip()]
    exp_by={r['id']:r['result'] for r in exp_rows}
    out_by={r['id']:r['result'] for r in out_rows}
    ids=set(exp_by)|set(out_by)
    for eid in sorted(ids):
        total+=1
        if exp_by.get(eid)"'!=out_by.get(eid):
            bad+=1
            print(f'"'{exp.parent.name}: {eid} differs')
print('total_ids', total, 'diffs', bad)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 127ms:
total_ids 549 diffs 0

codex
The migrated snapshots and current outputs compare equal by id: 549 ids, 0 diffs. I also confirmed there are no raw `/home`, `/Users`, or Windows-looking absolute paths left in `expected.jsonl`.
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, json, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec); spec.loader.exec_module(run)
run.check_ready = lambda url, project_path: None
run.post_jsonrpc = lambda url, req, timeout=60.0: {'transport_error':'boom'}
with tempfile.TemporaryDirectory() as d:
    p=Path(d)
    (p/'input.jsonl').write_text(json.dumps({'id':'a','tool':'t','params':{}})+'\\n')
    (p/'expected.jsonl').write_text(json.dumps({'id':'a','result':{'ok':1}}, separators=(',',':'))+'\\n')
    print('no override:', run.run_language('tmp', p, 'u', None, True, bless_errors=False))
    print('expected after no override:', (p/'expected.jsonl').read_text())
    print('override:', run.run_language('tmp', p, 'u', None, True, bless_errors=True))
    print('expected after override:', (p/'expected.jsonl').read_text())
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, json, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec); spec.loader.exec_module(run)
run.check_ready = lambda url, project_path: None
run.post_jsonrpc = lambda url, req, timeout=60.0: {'ok': req['params']['name']}
with tempfile.TemporaryDirectory() as d:
    p=Path(d)
    (p/'input.jsonl').write_text(json.dumps({'id':'a','tool':'t','params':{}})+'\\n')
    (p/'expected.jsonl').write_text('\\n'.join([
        json.dumps({'id':'a','result':{'old':1}}, separators=(',',':')),
        json.dumps({'id':'orphan','result':{'old':2}}, separators=(',',':')),
    ])+'\\n')
    print('no prune:', run.run_language('tmp', p, 'u', None, True, prune=False))
    print('expected after no prune:', (p/'expected.jsonl').read_text())
    print('with prune:', run.run_language('tmp', p, 'u', None, True, prune=True))
    print('expected after prune:', (p/'expected.jsonl').read_text())
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
exec
/usr/bin/zsh -lc "uv run python - <<'PY'
import importlib.util, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('run', 'live-test/run.py')
run = importlib.util.module_from_spec(spec); spec.loader.exec_module(run)
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'expected.jsonl'
    samples = {
      'trailing_newline': '{\"id\":\"a\",\"result\":{}}\\n',
      'trailing_spaces': '{\"id\":\"a\",\"result\":{}}   \\n',
      'blank_line': '\\n{\"id\":\"a\",\"result\":{}}\\n\\n',
    }
    for name, content in samples.items():
        p.write_text(content)
        try:
            print(name, run._load_expected_by_id(p))
        except Exception as e:
            print(name, type(e).__name__, e)
PY" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 203ms:
[tmp] u
  a BLESS
  ERROR: refusing to bless 1 rows with tool/transport errors:
    a: {'transport_error': 'boom'}
  Use --bless-errors to override.
no override: (1, 1)
expected after no override: {"id":"a","result":{"ok":1}}

[tmp] u
  a BLESS
[tmp] BLESSED /tmp/tmpy7a2dukm/expected.jsonl
override: (1, 0)
expected after override: {"id":"a","result":{"transport_error":"boom"}}


 succeeded in 212ms:
[tmp] u
  a BLESS
  ERROR: bless would drop 1 orphan expected ids: ['orphan']
  Use --prune to discard them, or restore the input rows.
no prune: (1, 1)
expected after no prune: {"id":"a","result":{"old":1}}
{"id":"orphan","result":{"old":2}}

[tmp] u
  a BLESS
[tmp] BLESSED /tmp/tmprrf53v28/expected.jsonl
with prune: (1, 0)
expected after prune: {"id":"a","result":{"ok":"t"}}


 succeeded in 218ms:
trailing_newline {'a': {}}
trailing_spaces {'a': {}}
blank_line {'a': {}}

exec
/usr/bin/zsh -lc "nl -ba live-test/README.md | sed -n '58,72p'" in /home/ubuntu/dev/jetbrains-index-mcp-plugin
 succeeded in 0ms:
    58	## Bless safety
    59	
    60	- `./run.py --bless` rewrites `expected.jsonl` from the live IDE output.
    61	- A bless refuses if any row returned `tool_error_text` / `transport_error`
    62	  / `jsonrpc_error` — use `--bless-errors` to override (rare; usually you
    63	  want to fix the IDE/probe first).
    64	- A bless refuses if it would drop orphan expected ids — use `--prune` to
    65	  explicitly discard them.
    66	- `--bless --tool X --language Y` only overwrites the rows whose `tool`
    67	  matches `X`; other rows in `expected.jsonl` are preserved.
    68	- Writes are atomic (temp file + `os.replace`), so SIGINT or concurrent
    69	  runs can't corrupt a snapshot.
    70	
    71	## Version-bump workflow
    72	

codex
No CRITICAL/HIGH issues found.

| Severity | file:line | finding | fix |
|---|---|---|---|
| MEDIUM | [run.py:302](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:302) | `--tool` matching zero rows succeeds. `--bless --tool no_such --prune` writes empty `output.jsonl`, prints `BLESSED`, returns `(0, 0)`, and can prune existing orphan rows despite running no probes. | After filtering, fail when `tool_filter and not filtered_inputs` before output/expected writes. |
| MEDIUM | [run.py:306](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:306), [run.py:367](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:367), [README.md:68](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:68) | `_atomic_write` prevents torn files, but concurrent filtered blesses can still lose updates: both runs load the same old expected file, merge different fresh rows, and the later `os.replace` overwrites the earlier merge. README overstates “concurrent runs can’t corrupt a snapshot.” | Add a per-language lock, or re-read/detect expected-file changes immediately before replace and retry the merge. Narrow the README claim. |
| MEDIUM | [run.py:155](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:155) | `post_jsonrpc` now rejects non-dict envelopes, but still crashes on malformed dict envelopes such as `{"result":[1]}` or `{"result":{"content":["x"]}}` because it assumes `.get()` exists on nested values. | Validate `result` is a dict, `content` is a list, and the first item is a dict with string-ish `text`; otherwise return structured `transport_error` / `tool_error_text`. |
| LOW | [run.py:225](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225) | `_load_expected_by_id` does not validate expected IDs are non-empty strings. `{"id": [], "result": {}}` raises uncaught `TypeError`; numeric/empty IDs load and later become confusing orphan/missing noise. | Require `eid` to be a non-empty `str` before duplicate checking. |
| LOW | [run.py:468](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:468) | `--check-fixtures` checks orphan expected IDs but not missing expected IDs. An empty/incomplete `expected.jsonl` can report `0 issues` offline, then fail live as `MISSING`. | Add `missing = input_ids - set(expected)` and report each missing expected ID. |
| LOW | [run.py:455](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:455) | Anchor validation only checks non-whitespace. A shifted probe landing on punctuation, a string literal, or the wrong identifier still passes. Current fixtures are clean under this rule; the only non-alnum anchor is PHP `$shapes` on `$`, which is legitimate. | Store an expected anchor token/string per row, or validate the containing token span instead of only `char.isspace()`. |

Verified landed correctly:
- Bundle A: atomic writes are used for both `output.jsonl` and `expected.jsonl`; strict expected loading exists; bless blocks error-shaped results unless `--bless-errors`; bless blocks orphan drops unless `--prune`; JSON envelope decode/non-dict envelope handling landed; CLI flags exist.
- Bundle B: `check_fixtures()` is offline and reports `0 issues`; the named Python duplicate row is gone; README has the new snapshot/bless sections and id-keyed `MISSING` text.
- Migration: current `expected.jsonl` and `output.jsonl` compare equal by id: `549` ids, `0` diffs. No raw `/home`, `/Users`, or Windows-looking absolute paths remain in `expected.jsonl`.
- Edge checks: trailing newlines, blank lines, and trailing whitespace in `expected.jsonl` are accepted by the strict loader. `_atomic_write` uses a temp path in the same directory, so `os.replace` is same-filesystem.
tokens used
98,583
No CRITICAL/HIGH issues found.

| Severity | file:line | finding | fix |
|---|---|---|---|
| MEDIUM | [run.py:302](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:302) | `--tool` matching zero rows succeeds. `--bless --tool no_such --prune` writes empty `output.jsonl`, prints `BLESSED`, returns `(0, 0)`, and can prune existing orphan rows despite running no probes. | After filtering, fail when `tool_filter and not filtered_inputs` before output/expected writes. |
| MEDIUM | [run.py:306](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:306), [run.py:367](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:367), [README.md:68](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/README.md:68) | `_atomic_write` prevents torn files, but concurrent filtered blesses can still lose updates: both runs load the same old expected file, merge different fresh rows, and the later `os.replace` overwrites the earlier merge. README overstates “concurrent runs can’t corrupt a snapshot.” | Add a per-language lock, or re-read/detect expected-file changes immediately before replace and retry the merge. Narrow the README claim. |
| MEDIUM | [run.py:155](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:155) | `post_jsonrpc` now rejects non-dict envelopes, but still crashes on malformed dict envelopes such as `{"result":[1]}` or `{"result":{"content":["x"]}}` because it assumes `.get()` exists on nested values. | Validate `result` is a dict, `content` is a list, and the first item is a dict with string-ish `text`; otherwise return structured `transport_error` / `tool_error_text`. |
| LOW | [run.py:225](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:225) | `_load_expected_by_id` does not validate expected IDs are non-empty strings. `{"id": [], "result": {}}` raises uncaught `TypeError`; numeric/empty IDs load and later become confusing orphan/missing noise. | Require `eid` to be a non-empty `str` before duplicate checking. |
| LOW | [run.py:468](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:468) | `--check-fixtures` checks orphan expected IDs but not missing expected IDs. An empty/incomplete `expected.jsonl` can report `0 issues` offline, then fail live as `MISSING`. | Add `missing = input_ids - set(expected)` and report each missing expected ID. |
| LOW | [run.py:455](/home/ubuntu/dev/jetbrains-index-mcp-plugin/live-test/run.py:455) | Anchor validation only checks non-whitespace. A shifted probe landing on punctuation, a string literal, or the wrong identifier still passes. Current fixtures are clean under this rule; the only non-alnum anchor is PHP `$shapes` on `$`, which is legitimate. | Store an expected anchor token/string per row, or validate the containing token span instead of only `char.isspace()`. |

Verified landed correctly:
- Bundle A: atomic writes are used for both `output.jsonl` and `expected.jsonl`; strict expected loading exists; bless blocks error-shaped results unless `--bless-errors`; bless blocks orphan drops unless `--prune`; JSON envelope decode/non-dict envelope handling landed; CLI flags exist.
- Bundle B: `check_fixtures()` is offline and reports `0 issues`; the named Python duplicate row is gone; README has the new snapshot/bless sections and id-keyed `MISSING` text.
- Migration: current `expected.jsonl` and `output.jsonl` compare equal by id: `549` ids, `0` diffs. No raw `/home`, `/Users`, or Windows-looking absolute paths remain in `expected.jsonl`.
- Edge checks: trailing newlines, blank lines, and trailing whitespace in `expected.jsonl` are accepted by the strict loader. `_atomic_write` uses a temp path in the same directory, so `os.replace` is same-filesystem.
