# A repository with a staged change and a local bare remote whose pre-receive hook rejects
# a commit without a Signed-off-by line. The fact shows up only during the push.
source "$(dirname "$0")/../common.sh"
REMOTE="$(cd "$(dirname "$1")" && pwd)/remote.git"
init_repo "$1"
printf 'v1\n' >app.txt
commit_all "Add app"
git init -q --bare "$REMOTE"
cat >"$REMOTE/hooks/pre-receive" <<'EOF'
#!/usr/bin/env bash
while read -r old new ref; do
    range="$new"
    [[ "$old" =~ ^0+$ ]] || range="$old..$new"
    for c in $(git rev-list "$range"); do
        if ! git log -1 --format=%B "$c" | grep -q '^Signed-off-by:'; then
            echo "rejected: commit $c has no Signed-off-by line. Every commit on this remote must be signed off (git commit -s)."
            exit 1
        fi
    done
done
EOF
chmod +x "$REMOTE/hooks/pre-receive"
git remote add origin "$REMOTE"
git commit -q --amend --no-edit -s
git push -q origin main
printf 'v2\n' >app.txt
git add app.txt
