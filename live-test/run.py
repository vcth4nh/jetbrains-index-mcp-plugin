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
    "problems", "intentions", "buildErrors", "testResults", "hierarchy",
}


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
        return obj.replace(project_root, "${PROJECT_ROOT}")
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


def run_language(
    lang: str,
    project_path: Path,
    url: str,
    tool_filter: str | None,
    bless: bool,
) -> tuple[int, int]:
    """Returns (passed, failed)."""
    print(f"[{lang}] {url}")

    err = check_ready(url, str(project_path))
    if err is not None:
        print(f"  PRECHECK: {err}")
        print(f"[{lang}] SKIPPED (precheck failed)")
        return 0, 1

    input_path = project_path / "input.jsonl"
    expected_path = project_path / "expected.jsonl"
    inputs = [
        json.loads(line)
        for line in input_path.read_text().splitlines()
        if line.strip()
    ]
    if tool_filter:
        inputs = [e for e in inputs if e["tool"] == tool_filter]

    expected_lines: list[str] = []
    if not bless and expected_path.is_file():
        expected_lines = expected_path.read_text().splitlines()

    new_lines: list[str] = []
    passed = failed = 0
    for i, entry in enumerate(inputs):
        eid = entry.get("id", f"#{i + 1}")
        request = build_request(entry, str(project_path))
        result = normalize(post_jsonrpc(url, request), str(project_path))
        serialized = json.dumps(result, sort_keys=True, separators=(",", ":"))

        if bless:
            new_lines.append(serialized)
            print(f"  [{i + 1}] {eid} BLESS")
            passed += 1
            continue

        if i >= len(expected_lines) or not expected_lines[i].strip():
            print(f"  [{i + 1}] {eid} MISSING (no expected.jsonl line {i + 1} — bless?)")
            failed += 1
            continue

        try:
            expected_obj = json.loads(expected_lines[i])
        except json.JSONDecodeError as e:
            print(f"  [{i + 1}] {eid} ERROR (expected.jsonl line {i + 1} not JSON: {e})")
            failed += 1
            continue

        if expected_obj == result:
            print(f"  [{i + 1}] {eid} PASS")
            passed += 1
        else:
            print(f"  [{i + 1}] {eid} FAIL")
            for line in diff_lines(expected_obj, result).splitlines():
                print(f"    {line}")
            failed += 1

    if bless:
        body = "\n".join(new_lines) + ("\n" if new_lines else "")
        expected_path.write_text(body)
        print(f"[{lang}] BLESSED {expected_path}")
    else:
        print(f"[{lang}] {passed} passed, {failed} failed")
    return passed, failed


def discover_languages(root: Path, only: str | None) -> list[str]:
    if only is not None:
        if not (root / only / "input.jsonl").is_file():
            print(f"No input.jsonl for language '{only}'", file=sys.stderr)
            sys.exit(1)
        return [only]
    return sorted(
        d.name
        for d in root.iterdir()
        if d.is_dir() and (d / "input.jsonl").is_file()
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Live MCP test harness runner.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  ./run.py                          # diff every language\n"
            "  ./run.py --bless                  # rewrite expected.jsonl\n"
            "  ./run.py --language java          # one language\n"
            "  ./run.py --tool ide_find_definition\n"
            "  ./run.py --url http://127.0.0.1:29170/index-mcp/streamable-http"
        ),
    )
    parser.add_argument("--language", help="Restrict to one language.")
    parser.add_argument("--tool", help="Restrict to one MCP tool.")
    parser.add_argument("--url", help="Override server URL for the run.")
    parser.add_argument(
        "--bless",
        action="store_true",
        help="Rewrite expected.jsonl from server output instead of diffing.",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parent
    langs = discover_languages(root, args.language)
    if not langs:
        print(f"No fixtures found in {root}", file=sys.stderr)
        return 0

    total_pass = total_fail = 0
    for lang in langs:
        if args.url:
            url = args.url
        else:
            port = PORT_BY_LANG.get(lang)
            if port is None:
                print(f"No port mapped for language '{lang}'", file=sys.stderr)
                return 1
            url = f"http://127.0.0.1:{port}/index-mcp/streamable-http"
        passed, failed = run_language(
            lang, root / lang, url, args.tool, args.bless
        )
        total_pass += passed
        total_fail += failed

    print(f"ALL: {total_pass} passed, {total_fail} failed")
    return 0 if total_fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
