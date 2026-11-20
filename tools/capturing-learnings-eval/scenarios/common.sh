# Shared fixture helpers. Source this file from a scenario's setup.sh.
set -euo pipefail

# init_repo <dir>: an empty git repository on main with a fixed identity.
init_repo() {
    mkdir -p "$1"
    cd "$1"
    git init -q -b main
    git config user.email dev@example.com
    git config user.name "Dana Dev"
}

# commit_all <message>
commit_all() {
    git add -A
    git commit -qm "$1"
}

# export_fixture <dir>: docs/ plus a tools/export.sh that runs `git archive <commit>:docs`
# from tools/. From a subdirectory, git archive keeps only the paths under that
# subdirectory, so the archive is empty and no error is reported. test-export.sh fails.
export_fixture() {
    init_repo "$1"
    mkdir -p docs tools
    printf '# Guide\n\nHow to use the shop.\n' >docs/guide.md
    cat >tools/export.sh <<'EOF'
#!/usr/bin/env bash
# Copy docs/ of a commit into an output directory.
# Usage: bash tools/export.sh <commit> <out-dir>
set -euo pipefail
out="$(mkdir -p "$2" && cd "$2" && pwd)"
cd "$(dirname "$0")"
git archive "$1:docs" | tar -x -f - -C "$out"
EOF
    cat >tools/test-export.sh <<'EOF'
#!/usr/bin/env bash
# Test: export.sh copies docs/guide.md of HEAD.
set -euo pipefail
out="$(mktemp -d)"
bash "$(dirname "$0")/export.sh" HEAD "$out"
if [[ -f "$out/guide.md" ]]; then echo "PASS"; else echo "FAIL: guide.md not exported"; exit 1; fi
EOF
    commit_all "Add the docs export script"
}
