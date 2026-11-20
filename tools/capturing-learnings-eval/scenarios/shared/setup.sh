source "$(dirname "$0")/../common.sh"
source "$(dirname "$0")/fixture.sh"
shared_fixture "$1"
cat >AGENTS.md <<'EOF'
# AGENTS.md

A small billing service in Python.

Run the tests with `python3 -m unittest`.
EOF
commit_all "Add agent instructions"
