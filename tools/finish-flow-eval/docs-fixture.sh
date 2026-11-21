# Docs fixtures of the finish-flow eval: scenarios D-1 to D-6. Sourced by lib.sh.
# shellcheck shell=bash

DOCS_VARIANTS="docs docs-design docs-conflict docs-refactor docs-nomap docs-supersede docs-append"
DOCS_BRANCH="add-reuse-grace"
ROTATION_ANCHOR="decision-revoke-the-family-on-any-reuse"

make_docs_fixture() {
    local dir="$1" variant="$2"
    mkdir -p "$dir"
    dir="$(cd "$dir" && pwd -P)"
    local remote="$dir/remote.git" shop="$dir/shop" mate="$dir/teammate"
    local designs
    designs=$(design_dir "$variant")
    git init -q --bare -b main "$remote"
    git clone -q "$remote" "$shop" 2>/dev/null
    identity "$shop"

    # --- main: a shop with a map, an architecture document, a living spec, one design doc
    put "$shop/.gitignore" $'.worktrees/\n.claude/'
    put "$shop/AGENTS.md" '# Shop

Token service of the shop, in POSIX shell.

## Checks

Full suite: `sh test.sh`
Fast check: `sh build.sh`'
    if [[ "$variant" != docs-nomap ]]; then
        cat >>"$shop/AGENTS.md" <<'EOF'

## Docs

Architecture: `ARCHITECTURE.md`
Living spec: `docs/spec/<area>.md`
Design docs: `docs/designs/YYYY-MM-DD-<topic>-design.md`
Plans: `docs/plans/`, deleted when the feature lands
Find a decision: `rg -n -A2 '^### Decision:' docs/designs/`
Find a rule and its citations: `rg -nF '[auth.rotate]' .`
Before you change code in an area, read its file in `docs/spec/`.
EOF
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
for f in $(find . -name "*.sh" -not -path "./.git/*"); do
    sh -n "$f" || { echo "SYNTAX ERROR: $f"; exit 1; }
done
echo "build ok"'
    put "$shop/src/clock.sh" 'now_ts() { echo 1700000000; }'
    put "$shop/src/rotate.sh" '# spec [auth.rotate] [auth.revoke-before-return]
revoke_token() { echo "revoked-$1"; }
rotate_token() { revoke_token "$1" >/dev/null; echo "tok-$1-$(now_ts)-r"; }
# spec [auth.family-revoke]
revoke_family() { echo "family-$1-revoked"; }'
    put "$shop/src/store.sh" 'store_put() { echo "$1" >>"${TOKEN_STORE:-/dev/null}"; }'
    put "$shop/tests/rotate.test.sh" '. ./src/clock.sh
. ./src/rotate.sh
[ "$(rotate_token alice)" = "tok-alice-1700000000-r" ] || { echo "rotate_token: wrong token"; exit 1; }
[ "$(revoke_family f1)" = "family-f1-revoked" ] || { echo "revoke_family: wrong result"; exit 1; }'
    put "$shop/tests/store.test.sh" '. ./src/store.sh
TOKEN_STORE=$(mktemp) store_put tok-1
exit 0'
    put "$shop/ARCHITECTURE.md" '# Architecture

Token service of the shop. Clients trade refresh tokens for access tokens.

## Components

```mermaid
flowchart LR
    api[API] --> auth[Auth]
    auth --> store[(Token store)]
```

- auth: issues, rotates and revokes tokens. Spec: `docs/spec/auth.md`
- store: keeps issued tokens.

## Invariants

- Only the store writes token data.

## Code map

- `src/rotate.sh`: auth
- `src/store.sh`: store'
    put "$shop/docs/spec/auth.md" '# Auth

*Informative.* A client trades a refresh token for a new pair. Each refresh token
works once, because the auth service revokes it during the trade.

- The auth service MUST issue a new refresh token on every refresh. `[auth.rotate]`
- The auth service MUST revoke the presented refresh token before it returns the new one. `[auth.revoke-before-return]`
- The auth service MUST revoke the token family on reuse of a revoked token. `[auth.family-revoke]`
- The auth service MUST reject an access token whose `exp` is in the past. `[auth.exp]`'
    put "$shop/$designs/2026-11-01-rotation-design.md" '---
area: auth
---

# Token rotation

## Approaches

### Decision: Revoke the family on any reuse

Status: Accepted
Area: auth
Context: A reused refresh token means that someone copied it.
Options:
- Revoke the whole family: stops the thief, and logs the user out.
- Reject only the reused token: the thief keeps the new one.
Chosen: Revoke the family, because a copied token is a theft.
Consequences: [auth.family-revoke] holds.'
    commit_all "$shop" "chore: start the shop"
    git -C "$shop" push -q -u origin main 2>/dev/null
    local main_initial
    main_initial=$(git -C "$shop" rev-parse main)

    # --- the branch
    local branch="$DOCS_BRANCH" marker="$designs/2026-11-21-grace-design.md"
    [[ "$variant" == docs-refactor ]] && branch=move-store && marker=src/persistence/store.sh
    [[ "$variant" == docs-design ]] && marker=none
    local wt="$shop/.worktrees/$branch"
    git -C "$shop" worktree add -q -b "$branch" "$wt" main 2>/dev/null

    if [[ "$variant" == docs-refactor ]]; then
        put "$wt/docs/plans/move.md" $'# Move the store\n\n- [ ] Move `src/store.sh` to `src/persistence/`\n- [ ] Fix the test'
        commit_all "$wt" "docs: plan the store move"
        mkdir -p "$wt/src/persistence"
        git -C "$wt" mv src/store.sh src/persistence/store.sh
        sed 's#\./src/store\.sh#./src/persistence/store.sh#' "$wt/tests/store.test.sh" >"$wt/tests/store.tmp" &&
            mv "$wt/tests/store.tmp" "$wt/tests/store.test.sh"
        commit_all "$wt" "refactor: move the store under src/persistence"
        sed 's/- \[ \]/- [x]/' "$wt/docs/plans/move.md" >"$wt/docs/plans/move.tmp" && mv "$wt/docs/plans/move.tmp" "$wt/docs/plans/move.md"
        commit_all "$wt" "chore: tick the move plan"
    elif [[ "$variant" != docs-design ]]; then
        local replaces=""
        [[ "$variant" == docs-supersede ]] && replaces=$'\n'"Replaces: $designs/2026-11-01-rotation-design.md#$ROTATION_ANCHOR"
        put "$wt/$designs/2026-11-21-grace-design.md" "---
area: auth
---

# Reuse grace period

Mobile clients retry on a bad network. Today the retry revokes the whole family.

## Approaches

### Decision: Accept a reused refresh token for 10 seconds

Status: Accepted
Area: auth
Context: A retry within seconds is not a theft.
Options:
- A 10-second grace period: no client change. A stolen token works for 10 seconds.
- A per-client retry token: exact. Every client must change.
Chosen: The grace period, because the clients ship monthly.
Consequences: Adds [auth.reuse-grace].$replaces"
        commit_all "$wt" "docs: design the reuse grace period"
        put "$wt/docs/plans/grace.md" $'# Grace period plan\n\n- [ ] Add `within_grace`\n- [ ] Test 5 and 11 seconds'
        commit_all "$wt" "docs: plan the grace period"
        # Every variant narrows the family rule, or the new grace rule would contradict it.
        if true; then
            sed 's/on reuse of a revoked token\. `\[auth.family-revoke\]`/on reuse of a revoked token more than 10 seconds after its revocation. `[auth.family-revoke]`/' \
                "$wt/docs/spec/auth.md" >"$wt/docs/spec/auth.tmp" && mv "$wt/docs/spec/auth.tmp" "$wt/docs/spec/auth.md"
        fi
        printf '%s\n' '- The auth service MUST accept a reuse of a revoked token within 10 seconds of its revocation. `[auth.reuse-grace]`' \
            '' '  *Implementation status: planned.*' >>"$wt/docs/spec/auth.md"
        commit_all "$wt" "docs: add the grace rule, planned"
        printf '%s\n' '# spec [auth.reuse-grace]' 'within_grace() { [ "$1" -le 10 ]; }' \
            '# A reuse of a revoked token, $2 seconds after its revocation.' \
            'on_reuse() { if within_grace "$2"; then echo "same-$1"; else revoke_family "$1"; fi; }' >>"$wt/src/rotate.sh"
        printf '%s\n' '# test: grace windw' '[ "$(on_reuse f1 5)" = "same-f1" ] || { echo "on_reuse: 5 s revoked"; exit 1; }' \
            '[ "$(on_reuse f1 11)" = "family-f1-revoked" ] || { echo "on_reuse: 11 s kept"; exit 1; }' 'exit 0' >>"$wt/tests/rotate.test.sh"
        python3 - "$wt/docs/spec/auth.md" <<'EOF'
import sys
p = sys.argv[1]; s = open(p).read()
s = s.replace("\n\n  *Implementation status: planned.*\n", "\n")
open(p, "w").write(s)
EOF
        commit_all "$wt" "feat: accept a reuse within 10 seconds"
        sed 's/- \[ \]/- [x]/' "$wt/docs/plans/grace.md" >"$wt/docs/plans/grace.tmp" && mv "$wt/docs/plans/grace.tmp" "$wt/docs/plans/grace.md"
        commit_all "$wt" "chore: tick the grace plan"
        sed 's/grace windw/grace window/' "$wt/tests/rotate.test.sh" >"$wt/tests/rotate.tmp" && mv "$wt/tests/rotate.tmp" "$wt/tests/rotate.test.sh"
        commit_all "$wt" "fix test name"
    fi

    # --- a teammate lands one commit
    git clone -q "$remote" "$mate" 2>/dev/null
    identity "$mate"
    git -C "$mate" config user.name "Teammate"
    case "$variant" in
    docs-conflict)
        sed 's/on reuse of a revoked token\. `\[auth.family-revoke\]`/on any reuse of a revoked token, and MUST log the reuse. `[auth.family-revoke]`/' \
            "$mate/docs/spec/auth.md" >"$mate/docs/spec/auth.tmp" && mv "$mate/docs/spec/auth.tmp" "$mate/docs/spec/auth.md"
        commit_all "$mate" "docs: log each family revocation"
        ;;
    docs-append)
        printf '%s\n' '- The auth service MUST log each revocation. `[auth.audit-log]`' >>"$mate/docs/spec/auth.md"
        commit_all "$mate" "docs: add the audit log rule"
        ;;
    *)
        printf '\nOpening hours: 9 to 17.\n' >>"$mate/README.md"
        commit_all "$mate" "docs: add the opening hours"
        ;;
    esac
    git -C "$mate" push -q origin main 2>/dev/null
    local teammate1
    teammate1=$(git -C "$mate" rev-parse main)

    python3 - "$dir/meta.json" "$variant" "$branch" "$wt" "$main_initial" "$teammate1" "$marker" "$designs" <<'EOF'
import json, sys
p, variant, branch, wt, main_initial, teammate1, marker, designs = sys.argv[1:]
json.dump({"variant": variant, "branch": branch, "worktree": wt, "main_initial": main_initial,
           "teammate1": teammate1, "landed_marker": marker, "designs": designs}, open(p, "w"), indent=2)
EOF
}

# design_dir <variant>: the design docs folder that the project uses.
design_dir() {
    if [[ "$1" == docs-nomap ]]; then echo docs/adr; else echo docs/designs; fi
}

# selftest_docs_fixture <dir> <variant>
selftest_docs_fixture() {
    local dir variant="$2"
    dir="$(cd "$1" && pwd -P)"
    local remote="$dir/remote.git" shop="$dir/shop"
    [[ -f "$dir/meta.json" ]] || die "docs selftest: no meta.json"
    local branch worktree main_initial teammate1 designs
    branch=$(meta "$dir" branch)
    worktree=$(meta "$dir" worktree)
    main_initial=$(meta "$dir" main_initial)
    teammate1=$(meta "$dir" teammate1)
    designs=$(design_dir "$variant")

    [[ "$(git -C "$remote" rev-parse main)" == "$teammate1" ]] || die "docs selftest: remote main is not the teammate commit"
    [[ "$(git -C "$shop" rev-parse main)" == "$main_initial" ]] || die "docs selftest: local main moved"
    git -C "$shop" cat-file -e "$teammate1" 2>/dev/null && die "docs selftest: shop already has the teammate commit"
    [[ "$(git -C "$worktree" branch --show-current)" == "$branch" ]] || die "docs selftest: worktree not on $branch"
    [[ -z "$(git -C "$worktree" status --porcelain)" ]] || die "docs selftest: worktree not clean"

    local f
    for f in AGENTS.md ARCHITECTURE.md docs/spec/auth.md "$designs/2026-11-01-rotation-design.md" test.sh build.sh; do
        git -C "$shop" cat-file -e "$main_initial:$f" || die "docs selftest: $f not on main"
    done
    local agents spec
    agents=$(git -C "$shop" show "$main_initial:AGENTS.md")
    spec=$(git -C "$shop" show "$main_initial:docs/spec/auth.md")
    if [[ "$variant" == docs-nomap ]]; then
        grep -q '^## Docs' <<<"$agents" && die "docs selftest: nomap has a map"
    else
        grep -q '^## Docs' <<<"$agents" || die "docs selftest: no map"
    fi
    local id
    for id in auth.rotate auth.revoke-before-return auth.family-revoke auth.exp; do
        grep -qF "[$id]" <<<"$spec" || die "docs selftest: $id missing on main"
    done
    git -C "$shop" show "$main_initial:ARCHITECTURE.md" | grep -q 'src/store.sh' ||
        die "docs selftest: code map does not name src/store.sh"

    local added
    added=$(git -C "$shop" diff --name-only --diff-filter=A "$main_initial" "$branch")
    case "$variant" in
    docs-design)
        [[ "$(git -C "$shop" rev-parse "$branch")" == "$main_initial" ]] || die "docs selftest: design branch has commits"
        ;;
    docs-refactor)
        grep -q "^$designs/" <<<"$added" && die "docs selftest: refactor adds a design doc"
        git -C "$shop" cat-file -e "$branch:src/persistence/store.sh" || die "docs selftest: store not moved"
        grep -q '^docs/plans/' <<<"$added" || die "docs selftest: refactor has no plan"
        ;;
    *)
        grep -qx "$designs/2026-11-21-grace-design.md" <<<"$added" || die "docs selftest: no grace design doc"
        grep -qx 'docs/plans/grace.md' <<<"$added" || die "docs selftest: no plan"
        local tip_spec
        tip_spec=$(git -C "$shop" show "$branch:docs/spec/auth.md")
        grep -qF '[auth.reuse-grace]' <<<"$tip_spec" || die "docs selftest: branch lacks the grace rule"
        grep -q 'Implementation status: planned' <<<"$tip_spec" && die "docs selftest: the built rule is still planned"
        [[ "$(git -C "$shop" log --format=%s "$main_initial..$branch" -- docs/plans | wc -l | tr -d ' ')" -ge 2 ]] ||
            die "docs selftest: the plan is not edited by a later commit"
        ;;
    esac
    if [[ "$variant" == docs-supersede ]]; then
        git -C "$shop" show "$branch:$designs/2026-11-21-grace-design.md" | grep -q "^Replaces: .*#$ROTATION_ANCHOR" ||
            die "docs selftest: no Replaces line"
    fi
    if [[ "$variant" == docs-conflict || "$variant" == docs-append ]]; then
        local scratch
        scratch=$(mktemp -d)
        git clone -q "$remote" "$scratch/c" 2>/dev/null
        git -C "$scratch/c" fetch -q "$shop" "$branch:$branch"
        if git -C "$scratch/c" -c user.name=s -c user.email=s@example.com rebase -q main "$branch" >/dev/null 2>&1; then
            die "docs selftest: $variant rebases without a conflict"
        fi
        local conflicted
        conflicted=$(git -C "$scratch/c" diff --name-only --diff-filter=U)
        [[ "$conflicted" == docs/spec/auth.md ]] || die "docs selftest: conflict is in '$conflicted', not auth.md"
    fi
    (cd "$worktree" && sh test.sh >/dev/null 2>&1) || die "docs selftest: suite fails on the branch"
    rm -f "$dir/suite.log" "$dir/fast.log"
}
