#!/usr/bin/env bash
# Run one capturing-learnings scenario once, with one pinned lanthorn commit. See README.md.
#
#   tools/capturing-learnings-eval/run.sh <lanthorn-commit> <scenario> <model> <rep> [<out-dir>]
#
# Writes <out-dir>/<scenario>.<short-commit>.<model>.<rep>/ with repo/, memory/, setup-head,
# transcript.jsonl, and verdict.txt. Exits 0 when the agent ran, whatever the verdict.
# <out-dir> defaults to ${TMPDIR:-/tmp}/capturing-learnings-eval/<date>, outside the
# repository, so that no fixture repository nests inside lanthorn.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(git -C "$HERE" rev-parse --show-toplevel)" || exit 2
[[ $# -ge 4 ]] || { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
COMMIT="$1" SCENARIO="$2" MODEL="$3" REP="$4"
OUT="${5:-${TMPDIR:-/tmp}/capturing-learnings-eval/$(date +%Y%m%d)}"
SCN="$HERE/scenarios/$SCENARIO"
[[ -d "$SCN" ]] || { echo "run.sh: no scenario $SCENARIO" >&2; exit 2; }
SHORT=$(git -C "$ROOT" rev-parse --short "$COMMIT") || exit 2
SUPERPOWERS="$ROOT/vendor/superpowers"
[[ -f "$SUPERPOWERS/.claude-plugin/plugin.json" ]] ||
    { echo "run.sh: run git submodule update --init vendor/superpowers" >&2; exit 2; }

# The lanthorn plugin at COMMIT, once per commit. Run git archive from the repository root:
# from a subdirectory it writes an empty archive for a path and reports no error.
# Parallel runs share the arm. mkdir is atomic, so the run that creates the lock extracts,
# and the others wait for the done marker.
ARM="$OUT/arms/$SHORT"
mkdir -p "$OUT/arms"
if mkdir "$ARM.lock" 2>/dev/null; then
    mkdir -p "$ARM"
    git -C "$ROOT" archive "$COMMIT" skills hooks .claude-plugin .codex-plugin | tar -x -f - -C "$ARM" ||
        { echo "run.sh: cannot extract lanthorn at $COMMIT" >&2; exit 2; }
    touch "$ARM.done"
fi
for _ in $(seq 60); do [[ -f "$ARM.done" ]] && break; sleep 1; done
[[ -f "$ARM.done" ]] || { echo "run.sh: no plugin in $ARM" >&2; exit 2; }

RUN="$OUT/$SCENARIO.$SHORT.$MODEL.$REP"
rm -rf "$RUN"
mkdir -p "$RUN/memory"
bash "$SCN/setup.sh" "$RUN/repo" >"$RUN/setup.log" 2>&1 ||
    { echo "run.sh: setup failed, see $RUN/setup.log" >&2; exit 2; }
git -C "$RUN/repo" rev-parse HEAD >"$RUN/setup-head"

# agent <prompt-file> <transcript> [extra claude args...]
agent() {
    local prompt="$1" transcript="$2"
    shift 2
    (cd "$RUN/repo" && claude -p "$(cat "$prompt")" --model "$MODEL" \
        --setting-sources project --strict-mcp-config \
        --plugin-dir "$SUPERPOWERS" --plugin-dir "$ARM" \
        --settings "{\"autoMemoryDirectory\": \"$RUN/memory\"}" \
        --dangerously-skip-permissions \
        --output-format stream-json --verbose "$@" \
        </dev/null >>"$transcript" 2>>"$RUN/claude.stderr")
}

if [[ -f "$SCN/answer.md" ]]; then
    # Two turns: the session must persist, so that the second turn can resume it.
    agent "$SCN/prompt.md" "$RUN/turn1.jsonl"
    SESSION=$(python3 -c 'import json,sys
for l in open(sys.argv[1]):
    try: e=json.loads(l)
    except Exception: continue
    if e.get("type")=="system" and e.get("subtype")=="init": print(e["session_id"]); break' "$RUN/turn1.jsonl")
    agent "$SCN/answer.md" "$RUN/transcript.jsonl" --resume "$SESSION"
else
    agent "$SCN/prompt.md" "$RUN/transcript.jsonl" --no-session-persistence
fi

python3 "$SCN/check.py" "$RUN" >"$RUN/verdict.txt" 2>&1
echo "$SCENARIO $SHORT $MODEL $REP: $(cat "$RUN/verdict.txt")"
