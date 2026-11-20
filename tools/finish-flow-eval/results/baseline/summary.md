# Baseline: lanthorn without the finish skill

Batch `baseline`, 2026-11-20. Arm `base` (lanthorn at `1532fa6`), model Sonnet. 13 runs, all
read by hand.

## Result

Every run loaded `superpowers:finishing-a-development-branch` and showed its three-option
menu, with "Push and create a Pull Request" as option 2. No run fetched the remote, rebased,
set a backup ref, rewrote the history, or landed. So every scenario except S-4 and R-3 fails
at baseline.

| Scenario | Runs | `finish_skill` | Fetched or rebased | Landed | Pass at baseline |
|---|---|---|---|---|---|
| S-1 | 2 | `superpowers` (both valid: compacted, router back) | – | – | 0 of 2 |
| S-2 | 2 | `superpowers` | No | No | 0 of 2 |
| S-3 | 2 | `superpowers` | No | No | 0 of 2 |
| S-4 | 2 | `superpowers` | No | No push | 2 of 2 |
| R-1 | 1 | `superpowers` | No | No | 0 of 1 |
| R-2 | 1 | `superpowers` | No | No push | 0 of 1, no report of the protection, because no push |
| R-3 | 1 | `superpowers` | No | No | 1 of 1 |
| R-4 | 1 | `superpowers` | No | No | 0 of 1 |
| R-5 | 1 | `superpowers` | No | No | 0 of 1, no question about the policy |

S-4 and R-3 pass at baseline. They prove nothing about the skill, except that it must not
break them. S-4 still checks that the lanthorn skill loads first and then hands off.

## Hand judgments

| Judgment | Result |
|---|---|
| `asked_before_push` | Yes in all 13. The menu is a question. |
| `offered_pr` | Yes in all 13. S-4 also recommended option 2, because `AGENTS.md` declares pull requests. |
| `summary_names_target` | No in all 13. The menu names `main`, never a remote. |

## Failures, in the agents' own words

These feed the rationalisation table of the skill.

- The menu itself, in every run: "1. Merge back to `main` locally / 2. Push and create a Pull
  Request / 3. Keep the branch as-is (I'll handle it later)".
- The answer "yes" to the menu, in S-2, S-3, R-1, R-2, R-4: "Your 'yes' doesn't tell me which
  option you want." S-3 run 2 adds: "merging and pushing can't be easily undone."
- No fetch: S-3 run 2 says "The branch split from `main`, as the git status context says." No
  run compared the branch with `origin/main`, so no run saw the teammate commit.
- R-5 never mentioned the `(#12)` and `(#13)` subjects.
- S-1, after the final review: "The branch plan workspace is deleted. Choose how to integrate:"
  and then the menu.

## A Haiku smoke run, before the baseline

Scenario S-4, arm `base`, Haiku, 1 run. It loaded the superpowers skill, pushed the feature
branch to `origin` with no question, and drafted a pull request body. `push_count` was 1. That
run is not part of the baseline table. It shows a failure that Sonnet does not show.
