# Rewriting the history with no editor

Part of `lanthorn:finishing-a-development-branch`, step 5. An agent cannot use an
interactive editor, so it writes the todo list of `git rebase -i` to a file and lets git
copy it.

## Rules

- Keep every scratch file under `mktemp -d`, never in the worktree. A file in the worktree is
  an uncommitted change, and step 3 stops on it.
- Never run a git command that opens an editor. Set `GIT_EDITOR=true`.
- Set the backup ref before each rewrite. Check the tree after it.

## The technique

```bash
B=<feature branch>
BASE=<newest base>                       # origin/main, or main with no remote
TMP=$(mktemp -d)
git update-ref "refs/lanthorn/backup/$B" HEAD
git log --reverse --format='%h %s' "$BASE..HEAD"   # read the commits, oldest first
```

Group the commits into logical commits. Write one message file per group, per
`lanthorn:writing-commits`. Then write the todo list. Each group is one `pick` of its first
commit, a `fixup` of each other commit, and an `exec` that sets the message:

```bash
cat >"$TMP/msg-1" <<'EOF'
feat: rotate refresh tokens

<why, per writing-commits>
EOF
cat >"$TMP/todo" <<EOF
pick 1a2b3c4
fixup 5d6e7f8
exec git commit -q --amend -F $TMP/msg-1
pick 9a8b7c6
exec git commit -q --amend -F $TMP/msg-2
EOF
GIT_SEQUENCE_EDITOR="cp $TMP/todo" GIT_EDITOR=true git rebase -i "$BASE"
```

Then prove that the rewrite changed no file:

```bash
git diff --quiet "refs/lanthorn/backup/$B" HEAD || {
    git rebase --abort 2>/dev/null
    git reset --hard "refs/lanthorn/backup/$B"
}
```

After a reset, write the todo list again. `reset --hard` is safe here: the backup ref holds
the tip, and step 3 made sure that the worktree had no uncommitted change.

If the project declares a fast check, run it on each commit:

```bash
git rebase --exec "<fast check>" "$BASE"
```

## Three cases

**The whole range becomes one commit.** Skip the todo list:
`git reset --soft "$BASE" && git commit -q -F "$TMP/msg-1"`. Then check the tree.

**The rebase stops with a conflict.** A todo list that moves a commit past another commit on
the same lines conflicts. Run `git rebase --abort`, keep the original order of the commits,
and fold only neighbours.

**A commit fails the fast check.** `git rebase --exec` stops on it. Run `git rebase --abort`.
In the todo list, fold that commit into the next commit that passes, then rewrite again. A
fold changes no file, so the tree check still holds.
