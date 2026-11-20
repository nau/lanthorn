# Shared functions of the spec-style eval. Source this file, do not run it.
# shellcheck shell=bash

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORACLE_DIR="$HARNESS_DIR/oracle"
TEMPLATE_DIR="${TEMPLATE_DIR:-$(cd "$HARNESS_DIR/../../../validator.g8" 2>/dev/null && pwd)}"

# The validator name. The template turns it into the object name and the file names.
# The skeleton and the hidden tests use this name, so do not change it alone.
PROJECT_NAME="Deposit"

die() {
    echo "spec-style-eval: $*" >&2
    exit 2
}

require_tools() {
    local tool
    for tool in "$@"; do
        command -v "$tool" >/dev/null 2>&1 || die "missing tool: $tool"
    done
    [[ -n "$TEMPLATE_DIR" && -f "$TEMPLATE_DIR/default.properties" ]] ||
        die "validator.g8 template not found (set TEMPLATE_DIR)"
}

# scaffold <parent-dir>
# Renders the template into <parent-dir>/deposit, puts the skeleton in, and checks that the
# untouched project compiles and that its tests pass. Prints the project path.
scaffold() {
    local parent="$1"
    mkdir -p "$parent"
    # `--server` keeps sbt 2 in one foreground process. Its default thin client fails on `new`.
    (cd "$parent" && sbt --server --allow-empty --batch -Dsbt.supershell=false \
        "new file://$TEMPLATE_DIR --name=$PROJECT_NAME" >"$parent/sbt-new.log" 2>&1) ||
        die "template render failed, see $parent/sbt-new.log"
    local project="$parent/deposit"
    [[ -f "$project/project.scala" ]] || die "template render gave no project in $project"
    cp "$ORACLE_DIR/Deposit.scala" "$project/Deposit.scala"
    rm -rf "$parent/target" "$parent/project"
    (cd "$project" && scala-cli compile . >/dev/null 2>&1 && scala-cli test . >/dev/null 2>&1) ||
        die "baseline failed: the scaffold does not compile or its tests fail"
    echo "$project"
}

# score_hidden <scaffold-project> <implementation-dir> <score-dir>
# Builds a clean project in <score-dir>: the build file of the scaffold, the main sources of
# the implementation, and the hidden tests. It leaves two logs there:
#   compile.log      scala-cli compile of the main sources alone
#   hidden-test.log  scala-cli test with the hidden tests
# The tests of the implementer are left out. A broken test file of the implementer must not
# change the hidden-test score.
score_hidden() {
    local base="$1" impl="$2" score="$3"
    rm -rf "$score"
    mkdir -p "$score"
    local file
    for file in "$impl"/*.scala; do
        [[ -e "$file" ]] || continue
        case "$file" in
        *.test.scala) ;;
        */project.scala) ;;
        *) cp "$file" "$score/" ;;
        esac
    done
    cp "$base/project.scala" "$score/project.scala"
    (cd "$score" && scala-cli compile . >compile.log 2>&1)
    echo "exit=$?" >>"$score/compile.log"
    cp "$ORACLE_DIR/HiddenOracle.test.scala" "$score/"
    (cd "$score" && scala-cli test . >hidden-test.log 2>&1)
    echo "exit=$?" >>"$score/hidden-test.log"
}

# export_skill <commit> <dir>
# Writes the spec-writing skill of <commit> to <dir>: the skill text as STYLE.md, and the
# references/ directory of the skill next to it when that commit has one. STYLE.md names
# those files by a relative path, so the writer finds them from there.
export_skill() {
    local commit="$1" dir="$2" skill="skills/spec-writing"
    git -C "$HARNESS_DIR" show "$commit:$skill/SKILL.md" >"$dir/STYLE.md" || return 1
    git -C "$HARNESS_DIR" cat-file -e "$commit:$skill/references" 2>/dev/null || return 0
    mkdir -p "$dir/references" || return 1
    # Run `git archive` from the repository root. From a subdirectory it writes an empty
    # archive for a `commit:path` tree, and reports no error.
    local root
    root=$(git -C "$HARNESS_DIR" rev-parse --show-toplevel) || return 1
    git -C "$root" archive "$commit:$skill/references" | tar -x -f - -C "$dir/references"
}

# run_with_timeout <seconds> <command...>
# Exit status 124 means the command ran out of time.
run_with_timeout() {
    local seconds="$1"
    shift
    "$@" &
    local pid=$!
    (
        sleep "$seconds"
        kill -TERM "$pid" 2>/dev/null
    ) &
    local watchdog=$!
    wait "$pid"
    local status=$?
    if kill -0 "$watchdog" 2>/dev/null; then
        kill "$watchdog" 2>/dev/null
        wait "$watchdog" 2>/dev/null
    else
        status=124
    fi
    return "$status"
}
