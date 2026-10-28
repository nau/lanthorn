#!/usr/bin/env bash
# Semantic Scala lookup through a standalone Metals MCP server.
# Needs metals-mcp, curl and jq on PATH. See ../references/metals.md.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: metals.sh <command> [args]

  start                       Start or reuse the server for this build, then compile it
  status                      Print the server URL, or exit 1 if none answers
  modules                     List build targets (modules)
  find <Name> [kind...]       Find symbols by simple name. kind: class object trait method function package
  inspect <fqcn> <ctx>        Members of a type, or signatures of a method
  source <fqcn> <ctx>         Source of a symbol, also from library source jars
  docs <fqcn> <ctx>           ScalaDoc of a symbol
  usages <fqcn> <ctx>         Every reference to a symbol. Compiles first, refuses a fallback answer
  jar <fqcn> <module>         Library coordinates and source jar that define a symbol
  import                      Re-import the build. Run it after you change a build file
  call <tool> [json]          Call any Metals MCP tool with raw JSON arguments
  stop                        Stop the server that this script started

<ctx> is a module name from `modules`, or a path to a .scala file in that module.
It picks the classpath that resolves <fqcn>. `usages` still searches every module.
Set METALS_WORKSPACE to override the build root.
Exit codes: 0 ok, 1 error, 3 empty or untrusted answer.
EOF
}

die() {
    echo "metals.sh: $*" >&2
    exit 1
}

for bin in curl jq metals-mcp; do
    command -v "$bin" >/dev/null || die "$bin is not on PATH. See references/metals.md, Setup."
done

is_build_root() {
    [[ -f "$1/build.sbt" || -f "$1/build.mill" || -f "$1/build.sc" ]]
}

find_root() {
    if [[ -n "${METALS_WORKSPACE:-}" ]]; then
        echo "$METALS_WORKSPACE"
        return
    fi
    local top dir
    top=$(git rev-parse --show-toplevel 2>/dev/null || true)
    if [[ -n "$top" ]] && is_build_root "$top"; then
        echo "$top"
        return
    fi
    dir=$PWD
    while [[ "$dir" != "/" ]]; do
        if is_build_root "$dir"; then
            echo "$dir"
            return
        fi
        dir=$(dirname "$dir")
    done
    die "no build.sbt, build.mill or build.sc above $PWD. Set METALS_WORKSPACE."
}

ROOT=$(cd "$(find_root)" && pwd -P)
STATE="$ROOT/.metals"
PID_FILE="$STATE/mcp-server.pid"
LOG_FILE="$STATE/mcp-server.log"
ACCEPT='Accept: application/json, text/event-stream'
INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"metals.sh","version":"1"}}}'

server_url() {
    [[ -f "$STATE/mcp.json" ]] || return 1
    jq -r '[.servers[]?.url, .mcpServers[]?.url] | map(select(. != null)) | first // empty' \
        "$STATE/mcp.json"
}

alive() {
    local url
    url=$(server_url 2>/dev/null) || return 1
    [[ -n "$url" ]] || return 1
    curl -sf -o /dev/null --max-time 5 -X POST "$url" \
        -H 'Content-Type: application/json' -H "$ACCEPT" -d "$INIT"
}

# Prints the text of one tool call. Exits 1 when Metals reports an error.
rpc() {
    local tool=$1 args=$2 url hdr sid body
    url=$(server_url) || die "no server for $ROOT. Run: metals.sh start"
    hdr=$(mktemp)
    if ! curl -sf -D "$hdr" -o /dev/null -X POST "$url" \
        -H 'Content-Type: application/json' -H "$ACCEPT" -d "$INIT"; then
        rm -f "$hdr"
        die "server at $url does not answer. Run: metals.sh start"
    fi
    sid=$(awk 'tolower($1) == "mcp-session-id:" { print $2 }' "$hdr" | tr -d '\r')
    rm -f "$hdr"
    local h=(-H 'Content-Type: application/json' -H "$ACCEPT" -H "Mcp-Session-Id: $sid")
    curl -sf -o /dev/null -X POST "$url" "${h[@]}" \
        -d '{"jsonrpc":"2.0","method":"notifications/initialized"}' || true
    body=$(jq -cn --arg t "$tool" --argjson a "$args" \
        '{jsonrpc:"2.0", id:2, method:"tools/call", params:{name:$t, arguments:$a}}')
    curl -sf --max-time "${METALS_CALL_TIMEOUT:-900}" -X POST "$url" "${h[@]}" -d "$body" |
        sed 's/^data: //' | grep '^{' |
        jq -r 'if .error then ("ERROR: " + .error.message) | halt_error(1)
               elif .result.isError then (.result.content | map(.text) | join("\n") + "\n") | halt_error(1)
               else .result.content | map(.text) | join("\n") end'
}

abs_path() {
    echo "$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")"
}

# JSON arguments for a symbol query. <ctx> is a .scala file or a module name.
symbol_args() {
    local fqcn=$1 ctx=${2:-}
    [[ -n "$ctx" ]] || die "missing <ctx>. Pass a module from 'metals.sh modules' or a .scala file."
    if [[ -f "$ctx" ]]; then
        jq -cn --arg f "$fqcn" --arg p "$(abs_path "$ctx")" '{fqcn:$f, fileInFocus:$p}'
    else
        jq -cn --arg f "$fqcn" --arg m "$ctx" '{fqcn:$f, module:$m}'
    fi
}

# Compile every module, so that the index matches the files on disk.
# Incremental: a no-op compile takes under a second on a small project.
compile_all() {
    local out
    out=$(rpc compile-full '{}' 2>&1) || true
    if ! grep -qi 'success' <<<"$out"; then
        echo "WARNING: compile failed. Usages in files that fail to compile are missing." >&2
        head -20 <<<"$out" >&2
    fi
}

cmd=${1:-}
if [[ -z "$cmd" ]]; then
    usage
    exit 1
fi
shift

case "$cmd" in
    start)
        deadline=$((SECONDS + ${METALS_START_TIMEOUT:-900}))
        if alive; then
            echo "Reusing Metals for $ROOT at $(server_url)"
        else
            mkdir -p "$STATE"
            rm -f "$STATE/mcp.json"
            # Redirect every stream of the background job. An inherited pipe keeps
            # the caller's shell waiting until the server exits.
            (cd "$ROOT" && exec nohup metals-mcp --workspace "$ROOT") \
                </dev/null >"$LOG_FILE" 2>&1 &
            echo $! >"$PID_FILE"
            echo "Starting Metals for $ROOT. The first import can take minutes. Log: $LOG_FILE"
            until alive; do
                kill -0 "$(cat "$PID_FILE")" 2>/dev/null || die "metals-mcp exited. See $LOG_FILE"
                ((SECONDS < deadline)) || die "not ready after ${METALS_START_TIMEOUT:-900} s. See $LOG_FILE"
                sleep 2
            done
            echo "Metals answers at $(server_url)"
        fi
        # The server answers before the build import ends. Until then it has no modules.
        until rpc list-modules '{}' 2>/dev/null | grep "^- " >/dev/null; do
            ((SECONDS < deadline)) || die "build import not done after ${METALS_START_TIMEOUT:-900} s. See $LOG_FILE"
            sleep 3
        done
        echo "Compiling all modules, so that usages are indexed..."
        compile_all
        echo "Ready."
        rpc list-modules '{}'
        ;;
    status)
        if alive; then
            server_url
        else
            echo "no server for $ROOT" >&2
            exit 1
        fi
        ;;
    modules)
        rpc list-modules '{}'
        ;;
    find)
        name=${1:?missing <Name>}
        shift
        if (($#)); then
            rpc typed-glob-search \
                "$(jq -cn --arg q "$name" '{query:$q, symbolType:$ARGS.positional}' --args "$@")"
        else
            rpc glob-search "$(jq -cn --arg q "$name" '{query:$q}')"
        fi
        ;;
    inspect)
        rpc inspect "$(symbol_args "${1:?missing <fqcn>}" "${2:-}")"
        ;;
    source)
        rpc get-source "$(symbol_args "${1:?missing <fqcn>}" "${2:-}")"
        ;;
    docs)
        rpc get-docs "$(symbol_args "${1:?missing <fqcn>}" "${2:-}")"
        ;;
    usages)
        fqcn=${1:?missing <fqcn>}
        ctx=${2:-}
        args=$(symbol_args "$fqcn" "$ctx")
        compile_all
        # Metals answers an FQCN it cannot resolve with the enclosing object or
        # package, and reports no error. The first inspect line names what it
        # resolved. A val has no inspect line, which is fine.
        found=$(rpc inspect "$args" | grep -v '^\[Inspected' | grep -m1 . || true)
        name=${fqcn##*.}
        if [[ -n "$found" && "$found" != *"$name"* ]]; then
            echo "UNTRUSTED: Metals did not resolve $fqcn. It resolved: $found" >&2
            echo "Causes: the member is inherited (use the declaring type), the method is a" >&2
            echo "library inline method, or <ctx> is wrong. Use grep for this symbol." >&2
            exit 3
        fi
        # One line per file:line. Metals repeats a line once for each reference on it.
        out=$(rpc get-usages "$args" | sed "s#^.*$ROOT/##" | sort -t: -k1,1 -k2,2n | uniq -c |
            awk '{ if ($1 > 1) print $2 " (" $1 " references)"; else print $2 }')
        if [[ -z "$out" ]]; then
            echo "EMPTY: no usages for $fqcn. This does not prove that there are none." >&2
            echo "Check <ctx>, the compile output, and a plain grep." >&2
            exit 3
        fi
        echo "$out"
        ;;
    jar)
        fqcn=${1:?missing <fqcn>}
        module=${2:?missing <module>}
        cfg="$ROOT/.bloop/$module.json"
        [[ -f "$cfg" ]] || die "no $cfg. Pass a module from 'metals.sh modules', after start."
        # Drop trailing segments until a class file matches, so a member FQCN works too.
        path=${fqcn//.//}
        while [[ "$path" == */* ]]; do
            hits=$(jq -r '.project.resolution.modules[] | . as $m | .artifacts[]
                    | select(.classifier == null)
                    | "\($m.organization):\($m.name):\($m.version) \(.path)"' "$cfg" |
                while read -r coord file; do
                    if unzip -Z1 "$file" 2>/dev/null | grep -xE "$path(\\\$)?\\.class" >/dev/null; then
                        echo "$coord ${file%.jar}-sources.jar"
                    fi
                done)
            if [[ -n "$hits" ]]; then
                echo "$hits"
                exit 0
            fi
            path=${path%/*}
        done
        echo "NOT FOUND: no library jar of $module defines $fqcn. It can be project code." >&2
        exit 3
        ;;
    import)
        rpc import-build '{}'
        compile_all
        ;;
    call)
        rpc "${1:?missing <tool>}" "${2:-"{}"}"
        ;;
    stop)
        # The metals-mcp launcher runs java as a child, so stop both by workspace.
        if pkill -f -- "metals-mcp --workspace $ROOT\$"; then
            rm -f "$PID_FILE" "$STATE/mcp.json"
            echo "Stopped Metals for $ROOT"
        else
            echo "No metals-mcp runs for $ROOT"
        fi
        ;;
    -h | --help | help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
