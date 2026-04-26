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
