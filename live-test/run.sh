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

# Normalize a result JSON: apply jq filter, substitute project absolute path with ${PROJECT_ROOT}, sort keys.
normalize() {
    local result_json="$1"
    local project_path="$2"
    jq -cS "$NORMALIZE_FILTER" <<< "$result_json" \
        | sed "s|$project_path|\${PROJECT_ROOT}|g"
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

    if ! check_ready "$url" "$project_path"; then
        echo "[$lang] SKIPPED (precheck failed)"
        total_fail=$((total_fail + 1))
        continue
    fi

    line_no=0
    while IFS= read -r line; do
        line_no=$((line_no + 1))
        [ -z "$line" ] && continue
        if [ -n "$FLAG_TOOL" ]; then
            tool="$(jq -r '.tool' <<< "$line")"
            [ "$tool" = "$FLAG_TOOL" ] || continue
        fi
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

        expected_line=""
        if [ -f "$expected_file" ]; then
            expected_line="$(sed -n "${line_no}p" "$expected_file")"
        fi
        if [ -z "$expected_line" ]; then
            echo "  [$line_no] $id MISSING (no expected.jsonl line $line_no — bless?)"
            lang_fail=$((lang_fail + 1))
            continue
        fi
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
