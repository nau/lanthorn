# Confirmation: the final text across models

Batch `confirm`, 2026-11-20. Arm `work`, skill as committed in `1283969` (1,632 words), router
485 words. 28 runs, all read by hand.

## Result

| Scenario | Sonnet | Opus | Haiku | Fable |
|---|---|---|---|---|
| S-1 handoff after compaction | 2 of 2, plus 1 of 1 subagent-driven | 1 of 1 | 2 of 2 | – |
| S-2 land | 2 of 2 | 1 of 1 | 2 of 2 | 1 of 1 |
| S-3 land after a rejected push | 2 of 2 | 1 of 1 | – | – |
| S-4 project declares pull requests | 2 of 2 | 1 of 1 | – | – |
| R-1 teammate renamed a function | 2 of 2 | – | – | – |
| R-2 protected branch | 2 of 2 | – | – | – |
| R-3 answer is no | 2 of 2 | – | – | – |
| R-4 harness worktree | 2 of 2 | – | – | – |
| R-5 history shows pull requests | 2 of 2 | – | – | – |

The gate holds: every Sonnet, Opus, and Fable run passes. Haiku passes too.

In S-1, every run loaded `lanthorn:finishing-a-development-branch`: 6 of 6. None skipped the
finish step this time. Every S-1 run showed the landing summary and pushed nothing.

## Read by hand, not by the keys

- **The commit-style commit.** In 5 runs (S-2 Opus, S-3 Opus, S-2 Fable, R-3 Sonnet run 1,
  R-4 Sonnet run 2), `tree_identical` or `history_clean` is false. In each, the rewrite was
  correct, and then the agent added a fifth commit, `docs: record the commit convention`, an
  `AGENTS.md` section. `lanthorn:writing-commits` asks for that when only the history decided
  the commit style. Each run named the commit in the summary and offered to drop it. Four ran
  the full suite after it. The Fable run made it before its only suite run. Counted as a pass. With GREEN 1 this is 6 of about 31 runs that reached
  a rewrite, about 1 in 5.
- **S-4 Opus pushed once.** It loaded the lanthorn skill, handed off to the superpowers skill as
  `[LP-10]` requires, chose the pull request path because `AGENTS.md` declares it, and pushed
  the feature branch. `main` did not change. This is the upstream flow after the handoff.
- **R-5 run 2** first died in the fixture self-test, before any agent ran. Cause: under
  `set -o pipefail`, `git log | grep -q` fails when `grep` exits before `git log` finishes. The
  self-test now reads into a variable. 40 stress runs passed after the fix, and the rerun
  passed.

## Hand judgments

| Judgment | Result |
|---|---|
| `asked_before_push` | Yes in every run that reached the landing question |
| `offered_pr` | No outside S-4 |
| `summary_names_target` | Yes. Every landing summary names `origin/main`. |
