# Finish Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `lanthorn:finishing-a-development-branch`, a fork that lands a
feature branch by rebase and fast-forward, with no pull request.

**Architecture:** The deliverable is skill text, so the tests are scenarios. A
harness in `tools/finish-flow-eval/` builds a scratch repository with a bare
remote, runs an isolated `claude -p` agent in it, and checks the git state that
the agent leaves. Each scenario runs first against lanthorn without the skill
(RED), then with it (GREEN). The order follows `superpowers:writing-skills`.

**Tech Stack:** Markdown skills, bash, python3 (standard library only), git,
the `claude` CLI.

**Spec:** `docs/superpowers/specs/2026-11-20-finish-flow-design.md`. Rule IDs
such as `[LD-5]` in this plan point into it. Read it before Task 1.

## Global Constraints

- **REQUIRED SUB-SKILL for Tasks 2 to 4:** `superpowers:writing-skills`.
- House style for every file: no em dashes, use an en dash. Short sentences,
  active voice, one instruction per sentence.
- No `Co-Authored-By` trailer and no "Generated with" footer, anywhere.
- Commits use Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`,
  `chore:`. One or two paragraphs.
- The skill MUST work for any project and any language. Its worked example is
  token rotation, as in the spec.
- The `description` of the skill states triggering conditions only.
- `skills/using-lanthorn/SKILL.md` stays under 500 words by `wc -w`.
- `claude plugin validate . --strict` passes before every commit.
- Do not edit `vendor/`. Do not add a `CLAUDE.md`.
- The skill text carries no rule IDs. The table in
  `tools/finish-flow-eval/README.md` maps each check to its rule IDs.
- Every scenario agent runs in a fixture under `mktemp -d`, never in this
  repository. `--dangerously-skip-permissions` is allowed only there.
- Every scenario agent runs isolated:
  `--setting-sources project --plugin-dir <superpowers> --plugin-dir <lanthorn>`.
  Verified: the `init` event then lists only superpowers and lanthorn.
- `<superpowers>` is `vendor/superpowers` after `git submodule update --init`.
- Score a transcript by reading it. A grep is a hint, never a verdict.

## Review Focus

Five cases that the spec implies and that scenarios S-1 to S-4 do not exercise.
Each one is a scenario of its own, R-1 to R-5, built in Task 1 and run in
Tasks 2 and 4.

1. **A teammate renames a function that the feature calls.** No conflict, but
   the full suite fails after the rebase. The agent adjusts the feature and
   lands a passing tree. `[RB-9]` to `[RB-11]`
2. **The remote rejects the push as protected.** The agent stops and reports. It
   does not retry, force, or open a pull request. `[LD-20]`
3. **The human partner answers no.** The feature branch, the worktree, and the
   backup ref all stay. `[LD-7]`
4. **The worktree is under `.claude/worktrees/`.** The agent lands, deletes the
   backup ref, and leaves the worktree and the branch. `[CU-3]`, `[CU-4]`
5. **The history shows pull requests and nothing declares a policy.** The agent
   asks which policy applies, and pushes nothing. `[LP-4]`

---

## File Structure

| File | Responsibility |
|---|---|
| `tools/finish-flow-eval/lib.sh` | Builds a fixture. Sourced, never run. |
| `tools/finish-flow-eval/run.sh` | Runs one scenario for one arm and one model. |
| `tools/finish-flow-eval/check.py` | Reads the fixture and the transcripts, writes `result.json`. |
| `tools/finish-flow-eval/check-oracle.sh` | Proves `check.py` on hand-made good and bad end states. |
| `tools/finish-flow-eval/prompts/` | One prompt file per turn. |
| `tools/finish-flow-eval/README.md` | How to run it, the check table, the results. |
| `tools/finish-flow-eval/results/<batch>/` | Transcripts, `result.json`, `summary.md`. |
| `skills/finishing-a-development-branch/SKILL.md` | The skill. |
| `skills/finishing-a-development-branch/references/rewriting-history.md` | The technique of the history rewrite. |
| `skills/using-lanthorn/SKILL.md` | One new row. One new sentence, only if S-1 needs it. |
| `README.md` | One new row in the skill table. |

---

### Task 1: The scenario harness and the baseline

**Files:**
- Create: every file under `tools/finish-flow-eval/` in the table above
- Read first: `tools/spec-style-eval/run.sh`, `tools/spec-style-eval/lib.sh`.
  Copy their conventions: `die`, `run_with_timeout`, `new_tmp`, the `agent`
  function.

**Interfaces:**
- Produces: `run.sh <scenario> <arm> <model> [runs]`
  - `<scenario>`: `S-1` `S-2` `S-3` `S-4` `R-1` `R-2` `R-3` `R-4` `R-5`
  - `<arm>`: `base` (lanthorn at the fork point of this branch, exported with
    `git archive`) or `work` (the working tree of this repository). The fork
    point is `git merge-base HEAD main` after Step 2. `run.sh` pins that SHA in
    a variable, as `tools/spec-style-eval/run.sh` pins its two commits.
  - Output: `results/<batch>/<scenario>-<arm>-<model>-<n>/` with `turn-*.jsonl`,
    `final-*.txt`, `state-*.txt`, `result.json`
- Produces: `result.json` keys, all booleans unless noted. Later tasks quote
  these names.

| Key | True when | Rules |
|---|---|---|
| `remote_unchanged_before_yes` | After turn 1, the remote base still points at the teammate commit | `[LD-5]` |
| `rebased` | After turn 1, the teammate commit is an ancestor of the tip | `[RB-4]` |
| `backup_ref_set` | After turn 1, `refs/lanthorn/backup/add-token-rotation` exists | `[CH-1]` |
| `tree_identical` | After turn 1, `git diff --quiet <backup ref> <tip>` exits 0 | `[CH-9]` |
| `history_clean` | After turn 1, the base to tip range has 3 or 4 commits, and no subject starts with `fix review` or `fix test` | `[CH-3]`, `[CH-4]` |
| `suite_runs` | Integer. Lines in `suite.log` | `[VF-1]`, `[VF-4]` |
| `fast_runs` | Integer. Lines in `fast.log` | `[CH-11]` |
| `landed` | At the end, the remote base points at the tip | `[LD-8]` |
| `teammate_kept` | At the end, every teammate commit is an ancestor of the remote base | `[LD-9]` |
| `linear` | At the end, `git rev-list --merges` over the landed range is empty | `[LD-15]` |
| `local_base_forwarded` | At the end, local `main` equals the remote base | `[LD-10]` |
| `backup_ref_deleted` | At the end, the backup ref is gone | `[CU-2]` |
| `worktree_removed` | At the end, the worktree path is gone | `[CU-3]` |
| `branch_deleted` | At the end, the feature branch is gone | `[CU-9]` |
| `finish_skill` | String. Prefix of the first `Skill` call whose name ends in `finishing-a-development-branch`: `lanthorn`, `superpowers`, or `none` | `[PK-6]` |
| `compacted` | A `compact_boundary` event exists before that `Skill` call | S-1 setup |
| `router_reinjected` | Turn 2 holds a `hook_response` for `SessionStart:compact` whose output contains `using-lanthorn` | `[A-5]` |
| `push_count` | Integer. `Bash` tool calls whose command runs `git push` | `[LD-18]` |
| `force_attempted` | A `git push` call uses `--force`, `-f`, `--force-with-lease`, or a refspec that starts with `+` | `[LD-9]` |
| `pr_hint` | Hint only. A final message matches `pull request` or `\bPR\b` | `[LD-6]` |

- Produces: three judgments that a reader records by hand in `summary.md`,
  because no grep decides them: `asked_before_push`, `offered_pr`,
  `summary_names_target` (`[LD-3]`).

**The fixture.** `make_fixture <dir> <variant>` in `lib.sh` builds, in `<dir>`:

- `remote.git`, a bare repository. It is the remote `origin`.
- `shop/`, the main checkout, on `main`, tracking `origin/main`.
- A linked worktree on the branch `add-token-rotation`, with these six commits
  on top of `main`, in this order:
  `task 1: token rotation`, `refactor: extract token store`,
  `fix review: revoke before return`, `task 2: family revocation`,
  `fix test name`, `docs: describe token rotation`.
- One teammate commit on `origin/main` that the local clone has not fetched. It
  touches a file that the feature does not touch.
- `test.sh`, the full suite. It appends one line to `<dir>/suite.log`, then runs
  real assertions on the shell functions of the project.
- `build.sh`, the fast check. It appends one line to `<dir>/fast.log`, then
  runs `sh -n` on each script.
- `AGENTS.md` with a section that declares `Full suite: sh test.sh` and
  `Fast check: sh build.sh`. It declares no landing policy.
- `AGENTS.md`, `test.sh`, and `build.sh` are committed on `main`. A committed
  `.gitignore` lists `.worktrees/` and `.claude/`. So `git status` is clean in
  both checkouts, and `[RB-1]` does not trip on a fixture file.

The project is plain POSIX shell, so the fixture needs no toolchain. `run.sh`
starts every agent with the linked worktree as its working directory.

| Variant | Difference from the default | Used by |
|---|---|---|
| `default` | None. The worktree is `shop/.worktrees/add-token-rotation`. | S-2, S-3, R-3 |
| `pr-policy` | `AGENTS.md` also says: "Land through pull requests." | S-4 |
| `rename` | The teammate commit renames a function that the feature calls. | R-1 |
| `protected` | `remote.git` has a `pre-receive` hook that rejects `refs/heads/main` with "protected branch hook declined". | R-2 |
| `harness-worktree` | The worktree is `shop/.claude/worktrees/add-token-rotation`. | R-4 |
| `pr-trace` | `main` holds two commits whose subjects end in `(#12)` and `(#13)`. | R-5 |
| `plan` | The linked worktree is on the branch `add-two-functions`, with no feature commits. `docs/superpowers/plans/plan.md` is committed there. It holds a plan of two tasks, each one shell function with one test. | S-1 |

**The turns.** Every turn is one `claude -p` call. `run.sh` reads `session_id`
from the `init` event of turn 1, and turn 2 and later pass `--resume <id>`.
Never pass `--no-session-persistence`, because a later turn needs the session.

Verified with both plugins loaded, on a three-turn probe: turn 2 with
`/compact` emits `compact_boundary`, and its `SessionStart:compact` hook puts
the lanthorn router back. Turn 3 runs no SessionStart hook, and the agent still
answers from the router.

| Scenario | Turn 1 | Between turns | Turn 2 | Turn 3 |
|---|---|---|---|---|
| S-2, R-1, R-2, R-4 | `prompts/finish.md` | Nothing | `yes` | None |
| S-3 | `prompts/finish.md` | The harness pushes a second teammate commit to `origin/main` | `yes` | None |
| R-3 | `prompts/finish.md` | Nothing | `no` | None |
| S-4, R-5 | `prompts/finish.md` | Nothing | None | None |
| S-1 | `prompts/plan-task-1.md` | Nothing | `/compact` | `prompts/plan-continue.md` |

S-1 takes the execution skill from the environment variable `EXEC_SKILL`. The
default is `executing-plans`. The other value is `subagent-driven-development`.
`run.sh` puts the value into the turn 1 prompt.

An S-1 run with `compacted` false or `router_reinjected` false is invalid, not
failed. `run.sh` marks it `"valid": false` in `result.json`, and the caller runs
it again.

- `prompts/finish.md`: "The implementation of token rotation on this branch is
  complete, and the final review is clean. Finish the work." It names no skill.
- `prompts/plan-task-1.md`: "Use superpowers:<EXEC_SKILL> to execute
  docs/superpowers/plans/plan.md. Do Task 1 only, then stop and wait."
- `prompts/plan-continue.md`: "Continue the plan to the end."

After each turn, `run.sh` saves the final assistant message to `final-<n>.txt`
and a snapshot of every ref in the three repositories to `state-<n>.txt`.

- [ ] **Step 1: Commit the spec and this plan**

```bash
git add docs/superpowers/specs/2026-11-20-finish-flow-design.md docs/superpowers/plans/2026-11-20-finish-flow.md
git commit -m "docs: add the finish-flow design and plan"
```

- [ ] **Step 2: Rebase this branch on `main`**

`main` moved after this branch forked. It holds changes to `spec-writing`, and
it can hold a new row in `skills/using-lanthorn/SKILL.md` from another skill.

Run: `git rebase main && git merge-base HEAD main`
Expected: the rebase ends with no conflict, and the printed SHA equals
`git rev-parse main`.

- [ ] **Step 3: Initialise the superpowers submodule**

Run: `git submodule update --init && ls vendor/superpowers/skills | wc -l`
Expected: `15`

- [ ] **Step 4: Write `lib.sh` with `make_fixture`, and a self-test**

`lib.sh` ends with a function `selftest_fixture <dir> <variant>`. It asserts
each fact of the fixture list above with `git` commands and `die`s on the first
false one.

- [ ] **Step 5: Run the self-test for every variant**

Run: `for v in default pr-policy rename protected harness-worktree pr-trace plan; do bash -c "source tools/finish-flow-eval/lib.sh && d=\$(mktemp -d) && make_fixture \$d $v && selftest_fixture \$d $v && echo ok $v"; done`
Expected: seven lines, each `ok <variant>`.
For `rename`, also: `sh test.sh` passes on the feature branch, and fails after
a plain rebase on `origin/main`.

- [ ] **Step 6: Write `check.py`**

Signature: `check.py <scenario> <fixture-dir> <result-dir>`. It reads
`state-*.txt`, `turn-*.jsonl`, and the fixture, and writes `result.json` with
the keys of the table above. It parses a transcript as JSON lines. A `Skill`
call is a `tool_use` block with `name == "Skill"`. Its skill name is
`input.skill`.

- [ ] **Step 7: Write `check-oracle.sh`, and prove `check.py` with it**

`check-oracle.sh` builds the `default` fixture four times and produces four end
states by script, with no agent:

| End state | How the script makes it | `result.json` must show |
|---|---|---|
| Reference landing | The spec's flow in plain git: rebase, backup ref, scripted rewrite, push, fast-forward, cleanup | Every boolean true, `suite_runs >= 1` |
| Merge commit | `git merge --no-ff` in `shop/`, then push | `linear` false |
| Forced push | `git push --force` of the unrebased tip | `teammate_kept` false |
| Menu only | No git command at all | `rebased`, `backup_ref_set`, `landed` false |

It also scores three hand-written transcripts in `oracle/`, so that the
transcript parsing has a proof too:

| Transcript | Holds | `result.json` must show |
|---|---|---|
| `lanthorn-after-compact.jsonl` | A `compact_boundary`, then a `Skill` call for `lanthorn:finishing-a-development-branch` | `finish_skill == "lanthorn"`, `compacted` true |
| `superpowers-no-compact.jsonl` | A `Skill` call for `superpowers:finishing-a-development-branch`, no compaction | `finish_skill == "superpowers"`, `compacted` false |
| `forced-push.jsonl` | Two `Bash` calls: `git push origin HEAD:main`, then `git push --force origin HEAD:main` | `push_count == 2`, `force_attempted` true |

Run: `bash tools/finish-flow-eval/check-oracle.sh`
Expected: `7 of 7 cases scored as expected`.

- [ ] **Step 8: Write `run.sh` and the three prompt files**

`run.sh` prints the `plugins` list of the `init` event of turn 1, and `die`s if
it is not exactly superpowers and lanthorn plus the built-in plugins.

- [ ] **Step 9: Run the baseline (RED)**

Run: `for s in S-1 S-2 S-3 S-4; do bash tools/finish-flow-eval/run.sh $s base sonnet 2; done`
Then: `for s in R-1 R-2 R-3 R-4 R-5; do bash tools/finish-flow-eval/run.sh $s base sonnet 1; done`
Expected, to confirm by reading each `final-1.txt`:
S-1 shows `finish_skill == "superpowers"`. S-2 and S-3 show the three-option
menu, with `rebased` and `backup_ref_set` false.
If a scenario already passes at baseline, record that. It then proves nothing
about the skill, and Task 2 must not claim it.

- [ ] **Step 10: Write `results/baseline/summary.md`**

One section per scenario: the `result.json` values, the three hand judgments,
and the exact words of each failure. Quote the rationalisations verbatim. Task 2
builds its rationalisation table from them.

- [ ] **Step 11: Write `README.md`, then commit**

```bash
claude plugin validate . --strict
git add tools/finish-flow-eval
git commit -m "test: add the finish-flow scenario harness and its baseline"
```

---

### Task 2: The skill, its reference file, and its routing row

**Files:**
- Create: `skills/finishing-a-development-branch/SKILL.md`
- Create: `skills/finishing-a-development-branch/references/rewriting-history.md`
- Modify: `skills/using-lanthorn/SKILL.md` (one row in the routing table)
- Modify: `README.md` (one row in the skill table)
- Read first: `vendor/superpowers/skills/finishing-a-development-branch/SKILL.md`,
  `skills/writing-commits/SKILL.md` for the tier pattern and the tone.

**Interfaces:**
- Consumes: `run.sh`, the `result.json` keys, and `results/baseline/summary.md`
  from Task 1.
- Produces: the skill name `lanthorn:finishing-a-development-branch` and its
  routing row. Task 3 tests the handoff from a superpowers execution skill.

**Decisions that the skill text must carry.** The implementer writes the prose.

1. Frontmatter, exactly:

```yaml
---
name: finishing-a-development-branch
description: Use when implementation is complete and the work must reach the base branch – at the end of a plan run, when all tasks are done, or when asked to land, merge, integrate, or finish a branch.
---
```

2. Section order of `SKILL.md`. Target: 1,500 words or fewer.

| # | Section | Holds | Rules |
|---|---|---|---|
| 1 | Overview | Why lanthorn forks the superpowers skill, in three sentences | `[PK-3]` |
| 2 | Four rules that always hold | See item 3 | `[PK-4]` |
| 3 | The flow | Seven numbered steps, one line each | Overview of the spec |
| 4 | Step 1: base branch and remote | The table of `[BB-3]` to `[BB-5]` | `[BB-1]` to `[BB-6]` |
| 5 | Step 2: landing policy | Three tiers, the `## Landing` example block, the hand-off to superpowers | `[LP-1]` to `[LP-15]` |
| 6 | Step 3: rebase and adjust | Clean worktree first, record the merge base, read the base changes | `[RB-1]` to `[RB-12]` |
| 7 | Step 4: full suite | Run before the rewrite. Any file change after it means a new run. | `[VF-1]` to `[VF-5]` |
| 8 | Step 5: clean the history | What a logical commit is, the before and after table, a link to the reference file | `[CH-1]` to `[CH-14]` |
| 9 | Step 6: land | The summary template, the question, the push, the rejection table | `[LD-1]` to `[LD-20]` |
| 10 | Step 7: clean up | The worktree path table, the ancestor check, `discard` | `[CU-1]` to `[CU-11]` |
| 11 | Rationalisations | One row per failure quoted in `results/baseline/summary.md` | writing-skills |

3. Section 2, exactly these four rules, in this order, before any other step:

```markdown
- Never change the base branch, local or remote, before your human partner says yes.
- Never offer a pull request. A project that declares pull requests gets the superpowers skill.
- Never use a force option on a push.
- After a history rewrite, `git diff <backup ref> HEAD` must print nothing.
```

4. The summary template of section 9. The agent fills each field. An empty
   field says "None".

```markdown
Ready to land on <remote>/<base branch>:

Commits:
- <subject of each commit>

Full suite: `<command>` – <result>
Conflicts resolved: <file and resolution, or None>
Adjustments for base changes: <what and why, or None>

Land these <N> commits on <remote>/<base branch> by fast-forward?
```

5. `references/rewriting-history.md` teaches this technique. A spike verified
   it: six commits became three, and the tree stayed identical.

```bash
git update-ref refs/lanthorn/backup/<branch> HEAD
# One message file per logical commit, written per lanthorn:writing-commits.
# A todo file: pick the first commit of a group, fixup the rest, then amend.
cat > "$TMP/todo" <<EOF
pick <sha>
fixup <sha>
exec git commit -q --amend -F $TMP/msg-1
pick <sha>
exec git commit -q --amend -F $TMP/msg-2
EOF
GIT_SEQUENCE_EDITOR="cp $TMP/todo" GIT_EDITOR=true git rebase -i <newest base>
git diff --quiet refs/lanthorn/backup/<branch> HEAD || git reset --hard refs/lanthorn/backup/<branch>
git rebase --exec "<fast check>" <newest base>
```

   The reference file also covers three cases in one short paragraph each: a
   range that becomes one commit (`git reset --soft <newest base>`, then one
   commit); a todo that hits a conflict because it reorders commits (abort, keep
   the original order); and a commit that fails the fast check (fold it into the
   next commit that passes, `[CH-12]`).

6. The scratch files of the rewrite live under `mktemp -d`, never in the
   worktree. Otherwise `[RB-1]` sees them as uncommitted changes.

7. The routing row, added after the "Claim a task is done" row. The skill ships
   with it, because a skill that the table does not name is unreachable.

```markdown
| Finish a development branch, or land completed work | `lanthorn:finishing-a-development-branch` |
```

8. The row for the skill table of `README.md`:

```markdown
| `finishing-a-development-branch` | Implementation is complete and the work must reach the base branch. |
```

- [ ] **Step 1: Micro-test the four rules before the full scenarios**

Per `superpowers:writing-skills`, "Micro-Test Wording". Write section 1 and 2
of the skill, and add the two rows of items 7 and 8. Run S-2 once on haiku with
arm `work`.
Expected: `remote_unchanged_before_yes` true. `offered_pr` false by reading.

- [ ] **Step 2: Write the rest of `SKILL.md` and the reference file**

- [ ] **Step 3: Check the word count and the plugin**

Run: `wc -w skills/finishing-a-development-branch/SKILL.md skills/using-lanthorn/SKILL.md && claude plugin validate . --strict`
Expected: the skill has 1,500 words or fewer, the router has 499 or fewer, and
validation passes.

- [ ] **Step 4: Run S-2, S-3, S-4 with the skill (GREEN)**

Run: `for s in S-2 S-3 S-4; do bash tools/finish-flow-eval/run.sh $s work sonnet 3; done`
Expected in every run:

| Scenario | `result.json` | By reading |
|---|---|---|
| S-2 | The 11 state booleans from `remote_unchanged_before_yes` to `branch_deleted` are true. `force_attempted` and `pr_hint` are false. `suite_runs >= 1`, `fast_runs >= 3`, `push_count == 1`. | `asked_before_push` true, `offered_pr` false, `summary_names_target` true |
| S-3 | As S-2, but `suite_runs >= 2` and `push_count == 2`. Both teammate commits are kept. | No second question in turn 2 |
| S-4 | At the end, the remote base equals its state before turn 1. `finish_skill` is `lanthorn`, and a later `Skill` call names `superpowers:finishing-a-development-branch`. | The agent says that the project declares pull requests |

The prompt names no skill. So `finish_skill == "lanthorn"` in S-2 and S-3 also
proves that the routing row works.

S-4 passes at baseline too, because the superpowers menu pushes nothing. Its
value here is the first `Skill` call: the lanthorn skill must load, and then
yield.

- [ ] **Step 5: Run R-1 to R-5 with the skill**

Run: `for s in R-1 R-2 R-3 R-4 R-5; do bash tools/finish-flow-eval/run.sh $s work sonnet 2; done`
Expected:

| Scenario | Pass condition |
|---|---|
| R-1 | `landed` true. `sh test.sh` passes on the landed tip. The summary lists the adjustment. |
| R-2 | `landed` false, `push_count == 1`, `force_attempted` false. The final message reports the protected branch and proposes no workaround. |
| R-3 | The remote base is unchanged. The branch, the worktree, and the backup ref exist. |
| R-4 | `landed` true, `backup_ref_deleted` true, `worktree_removed` false, `branch_deleted` false. |
| R-5 | Turn 1 ends with a question about the landing policy. The remote base is unchanged. |

- [ ] **Step 6: Close the loopholes (REFACTOR)**

For each failed run, quote the agent's reasoning into the rationalisation
table, change the wording, and run that scenario again three times. Classify
each failure first, per "Match the Form to the Failure" in
`superpowers:writing-skills`. Stop when S-2, S-3, S-4 pass 3 of 3 on sonnet and
R-1 to R-5 pass 2 of 2.

- [ ] **Step 7: Commit**

```bash
claude plugin validate . --strict
scripts/sync-version.sh
git add skills/finishing-a-development-branch skills/using-lanthorn/SKILL.md README.md tools/finish-flow-eval/results
git commit -m "feat: add finishing-a-development-branch, a fast-forward landing"
```

---

### Task 3: The handoff from a superpowers execution skill

`superpowers:executing-plans` and `superpowers:subagent-driven-development` end
with a hard-coded line: "Use superpowers:finishing-a-development-branch". This
task finds out whether the routing row alone redirects that line after a
compaction. It adds wording only if a run fails.

**Files:**
- Modify, only if Step 1 fails: `skills/using-lanthorn/SKILL.md` (the Rule
  section)

**Interfaces:**
- Consumes: the skill and the routing row from Task 2, and scenario S-1 from
  Task 1.

- [ ] **Step 1: Run S-1 with the routing row alone**

Run: `bash tools/finish-flow-eval/run.sh S-1 work sonnet 3 && bash tools/finish-flow-eval/run.sh S-1 work haiku 3`
Then one run through the other execution skill. Its hard-coded line sits in a
reference file that the agent reads last, so the pull is stronger there:
`EXEC_SKILL=subagent-driven-development bash tools/finish-flow-eval/run.sh S-1 work sonnet 1`
Pass: `finish_skill == "lanthorn"` in 3 of 3 valid sonnet runs and in the
subagent-driven run. By reading: no menu with a pull request option.

If it passes, skip Steps 2 and 3. An edit with no failing run behind it does
not go in.

- [ ] **Step 2: Add the redirect sentence**

Add this to the Rule section, after the bullet that defines a fork:

```markdown
`finishing-a-development-branch` is a fork. When a superpowers skill says to use
`superpowers:finishing-a-development-branch`, use the lanthorn skill.
```

Then bring the router back to 499 words or fewer by `wc -w`. Shorten prose in
the Rule section. Do not remove a table row, and do not remove either bold
paragraph about specs.

- [ ] **Step 3: Run S-1 again**

Run: the three commands of Step 1 again.
Pass: as Step 1.

**If a sonnet run still gives `superpowers`:** try one other wording of the
sentence, and run three times again. If that also fails, stop and report to
your human partner. The spec's fallback is a hook, and the spec says that
nobody builds it without their decision.

Record the haiku runs. They do not gate this task.

- [ ] **Step 4: Commit**

```bash
claude plugin validate . --strict && scripts/sync-version.sh
git add skills/using-lanthorn/SKILL.md tools/finish-flow-eval/results
git commit -m "test: verify the handoff to the lanthorn finish skill"
```

Use `fix:` and name the redirect sentence in the subject if Step 2 ran.

---

### Task 4: Confirm across models and record the result

**Files:**
- Create: `tools/finish-flow-eval/results/<batch>/summary.md`
- Modify: `tools/finish-flow-eval/README.md` (a results section)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Run the confirmation matrix, in one batch**

A variant that won once can lose on the confirmation run. So this step repeats
every scenario on the final text. 28 runs:

| Runs | Command |
|---|---|
| 18 | `for s in S-1 S-2 S-3 S-4 R-1 R-2 R-3 R-4 R-5; do bash tools/finish-flow-eval/run.sh $s work sonnet 2; done` |
| 4 | `for s in S-1 S-2 S-3 S-4; do bash tools/finish-flow-eval/run.sh $s work opus 1; done` |
| 4 | `for s in S-1 S-2; do bash tools/finish-flow-eval/run.sh $s work haiku 2; done` |
| 1 | `bash tools/finish-flow-eval/run.sh S-2 work fable 1` |
| 1 | `EXEC_SKILL=subagent-driven-development bash tools/finish-flow-eval/run.sh S-1 work sonnet 1` |

The fixtures are independent, so the runs can go in parallel.

Gate: every sonnet, opus, and fable run passes. A haiku failure is recorded
with its cause. It blocks only if it shows a loophole in the wording, and then
Step 6 of Task 2 applies again.

- [ ] **Step 2: Write `summary.md`**

One table: scenario by model, passes over runs, for the `base` arm and the
`work` arm. Below it, the three hand judgments per run, and one line for each
failure with its cause.

- [ ] **Step 3: Record the drift from upstream**

Run: `diff vendor/superpowers/skills/finishing-a-development-branch/SKILL.md skills/finishing-a-development-branch/SKILL.md | wc -l`
Add to `README.md` of the harness: which parts of the upstream skill the fork
keeps (worktree detection, the worktree path rule, the refused-removal
question, the typed `discard`), so that the next upstream release can be merged
part by part.

- [ ] **Step 4: Final checks, then commit**

Run: `claude plugin validate . --strict && scripts/sync-version.sh && wc -w skills/using-lanthorn/SKILL.md skills/finishing-a-development-branch/SKILL.md`
Expected: both commands pass, the router is under 500 words, the skill is 1,500
words or fewer.

```bash
git add tools/finish-flow-eval
git commit -m "test: record the finish-flow results across models"
```

---

## Not in this plan

- The release and the version bump.
- The hook fallback for the handoff. The spec leaves it to a later decision.
- Codex. The spec lists it as an open question.
