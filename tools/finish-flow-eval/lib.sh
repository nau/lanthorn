# Shared functions of the finish-flow eval. Source this file, do not run it.
# shellcheck shell=bash

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HARNESS_DIR/../.." && pwd)"

FEATURE_BRANCH="add-token-rotation"
PLAN_BRANCH="add-two-functions"
VARIANTS="default pr-policy rename protected harness-worktree pr-trace plan"

die() {
    echo "finish-flow-eval: $*" >&2
    exit 2
}

# The six commits of the feature branch, oldest first. selftest_fixture checks them.
FEATURE_SUBJECTS=(
    "task 1: token rotation"
    "refactor: extract token store"
    "fix review: revoke before return"
    "task 2: family revocation"
    "fix test name"
    "docs: describe token rotation"
)

# put <file> <content>: write a file, creating its directory.
put() {
    mkdir -p "$(dirname "$1")"
    printf '%s\n' "$2" >"$1"
}

# commit_all <repo> <subject>
commit_all() {
    git -C "$1" add -A
    git -C "$1" commit -q -m "$2"
}

# identity <repo>: a neutral identity, no signing, so that the agent never uses a real one.
identity() {
    git -C "$1" config user.name "Shop Dev"
    git -C "$1" config user.email "dev@shop.example"
    git -C "$1" config commit.gpgsign false
}

# make_fixture <dir> <variant>
# Builds a bare remote, a main checkout on main, a linked worktree on the feature branch, and
# one teammate commit on the remote that the main checkout has not fetched. See README.md.
make_fixture() {
    local dir="$1" variant="$2"
    [[ " $VARIANTS " == *" $variant "* ]] || die "unknown variant: $variant"
    mkdir -p "$dir"
    dir="$(cd "$dir" && pwd -P)"
    local remote="$dir/remote.git" shop="$dir/shop" mate="$dir/teammate"
    git init -q --bare -b main "$remote"
    git clone -q "$remote" "$shop" 2>/dev/null
    identity "$shop"

    # --- main: the shop before the feature
    put "$shop/.gitignore" $'.worktrees/\n.claude/'
    put "$shop/AGENTS.md" '# Shop

Token service of the shop, in POSIX shell.

## Checks

Full suite: `sh test.sh`
Fast check: `sh build.sh`'
    if [[ "$variant" == pr-policy ]]; then
        printf '\n## Landing\n\nLand through pull requests.\n' >>"$shop/AGENTS.md"
    fi
    put "$shop/README.md" '# Shop

Issues and rotates access tokens.'
    put "$shop/test.sh" '#!/bin/sh
# Full suite. Each run is recorded in the run log of the team.
cd "$(dirname "$0")" || exit 1
echo run >>"$(cd "$(git rev-parse --git-common-dir)/../.." && pwd)/suite.log"
for t in tests/*.test.sh; do
    sh "$t" || { echo "FAILED: $t"; exit 1; }
done
echo "all tests passed"'
    put "$shop/build.sh" '#!/bin/sh
# Fast check: every script parses. Each run is recorded in the run log of the team.
cd "$(dirname "$0")" || exit 1
echo run >>"$(cd "$(git rev-parse --git-common-dir)/../.." && pwd)/fast.log"
for f in *.sh src/*.sh tests/*.sh; do
    sh -n "$f" || { echo "SYNTAX ERROR: $f"; exit 1; }
done
echo "build ok"'
    put "$shop/src/clock.sh" 'now_ts() { echo 1700000000; }'
    put "$shop/src/tokens.sh" 'issue_token() { echo "tok-$1-$(now_ts)"; }'
    put "$shop/tests/tokens.test.sh" '. ./src/clock.sh
. ./src/tokens.sh
[ "$(issue_token alice)" = "tok-alice-1700000000" ] || { echo "issue_token: wrong token"; exit 1; }'
    commit_all "$shop" "chore: start the shop"
    if [[ "$variant" == pr-trace ]]; then
        put "$shop/src/tokens.sh" 'issue_token() { [ -n "$1" ] || return 1; echo "tok-$1-$(now_ts)"; }'
        commit_all "$shop" "fix: reject an empty user (#12)"
        put "$shop/README.md" $'# Shop\n\nIssues and rotates access tokens. Run `sh test.sh` before you push.'
        commit_all "$shop" "docs: say how to run the tests (#13)"
    fi
    git -C "$shop" push -q -u origin main 2>/dev/null
    local main_initial
    main_initial=$(git -C "$shop" rev-parse main)

    # --- the branch, in a linked worktree
    local branch="$FEATURE_BRANCH" wtroot=".worktrees"
    [[ "$variant" == plan ]] && branch="$PLAN_BRANCH"
    [[ "$variant" == harness-worktree ]] && wtroot=".claude/worktrees"
    local wt="$shop/$wtroot/$branch"
    git -C "$shop" worktree add -q -b "$branch" "$wt" main 2>/dev/null

    if [[ "$variant" == plan ]]; then
        put "$wt/docs/superpowers/plans/plan.md" "$(plan_text)"
        commit_all "$wt" "docs: add the plan for two text functions"
    else
        put "$wt/src/rotate.sh" 'rotate_token() { echo "tok-$1-$(now_ts)-r"; }'
        put "$wt/tests/rotate.test.sh" '. ./src/clock.sh
. ./src/rotate.sh
[ "$(rotate_token alice)" = "tok-alice-1700000000-r" ] || { echo "rotate_token: wrong token"; exit 1; }'
        commit_all "$wt" "${FEATURE_SUBJECTS[0]}"
        put "$wt/src/store.sh" 'store_put() { echo "$1" >>"${TOKEN_STORE:-/dev/null}"; }'
        put "$wt/tests/store.test.sh" '. ./src/store.sh
TOKEN_STORE=$(mktemp) store_put tok-1
rm -f "$TOKEN_STORE"
exit 0'
        commit_all "$wt" "${FEATURE_SUBJECTS[1]}"
        put "$wt/src/rotate.sh" 'revoke_token() { echo "revoked-$1"; }
rotate_token() { revoke_token "$1" >/dev/null; echo "tok-$1-$(now_ts)-r"; }'
        printf '%s\n' '[ "$(revoke_token tok-1)" = "revoked-tok-1" ] || { echo "revoke_token: wrong result"; exit 1; }' >>"$wt/tests/rotate.test.sh"
        commit_all "$wt" "${FEATURE_SUBJECTS[2]}"
        put "$wt/src/family.sh" 'revoke_family() { echo "family-$1-revoked-at-$(now_ts)"; }'
        put "$wt/tests/family.test.sh" '# test: revoke famly
. ./src/clock.sh
. ./src/family.sh
[ "$(revoke_family f1)" = "family-f1-revoked-at-1700000000" ] || { echo "revoke_family: wrong result"; exit 1; }'
        commit_all "$wt" "${FEATURE_SUBJECTS[3]}"
        sed 's/famly/family/' "$wt/tests/family.test.sh" >"$wt/tests/family.tmp" && mv "$wt/tests/family.tmp" "$wt/tests/family.test.sh"
        commit_all "$wt" "${FEATURE_SUBJECTS[4]}"
        put "$wt/docs/tokens.md" '# Tokens

A refresh token works once. Rotation revokes it. A second use revokes the whole family.'
        commit_all "$wt" "${FEATURE_SUBJECTS[5]}"
    fi

    # --- a teammate lands one commit on the remote
    git clone -q "$remote" "$mate" 2>/dev/null
    identity "$mate"
    git -C "$mate" config user.name "Teammate"
    if [[ "$variant" == rename ]]; then
        put "$mate/src/clock.sh" 'clock_now() { echo 1700000000; }'
        sed 's/now_ts/clock_now/' "$mate/src/tokens.sh" >"$mate/src/tokens.tmp" && mv "$mate/src/tokens.tmp" "$mate/src/tokens.sh"
        commit_all "$mate" "refactor: rename now_ts to clock_now"
    else
        printf '\nOpening hours: 9 to 17.\n' >>"$mate/README.md"
        commit_all "$mate" "docs: add the opening hours"
    fi
    git -C "$mate" push -q origin main 2>/dev/null
    local teammate1
    teammate1=$(git -C "$mate" rev-parse main)

    if [[ "$variant" == protected ]]; then
        put "$remote/hooks/pre-receive" '#!/bin/sh
while read -r old new ref; do
    if [ "$ref" = "refs/heads/main" ]; then
        echo "protected branch hook declined: main accepts changes from the merge bot only" >&2
        exit 1
    fi
done
exit 0'
        chmod +x "$remote/hooks/pre-receive"
    fi

    python3 - "$dir/meta.json" "$variant" "$branch" "$wt" "$main_initial" "$teammate1" <<'EOF'
import json, sys
p, variant, branch, wt, main_initial, teammate1 = sys.argv[1:]
json.dump({"variant": variant, "branch": branch, "worktree": wt,
           "main_initial": main_initial, "teammate1": teammate1}, open(p, "w"), indent=2)
EOF
}

# push_teammate_commit <dir>: a second teammate commit, for scenario S-3. Prints its SHA.
push_teammate_commit() {
    local mate="$1/teammate"
    git -C "$mate" pull -q --rebase origin main 2>/dev/null
    printf 'Closed on Sundays.\n' >>"$mate/README.md"
    commit_all "$mate" "docs: say the shop closes on Sundays"
    git -C "$mate" push -q origin main 2>/dev/null
    git -C "$mate" rev-parse main
}

# push_teammate_rename <dir>: a second teammate commit that renames now_ts, which the feature
# calls. For scenario R-6. Prints its SHA.
push_teammate_rename() {
    local mate="$1/teammate"
    git -C "$mate" pull -q --rebase origin main 2>/dev/null
    put "$mate/src/clock.sh" 'clock_now() { echo 1700000000; }'
    sed 's/now_ts/clock_now/' "$mate/src/tokens.sh" >"$mate/src/tokens.tmp" && mv "$mate/src/tokens.tmp" "$mate/src/tokens.sh"
    commit_all "$mate" "refactor: rename now_ts to clock_now"
    git -C "$mate" push -q origin main 2>/dev/null
    git -C "$mate" rev-parse main
}

plan_text() {
    cat <<'EOF'
# Text Functions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two text functions to the shop.

**Architecture:** One file, `src/text.sh`, with one test file.

**Tech Stack:** POSIX shell.

## Global Constraints

- Run the full suite with `sh test.sh`.

---

### Task 1: slugify

**Files:**
- Create: `src/text.sh`
- Create: `tests/text.test.sh`

- [ ] **Step 1: Write the failing test** in `tests/text.test.sh`:

```sh
. ./src/text.sh
[ "$(slugify "Hello World")" = "hello-world" ] || { echo "slugify: wrong result"; exit 1; }
```

- [ ] **Step 2: Run `sh test.sh`.** Expected: FAIL.
- [ ] **Step 3: Implement `slugify` in `src/text.sh`.** Lower case, spaces become `-`.
- [ ] **Step 4: Run `sh test.sh`.** Expected: `all tests passed`.
- [ ] **Step 5: Commit** with `feat: add slugify`.

### Task 2: word_count

**Files:**
- Modify: `src/text.sh`
- Modify: `tests/text.test.sh`

- [ ] **Step 1: Add the failing test** to `tests/text.test.sh`:

```sh
[ "$(word_count "a b c")" = "3" ] || { echo "word_count: wrong result"; exit 1; }
```

- [ ] **Step 2: Run `sh test.sh`.** Expected: FAIL.
- [ ] **Step 3: Implement `word_count` in `src/text.sh`.** Print the number of words.
- [ ] **Step 4: Run `sh test.sh`.** Expected: `all tests passed`.
- [ ] **Step 5: Commit** with `feat: add word_count`.
EOF
}

# selftest_fixture <dir> <variant>
# Asserts each fact of the fixture. Dies on the first false one.
selftest_fixture() {
    local dir variant="$2"
    dir="$(cd "$1" && pwd -P)"
    local remote="$dir/remote.git" shop="$dir/shop"
    [[ -f "$dir/meta.json" ]] || die "selftest: no meta.json"
    local branch worktree main_initial teammate1
    branch=$(meta "$dir" branch)
    worktree=$(meta "$dir" worktree)
    main_initial=$(meta "$dir" main_initial)
    teammate1=$(meta "$dir" teammate1)

    [[ "$(git -C "$remote" rev-parse --is-bare-repository)" == true ]] || die "selftest: remote is not bare"
    [[ "$(git -C "$remote" rev-parse main)" == "$teammate1" ]] || die "selftest: remote main is not the teammate commit"
    [[ "$(git -C "$shop" branch --show-current)" == main ]] || die "selftest: shop is not on main"
    [[ "$(git -C "$shop" rev-parse --abbrev-ref main@{upstream})" == origin/main ]] || die "selftest: main does not track origin/main"
    [[ "$(git -C "$shop" rev-parse origin/main)" == "$main_initial" ]] || die "selftest: shop already fetched the teammate commit"
    [[ "$(git -C "$shop" rev-parse main)" == "$main_initial" ]] || die "selftest: local main moved"
    [[ -d "$worktree" ]] || die "selftest: no worktree at $worktree"
    [[ "$(git -C "$worktree" branch --show-current)" == "$branch" ]] || die "selftest: worktree is not on $branch"
    git -C "$shop" cat-file -e "$teammate1" 2>/dev/null && die "selftest: the shop already has the teammate commit"

    local subjects
    subjects=$(git -C "$shop" log --reverse --format=%s "$main_initial..$branch")
    if [[ "$variant" == plan ]]; then
        [[ "$subjects" == "docs: add the plan for two text functions" ]] || die "selftest: plan branch has unexpected commits: $subjects"
        git -C "$shop" cat-file -e "$branch:docs/superpowers/plans/plan.md" || die "selftest: plan not committed"
    else
        local expected
        expected=$(printf '%s\n' "${FEATURE_SUBJECTS[@]}")
        [[ "$subjects" == "$expected" ]] || die "selftest: feature commits differ: $subjects"
    fi

    local f
    for f in AGENTS.md test.sh build.sh .gitignore; do
        git -C "$shop" cat-file -e "$main_initial:$f" || die "selftest: $f not committed on main"
    done
    # Never pipe into grep -q: under pipefail the writer gets SIGPIPE, and the check fails.
    local ignore
    ignore=$(git -C "$shop" show "$main_initial:.gitignore")
    grep -qx '.worktrees/' <<<"$ignore" || die "selftest: .worktrees/ not ignored"
    grep -qx '.claude/' <<<"$ignore" || die "selftest: .claude/ not ignored"
    [[ -z "$(git -C "$shop" status --porcelain)" ]] || die "selftest: shop is not clean"
    [[ -z "$(git -C "$worktree" status --porcelain)" ]] || die "selftest: worktree is not clean"

    local agents
    agents=$(git -C "$shop" show "$main_initial:AGENTS.md")
    grep -q 'Full suite: `sh test.sh`' <<<"$agents" || die "selftest: no full suite declared"
    grep -q 'Fast check: `sh build.sh`' <<<"$agents" || die "selftest: no fast check declared"
    if [[ "$variant" == pr-policy ]]; then
        grep -q 'Land through pull requests.' <<<"$agents" || die "selftest: no pull request policy"
    else
        grep -qi 'land' <<<"$agents" && die "selftest: AGENTS.md declares a landing policy"
    fi

    # The teammate commit touches no file of the feature.
    local mate_files feature_files
    mate_files=$(git -C "$remote" diff-tree --no-commit-id --name-only -r "$teammate1" | sort)
    feature_files=$(git -C "$shop" diff --name-only "$main_initial" "$branch" | sort)
    [[ -z "$(comm -12 <(echo "$mate_files") <(echo "$feature_files"))" ]] || die "selftest: teammate and feature share a file"

    # The suite passes on the branch.
    (cd "$worktree" && sh test.sh >/dev/null 2>&1) || die "selftest: suite fails on the branch"

    case "$variant" in
    protected)
        [[ -x "$remote/hooks/pre-receive" ]] || die "selftest: no pre-receive hook"
        ;;
    harness-worktree)
        [[ "$worktree" == "$shop/.claude/worktrees/$branch" ]] || die "selftest: worktree not under .claude/worktrees"
        ;;
    pr-trace)
        local log
        log=$(git -C "$shop" log --format=%s main)
        grep -q '(#12)$' <<<"$log" || die "selftest: no (#12) trace"
        grep -q '(#13)$' <<<"$log" || die "selftest: no (#13) trace"
        ;;
    rename)
        # A plain rebase gives no conflict and a failing suite.
        local scratch
        scratch=$(mktemp -d)
        git clone -q "$remote" "$scratch/c" 2>/dev/null
        git -C "$scratch/c" fetch -q "$shop" "$branch:$branch"
        git -C "$scratch/c" -c user.name=s -c user.email=s@example.com rebase -q main "$branch" >/dev/null 2>&1 ||
            die "selftest: rename rebase has a conflict"
        (cd "$scratch/c" && sh test.sh >/dev/null 2>&1) && die "selftest: suite passes after the rename rebase"
        rm -rf "$scratch"
        ;;
    esac
    [[ "$variant" == harness-worktree ]] || [[ "$worktree" == "$shop/.worktrees/$branch" ]] ||
        die "selftest: worktree not under .worktrees"
    rm -f "$dir/suite.log" "$dir/fast.log"
}

# meta <dir> <key>
meta() {
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$1/meta.json" "$2"
}
