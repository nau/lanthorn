---
name: finishing-a-development-branch
description: Use when implementation is complete and the work must reach the base branch – at the end of a plan run, when all tasks are done, or when asked to land, merge, integrate, or finish a branch.
---

# Finishing a Development Branch

## Overview

This skill forks `superpowers:finishing-a-development-branch`. The upstream skill offers a
menu with a pull request, and it merges with a plain `git merge`. This fork lands finished
work on the base branch by rebase and fast-forward: a linear history of logical commits, no
pull request, no merge commit, and no pushed feature branch. A project that declares pull
requests keeps them, through the upstream skill.

**Announce at start:** "I'm using lanthorn's finishing-a-development-branch skill to land
this work."

## Four rules that always hold

- Never change the base branch, local or remote, before your human partner says yes.
- Never offer a pull request. A project that declares pull requests gets the superpowers skill.
- Never use a force option on a push.
- After a history rewrite, `git diff <backup ref> HEAD` must print nothing.

## The flow

1. Find the base branch and the remote, then fetch.
2. Resolve the landing policy.
3. Rebase on the newest base, adjust the feature to what the base changed, then run the
   docs step.
4. Run the full suite.
5. Clean the history.
6. Show the summary, ask one question, then land.
7. Clean up.

Do steps 1 to 5 without a question, unless a step says to ask. They change only the feature
branch, and the backup ref undoes the rewrite.

## Step 1: Base branch and remote

- **Base branch:** the one that the plan, the conversation, or the session's git context
  names. If none names it and one branch is the clear candidate, take it, and mark it
  "guessed" in the summary of step 6. If more than one branch could be the base, such as
  `main` and `develop`, ask before step 3.
- **Remote:** the one that the base branch tracks: `git rev-parse --abbrev-ref main@{upstream}`.

| Remotes | `main` tracks | Use |
|---|---|---|
| `origin` | `origin/main` or nothing | `origin` |
| `origin`, `upstream` | `upstream/main` | `upstream` |
| `origin`, `upstream` | nothing | Ask which remote |
| none | nothing | No remote. Land locally. |

Then fetch: `git fetch <remote>`. The **newest base** is `<remote>/<base>`, or the local base
branch when there is no remote. Never trust the local base branch while a remote exists: a
teammate may have landed since your last fetch.

## Step 2: Landing policy

Three tiers. Stop at the first that answers.

1. **The instruction file declares it.** `AGENTS.md` or `CLAUDE.md` at the project root.
2. **The history shows pull requests.** Search the last 20 commits of the newest base for a
   merge commit, or a subject that ends with a pull request number, such as
   `feat: rotate tokens (#41)`. If you find one, ask your human partner which policy
   applies. Record the answer in the instruction file, in its own commit on the feature
   branch. Ask before you create a new instruction file.
3. **Nothing answers.** Land by fast-forward. Do not record this default.

If the policy is pull requests, or your human partner asks for a pull request: stop here and
use `superpowers:finishing-a-development-branch`.

Read the commands from the same file. The full suite comes from the instruction file, else
from the build files, else ask. Run a fast check only if the instruction file declares one.

```markdown
## Landing

Land on main by fast-forward. No pull requests.
Full suite: `make test`
Fast check: `make build`
```

## Step 3: Rebase and adjust

1. Stop if the worktree has uncommitted changes. Commit them first, or ask.
2. Record the merge base: `OLD=$(git merge-base HEAD <newest base>)`.
3. Rebase: `git rebase <newest base>`.
4. Resolve each conflict. Record each file and how you resolved it. If a resolution changes
   behaviour that the design of the feature states, stop and ask. A conflict in a file of
   the living spec follows the docs step below.
   - Each side only adds rules with different IDs: keep both. No question.
   - Both sides change the same rule ID: stop and ask, naming the ID and both texts.
5. Read what the base changed: `git log --stat $OLD..<newest base>`. For each change that
   touches code the feature uses or changes, read the diff.
6. If a base change breaks the feature or duplicates it, fix the feature in a new commit,
   test first. Record each adjustment.
7. Run the docs step: follow `lanthorn:keeping-project-docs`,
   `references/docs-step.md`. It runs on every landing, also one with no design doc. In a
   project with no `## Docs` map, it only proposes a map in the summary. Its changes are
   part of the landing, and the full suite of step 4 tests them.

## Step 4: Full suite

Run the full suite on the tip, after the rebase and the adjustments, before the rewrite.
Record the command and the result.

- It fails: do not rewrite, do not land. Use `superpowers:systematic-debugging`.
- The tree that lands must be identical to a tree that passed. A new adjustment needs a new
  run. The rewrite of step 5 changes no file, so it needs none.

## Step 5: Clean the history

Replace the working commits with a few logical commits. A **logical commit** has one reason
to change, and a reader can revert it alone.

- Fold each fix into the commit that it fixes.
- Keep a refactoring that prepares a change apart from that change.
- Do not join two independent changes. Do not split one change to get more commits.
- If the commits are logical already, skip the rewrite.
- Write each message per `lanthorn:writing-commits`, for the wording only. Add no other
  commit during the landing, such as one that records the commit style in `AGENTS.md`. If a
  skill asks you to record something, propose it in the summary instead. The changes of the
  docs step are part of the landing, and fold into the feature's commits.

| Before | After |
|---|---|
| `task 1: token rotation` | `feat: rotate refresh tokens` |
| `fix review: revoke before return` | folded into the commit above |
| `refactor: extract token store` | `refactor: extract the token store` |
| `task 2: family revocation` | `feat: revoke the token family on replay` |
| `fix test name` | folded into the commit above |

Before each rewrite, set the backup ref: `git update-ref refs/lanthorn/backup/<branch> HEAD`.
Then follow [references/rewriting-history.md](references/rewriting-history.md). It rewrites
with no editor, checks the tree, and runs the fast check on each commit.

**Fast check:** if the project declares one, run it on every commit each time you reach this
step, also when you skip the rewrite, and again after each later rebase:
`git rebase --exec "<fast check>" <newest base>`.

## Step 6: Land

Show the summary, then ask. Fill every field. Write "None" for an empty one.

```markdown
Ready to land on <remote>/<base> (base <named by the plan | named in the conversation | guessed>):

Commits:
- <subject of each commit>

Full suite: `<command>` – <result>
Conflicts resolved: <file and resolution, or None>
Adjustments for base changes: <what and why, or None>
Rule changes: <rule IDs added, changed, or withdrawn, or None>
Planned rules: <rule IDs still planned, or None>
Docs: <architecture updated, decisions superseded, plans deleted, map proposed, or None>
Proposals: <anything a skill asked you to record, or None>

Land these <N> commits on <remote>/<base> by fast-forward?
```

**No:** keep the branch, the worktree, and the backup ref. Stop.

**Yes:**

1. Push without force: `git push <remote> HEAD:<base>`.
2. Fast-forward the local base branch to `<remote>/<base>`:
   - A worktree has the base checked out (`git worktree list`): run
     `git -C <that worktree> merge --ff-only <remote>/<base>`. Git refuses to move a
     checked-out branch from anywhere else.
   - No worktree has it: `git fetch . <remote>/<base>:<base>`.
   - Never switch the branch of a worktree other than your own.
   - If git refuses the fast-forward, leave the base branch as it is and report it.
3. With no remote, fast-forward the base branch to the tip the same way.

Never create a merge commit.

**The push is rejected:**

| Git says | Means | Do |
|---|---|---|
| `(fetch first)` or `(non-fast-forward)` | A teammate landed first | Fetch, then steps 3 to 5 again. Ask again only if that rebase had a conflict or an adjustment. |
| Anything else, such as `(protected branch hook declined)` | Someone else decided | Stop and report. Do not work around it. |

Push at most 3 times in one finish. After the third rejection, stop and report.

## Step 7: Clean up

Only after a landing, or after your human partner typed `discard`.

1. Delete the backup ref: `git update-ref -d refs/lanthorn/backup/<branch>`.
2. Remove the worktree only if its parent directory is `.worktrees` or `worktrees` at the
   root of the main checkout. Run `git worktree remove <path>` from the main checkout.
3. Leave any other worktree, and its branch, in place, for example one under
   `.claude/worktrees/`. The harness owns it.
4. Never force a removal. If git refuses, show `git status --porcelain` of the worktree and
   ask.
5. Delete the feature branch only if no worktree has it checked out. First check that the
   newest base holds it: `git merge-base --is-ancestor <branch> <remote>/<base>`. Then
   `git branch -D <branch>`. Plain `git branch -d` compares with the current checkout, not
   with the remote, so it can refuse a branch that landed.
6. Never delete a remote copy of the feature branch. Report it, if one exists.

To discard, confirm first, and wait for the exact word:

```
This will permanently delete branch <name>, its commits <list>, and the worktree at <path>.
Type 'discard' to confirm.
```

## Rationalisations

| Thought | Reality |
|---|---|
| "I'll show the three options: merge, pull request, keep" | There is no menu. Ask one question: land or not. |
| "'Yes' does not pick an option" | The question has one yes or no answer. Yes means land. |
| "Merging and pushing are hard to undo, so I'll wait" | That is why you ask once, with the summary. After a yes, land. |
| "The git context says the branch split from main, so no fetch" | Fetch. The context is from the start of the session. A teammate may have landed since. |
| "I'll push the feature branch first, to be safe" | No branch push. The feature reaches the remote only as the new base. |
| "The rebase had no conflict, so the base changes do not matter" | No conflict is not no break. Read the base changes. |
| "The push was rejected, `--force` will fix it" | A rejection means the remote moved. Fetch and rebase. |
| "The branch is protected, so I'll open a pull request instead" | Stop and report. The protection is someone else's decision. |
| "`git branch -d` refused, so the branch did not land" | Check `git merge-base --is-ancestor`. It compares with the right base. |
| "This worktree under `.claude/worktrees/` is clutter" | The harness owns it. Leave it and its branch. |
| "No design doc on this branch, so no docs step" | The docs step runs on every landing. A refactor can still change the architecture document. |
| "Both changes to that rule look compatible, so I'll merge them" | Two branches that change one rule is a design question. Ask. |
