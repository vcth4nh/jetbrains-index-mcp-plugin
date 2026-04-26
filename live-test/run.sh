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
