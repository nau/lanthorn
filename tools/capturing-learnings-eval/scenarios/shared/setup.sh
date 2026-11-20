HERE_SHARED="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE_SHARED/../common.sh"
source "$HERE_SHARED/fixture.sh"
shared_fixture "$1"
cat >AGENTS.md <<'EOF'
# AGENTS.md

A small billing service in Python.

Run the tests with `python3 -m unittest`.
EOF
commit_all "Add agent instructions"
