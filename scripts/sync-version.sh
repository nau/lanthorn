#!/usr/bin/env bash
# Keep every host manifest on the same version.
#
# .claude-plugin/plugin.json is the source of truth. Each additional host adds
# another manifest carrying the same version string, and a version that drifts
# is invisible until a user installs the stale one.
#
# Usage:
#   scripts/sync-version.sh           check only, non-zero exit on drift
#   scripts/sync-version.sh 0.2.0     set every manifest to 0.2.0

set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${REPO}/.claude-plugin/plugin.json"
FOLLOWERS=("${REPO}/.codex-plugin/plugin.json")

read_version() {
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$1"
}

set_version() {
    python3 - "$1" "$2" <<'PY'
import json, sys
path, version = sys.argv[1], sys.argv[2]
with open(path) as f:
    data = json.load(f)
data["version"] = version
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY
}

if [ $# -ge 1 ]; then
    set_version "$SOURCE" "$1"
fi

want="$(read_version "$SOURCE")"
status=0

for manifest in "${FOLLOWERS[@]}"; do
    [ -f "$manifest" ] || continue
    got="$(read_version "$manifest")"
    if [ "$got" = "$want" ]; then
        printf 'ok    %s %s\n' "$want" "${manifest#"$REPO"/}"
    elif [ $# -ge 1 ]; then
        set_version "$manifest" "$want"
        printf 'set   %s %s (was %s)\n' "$want" "${manifest#"$REPO"/}" "$got"
    else
        printf 'DRIFT %s %s (expected %s)\n' "$got" "${manifest#"$REPO"/}" "$want"
        status=1
    fi
done

exit "$status"
