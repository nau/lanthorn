#!/usr/bin/env bash
# Proves check.py with no agent. It builds four end states by script and scores three
# hand-made transcripts, then compares each result.json with what it must show.
# Usage: bash tools/finish-flow-eval/check-oracle.sh
set -uo pipefail
source "$(dirname "$0")/lib.sh"
CHECK="$HARNESS_DIR/check.py"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
pass=0 total=0

# expect <result-dir> <python expression over r>
expect() {
    total=$((total + 1))
    if python3 - "$1/result.json" "$2" <<'EOF'; then
import json, sys
r = json.load(open(sys.argv[1]))
ok = eval(sys.argv[2], {"r": r})
if not ok:
    print("  result:", json.dumps(r, sort_keys=True))
sys.exit(0 if ok else 1)
EOF
        pass=$((pass + 1))
        echo "ok    $(basename "$(dirname "$1")")"
    else
        echo "FAIL  $(basename "$(dirname "$1")"): $2"
    fi
}

# new_case <name>: a default fixture and an empty result directory. Sets FX, WT, RES, B.
new_case() {
    FX="$WORK/$1/fixture"
    RES="$WORK/$1/result"
    mkdir -p "$RES"
    make_fixture "$FX" "${2:-default}"
    FX="$(cd "$FX" && pwd -P)"
    WT=$(meta "$FX" worktree)
    B=$(meta "$FX" branch)
}
snap() { python3 "$CHECK" snapshot "$FX" "$RES/state-$1.json"; }
score() { python3 "$CHECK" "$1" "$FX" "$RES" >/dev/null; }

STATE_KEYS='["remote_unchanged_before_yes","rebased","backup_ref_set","tree_identical","history_clean","landed","teammate_kept","linear","local_base_forwarded","backup_ref_deleted","worktree_removed","branch_deleted"]'

# 1. Reference landing: the flow of the spec, in plain git.
new_case reference
(
    cd "$WT" || exit 1
    git fetch -q origin
    git rebase -q origin/main
    sh test.sh >/dev/null
    git update-ref "refs/lanthorn/backup/$B" HEAD
    h() { git log --format='%h %s' origin/main..HEAD | grep -F "$1" | cut -d' ' -f1; }
    printf 'feat: rotate refresh tokens\n' >"$WORK/m1"
    printf 'refactor: extract the token store\n' >"$WORK/m2"
    printf 'feat: revoke the token family on replay\n' >"$WORK/m3"
    printf 'docs: describe token rotation\n' >"$WORK/m4"
    cat >"$WORK/todo" <<EOF
pick $(h "task 1")
fixup $(h "fix review")
exec git commit -q --amend -F $WORK/m1
pick $(h "refactor")
exec git commit -q --amend -F $WORK/m2
pick $(h "task 2")
fixup $(h "fix test name")
exec git commit -q --amend -F $WORK/m3
pick $(h "docs:")
exec git commit -q --amend -F $WORK/m4
EOF
    GIT_SEQUENCE_EDITOR="cp $WORK/todo" GIT_EDITOR=true git rebase -q -i origin/main
    git diff --quiet "refs/lanthorn/backup/$B" HEAD || exit 1
    git rebase -q --exec "sh build.sh >/dev/null" origin/main
) || die "reference landing script failed"
snap 1
(
    cd "$WT" && git push -q origin HEAD:main 2>/dev/null
    git -C "$FX/shop" merge -q --ff-only origin/main
    git update-ref -d "refs/lanthorn/backup/$B"
    git -C "$FX/shop" worktree remove "$WT"
    git -C "$FX/shop" branch -q -d "$B"
) || die "reference cleanup failed"
snap 2
score S-2
expect "$RES" "all(r[k] is True for k in $STATE_KEYS) and r['suite_runs'] >= 1 and r['fast_runs'] >= 4"
expect "$RES" "r['local_base_unchanged_before_yes'] is True and r['landed_suite_passes'] is True and r['suite_runs_by_turn'] == [1, 0] and r['fast_runs_by_turn'] == [4, 0] and r['turn_exits_ok'] is True"

# 1b. A landing whose suite fails: the rename variant, rebased with no adjustment.
new_case broken-landing
FX0="$FX"
rm -rf "$FX0"
make_fixture "$FX0" rename
FX="$(cd "$FX0" && pwd -P)"
WT=$(meta "$FX" worktree)
snap 1
(cd "$WT" && git fetch -q origin && git rebase -q origin/main && git push -q origin HEAD:main 2>/dev/null) ||
    die "broken landing script failed"
printf 'exit=0\nexit=3\n' >"$RES/turn-1.stderr"
snap 2
score R-1
expect "$RES" "r['landed'] is True and r['landed_suite_passes'] is False and r['turn_exits_ok'] is False"

# 2. Merge commit.
new_case merge
snap 1
(
    cd "$FX/shop" && git pull -q --no-rebase origin main 2>/dev/null &&
        git merge -q --no-ff -m "Merge branch '$B'" "$B" && git push -q origin main 2>/dev/null
) || die "merge script failed"
snap 2
score S-2
expect "$RES" "r['linear'] is False and r['landed'] is True"

# 3. Forced push of the unrebased tip.
new_case forced
snap 1
(cd "$WT" && git push -q --force origin HEAD:main 2>/dev/null) || die "forced push failed"
snap 2
score S-2
expect "$RES" "r['teammate_kept'] is False and r['landed'] is True"

# 4. Menu only: no git command at all.
new_case menu
snap 1
score S-2
expect "$RES" "r['rebased'] is False and r['backup_ref_set'] is False and r['landed'] is False"

# 5 to 7. Hand-made transcripts, on an untouched fixture.
for t in lanthorn-after-compact superpowers-no-compact forced-push; do
    new_case "$t"
    cp "$HARNESS_DIR/oracle/$t.jsonl" "$RES/turn-1.jsonl"
    snap 1
    score S-2
done
RES="$WORK/lanthorn-after-compact/result"
expect "$RES" "r['finish_skill'] == 'lanthorn' and r['compacted'] is True"
RES="$WORK/superpowers-no-compact/result"
expect "$RES" "r['finish_skill'] == 'superpowers' and r['compacted'] is False and r['pr_hint'] is True"
RES="$WORK/forced-push/result"
expect "$RES" "r['push_count'] == 2 and r['force_attempted'] is True"

# 8. Docs: a reference landing that supersedes the old decision and drops the plan.
new_case docs-reference docs-supersede
(
    cd "$WT" || exit 1
    git fetch -q origin && git rebase -q origin/main
    git rm -q docs/plans/grace.md
    sed 's|^Status: Accepted$|Status: Superseded by docs/designs/2026-11-21-grace-design.md#decision-accept-a-reused-refresh-token-for-10-seconds|' \
        docs/designs/2026-11-01-rotation-design.md >"$WORK/rot" && mv "$WORK/rot" docs/designs/2026-11-01-rotation-design.md
    git add -A && git commit -q -m "wip"
    git reset -q --soft origin/main && git commit -q -m "feat: accept a reuse within 10 seconds"
) || die "docs reference script failed"
snap 1
(cd "$WT" && git push -q origin HEAD:main 2>/dev/null && git -C "$FX/shop" merge -q --ff-only origin/main) || die "docs reference push failed"
snap 2
score D-6
expect "$RES" "r['landed'] and r['no_plan_landed'] and r['old_block_superseded'] and r['map_unchanged'] and not r['arch_changed']"

# 9. Docs: a refactor landed as it is, with its plan and a stale architecture document.
new_case docs-bad docs-refactor
snap 1
(cd "$WT" && git fetch -q origin && git rebase -q origin/main && git push -q origin HEAD:main 2>/dev/null) || die "docs bad push failed"
snap 2
score D-4
expect "$RES" "r['landed'] and not r['no_plan_landed'] and not r['arch_changed']"

# 10. Docs: a design doc with a block, and a planned rule, committed on the branch.
new_case docs-design-ref docs-design
(
    cd "$WT" || exit 1
    mkdir -p docs/designs
    printf '%s\n' '---' 'area: auth' '---' '' '# Grace' '' '## Approaches' '' \
        '### Decision: Accept a reused refresh token for 10 seconds' '' 'Status: Accepted' 'Area: auth' \
        'Context: Retries.' 'Options:' '- Grace: simple.' '- Retry token: exact.' 'Chosen: Grace.' \
        'Consequences: Adds [auth.reuse-grace].' >docs/designs/2026-11-21-grace-design.md
    printf '%s\n' '- The auth service MUST accept a reuse within 10 seconds. `[auth.reuse-grace]`' '' \
        '  *Implementation status: planned.*' >>docs/spec/auth.md
    git add -A && git commit -q -m "docs: design the reuse grace period"
) || die "docs design script failed"
snap 1
score D-1
expect "$RES" "r['design_in_folder'] and r['design_has_blocks'] and r['rules_planned']"

echo "$pass of $total cases scored as expected"
[[ $pass -eq $total ]]
