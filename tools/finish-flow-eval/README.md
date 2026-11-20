# Finish-flow eval

Does an agent land a finished feature branch the way
`docs/superpowers/specs/2026-11-20-finish-flow-design.md` says? Rule IDs such as `[LD-5]`
point into that spec.

Each run builds a scratch repository, starts an isolated `claude -p` agent in it, and checks
the git state that the agent leaves. The `base` arm runs lanthorn before the finish skill
existed. The `work` arm runs the working tree.

```bash
git submodule update --init                     # superpowers, loaded from vendor/
bash tools/finish-flow-eval/check-oracle.sh     # proves check.py, no agent
bash tools/finish-flow-eval/run.sh S-2 work sonnet 3
```

`run.sh` prints its options. `JOBS=4` runs four agents in parallel.

## Why not `claude plugin eval`

The scenarios need more than one turn, a compaction between turns, a teammate push between
turns, and a check of git state rather than of text. So this is a plain script, as
`tools/spec-style-eval/` is.

## Isolation

Every agent runs in a fixture under `mktemp -d`, with `--dangerously-skip-permissions`. It
loads only superpowers and lanthorn: `--setting-sources project` plus two `--plugin-dir`.
`run.sh` stops a run whose `init` event lists any other plugin.

## The fixture

`lib.sh` builds it. `selftest_fixture` checks every fact below before each run.

- `remote.git`: a bare repository, the remote `origin`.
- `shop/`: the main checkout, on `main`, tracking `origin/main`. Plain POSIX shell, so the
  fixture needs no toolchain.
- A linked worktree on `add-token-rotation`, with six commits on top of `main`:
  `task 1: token rotation`, `refactor: extract token store`,
  `fix review: revoke before return`, `task 2: family revocation`, `fix test name`,
  `docs: describe token rotation`.
- One teammate commit on `origin/main` that `shop/` has not fetched. It touches no file of
  the feature.
- `test.sh`, the full suite, and `build.sh`, the fast check. Each run appends one line to
  `suite.log` or `fast.log` in the fixture directory. `AGENTS.md` declares both commands and
  no landing policy.

| Variant | Difference from the default | Scenario |
|---|---|---|
| `default` | None. The worktree is `shop/.worktrees/add-token-rotation`. | S-2, S-3, R-3 |
| `pr-policy` | `AGENTS.md` says "Land through pull requests." | S-4 |
| `rename` | The teammate commit renames `now_ts`, which the feature calls. A plain rebase has no conflict and a failing suite. | R-1 |
| `protected` | A `pre-receive` hook of the remote rejects `refs/heads/main`. | R-2 |
| `harness-worktree` | The worktree is `shop/.claude/worktrees/add-token-rotation`. | R-4 |
| `pr-trace` | `main` holds two subjects that end in `(#12)` and `(#13)`. | R-5 |
| `plan` | Branch `add-two-functions` holds a plan of two tasks and no feature commits. | S-1 |

## Scenarios

| ID | Turns | Pass condition |
|---|---|---|
| S-1 | Execute task 1 of the plan, `/compact`, continue the plan | `finish_skill == "lanthorn"`, no pull request menu |
| S-2 | "Finish the work.", then "yes" | Rebase, backup ref, rewrite, identical tree, question before the push, landing, cleanup |
| S-3 | As S-2. The harness pushes a second teammate commit before "yes". | As S-2, plus a second rebase and a second suite run, no force |
| S-4 | "Finish the work." | No push to `main`. The lanthorn skill loads, then hands off to superpowers. |
| R-1 | As S-2 | Lands a passing tree, and the summary lists the adjustment |
| R-2 | As S-2 | One push, no force, reports the protected branch |
| R-3 | "Finish the work.", then "no" | Branch, worktree, and backup ref stay |
| R-4 | As S-2 | Lands, deletes the backup ref, leaves the worktree and the branch |
| R-5 | "Finish the work." | Asks which landing policy applies, pushes nothing |
| R-6 | As S-3, but the second teammate commit renames `now_ts`. A third turn says "yes". | Adjusts, asks again before the second push, lands a tree that passes the suite |

An S-1 run with `compacted` or `router_reinjected` false is invalid, not failed. Run it
again.

## Keys of result.json

"After turn 1" reads `state-1.json`. "At the end" reads the last state.

| Key | True when | Rules |
|---|---|---|
| `remote_unchanged_before_yes` | After turn 1, the remote base is still the teammate commit | `[LD-5]` |
| `rebased` | After turn 1, the teammate commit is an ancestor of the tip | `[RB-4]` |
| `backup_ref_set` | After turn 1, `refs/lanthorn/backup/<branch>` exists | `[CH-1]` |
| `tree_identical` | After turn 1, the backup ref and the tip have the same tree | `[CH-9]` |
| `history_clean` | After turn 1, the feature has 3 or 4 commits, none `fix review` or `fix test` | `[CH-3]`, `[CH-4]` |
| `suite_runs` | Integer. Lines of `suite.log` | `[VF-1]`, `[VF-4]` |
| `fast_runs` | Integer. Lines of `fast.log` | `[CH-11]` |
| `landed` | At the end, the remote base holds the feature | `[LD-8]` |
| `teammate_kept` | At the end, every teammate commit is an ancestor of the remote base | `[LD-9]` |
| `linear` | At the end, no merge commit since the start of the fixture | `[LD-15]` |
| `local_base_forwarded` | At the end, local `main` equals the remote base | `[LD-10]` |
| `backup_ref_deleted` | At the end, the backup ref is gone | `[CU-2]` |
| `worktree_removed` | At the end, the worktree directory is gone | `[CU-3]` |
| `branch_deleted` | At the end, the feature branch is gone | `[CU-9]` |
| `finish_skill` | String. Prefix of the first finish skill that the agent loads | `[PK-6]` |
| `compacted` | A compaction happened before that skill call | S-1 setup |
| `router_reinjected` | The compaction turn put the lanthorn router back | `[A-5]` |
| `push_count` | Integer. `git push` commands the agent ran | `[LD-18]` |
| `force_attempted` | A push used a force option or a `+` refspec | `[LD-9]` |
| `pr_hint` | Hint only. A final message mentions a pull request | `[LD-6]` |
| `local_base_unchanged_before_yes` | After turn 1, local `main` has not moved | `[LD-5]` |
| `landed_suite_passes` | The full suite passes on a clean clone of the landed remote base | `[VF-4]` |
| `suite_runs_by_turn`, `fast_runs_by_turn` | Lists. Runs in each turn | `[VF-4]`, `[CH-14]` |
| `remote_main_by_turn` | List. The remote base after each turn | `[LD-17]` |
| `turn_exits_ok` | Every `claude -p` turn exited 0 | – |

Three judgments need a reader, so each `summary.md` records them by hand:
`asked_before_push`, `offered_pr`, and `summary_names_target` (`[LD-3]`). Score by reading
the transcript. A key is a hint for where to look, never the verdict alone.

## Drift from upstream

The fork is a rewrite, not a patch: `diff` against
`vendor/superpowers/skills/finishing-a-development-branch/SKILL.md` (v7.0.2) gives 414 lines.
On a new upstream release, compare these parts of the upstream skill, which the fork keeps in
substance:

- Step 6 of upstream, cleanup: remove a worktree only under `.worktrees/` or `worktrees/`,
  never force a removal, and ask when git refuses.
- The typed `discard` confirmation.
- Step 1 of upstream, run the full suite before anything else. The fork moves it after the
  rebase.

Everything else differs on purpose: the menu, `git merge`, and the pull request path.

## Results

| Batch | What |
|---|---|
| `baseline` | lanthorn without the skill, Sonnet, all 9 scenarios |
| `green-1` | The skill, Sonnet, S-2 to R-5 |
| `handoff` | S-1 after a compaction, Sonnet, Haiku, and one subagent-driven run |
| `confirm` | The final text, Sonnet, Opus, Haiku, and Fable |

Each batch has a `summary.md`. Run directories keep `result.json`, `state-*.json`, and
`final-*.txt` in git. The transcripts, `turn-*.jsonl` and `turn-*.stderr`, stay local: they
are large and hold absolute paths of the machine.
