# GREEN 1: the finish skill on Sonnet

Batch `green-1`, 2026-11-20. Arm `work`, model Sonnet, 19 runs, all read by hand. The skill
was 1,632 words, the router 485.

## Result

Every scenario passes in every run.

| Scenario | Runs | Pass | Notes |
|---|---|---|---|
| S-2 | 3 | 3 | Fetch, rebase, backup ref, 4 logical commits, identical tree, question, one push, cleanup |
| S-3 | 3 | 3 | First push rejected `(fetch first)`, rebase, suite again, second push. No second question. |
| S-4 | 3 | 3 | `finish_skills`: lanthorn, then superpowers. No push. The superpowers menu follows, as intended. |
| R-1 | 2 | 2 | Both found the `now_ts` rename and added a fix commit, named in "Adjustments for base changes". |
| R-2 | 2 | 2 | One push, no force. Reported the protected branch, kept the branch, worktree, and backup ref. |
| R-3 | 2 | 2 | "no" kept the branch, the worktree, and the backup ref. No push. |
| R-4 | 2 | 2 | Landed, deleted the backup ref, left the worktree under `.claude/worktrees/` and its branch. |
| R-5 | 2 | 2 | Asked "Which policy applies?", quoting the `(#12)` and `(#13)` subjects. No push. |

## Read by hand, not by the keys

- **R-1 run 2:** `history_clean` false, because the scorer allowed at most 4 commits. The fifth
  is the adjustment commit that `[RB-11]` requires. `check.py` now allows 5 for the rename
  variant. The fixture was gone, so this run was not scored again.
- **R-4 run 1:** `tree_identical` and `history_clean` false. After the tree check, the agent
  added `docs: record the commit conventions`, an `AGENTS.md` section, as
  `lanthorn:writing-commits` tells it to do when the history decided the commit style. It ran
  the full suite again and listed the commit in the summary as "your call to keep or drop
  it". Counted as a pass. It shows that `writing-commits` can add a commit to the instruction
  file during a landing.

## Hand judgments

| Judgment | Result |
|---|---|
| `asked_before_push` | Yes in all 14 runs that reached the landing question |
| `offered_pr` | No in all runs of the lanthorn skill. Every "pull request" in a message reports the policy check. |
| `summary_names_target` | Yes. Every landing summary starts "Ready to land on origin/main". |

## Against the baseline

| | Baseline (lanthorn without the skill) | GREEN 1 |
|---|---|---|
| Rebased onto the teammate commit (`rebased`) | 0 of 13 | 14 of 14 runs that reached step 3 |
| Landed in S-2 and S-3 | 0 of 4 | 6 of 6 |
| Offered a pull request | 13 of 13 | 0 of 16 outside S-4 |
