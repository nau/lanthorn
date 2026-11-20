source "$(dirname "$0")/../common.sh"
export_fixture "$1"
# A recorded learning that is wrong: it claims the call works from any directory.
python3 - <<'EOF'
path = "tools/export.sh"
s = open(path).read()
s = s.replace('git archive "$1:docs"',
              '# git archive <commit>:<path> resolves <path> from the repository root,\n'
              '# so it works from any directory.\n'
              'git archive "$1:docs"')
open(path, "w").write(s)
EOF
commit_all "Document the archive call"
