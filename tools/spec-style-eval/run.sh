#!/usr/bin/env bash
# Spec-style eval: does an agent implement a validator better from a spec written with the
# old `spec-writing` skill or with the new one? See README.md.
#
# One invocation writes ONE spec with the given skill version and implements it <runs> times.
#
#   design notes + skeleton -> spec writer + skill version -> spec
#   spec + skeleton         -> implementer, fresh context  -> validator + its own tests
#   validator + hidden tests -> score
#
# Usage:
#   tools/spec-style-eval/run.sh <old|new> <writer-model> <implementer-model> <runs>
#                                [--batch NAME] [--spec SPEC-DIR]
#
#   --batch NAME     results go to results/NAME/ (default: a timestamp)
#   --spec SPEC-DIR  skip the writer and implement the spec of an earlier run again,
#                    for example results/pilot/new-s1
#
# Environment:
#   TEMPLATE_DIR     path of the validator.g8 template (default: next to this repository)
#   JOBS             implementers that run at the same time (default 1)
#   IMPL_TIMEOUT     seconds for one implementer (default 3600)
#   KEEP_TEMP        set it to keep the temporary project directories
#   SETTING_SOURCES  `--setting-sources` of the agents (default: project). `project` keeps
#                    the plugins, hooks and CLAUDE.md of your user account out of the run.
#
# Both agents run with --dangerously-skip-permissions, each in its own temporary directory.
# Requires: git, sbt, scala-cli, python3, claude (Claude Code CLI, logged in).
set -uo pipefail
source "$(dirname "$0")/lib.sh"

# The two arms. Pinned, so that a later edit of the skill does not change the eval.
skill_commit() {
    case "$1" in
    old) echo 9330de6 ;;
    new) echo 79acec3 ;;
    *) return 1 ;;
    esac
}

usage() {
    sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

[[ $# -ge 4 ]] || usage
ARM="$1" WRITER_MODEL="$2" IMPL_MODEL="$3" RUNS="$4"
shift 4
BATCH=$(date +%Y%m%d-%H%M%S)
SPEC_DIR=""
while [[ $# -gt 0 ]]; do
    case "$1" in
    --batch) BATCH="$2" && shift 2 ;;
    --spec) SPEC_DIR="$(cd "$2" && pwd)" && shift 2 ;;
    *) usage ;;
    esac
done
COMMIT=$(skill_commit "$ARM") || usage
[[ "$RUNS" =~ ^[0-9]+$ ]] || usage
JOBS="${JOBS:-1}"
IMPL_TIMEOUT="${IMPL_TIMEOUT:-3600}"
WRITER_TIMEOUT=1800
SETTING_SOURCES="${SETTING_SOURCES:-project}"
require_tools git sbt scala-cli python3 claude

BATCH_DIR="$HARNESS_DIR/results/$BATCH"
mkdir -p "$BATCH_DIR"

# Each agent gets a temporary directory of its own, with a name that says nothing about the
# eval. No agent directory holds the notes, the hidden tests, or a path into this repository.
TMP_REGISTRY=$(mktemp "${TMPDIR:-/tmp}/deposit-dirs.XXXXXX")
new_tmp() {
    local dir
    dir=$(mktemp -d "${TMPDIR:-/tmp}/deposit.XXXXXX")
    echo "$dir" >>"$TMP_REGISTRY"
    echo "$dir"
}
cleanup() {
    if [[ -z "${KEEP_TEMP:-}" ]]; then
        while read -r dir; do rm -rf "$dir"; done <"$TMP_REGISTRY"
    fi
    rm -f "$TMP_REGISTRY"
}
trap cleanup EXIT

# agent <dir> <model> <timeout> <prompt-file> <transcript> [extra claude args...]
agent() {
    local dir="$1" model="$2" timeout="$3" prompt="$4" transcript="$5"
    shift 5
    (
        cd "$dir" &&
            run_with_timeout "$timeout" claude -p "$(cat "$prompt")" \
                --model "$model" --dangerously-skip-permissions \
                --setting-sources "$SETTING_SOURCES" \
                --output-format stream-json --verbose "$@" \
                </dev/null >"$transcript" 2>"$transcript.stderr"
    )
}

# --- Stage 1: the spec -----------------------------------------------------------------

if [[ -z "$SPEC_DIR" ]]; then
    n=1
    while [[ -e "$BATCH_DIR/$ARM-s$n" ]]; do n=$((n + 1)); done
    SPEC_DIR="$BATCH_DIR/$ARM-s$n"
    mkdir -p "$SPEC_DIR"
    WRITER_DIR=$(new_tmp)
    export_skill "$COMMIT" "$WRITER_DIR" || die "cannot read the skill at commit $COMMIT"
    cp "$ORACLE_DIR/notes.md" "$ORACLE_DIR/Deposit.scala" "$WRITER_DIR/"
    echo "spec-style-eval: writing spec $(basename "$SPEC_DIR") ($ARM skill, $WRITER_MODEL)"
    agent "$WRITER_DIR" "$WRITER_MODEL" "$WRITER_TIMEOUT" "$HARNESS_DIR/prompts/writer.md" \
        "$SPEC_DIR/writer.jsonl" --disallowedTools Skill
    writer_exit=$?
    [[ -s "$WRITER_DIR/SPEC.md" ]] ||
        die "the writer produced no SPEC.md (exit $writer_exit), see $SPEC_DIR/writer.jsonl"
    cp "$WRITER_DIR/SPEC.md" "$SPEC_DIR/spec.md"
    python3 "$HARNESS_DIR/score.py" writer --out "$SPEC_DIR/writer.json" \
        --spec "$SPEC_DIR/spec.md" --agent-log "$SPEC_DIR/writer.jsonl" \
        --meta "arm=$ARM" "skill_commit=$COMMIT" "writer_model=$WRITER_MODEL" \
        "spec_id=$(basename "$SPEC_DIR")" "agent_exit=$writer_exit" ||
        die "the writer run is not valid, see $SPEC_DIR/writer.json"
    # The implementers start after this point. Remove the notes from the disk first.
    rm -rf "$WRITER_DIR"
else
    [[ -s "$SPEC_DIR/spec.md" && -s "$SPEC_DIR/writer.json" ]] || die "no spec in $SPEC_DIR"
    grep -q "\"arm\": \"$ARM\"" "$SPEC_DIR/writer.json" ||
        die "$SPEC_DIR was not written with the $ARM skill"
    WRITER_MODEL=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["writer_model"])' \
        "$SPEC_DIR/writer.json")
fi
SPEC_ID=$(basename "$SPEC_DIR")

# --- Stage 2: the implementations --------------------------------------------------------

BASE=$(scaffold "$(new_tmp)") || exit 2

implement() {
    local id="$1"
    local out="$SPEC_DIR/$id"
    mkdir -p "$out"
    local project
    project="$(new_tmp)/deposit"
    cp -R "$BASE" "$project"
    rm -rf "$project/.scala-build" "$project/.bsp"
    cp "$SPEC_DIR/spec.md" "$project/SPEC.md"
    echo "$project" >"$out/project-dir"
    echo "spec-style-eval: $SPEC_ID/$id starts ($IMPL_MODEL)"
    agent "$project" "$IMPL_MODEL" "$IMPL_TIMEOUT" "$HARNESS_DIR/prompts/implementer.md" \
        "$out/agent.jsonl"
    echo "$?" >"$out/agent-exit"
    echo "spec-style-eval: $SPEC_ID/$id agent exited with $(cat "$out/agent-exit")"
}

score() {
    local id="$1"
    local out="$SPEC_DIR/$id"
    local project
    project=$(cat "$out/project-dir")
    # The own tests of the implementer, in its own project.
    (cd "$project" && scala-cli test . >"$out/own-test.log" 2>&1)
    echo "exit=$?" >>"$out/own-test.log"
    # Keep what the implementer wrote.
    mkdir -p "$out/project"
    cp "$project"/*.scala "$out/project/" 2>/dev/null
    cp "$project/SPEC-GAPS.md" "$out/" 2>/dev/null
    # The hidden tests, in a clean project. The implementer process has exited.
    score_hidden "$BASE" "$project" "$out/score"
    python3 "$HARNESS_DIR/score.py" result --out "$out/result.json" \
        --spec "$SPEC_DIR/spec.md" --impl "$out/project" --score "$out/score" \
        --hidden "$ORACLE_DIR/HiddenOracle.test.scala" \
        --own-test-log "$out/own-test.log" --agent-log "$out/agent.jsonl" \
        --gaps "$out/SPEC-GAPS.md" \
        --meta "arm=$ARM" "spec_id=$SPEC_ID" "impl_id=$id" "writer_model=$WRITER_MODEL" \
        "impl_model=$IMPL_MODEL" "agent_exit=$(cat "$out/agent-exit")" "batch=$BATCH"
    cp "$out/score/compile.log" "$out/score/hidden-test.log" "$out/" 2>/dev/null
    rm -rf "$out/score" "$out/project-dir" "$out/agent-exit"
}

first=1
while [[ -e "$SPEC_DIR/impl-$first" ]]; do first=$((first + 1)); done
ids=()
for ((i = 0; i < RUNS; i++)); do ids+=("impl-$((first + i))"); done

# Run the agents in groups of $JOBS. Score a group only after each of its agents has exited,
# so that no hidden test is on the disk while an agent of this invocation runs.
for ((start = 0; start < RUNS; start += JOBS)); do
    group=("${ids[@]:start:JOBS}")
    for id in "${group[@]}"; do implement "$id" & done
    wait
    for id in "${group[@]}"; do score "$id"; done
done

python3 "$HARNESS_DIR/score.py" summary "$BATCH_DIR" | tee "$BATCH_DIR/summary.md"
