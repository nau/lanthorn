# Finish flow: land a feature branch by fast-forward

## Overview

*Informative.* The finish flow takes a completed feature branch to the base
branch with no pull request and no merge commit. It is a fork of the
superpowers skill `finishing-a-development-branch`.

The agent first resolves the landing policy of the project. When the policy is
`fast-forward`, the agent rebases the feature branch on the remote base. Then it
reads what the base changed, and it adjusts the feature where those changes
touch it. Then it runs the full suite. Then it rewrites the feature commits into
a few logical commits, and it proves that the rewrite changed no file.

The agent shows the result and asks the human partner one question. After a
yes, it pushes the tip to the remote base without force. Then it fast-forwards
the local base, and it removes the branch and the worktree. If the remote
rejects the push because a teammate landed first, the agent starts again at the
rebase.

When the landing policy is `pull request`, the agent hands the work to the
superpowers skill.

## Goals and non-goals

*Informative.*

**Problem.** The superpowers skill shows a menu with a pull request option. It
merges with a plain `git merge`. It has no rebase step and no history cleanup. A
team of senior engineers who supervise agents wants a different end: a linear
history on the base branch, and a landing that takes minutes.

**Goals**

- The base branch gets a linear history of logical commits.
- A feature lands without a pull request and without a pushed feature branch.
- A teammate's work is never overwritten.
- The flow works for any project and any language.
- A project that declares pull requests keeps them.

**Non-goals**

- A rebase after each stage of the implementation. A later design covers it.
- The architecture document, the decision log, and the living spec. A later
  design covers them.
- Task size and model tiers for the implementation loop.
- A better pull request flow. The superpowers skill owns that path.
- A merge queue or a merge bot.
- Signed commits, and CI that runs before the landing.
- A standing permission to land without a question.
- A record of the agent session in the commit.

**Anti-goals.** Each of these makes the flow a failure, even if it runs:

- The agent offers a pull request in a project that lands by fast-forward.
- The agent forces a push.
- The history rewrite changes a file.
- The agent lands a tree that the full suite did not pass.
- The agent changes the base branch before the human partner says yes.

## Approaches

*Informative.*

| Decision | Options | Picked | By |
|---|---|---|---|
| What gates the base branch | A local run of the full suite. CI on a pushed feature branch. | Local run | Human partner |
| Tests per rewritten commit | Tip only. Every commit. Full suite on the tip, plus a fast check per commit when the project declares one. | The third | Human partner |
| How to build it in lanthorn | A: fork the finish skill only. B: a new skill with a new name. C: fork the two superpowers skills that call it. | A | Human partner |

**Why A.** The two superpowers execution skills end with a hard-coded line that
names the superpowers finish skill. A fork with the same name lets the router
rule "a lanthorn fork replaces the original" redirect that line. B has the same
risk as A, and the two names give the agent less reason to connect the skills. C
makes the handoff certain, but it adds about 6,000 words of fork to merge on
each superpowers release.

**Calls by the author.** The human partner saw the first five and did not
object. The rest are new in this document.

- The agent rewrites the history without a question first. `[CH-1]` and
  `[CH-9]` make that safe.
- The agent asks before the landing, and only there by default. `[LD-4]`
- A rejected push restarts the flow at the rebase, three pushes at most.
  `[LD-16]`, `[LD-18]`
- A project rule wins over the default. `[LP-1]`
- The agent leaves a worktree that the harness owns. `[CU-4]`
- The full suite runs before the history rewrite, not after it. `[VF-1]`
- A `pull request` policy hands the work to the superpowers skill. `[LP-9]`,
  `[LP-10]`
- The agent does not delete a remote copy of the feature branch. `[CU-7]`
- The remote is the one that the base branch tracks. `[BB-3]`
- The record of an answered landing policy lands with the feature. `[LP-5]`
- The agent fast-forwards the base branch inside the worktree that holds it.
  It never switches the branch of that worktree. `[LD-11]`, `[LD-12]`
- The agent deletes the feature branch only after it verifies that the newest
  base holds the tip. `[CU-10]`
- The fast check runs again after each later rebase. `[CH-14]`
- After a rejected push, the agent pushes again with no new question, when the
  second rebase had no conflict and no adjustment. `[LD-17]`
- A request for a pull request hands the work to the superpowers skill.
  `[LP-15]`

## Assumptions

- The full suite is fast enough to run on a developer machine before each
  landing. The project team keeps it so. `[A-1]`
- Each engineer may push to the remote base. The repository administrator
  grants that. `[A-2]`
- The remote rejects a push that is not a fast-forward. Git does that when the
  push carries no force option. `[A-3]`
- Nobody else builds on the feature branch. The team keeps each feature in the
  worktree of one engineer. `[A-4]`
- The routing table of `using-lanthorn` is in the context of the agent when a
  plan run ends. The SessionStart hook of lanthorn makes that true on Claude
  Code, also after a compaction. `[A-5]`

## Definitions

- **agent** – the coding agent that runs the finish flow.
- **human partner** – the engineer who supervises the agent.
- **feature branch** – the branch that holds the completed work.
- **base branch** – the branch that the feature branch forked from. It is
  usually `main`.
- **remote** – the remote that the base branch tracks, for example `origin`.
- **remote base** – the base branch on the remote, for example `origin/main`. A
  project with no remote has no remote base.
- **newest base** – the remote base, or the base branch when the project has no
  remote.
- **tip** – the newest commit of the feature branch.
- **instruction file** – `AGENTS.md` or `CLAUDE.md` at the root of the project.
- **landing policy** – one of `fast-forward` and `pull request`.
- **full suite** – the complete test command of the project.
- **fast check** – a command that proves that a commit builds. The instruction
  file declares it.
- **backup ref** – the ref `refs/lanthorn/backup/<feature branch>`. It points at
  the tip from before the history rewrite.
- **logical commit** – a commit that has one reason to change, and that a reader
  can revert alone.
- **land** – to make the remote base, then the local base branch, point at the
  tip.

## Find the base branch and the remote

**Overview** – *informative.* The agent first settles where the work lands: the
base branch, and the remote that receives the push. It takes both from what the
project already states. It asks only when it would otherwise guess, because a
wrong target means a push to the wrong branch.

**Who** – the agent.

**Trigger** – the finish flow starts.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The plan and the conversation | Text |
| Input | The git configuration of the project | Branch and remote settings |
| Output | The base branch | Branch |
| Output | The remote, or none | Remote |

**Checks enforced here**

- The agent MUST use the base branch that the plan or the conversation names.
  `[BB-1]`
- If neither names a base branch, the agent MUST ask the human partner to
  confirm its best guess. `[BB-2]`
- The agent MUST use the remote that the base branch tracks. `[BB-3]`
- If the base branch tracks no remote and the project has one remote, the agent
  MUST use that remote. `[BB-4]`
- If the base branch tracks no remote and the project has several remotes, the
  agent MUST ask which remote to use. `[BB-5]`
- The agent MUST fetch the remote base before it resolves the landing policy.
  `[BB-6]`

*Example, illustrative.* For `[BB-3]` to `[BB-5]`:

| Remotes | `main` tracks | The remote |
|---|---|---|
| `origin` | `origin/main` | `origin` |
| `origin` | Nothing | `origin` |
| `origin`, `upstream` | `upstream/main` | `upstream` |
| `origin`, `upstream` | Nothing | The agent asks |
| None | Nothing | None |

**Why:** A project with a fork has two remotes, and only one of them holds the
shared base branch. `[BB-6]` exists because a stale local copy can hide the
pull request traces that `[LP-2]` looks for. `[LD-3]` shows both names to the
human partner before anything is pushed.

## Resolve the landing policy

**Overview** – *informative.* The agent decides how this project lands work. A
declaration in the instruction file wins. Without a declaration, a trace of pull
requests in the history makes the agent ask. Without either, the default is
`fast-forward`.

**Who** – the agent.

**Trigger** – the base branch and the remote are known.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The instruction file | Markdown |
| Input | The last 20 commits of the newest base | Git log |
| Output | The landing policy | `fast-forward` or `pull request` |
| Output | The command of the full suite | Shell command |
| Output | The command of the fast check | Shell command, or none |

**Checks enforced here**

- The agent MUST use the landing policy that the instruction file declares.
  `[LP-1]`
- If the instruction file declares no landing policy, the agent MUST search the
  last 20 commits of the newest base for a pull request trace. `[LP-2]`
- A pull request trace is a merge commit, or a subject that ends with a pull
  request number. `[LP-3]`
- If the agent finds a pull request trace, it MUST ask the human partner which
  landing policy applies. `[LP-4]`
- After `[LP-4]`, the agent MUST record the landing policy in the instruction
  file, in its own commit on the feature branch. `[LP-5]`
- If the project has no instruction file, the agent MUST record the landing
  policy only after the human partner agrees to a new file. `[LP-6]`
- If the agent finds no declaration and no pull request trace, it MUST use
  `fast-forward`. `[LP-7]`
- The agent MUST NOT record a landing policy that `[LP-7]` chose. `[LP-8]`
- If the landing policy is `pull request`, the agent MUST stop the finish flow.
  `[LP-9]`
- After `[LP-9]`, the agent MUST use `superpowers:finishing-a-development-branch`.
  `[LP-10]`
- The agent MUST use the command of the full suite that the instruction file
  declares. `[LP-11]`
- If the instruction file declares no full suite, the agent MUST find the
  command in the build files of the project. `[LP-12]`
- If `[LP-12]` finds no command, the agent MUST ask the human partner for it.
  `[LP-13]`
- The agent MUST run a fast check only if the instruction file declares one.
  `[LP-14]`
- If the human partner asks for a pull request for this feature, the agent MUST
  use `superpowers:finishing-a-development-branch`. `[LP-15]`

*Example, illustrative.* A declaration in the instruction file:

```markdown
## Landing

Land on main by fast-forward. No pull requests.
Full suite: `make test`
Fast check: `make build`
```

*Example, illustrative.* Subjects that are a pull request trace for `[LP-3]`:

| Subject | Trace |
|---|---|
| `Merge pull request #41 from team/add-rotation` | Yes, a merge commit |
| `feat: rotate refresh tokens (#41)` | Yes, a pull request number at the end |
| `fix: reject token #41 on replay` | No, the number is not at the end |

**Why:** A project that reviews through pull requests has a reason for it. A
direct push to its base branch skips that review, and nobody can undo a push
that teammates already pulled. `[LP-8]` exists because a default that someone
writes into the instruction file becomes a decision that nobody made.

## Rebase and adjust

**Overview** – *informative.* The agent moves the feature branch onto the newest
base. Then it reads what the base changed since the feature branch forked. A
change that breaks the feature, or that duplicates it, gets a fix now, because
the rebase alone only proves that the text merges.

**Who** – the agent.

**Trigger** – the landing policy is `fast-forward`.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The feature branch | Branch |
| Input | The remote base, or the base branch when no remote exists | Branch |
| Output | The feature branch on top of the newest base | Branch |
| Output | A list of the conflicts and their resolutions | Text |
| Output | A list of the adjustments | Text |

**Checks enforced here**

- The agent MUST NOT start the rebase while the worktree has uncommitted
  changes. `[RB-1]`
- The agent MUST fetch the remote base before the rebase. `[RB-2]`
- The agent MUST record the merge base of the feature branch before the rebase.
  `[RB-3]`
- The agent MUST rebase the feature branch on the remote base. `[RB-4]`
- If the project has no remote, the agent MUST rebase on the base branch.
  `[RB-5]`
- The agent MUST resolve each conflict of the rebase. `[RB-6]`
- The agent MUST record each conflicted file with its resolution. `[RB-7]`
- If a resolution changes behaviour that the design of the feature states, the
  agent MUST stop and ask the human partner. `[RB-8]`
- If the base moved, the agent MUST read the base changes between the merge
  base of `[RB-3]` and the new base. `[RB-9]`
- The agent MUST check each base change that touches code which the feature
  uses or changes. `[RB-10]`
- If a base change breaks the feature or duplicates it, the agent MUST adjust
  the feature in a new commit. `[RB-11]`
- The agent MUST record each adjustment. `[RB-12]`

**Checks delegated elsewhere**

- An adjustment that changes behaviour gets a test first.
  `superpowers:test-driven-development` enforces that.

**Why:** Git reports a conflict only when two changes touch the same lines. A
teammate who renames a function that the feature calls causes no conflict and
a broken build. `[RB-9]` to `[RB-11]` catch what the rebase cannot see.
`[RB-8]` exists because a conflict between two behaviours is a design decision,
and the human partner owns the design.

## Verify

**Overview** – *informative.* The agent runs the full suite once, on the tip
that the rebase and the adjustments produced. The history rewrite comes after
this run. That order is safe, because the rewrite changes no file.

**Who** – the agent.

**Trigger** – the rebase and the adjustments are complete.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The command of the full suite | Shell command |
| Output | The result of the run | Pass or fail, with the output |

**Checks enforced here**

- The agent MUST run the full suite on the tip after the rebase and before the
  history rewrite. `[VF-1]`
- If the full suite fails, the agent MUST NOT rewrite the history. `[VF-2]`
- If the full suite fails, the agent MUST NOT land. `[VF-3]`
- The tree that the agent lands MUST be identical to a tree that passed the
  full suite. `[VF-4]`
- The agent MUST record the command and the result of the run. `[VF-5]`

*Example, illustrative.* For `[VF-4]`:

| Event after the passing run | Run the full suite again? |
|---|---|
| The history rewrite, and `[CH-9]` holds | No |
| A fold under `[CH-12]`, and `[CH-9]` holds | No |
| A new adjustment commit | Yes |
| A second rebase after a rejected push | Yes |

**Why:** A failure that shows up after the rewrite needs a fix commit, and a fix
commit needs a second rewrite. The run before the rewrite avoids that loop.
`[A-1]` makes a local run a practical gate.

## Clean the history

**Overview** – *informative.* The agent replaces the working commits of the
feature with a few logical commits. A backup ref keeps the old tip. The agent
compares the new tip with the backup ref, so a rewrite that changed a file
cannot pass.

**Who** – the agent.

**Trigger** – the full suite passed.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The commits between the base and the tip | Commits |
| Input | The command of the fast check, if declared | Shell command |
| Output | The backup ref | Ref |
| Output | The rewritten commits | Commits |

**Checks enforced here**

- The agent MUST set the backup ref to the tip before each rewrite. `[CH-1]`
- The agent MUST rewrite only the commits between the base and the tip.
  `[CH-2]`
- Each commit that the rewrite produces MUST be a logical commit. `[CH-3]`
- The agent MUST fold a fix into the commit that it fixes. `[CH-4]`
- The agent SHOULD keep a refactoring that prepares a change apart from that
  change. `[CH-5]`
- The agent MUST NOT join two independent changes into one commit. `[CH-6]`
- The agent MUST NOT split one logical commit to get more commits. `[CH-7]`
- The agent MUST NOT run a git command that opens an editor. `[CH-8]`
- After the rewrite, the tree of the tip MUST be identical to the tree of the
  backup ref. `[CH-9]`
- If `[CH-9]` fails, the agent MUST set the feature branch back to the backup
  ref before it rewrites again. `[CH-10]`
- If the project declares a fast check, the agent MUST run it on each commit
  between the newest base and the tip. `[CH-11]`
- The agent MUST fold a commit that fails the fast check into the next commit
  that passes it. `[CH-12]`
- If the commits between the base and the tip are logical commits already, the
  agent MUST skip the rewrite. `[CH-13]`
- After a return to `[RB-2]`, the agent MUST run the fast check of `[CH-11]`
  again. `[CH-14]`

**Checks delegated elsewhere**

- The message of each rewritten commit follows `lanthorn:writing-commits`.

*Example, illustrative.* A rewrite that satisfies `[CH-3]` to `[CH-6]`:

| Before | After |
|---|---|
| `task 1: token rotation` | `feat: rotate refresh tokens` |
| `fix review: revoke before return` | folded into the commit above |
| `fix lint` | folded into the commit above |
| `refactor: extract token store` | `refactor: extract the token store` |
| `task 2: family revocation` | `feat: revoke the token family on replay` |
| `fix test name` | folded into the commit above |

*Example, illustrative.* The check for `[CH-9]`. No output means that the trees
are identical:

```bash
git diff refs/lanthorn/backup/add-token-rotation HEAD
```

**Why:** A fix commit tells a reader how the agent got there, not what changed.
It also makes `git blame` point at the fix and not at the reason. `[CH-9]` turns
"the rewrite is safe" from a claim into a check, and it is why the agent may
rewrite without a question. `[A-4]` makes the rewrite harmless to teammates.
`[CH-12]` keeps `git bisect` usable without a full suite run per commit. A fold
changes no file, so `[CH-9]` still holds after it. `[CH-14]` exists because a
rebase replays each commit on new code, and a commit in the middle can stop
building. After a return to `[RB-2]`, the commits are usually logical already,
so `[CH-13]` skips the second rewrite.

## Land

**Overview** – *informative.* The agent shows what it is about to land and asks
one question. After a yes, it pushes the tip to the remote base, and the remote
accepts only a fast-forward. A rejection means that a teammate landed first, so
the agent goes back to the rebase and tries again.

**Who** – the agent.

**Trigger** – the history is clean, and `[VF-4]` holds.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The lists from `[RB-7]` and `[RB-12]`, and the record from `[VF-5]` | Text |
| Input | The answer of the human partner | Yes or no |
| Output | The remote base and the base branch at the tip | Branches |

**Checks enforced here**

- The agent MUST show a summary before the question. `[LD-1]`
- The summary MUST hold the subject of each commit, the record of `[VF-5]`, and
  the lists of `[RB-7]` and `[RB-12]`. `[LD-2]`
- The summary MUST name the base branch and the remote. `[LD-3]`
- The agent MUST ask the human partner whether to land on the base branch.
  `[LD-4]`
- The agent MUST NOT change the base branch or the remote base before the human
  partner says yes. `[LD-5]`
- The agent MUST NOT offer a pull request. `[LD-6]`
- If the human partner says no, the agent MUST keep the feature branch, the
  worktree, and the backup ref. `[LD-7]`
- The agent MUST push the tip to the remote base. `[LD-8]`
- The agent MUST NOT use a force option on any push. `[LD-9]`
- After the push succeeds, the agent MUST fast-forward the base branch to the
  remote base. `[LD-10]`
- If a worktree has the base branch checked out, the agent MUST run that
  fast-forward as a fast-forward-only merge inside that worktree. `[LD-11]`
- The agent MUST NOT switch the branch of a worktree other than its own.
  `[LD-12]`
- If the base branch cannot fast-forward, the agent MUST leave it unchanged and
  report it. `[LD-13]`
- If the project has no remote, the agent MUST fast-forward the base branch to
  the tip. `[LD-14]`
- The agent MUST NOT create a merge commit. `[LD-15]`
- If the remote rejects the push because the remote base holds a commit that
  the tip lacks, the agent MUST return to `[RB-2]`. `[LD-16]`
- After a return to `[RB-2]`, the agent MUST ask the question of `[LD-4]` again
  only if the rebase had a conflict or an adjustment. `[LD-17]`
- The agent MUST NOT push more than 3 times in one finish flow. `[LD-18]`
- If the remote rejects the third push, the agent MUST stop and report.
  `[LD-19]`
- If the remote rejects a push for any other reason, the agent MUST stop and
  report. `[LD-20]`

*Example, illustrative.* For `[LD-16]` to `[LD-20]`. Git reports a commit that
the tip lacks as `fetch first` or as `non-fast-forward`:

| Push | Result | The agent |
|---|---|---|
| 1 | `! [rejected] HEAD -> main (fetch first)` | Returns to `[RB-2]` |
| 2 | `! [rejected] HEAD -> main (non-fast-forward)` | Returns to `[RB-2]` |
| 3 | `! [rejected] HEAD -> main (fetch first)` | Stops and reports |
| Any | `! [remote rejected] HEAD -> main (protected branch hook declined)` | Stops and reports |

*Example, illustrative.* A push that satisfies `[LD-8]` and `[LD-9]`:

```bash
git push origin HEAD:main
```

*Example, illustrative.* For `[LD-10]` to `[LD-13]`, from a linked worktree:

| State of the base branch | The agent |
|---|---|
| Checked out in the main checkout, no overlapping local changes | Merges with fast-forward only, inside the main checkout |
| Checked out in the main checkout, git refuses the merge | Leaves it unchanged and reports |
| Checked out in no worktree | Moves the branch with a fetch that allows a fast-forward only |

**Why:** A push to the base branch reaches every teammate, so the human partner
decides it. `[LD-9]` and `[A-3]` together mean that a race between two engineers
ends in a rejected push, never in lost work. `[LD-20]` exists because a
protected branch or a failed login is a decision by someone else, and the agent
does not work around it. `[LD-18]` stops a loop on a base branch that moves
faster than the full suite runs. `[LD-11]` exists because git refuses to move a
branch that a worktree has checked out, except from inside that worktree.
`[LD-12]` keeps the checkout of the human partner on the branch that they chose.

## Clean up

**Overview** – *informative.* After the landing, the feature branch and the
backup ref hold nothing that the base branch lacks, so the agent removes them.
It removes a worktree only when superpowers created it. The harness removes its
own worktrees.

**Who** – the agent.

**Trigger** – the landing succeeded. A discard under `[CU-11]` is the only other
trigger.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Input | The path of the worktree | Path |
| Output | A report of what the agent removed and what it left | Text |

**Checks enforced here**

- Without a discard under `[CU-11]`, the agent MUST NOT clean up before the
  landing succeeds. `[CU-1]`
- The agent MUST delete the backup ref. `[CU-2]`
- The agent MUST remove the worktree only if its parent directory is
  `.worktrees` or `worktrees` at the root of the main checkout. `[CU-3]`
- The agent MUST leave any other worktree and its branch in place. `[CU-4]`
- The agent MUST NOT force the removal of a worktree. `[CU-5]`
- If git refuses the removal, the agent MUST show the uncommitted files and ask
  the human partner. `[CU-6]`
- The agent MUST NOT delete a remote copy of the feature branch. `[CU-7]`
- The agent MUST report a remote copy of the feature branch, if one exists.
  `[CU-8]`
- The agent MUST delete the feature branch only if no worktree has it checked
  out. `[CU-9]`
- After a landing, the agent MUST verify that the newest base holds the tip
  before it deletes the feature branch. `[CU-10]`
- The agent MUST discard work only after the human partner types the word
  `discard`. `[CU-11]`

*Example, illustrative.* For `[CU-3]`, with the main checkout at `/work/shop`:

| Path of the worktree | Remove it? |
|---|---|
| `/work/shop/.worktrees/add-token-rotation` | Yes |
| `/work/shop/worktrees/add-token-rotation` | Yes |
| `/work/shop/.claude/worktrees/add-token-rotation` | No, the harness owns it |
| `/work/elsewhere/add-token-rotation` | No |

*Example, illustrative.* The check for `[CU-10]`. Exit status 0 means that the
newest base holds the tip:

```bash
git merge-base --is-ancestor add-token-rotation origin/main
```

**Why:** `[CU-3]` to `[CU-6]` and `[CU-11]` come from the superpowers skill. A worktree outside those two directories belongs to the harness, and a
refused removal means that files exist nowhere else. `[CU-7]` exists because a
remote branch is visible to teammates, and nobody asked the agent to remove it.
`[CU-10]` exists because the safe delete of git compares with the current
checkout, not with the newest base. After `[LD-13]` it refuses a branch that
landed. The agent checks the real condition itself, then deletes.

## Packaging

**Overview** – *informative.* The finish flow ships as one skill in lanthorn,
with one reference file and one row in the routing table. Four scenarios prove
that it works. The first scenario tests the redirect from the hard-coded line
of superpowers.

**Who** – the builder.

**Trigger** – the human partner approved this design.

**Structure**

| Direction | Item | Type |
|---|---|---|
| Output | `skills/finishing-a-development-branch/SKILL.md` | Skill |
| Output | A reference file for the history rewrite | Markdown |
| Output | A row in the routing table of `using-lanthorn` | Table row |

**Checks enforced here**

- The skill MUST have the name `finishing-a-development-branch`. `[PK-1]`
- The description of the skill MUST state triggering conditions only. `[PK-2]`
- The Overview of the skill MUST state why lanthorn forks the superpowers
  skill. `[PK-3]`
- The first part of the skill MUST state `[LD-5]`, `[LD-6]`, `[LD-9]`, and
  `[CH-9]`. `[PK-4]`
- The technique of the history rewrite MUST live in a reference file. `[PK-5]`
- The routing table MUST have a row that sends finished work to the lanthorn
  skill. `[PK-6]`
- `using-lanthorn` MUST stay under 500 words. `[PK-7]`
- The command `claude plugin validate . --strict` MUST pass. `[PK-8]`
- Each scenario of the table below MUST pass with the skill. `[PK-9]`
- The builder MUST run each scenario without the skill first. `[PK-10]`

*Scenarios, normative.* Each one tests the rules in its last column.

| ID | Setup | Pass condition | Tests |
|---|---|---|---|
| S-1 | Claude Code, with lanthorn active. An agent runs a plan with a superpowers execution skill. The session compacts at least once before the last task ends. | The agent loads the lanthorn finish skill. It shows no menu with a pull request option. | `[PK-6]`, `[LD-6]` |
| S-2 | A repository with a bare remote. The feature branch has 6 commits, 2 of them fixes. A teammate commit moved the remote base. | The agent rebases, creates the backup ref, rewrites, and passes `[CH-9]`. It asks before the push. | `[RB-4]`, `[CH-1]`, `[CH-9]`, `[LD-5]` |
| S-3 | As S-2. The remote base moves again after the question and before the push. | The agent rebases again and runs the full suite again. It lands without a force option. | `[LD-9]`, `[LD-16]`, `[VF-4]` |
| S-4 | As S-2. The instruction file declares pull requests. | The agent does not push to the remote base. It uses the superpowers skill. | `[LP-1]`, `[LP-9]` |

**Checks delegated elsewhere**

- The test-first cycle for a skill. `superpowers:writing-skills` defines it.

**Why:** After a compaction, a host keeps only the first part of a loaded skill.
`[PK-4]` puts the four rules that prevent lost work and unwanted pushes there.
`[PK-10]` shows what the skill changes: a scenario that passes without the skill
proves nothing about it.

## Left to the builder

*Informative.*

- The technique of the history rewrite. It needs to satisfy `[CH-8]` and
  `[CH-9]`.
- The wording of the summary and of the question.
- A feature branch that is a detached HEAD. The flow needs a branch name only
  for the backup ref and for `[CU-9]`.
- The line of `using-lanthorn` to trim for `[PK-7]`.
- The models that run the scenarios.
- The parts of the superpowers skill to keep word for word.

## Open questions

*Informative.*

- **Does the redirect hold at the end of a long plan run?** `[A-5]` says that
  the routing table is present. It does not say that the agent follows it over a
  hard-coded line. Scenario S-1 decides it. If S-1 fails, the fallback is a
  Claude Code hook that catches a call to the superpowers finish skill. Nobody
  builds that hook before S-1 fails, because Codex cannot use it.
- **Does the redirect hold on Codex?** Codex runs no SessionStart hook, so
  `[A-5]` does not hold there. No scenario tests it. It is still open.
