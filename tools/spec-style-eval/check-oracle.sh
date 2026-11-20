#!/usr/bin/env bash
# Checks the oracle of the spec-style eval. Run it after any change to `oracle/`.
#
#   1. The reference validator passes every hidden test.
#   2. The skeleton, which rejects everything, passes none.
#   3. A flipped boundary in the reference fails exactly the test of that boundary.
#   4. A validator written "from memory" fails at least a third of the tests.
#
# Usage: tools/spec-style-eval/check-oracle.sh
# Requires: sbt, scala-cli, python3. Exit code 0 = the oracle holds.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
require_tools sbt scala-cli python3

WORKDIR=$(mktemp -d "${TMPDIR:-/tmp}/deposit-oracle.XXXXXX")
trap 'rm -rf "$WORKDIR"' EXIT
BASE=$(scaffold "$WORKDIR/base") || exit 2
HIDDEN="$ORACLE_DIR/HiddenOracle.test.scala"
failures=0

# variant <name> <validator-file> <expectation> [<sed-expression>]
# The expectation is a Python expression over `r`, the hidden-test score.
variant() {
    local name="$1" source="$2" expect="$3" edit="${4:-}"
    local impl="$WORKDIR/$name"
    mkdir -p "$impl"
    if [[ -n "$edit" ]]; then
        sed "$edit" "$source" >"$impl/Deposit.scala"
        cmp -s "$source" "$impl/Deposit.scala" && die "variant $name: the edit changed nothing"
    else
        cp "$source" "$impl/Deposit.scala"
    fi
    score_hidden "$BASE" "$impl" "$WORKDIR/$name-score"
    python3 "$HARNESS_DIR/score.py" expect "$name" "$expect" \
        "$WORKDIR/$name-score/hidden-test.log" "$HIDDEN" || failures=$((failures + 1))
}

REF="$ORACLE_DIR/Deposit.reference.scala"
variant reference "$REF" 'r["passed"] == r["total"]'
variant skeleton "$ORACLE_DIR/Deposit.scala" 'r["passed"] == 0'
variant flip-claim-bound "$REF" '[n.split()[0] for n in r["failed"]] == ["B3"]' \
    's/lower > booking.checkIn + Grace/lower >= booking.checkIn + Grace/'
variant flip-free-cancel-bound "$REF" '[n.split()[0] for n in r["failed"]] == ["B1"]' \
    's/if upper <= booking.cancelBy then/if upper < booking.cancelBy then/'
variant flip-rounding "$REF" '[n.split()[0] for n in r["failed"]] == ["S6"]' \
    's/divCeil/divFloor/'
variant from-memory "$ORACLE_DIR/Deposit.naive.scala" 'r["total"] - r["passed"] >= r["total"] / 3'

[[ $failures -eq 0 ]] || {
    echo "check-oracle: $failures check(s) failed" >&2
    exit 1
}
echo "check-oracle: the oracle holds"
