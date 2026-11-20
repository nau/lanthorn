#!/usr/bin/env bash
# Runs one scenario of the finish-flow eval.
#
#   run.sh <scenario> <arm> <model> [runs]
#
#   scenario  S-1 S-2 S-3 S-4 R-1 R-2 R-3 R-4 R-5 R-6 (see README.md)
#   arm       base  lanthorn at BASE_COMMIT, before the finish skill existed
#             work  the working tree of this repository
#   model     any --model value of the claude CLI: haiku, sonnet, opus, fable
#   runs      number of runs, default 1
#
# Environment:
#   BATCH         results/<BATCH>/ (default: a timestamp)
#   JOBS          runs in parallel (default 1)
#   TURN_TIMEOUT  seconds per turn (default 1800)
#   EXEC_SKILL    S-1 only: executing-plans (default) or subagent-driven-development
#   KEEP_TEMP     keep the fixture directories
#
# Every agent runs in a fixture under mktemp -d, with --dangerously-skip-permissions, and with
# only superpowers and lanthorn loaded: --setting-sources project plus two --plugin-dir.
# Requires: git, python3, claude (Claude Code CLI, logged in), the superpowers submodule.
set -uo pipefail
source "$(dirname "$0")/lib.sh"

# The fork point of the finish-flow branch: lanthorn with no finish skill.
BASE_COMMIT=1532fa686290e33948ec8b9f35ee32f8f26d59ce
SUPERPOWERS="$REPO_ROOT/vendor/superpowers"

usage() {
    sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

[[ $# -ge 3 ]] || usage
SCENARIO="$1" ARM="$2" MODEL="$3" RUNS="${4:-1}"
[[ "$RUNS" =~ ^[0-9]+$ ]] || usage
BATCH="${BATCH:-$(date +%Y%m%d-%H%M%S)}"
JOBS="${JOBS:-1}"
TURN_TIMEOUT="${TURN_TIMEOUT:-1800}"
EXEC_SKILL="${EXEC_SKILL:-executing-plans}"
command -v claude >/dev/null || die "missing tool: claude"
[[ -f "$SUPERPOWERS/.claude-plugin/plugin.json" ]] || die "run: git submodule update --init"

case "$SCENARIO" in
S-1) VARIANT=plan ;;
S-2 | S-3 | R-3 | R-6) VARIANT=default ;;
S-4) VARIANT=pr-policy ;;
R-1) VARIANT=rename ;;
R-2) VARIANT=protected ;;
R-4) VARIANT=harness-worktree ;;
R-5) VARIANT=pr-trace ;;
*) usage ;;
esac

# The prompt of each turn, one per line. "@file" reads a file of prompts/.
turns() {
    case "$SCENARIO" in
    S-1) printf '%s\n' "@plan-task-1.md" "/compact" "@plan-continue.md" ;;
    S-2 | S-3 | R-1 | R-2 | R-4) printf '%s\n' "@finish.md" "yes" ;;
    R-3) printf '%s\n' "@finish.md" "no" ;;
    R-6) printf '%s\n' "@finish.md" "yes" "yes" ;;
    S-4 | R-5) printf '%s\n' "@finish.md" ;;
    esac
}

prompt_text() {
    if [[ "$1" == @* ]]; then
        sed "s/EXEC_SKILL/$EXEC_SKILL/" "$HARNESS_DIR/prompts/${1#@}"
    else
        printf '%s' "$1"
    fi
}

case "$ARM" in
work) LANTHORN="$REPO_ROOT" ;;
base)
    LANTHORN=$(mktemp -d)
    git -C "$REPO_ROOT" archive "$BASE_COMMIT" | tar -x -f - -C "$LANTHORN" ||
        die "cannot export lanthorn at $BASE_COMMIT"
    [[ -f "$LANTHORN/skills/using-lanthorn/SKILL.md" ]] || die "export of $BASE_COMMIT is empty"
    ;;
*) usage ;;
esac

BATCH_DIR="$HARNESS_DIR/results/$BATCH"
mkdir -p "$BATCH_DIR"

# run_with_timeout <seconds> <command...>. Exit status 124 means the command ran out of time.
run_with_timeout() {
    local seconds="$1"
    shift
    "$@" &
    local pid=$!
    (sleep "$seconds" && kill -TERM "$pid" 2>/dev/null) &
    local watchdog=$!
    wait "$pid"
    local status=$?
    if kill -0 "$watchdog" 2>/dev/null; then
        kill "$watchdog" 2>/dev/null
        wait "$watchdog" 2>/dev/null
        return $status
    fi
    return 124
}

# field <transcript> <python expression over the init event e or the result event r>
init_field() {
    python3 - "$1" "$2" <<'EOF'
import json, sys
for line in open(sys.argv[1]):
    try:
        e = json.loads(line)
    except json.JSONDecodeError:
        continue
    if e.get("type") == "system" and e.get("subtype") == "init":
        print(eval(sys.argv[2], {"e": e}))
        break
EOF
}

final_message() {
    python3 - "$1" <<'EOF'
import json, sys
text = ""
for line in open(sys.argv[1]):
    try:
        e = json.loads(line)
    except json.JSONDecodeError:
        continue
    if e.get("type") == "result":
        text = e.get("result") or ""
print(text)
EOF
}

# one_run <result-dir>
one_run() {
    local res="$1" fx
    fx=$(mktemp -d)
    make_fixture "$fx" "$VARIANT"
    fx="$(cd "$fx" && pwd -P)"
    selftest_fixture "$fx" "$VARIANT"
    local wt sid="" i=0 line
    wt=$(meta "$fx" worktree)
    echo "finish-flow-eval: $(basename "$res") in $fx"
    while IFS= read -r line; do
        i=$((i + 1))
        local resume=()
        [[ -n "$sid" ]] && resume=(--resume "$sid")
        (
            cd "$wt" &&
                run_with_timeout "$TURN_TIMEOUT" claude -p "$(prompt_text "$line")" \
                    --model "$MODEL" --dangerously-skip-permissions \
                    --setting-sources project \
                    --plugin-dir "$SUPERPOWERS" --plugin-dir "$LANTHORN" \
                    --output-format stream-json --verbose ${resume[@]+"${resume[@]}"} \
                    </dev/null >"$res/turn-$i.jsonl" 2>"$res/turn-$i.stderr"
        )
        echo "exit=$?" >>"$res/turn-$i.stderr"
        if [[ $i -eq 1 ]]; then
            sid=$(init_field "$res/turn-1.jsonl" 'e["session_id"]')
            local plugins
            plugins=$(init_field "$res/turn-1.jsonl" '" ".join(sorted(p["name"] for p in e.get("plugins", []) if not p["name"].startswith("cc-plugin-")))')
            echo "$plugins" >"$res/plugins.txt"
            [[ "$plugins" == "lanthorn superpowers" ]] || die "$(basename "$res"): unexpected plugins: $plugins"
        fi
        final_message "$res/turn-$i.jsonl" >"$res/final-$i.txt"
        python3 "$HARNESS_DIR/check.py" snapshot "$fx" "$res/state-$i.json"
        if [[ ("$SCENARIO" == S-3 || "$SCENARIO" == R-6) && $i -eq 1 ]]; then
            local t2
            if [[ "$SCENARIO" == R-6 ]]; then t2=$(push_teammate_rename "$fx"); else t2=$(push_teammate_commit "$fx"); fi
            python3 - "$fx/meta.json" "$t2" <<'EOF'
import json, sys
m = json.load(open(sys.argv[1]))
m["teammate2"] = sys.argv[2]
json.dump(m, open(sys.argv[1], "w"), indent=2)
EOF
        fi
    done < <(turns)
    cp "$fx/meta.json" "$res/meta.json"
    [[ -f "$fx/suite.log" ]] && cp "$fx/suite.log" "$res/"
    [[ -f "$fx/fast.log" ]] && cp "$fx/fast.log" "$res/"
    python3 "$HARNESS_DIR/check.py" "$SCENARIO" "$fx" "$res" >/dev/null
    [[ -n "${KEEP_TEMP:-}" ]] || rm -rf "$fx"
    echo "finish-flow-eval: $(basename "$res") done"
}

# macOS ships bash 3.2: no `wait -n`, so the job limit polls.
# Plain mkdir claims a name atomically, so parallel invocations never share a directory.
NAME="$SCENARIO-$ARM-$MODEL"
[[ "$SCENARIO" == S-1 && "$EXEC_SKILL" != executing-plans ]] && NAME="$NAME-sdd"
for ((r = 1; r <= RUNS; r++)); do
    n=1
    until mkdir "$BATCH_DIR/$NAME-$n" 2>/dev/null; do n=$((n + 1)); done
    one_run "$BATCH_DIR/$NAME-$n" &
    while [[ $(jobs -rp | wc -l) -ge $JOBS ]]; do sleep 5; done
done
wait
[[ "$ARM" == base ]] && rm -rf "$LANTHORN"
exit 0
